package com.fiscore.core.services;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.*;
import com.fiscore.core.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requisitos del esquema de Hacienda que el modelo no cubría: la referencia al
 * documento que corrige una nota, la retención de renta y el ciclo de vida del
 * DTE con sus transiciones válidas.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Requisitos fiscales del documento")
class DteFiscalTest {

    @Autowired private FacturacionService facturacionService;
    @Autowired private ConfiguracionDteService configuracion;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private DteCorrelativoRepository correlativoRepository;
    @Autowired private CorrelativoService correlativoService;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private com.fiscore.core.repositories.RegistroHorasRepository registroHorasRepository;
    @Autowired private ProyectoRepository proyectoRepository;

    private Cliente cliente;

    @BeforeEach
    void sembrar() {
        // En orden inverso de dependencias: la base H2 se comparte entre clases
        // de prueba y borrar clientes con contratos vivos viola la clave foránea.
        facturaRepository.deleteAll();
        correlativoRepository.deleteAll();
        contratoRepository.deleteAll();
        registroHorasRepository.deleteAll();   // apuntan a proyecto
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        correlativoService.inicializar();

        configuracion.guardar(Map.of(ParametroDte.RETENCION_RENTA_APLICA.getClave(), "false"));

        Cliente c = new Cliente();
        c.setNombre("Inversiones ACME, S.A. de C.V.");
        c.setNit("0614-010101-001-0");
        c.setNrc("123456-7");
        c.setTipoCliente("JURIDICA");
        c.setEstado("ACTIVO");
        c.setFechaRegistro(LocalDate.now());
        cliente = clienteRepository.save(c);
    }

    /** Factura suelta con una línea del importe indicado, sin pasar por contrato. */
    private Factura facturaDe(String tipoDte, String base) {
        Factura f = new Factura();
        f.setTipoDte(tipoDte);
        f.setCliente(cliente);
        f.setEstado("EMITIDA");

        DetalleFactura d = new DetalleFactura();
        d.setNumItem(1);
        d.setDescripcion("Honorarios profesionales");
        d.setCantidad(BigDecimal.ONE);
        d.setPrecioUnitario(new BigDecimal(base));
        d.setVentaGravada(new BigDecimal(base));
        d.setVentaExenta(BigDecimal.ZERO);
        d.setVentaNoSujeta(BigDecimal.ZERO);
        d.setDescuento(BigDecimal.ZERO);
        d.setTipoItem("2");
        d.setUnidadMedida("Servicio");
        d.setFactura(f);
        // Mutable a propósito: la colección la gestiona Hibernate con
        // orphanRemoval, y una lista inmutable rompe al sincronizarla.
        f.setDetalles(new java.util.ArrayList<>(List.of(d)));
        return f;
    }

    // ---- Retención de renta ----

    @Test
    @DisplayName("Apagada por defecto: no descuenta nada")
    void sinRetencionPorDefecto() {
        Factura f = facturacionService.save(facturaDe("03", "500.00"));

        assertThat(f.getRetencionRenta()).isEqualByComparingTo("0.00");
        // 500 + 13% = 565
        assertThat(f.getMontoTotal()).isEqualByComparingTo("565.00");
    }

    @Test
    @DisplayName("Encendida y por encima del mínimo: se calcula y resta del total")
    void retieneSobreElMinimo() {
        configuracion.guardar(Map.of(ParametroDte.RETENCION_RENTA_APLICA.getClave(), "true"));

        Factura f = facturacionService.save(facturaDe("03", "500.00"));

        // 10% de 500
        assertThat(f.getRetencionRenta()).isEqualByComparingTo("50.00");
        // 500 + 65 de IVA - 50 retenidos
        assertThat(f.getMontoTotal()).isEqualByComparingTo("515.00");
    }

    @Test
    @DisplayName("Encendida pero por debajo del mínimo: no se retiene")
    void noRetieneBajoElMinimo() {
        configuracion.guardar(Map.of(ParametroDte.RETENCION_RENTA_APLICA.getClave(), "true"));

        // El mínimo por defecto son 100.00
        Factura f = facturacionService.save(facturaDe("03", "80.00"));

        assertThat(f.getRetencionRenta()).isEqualByComparingTo("0.00");
        assertThat(f.getMontoTotal()).isEqualByComparingTo("90.40");
    }

