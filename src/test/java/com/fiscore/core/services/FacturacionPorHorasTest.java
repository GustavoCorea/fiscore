package com.fiscore.core.services;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Emisión de la minuta a partir de las horas registradas.
 *
 * La tarifa se interpreta con IVA incluido, igual que los honorarios de
 * contrato: una sola convención en todo el sistema.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Facturación por horas")
class FacturacionPorHorasTest {

    @Autowired private FacturacionService facturacionService;
    @Autowired private RegistroHorasService registroService;
    @Autowired private RegistroHorasRepository registroRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private DteCorrelativoRepository correlativoRepository;
    @Autowired private CorrelativoService correlativoService;

    private Proyecto caso;

    @BeforeEach
    void sembrar() {
        registroRepository.deleteAll();
        facturaRepository.deleteAll();
        correlativoRepository.deleteAll();
        contratoRepository.deleteAll();
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        correlativoService.inicializar();

        Cliente c = new Cliente();
        c.setNombre("Inversiones ACME, S.A. de C.V.");
        c.setNit("0614-010101-001-0");
        c.setNrc("123456-7");   // contribuyente: el documento sale como CCF
        c.setEstado("ACTIVO");
        c = clienteRepository.save(c);

        Proyecto p = new Proyecto();
        p.setNombre("Demanda laboral");
        p.setCategoria("LEGAL");
        p.setEstado("EN_EJECUCION");
        p.setCliente(c);
        p.setTarifaHora(new BigDecimal("100.00"));
        p.setFechaCreacion(LocalDate.now());
        // Lo que haría ProyectoService al crearlo; aquí se graba por repositorio
        p.setFacturado(false);
        caso = proyectoRepository.save(p);
    }

    @Test
    @DisplayName("La minuta cobra las horas pendientes con la tarifa IVA incluido")
    void emiteLaMinuta() {
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("10.00"),
                "Redacción de la demanda", null, null, true);

        Factura f = facturacionService.generarDesdeHoras(caso, "CONTADO", null);

        // 10 h x 100 = 1000 con IVA dentro: base 884.96 + IVA 115.04
        assertThat(f.getMontoTotal()).isEqualByComparingTo("1000.00");
        assertThat(f.getSubtotalGravado()).isEqualByComparingTo("884.96");
        assertThat(f.getTipoDte()).isEqualTo("03");
        assertThat(f.getProyecto().getId()).isEqualTo(caso.getId());
    }

    @Test
    @DisplayName("Una línea por tarifa distinta, no una por registro")
    void agrupaPorTarifa() {
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("3.00"),
                "Estudio", null, null, true);
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("2.00"),
                "Audiencia", null, null, true);
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("1.00"),
                "Gestión del pasante", null, new BigDecimal("40.00"), true);

        Factura f = facturacionService.generarDesdeHoras(caso, "CONTADO", null);

        // Tres registros, dos tarifas: dos líneas
        assertThat(f.getDetalles()).hasSize(2);
        assertThat(f.getDetalles()).anyMatch(d -> d.getDescripcion().contains("5.00 h a $100.00"));
        assertThat(f.getDetalles()).anyMatch(d -> d.getDescripcion().contains("1.00 h a $40.00"));
        // 500 + 40
        assertThat(f.getMontoTotal()).isEqualByComparingTo("540.00");
    }

    @Test
    @DisplayName("Lo no cobrable se queda fuera de la minuta")
    void loNoCobrableNoSeFactura() {
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("4.00"),
                "Trabajo cobrable", null, null, true);
        RegistroHoras cortesia = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("2.00"), "Cortesía", null, null, false);

        Factura f = facturacionService.generarDesdeHoras(caso, "CONTADO", null);

        assertThat(f.getMontoTotal()).isEqualByComparingTo("400.00");
        assertThat(registroRepository.findById(cortesia.getId()).orElseThrow().estaFacturado()).isFalse();
    }

    @Test
    @DisplayName("Las horas cobradas quedan enlazadas y no se vuelven a facturar")
    void noSeCobraDosVeces() {
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("5.00"),
                "Trabajo", null, null, true);

        Factura primera = facturacionService.generarDesdeHoras(caso, "CONTADO", null);

        List<RegistroHoras> registros = registroRepository.findByProyectoIdOrderByFechaDescIdDesc(caso.getId());
        assertThat(registros).allMatch(RegistroHoras::estaFacturado);
        assertThat(registros.get(0).getFactura().getId()).isEqualTo(primera.getId());

        assertThatThrownBy(() -> facturacionService.generarDesdeHoras(caso, "CONTADO", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene horas pendientes");
    }

    @Test
    @DisplayName("El caso sigue abierto después de la minuta")
    void elCasoNoSeCierra() {
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("5.00"),
                "Primera entrega", null, null, true);
        facturacionService.generarDesdeHoras(caso, "CONTADO", null);

        Proyecto despues = proyectoRepository.findById(caso.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo("EN_EJECUCION");
        assertThat(despues.getFacturado()).isFalse();

        // Y el trabajo posterior se puede seguir cobrando
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("2.00"),
                "Segunda entrega", null, null, true);
        Factura segunda = facturacionService.generarDesdeHoras(despues, "CONTADO", null);
        assertThat(segunda.getMontoTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("Un registro sin tarifa detiene la emisión en lugar de perderse")
    void sinTarifaNoSeEmite() {
        caso.setTarifaHora(null);
        proyectoRepository.save(caso);
        registroService.registrar(caso.getId(), LocalDate.now(), new BigDecimal("3.00"),
                "Trabajo sin tarifa", null, null, true);

        assertThatThrownBy(() -> facturacionService.generarDesdeHoras(caso, "CONTADO", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tienen tarifa");
    }

    @Test
    @DisplayName("Un caso sin horas registradas no emite nada")
    void sinHorasNoHayMinuta() {
        assertThatThrownBy(() -> facturacionService.generarDesdeHoras(caso, "CONTADO", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tiene horas pendientes");
    }
}
