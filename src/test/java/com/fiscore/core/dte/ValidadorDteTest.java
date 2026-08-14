package com.fiscore.core.dte;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.DetalleFactura;
import com.fiscore.core.models.Factura;
import com.fiscore.core.services.ConfiguracionDteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Documento listo para transmitir")
class ValidadorDteTest {

    @Autowired private ValidadorDte validador;
    @Autowired private ConfiguracionDteService configuracion;

    @BeforeEach
    void emisorConDatosReales() {
        // Se apartan de los valores de ejemplo para que las pruebas de receptor
        // y documento no arrastren los avisos del emisor.
        configuracion.guardar(Map.of(
                ParametroDte.EMISOR_NOMBRE.getClave(), "Asesores Fiscales del Pacífico, S.A. de C.V.",
                ParametroDte.EMISOR_NIT.getClave(), "0614-250780-102-3",
                ParametroDte.EMISOR_NRC.getClave(), "987654-3",
                ParametroDte.EMISOR_COD_ACTIVIDAD.getClave(), "69100",
                ParametroDte.EMISOR_GIRO.getClave(), "Servicios jurídicos y contables",
                ParametroDte.EMISOR_DIRECCION.getClave(), "Av. Las Magnolias 45, San Salvador",
                ParametroDte.EMISOR_DEPARTAMENTO.getClave(), "06",
                ParametroDte.EMISOR_MUNICIPIO.getClave(), "20",
                ParametroDte.EMISOR_CORREO.getClave(), "dte@asesorespacifico.sv",
                ParametroDte.EMISOR_TIPO_ESTABLECIMIENTO.getClave(), "01"));
    }

    private Factura documentoCompleto(String tipoDte) {
        Cliente c = new Cliente();
        c.setNombre("Inversiones ACME, S.A. de C.V.");
        c.setNit("0614-010101-001-0");
        c.setNrc("123456-7");

        DetalleFactura d = new DetalleFactura();
        d.setNumItem(1);
        d.setDescripcion("Honorarios");
        d.setVentaGravada(new BigDecimal("100.00"));

        Factura f = new Factura();
        f.setTipoDte(tipoDte);
        f.setCliente(c);
        f.setNumeroControl("DTE-03-M001P001-000000000000001");
        f.setCodigoGeneracion("4391336A-6FAD-4054-8A92-CB7E5B78C15E");
        f.setMontoTotal(new BigDecimal("113.00"));
        f.setDetalles(new ArrayList<>(List.of(d)));
        return f;
    }

    @Test
    @DisplayName("Un documento completo no tiene nada que objetar")
    void documentoCorrecto() {
        assertThat(validador.problemas(documentoCompleto("03"))).isEmpty();
        assertThat(validador.puedeTransmitirse(documentoCompleto("03"))).isTrue();
    }

    @Test
    @DisplayName("Un código de catálogo igual al de ejemplo no es sospechoso")
    void losCodigosDeCatalogoNoDelatan() {
        // 06 es San Salvador de verdad y además es el valor de ejemplo: que
        // coincidan es normal y no debe generar aviso.
        configuracion.guardar(Map.of(
                ParametroDte.EMISOR_DEPARTAMENTO.getClave(),
                ParametroDte.EMISOR_DEPARTAMENTO.getValorPorDefecto()));

        assertThat(validador.problemas(documentoCompleto("03"))).isEmpty();
    }

    @Test
    @DisplayName("Avisa cuando el emisor sigue con los datos de ejemplo")
    void emisorSinConfigurar() {
        // Se devuelve el NIT al valor con el que viene la aplicación
        configuracion.guardar(Map.of(ParametroDte.EMISOR_NIT.getClave(),
                ParametroDte.EMISOR_NIT.getValorPorDefecto()));

        assertThat(validador.problemas(documentoCompleto("03")))
                .anyMatch(p -> p.contains("valor de ejemplo") && p.contains("NIT"));
    }

    @Test
    @DisplayName("Un crédito fiscal exige el NRC del cliente")
    void creditoFiscalSinNrc() {
        Factura f = documentoCompleto("03");
        f.getCliente().setNrc(null);

        assertThat(validador.problemas(f))
                .anyMatch(p -> p.contains("NRC"));
    }

    @Test
    @DisplayName("La misma venta como factura de consumidor final no exige NRC")
    void consumidorFinalSinNrc() {
        Factura f = documentoCompleto("01");
        f.getCliente().setNrc(null);

        assertThat(validador.problemas(f)).isEmpty();
    }

    @Test
    @DisplayName("Se enumeran todos los problemas juntos, no de uno en uno")
    void losProblemasSeAcumulan() {
        Factura f = documentoCompleto("03");
        f.getCliente().setNit(null);
        f.getCliente().setNrc(null);
        f.setNumeroControl(null);
        f.setMontoTotal(BigDecimal.ZERO);

        assertThat(validador.problemas(f)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("Una nota sin el documento que corrige no se transmite")
    void notaSinDocumentoRelacionado() {
        Factura f = documentoCompleto("05");

        assertThat(validador.problemas(f))
                .anyMatch(p -> p.contains("documento que corrige"));
    }
}