    // ---- Documento relacionado ----

    @Test
    @DisplayName("Una nota de crédito sin documento corregido se rechaza")
    void laNotaExigeDocumentoRelacionado() {
        assertThatThrownBy(() -> facturacionService.save(facturaDe("05", "100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("documento que corrige");
    }

    @Test
    @DisplayName("Una nota de crédito con su documento se guarda y conserva la referencia")
    void laNotaConDocumentoSeGuarda() {
        Factura original = facturacionService.save(facturaDe("03", "500.00"));

        Factura nota = facturaDe("05", "500.00");
        nota.setFacturaRelacionada(original);
        Factura guardada = facturacionService.save(nota);

        assertThat(guardada.getFacturaRelacionada().getId()).isEqualTo(original.getId());
        assertThat(guardada.getNumeroFacturaRelacionada()).isEqualTo(original.getNumeroFactura());
    }

    @Test
    @DisplayName("Una factura corriente no puede referirse a otro documento")
    void soloLasNotasSeRefierenAOtroDocumento() {
        Factura original = facturacionService.save(facturaDe("03", "500.00"));

        Factura otra = facturaDe("03", "200.00");
        otra.setFacturaRelacionada(original);

        assertThatThrownBy(() -> facturacionService.save(otra))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo las notas");
    }

    // ---- Ciclo de vida del DTE ----

    @Test
    @DisplayName("Toda factura nace pendiente de envío")
    void nacePendienteDeEnvio() {
        Factura f = facturacionService.save(facturaDe("03", "100.00"));
        assertThat(EstadoDte.desde(f.getEstadoDte())).isEqualTo(EstadoDte.PENDIENTE_ENVIO);
    }

    @Test
    @DisplayName("El camino normal avanza hasta aceptado")
    void caminoNormal() {
        Factura f = facturacionService.save(facturaDe("03", "100.00"));

        f = facturacionService.cambiarEstadoDte(f, EstadoDte.ENVIADO);
        f = facturacionService.cambiarEstadoDte(f, EstadoDte.ACEPTADO);

        assertThat(EstadoDte.desde(f.getEstadoDte())).isEqualTo(EstadoDte.ACEPTADO);
    }

    @Test
    @DisplayName("No se puede saltar de pendiente a aceptado sin haberlo enviado")
    void noSePuedeSaltarElEnvio() {
        Factura f = facturacionService.save(facturaDe("03", "100.00"));

        assertThatThrownBy(() -> facturacionService.cambiarEstadoDte(f, EstadoDte.ACEPTADO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no puede pasar a");
    }

    @Test
    @DisplayName("Un documento con sello no se anula solo en el sistema")
    void elDocumentoAceptadoNoSeAnulaEnLocal() {
        Factura f = facturacionService.save(facturaDe("03", "100.00"));
        f = facturacionService.cambiarEstadoDte(f, EstadoDte.ENVIADO);
        Factura aceptada = facturacionService.cambiarEstadoDte(f, EstadoDte.ACEPTADO);

        assertThatThrownBy(() -> facturacionService.anular(aceptada, "prueba"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalidarla ante el Ministerio");
    }

    @Test
    @DisplayName("Un rechazo se corrige y vuelve a la cola")
    void elRechazoSeReintenta() {
        Factura f = facturacionService.save(facturaDe("03", "100.00"));
        f = facturacionService.cambiarEstadoDte(f, EstadoDte.ENVIADO);
        f = facturacionService.cambiarEstadoDte(f, EstadoDte.RECHAZADO);
        f = facturacionService.cambiarEstadoDte(f, EstadoDte.PENDIENTE_ENVIO);

        assertThat(EstadoDte.desde(f.getEstadoDte())).isEqualTo(EstadoDte.PENDIENTE_ENVIO);
    }

    @Test
    @DisplayName("Invalidado es terminal")
    void invalidadoEsTerminal() {
        assertThat(EstadoDte.INVALIDADO.siguientesPosibles()).isEmpty();
        assertThat(EstadoDte.INVALIDADO.puedePasarA(EstadoDte.ENVIADO)).isFalse();
    }
}
