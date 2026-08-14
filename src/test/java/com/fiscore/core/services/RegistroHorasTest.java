package com.fiscore.core.services;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.models.RegistroHoras;
import com.fiscore.core.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Registro de horas por caso")
class RegistroHorasTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RegistroHorasService registroService;
    @Autowired private RegistroHorasRepository registroRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private ContratoRepository contratoRepository;

    private Proyecto caso;

    @BeforeEach
    void sembrar() {
        // En orden inverso de dependencias. La base H2 se comparte entre clases
        // de prueba, así que aquí hay contratos y facturas que dejó otra.
        registroRepository.deleteAll();
        facturaRepository.deleteAll();
        contratoRepository.deleteAll();
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();

        Cliente c = new Cliente();
        c.setNombre("Inversiones ACME, S.A. de C.V.");
        c.setNit("0614-010101-001-0");
        c.setEstado("ACTIVO");
        c = clienteRepository.save(c);

        Proyecto p = new Proyecto();
        p.setNombre("Demanda laboral");
        p.setCategoria("LEGAL");
        p.setEstado("EN_EJECUCION");
        p.setCliente(c);
        p.setTarifaHora(new BigDecimal("75.00"));
        p.setFechaCreacion(LocalDate.now());
        caso = proyectoRepository.save(p);
    }

    @Test
    @DisplayName("La tarifa enviada desde el formulario llega hasta el registro")
    void laTarifaViajaDesdeElFormulario() throws Exception {
        // Esta prueba existe por un fallo real: la tarifa estaba en la entidad y
        // en el servicio, pero el controlador arma el proyecto campo a campo
        // desde un Map y nadie la copiaba, asi que se guardaba nula y todas las
        // horas salian a cero. Las pruebas de servicio no lo veian porque fijan
        // la tarifa directamente sobre el objeto.
        String cuerpo = """
                {"nombre":"Caso con tarifa","categoria":"LEGAL","estado":"EN_EJECUCION",
                 "clienteId":%d,"presupuesto":"1000.00","tarifaHora":"90.00"}
                """.formatted(caso.getCliente().getId());

        String respuesta = mockMvc.perform(post("/proyectos/guardar")
                        .contentType("application/json").content(cuerpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long nuevoId = Long.valueOf(respuesta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        assertThat(proyectoRepository.findById(nuevoId).orElseThrow().getTarifaHora())
                .isEqualByComparingTo("90.00");

        RegistroHoras r = registroService.registrar(nuevoId, LocalDate.now(),
                new BigDecimal("2.00"), "Trabajo", null, null, true);
        assertThat(r.getImporte()).isEqualByComparingTo("180.00");
    }

    @Test
    @DisplayName("La tarifa del caso se copia al registro")
    void tomaLaTarifaDelCaso() {
        RegistroHoras r = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("3.50"), "Estudio del expediente", null, null, true);

        assertThat(r.getTarifaHora()).isEqualByComparingTo("75.00");
        assertThat(r.getImporte()).isEqualByComparingTo("262.50");
    }

    @Test
    @DisplayName("Cambiar la tarifa del caso no reprecifica el trabajo ya registrado")
    void laTarifaNoSeReescribe() {
        RegistroHoras marzo = registroService.registrar(caso.getId(), LocalDate.now().minusDays(30),
                new BigDecimal("10.00"), "Redacción de la demanda", null, null, true);

        caso.setTarifaHora(new BigDecimal("120.00"));
        proyectoRepository.save(caso);

        RegistroHoras septiembre = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("10.00"), "Audiencia", null, null, true);

        assertThat(registroRepository.findById(marzo.getId()).orElseThrow().getImporte())
                .isEqualByComparingTo("750.00");
        assertThat(septiembre.getImporte()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Sin persona indicada, el trabajo es de quien lo registra")
    void elAutorPorDefecto() {
        RegistroHoras r = registroService.registrar(caso.getId(), LocalDate.now(),
                BigDecimal.ONE, "Llamada con el cliente", null, null, true);

        assertThat(r.getUsuario()).isNotBlank();
    }

    @Test
    @DisplayName("Se puede registrar trabajo de otra persona")
    void trabajoDeOtro() {
        RegistroHoras r = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("2.00"), "Búsqueda de jurisprudencia", "pasante.lopez", null, true);

        assertThat(r.getUsuario()).isEqualTo("pasante.lopez");
    }

    @Test
    @DisplayName("Un registro de más de 24 horas es una errata, no un día largo")
    void topeDeHoras() {
        assertThatThrownBy(() -> registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("80.00"), "Maratón", null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24 horas");
    }

    @Test
    @DisplayName("No se registra trabajo en el futuro ni sin descripción ni con horas cero")
    void validacionesBasicas() {
        assertThatThrownBy(() -> registroService.registrar(caso.getId(), LocalDate.now().plusDays(1),
                BigDecimal.ONE, "Adivinación", null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");

        assertThatThrownBy(() -> registroService.registrar(caso.getId(), LocalDate.now(),
                BigDecimal.ONE, "  ", null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Describe");

        assertThatThrownBy(() -> registroService.registrar(caso.getId(), LocalDate.now(),
                BigDecimal.ZERO, "Nada", null, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mayores que cero");
    }

    @Test
    @DisplayName("El resumen separa lo pendiente, lo cobrado y lo que no se cobra")
    void resumenPorEstado() {
        registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("4.00"), "Trabajo facturable", null, null, true);
        registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("2.00"), "Cortesía al cliente", null, null, false);

        Map<String, Object> resumen = registroService.resumen(caso.getId());

        assertThat(resumen.get("registros")).isEqualTo(2);
        assertThat((BigDecimal) resumen.get("horasTotales")).isEqualByComparingTo("6.00");
        assertThat((BigDecimal) resumen.get("horasNoFacturables")).isEqualByComparingTo("2.00");
        assertThat((BigDecimal) resumen.get("importePendiente")).isEqualByComparingTo("300.00");
        assertThat((BigDecimal) resumen.get("importeFacturado")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Un registro sin facturar se corrige y se borra")
    void mientrasNoSeFactureSePuedeCorregir() {
        RegistroHoras r = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("3.00"), "Borrador", null, null, true);

        registroService.actualizar(r.getId(), null, new BigDecimal("5.00"),
                "Borrador corregido", null, null, true);
        assertThat(registroRepository.findById(r.getId()).orElseThrow().getHoras())
                .isEqualByComparingTo("5.00");

        registroService.eliminar(r.getId());
        assertThat(registroRepository.findById(r.getId())).isEmpty();
    }

    @Test
    @DisplayName("Lo ya cobrado no se toca: se corrige con una nota de crédito")
    void loFacturadoNoSeToca() {
        RegistroHoras r = registroService.registrar(caso.getId(), LocalDate.now(),
                new BigDecimal("3.00"), "Trabajo cobrado", null, null, true);

        // Se marca como cobrado sin pasar por la facturación, que es el
        // siguiente trozo; aquí solo interesa el candado.
        com.fiscore.core.models.Factura f = new com.fiscore.core.models.Factura();
        f.setNumeroFactura("CCF-00009");
        f.setTipoDte("03");
        f.setEstado("EMITIDA");
        f.setMontoTotal(new BigDecimal("225.00"));
        f = facturaRepository.save(f);

        r.setFactura(f);
        registroRepository.save(r);

        Long id = r.getId();
        assertThatThrownBy(() -> registroService.eliminar(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CCF-00009");

        assertThatThrownBy(() -> registroService.actualizar(id, null, BigDecimal.ONE, "Otra cosa", null, null, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nota de crédito");
    }
}
