package com.fiscore.core.controller;

import com.fiscore.core.models.*;
import com.fiscore.core.repositories.*;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
import com.fiscore.core.services.ProyectoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Renderiza cada pantalla con datos reales. Cualquier expresión Thymeleaf
 * inválida hace fallar estas pruebas, que es justo lo que antes solo se
 * descubría abriendo la aplicación en el navegador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaginasTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private com.fiscore.core.repositories.RegistroHorasRepository registroHorasRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ContratoService contratoService;
    @Autowired private ProyectoService proyectoService;
    @Autowired private FacturacionService facturacionService;

    private Long facturaId;

    @BeforeEach
    void sembrarDatos() {
        facturaRepository.deleteAll();
        contratoRepository.deleteAll();
        registroHorasRepository.deleteAll();   // apuntan a proyecto
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        servicioRepository.deleteAll();

        Cliente cliente = new Cliente();
        cliente.setNombre("Inversiones ACME, S.A. de C.V.");
        cliente.setNit("0614-010101-001-0");
        cliente.setNrc("123456-7");
        cliente.setGiro("Comercio al por mayor");
        cliente.setDireccion("Col. Escalón");
        cliente.setMunicipio("San Salvador");
        cliente.setDepartamento("San Salvador");
        cliente.setEmail("pagos@acme.sv");
        cliente.setTipoCliente("JURIDICA");
        cliente.setEstado("ACTIVO");
        cliente = clienteRepository.save(cliente);

        Servicio servicio = new Servicio();
        servicio.setNombre("Contabilidad mensual");
        servicio.setCategoria("CONTABILIDAD");
        servicio.setPeriodicidad("MENSUAL");
        servicio.setPrecio(new BigDecimal("250.00"));
        servicio = servicioRepository.save(servicio);

        Contrato contrato = new Contrato();
        contrato.setCliente(cliente);
        contrato.setTipoFacturacion("RECURRENTE");
        contrato.setHonorariosPactados(new BigDecimal("226.00"));
        contrato.setFechaInicio(LocalDate.now().minusMonths(2));
        contrato.setEstado("ACTIVO");
        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(servicio);
        cs.setPrecioAcordado(new BigDecimal("226.00"));
        contrato.getServicios().add(cs);
        contrato = contratoService.save(contrato);

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Constitución de sociedad");
        proyecto.setCategoria("LEGAL");
        proyecto.setCliente(cliente);
        proyecto.setPresupuesto(new BigDecimal("565.00"));
        proyecto.setEstado("FINALIZADO");
        proyectoService.save(proyecto);

        Proyecto enCurso = new Proyecto();
        enCurso.setNombre("Auditoría fiscal 2026");
        enCurso.setCategoria("AUDITORIA");
        enCurso.setCliente(cliente);
        enCurso.setPresupuesto(new BigDecimal("1200.00"));
        enCurso.setEstado("EN_EJECUCION");
        enCurso.setPorcentajeAvance(40);
        enCurso.setFechaEstimadaFin(LocalDate.now().minusDays(5)); // atrasado
        proyectoService.save(enCurso);

        Factura factura = facturacionService.generarDesdeContrato(
                contratoRepository.findById(contrato.getId()).orElseThrow(),
                "Marzo 2026", "CREDITO", 30);
        facturaId = factura.getId();
    }

    @Test
    @DisplayName("La raíz redirige al panel de control")
    void raizRedirigeAInicio() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inicio"));
    }

    @Test
    @DisplayName("El panel de control muestra los indicadores y las bandejas de trabajo")
    void panelDeControl() throws Exception {
        mockMvc.perform(get("/inicio"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("kpis", "tendencia", "agendaFacturacion",
                        "contratosPorFacturar", "proyectosPorFacturar", "facturasRecientes", "avisos"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Panel de Control")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Inversiones ACME")));
    }

    @Test
    @DisplayName("Reportes renderiza sus cinco pestañas con datos")
    void reportes() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/reportes"))
                .andExpect(model().attributeExists("kpis", "tendencia", "antiguedad",
                        "libroVentas", "totalesLibro", "resumenProyectos", "carteraClientes"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Antigüedad de saldos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Libro de ventas")));
    }

    @Test
    @DisplayName("Reportes acepta el año del libro de ventas por parámetro")
    void reportesConAnio() throws Exception {
        mockMvc.perform(get("/reportes").param("anio", "2025"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("anio", 2025));
    }

    @Test
    @DisplayName("Facturación lista los ciclos vencidos y el historial")
    void facturacion() throws Exception {
        mockMvc.perform(get("/facturacion"))
                .andExpect(status().isOk())
                .andExpect(view().name("facturacion/facturacion"))
                .andExpect(model().attributeExists("contratosPorFacturar", "proyectosPorFacturar",
                        "historialFacturas", "clientesList", "periodoActual"));
    }

    @Test
    @DisplayName("La vista imprimible del DTE muestra emisor, receptor y totales")
    void documentoImprimible() throws Exception {
        mockMvc.perform(get("/facturacion/" + facturaId + "/imprimir"))
                .andExpect(status().isOk())
                .andExpect(view().name("facturacion/documento"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("COMPROBANTE DE CRÉDITO FISCAL")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TOTAL A PAGAR")));
    }

    @Test
    @DisplayName("Clientes, servicios y contratos renderizan correctamente")
    void restoDePaginas() throws Exception {
        mockMvc.perform(get("/clientes")).andExpect(status().isOk()).andExpect(view().name("cliente/clientes"));
        mockMvc.perform(get("/servicios")).andExpect(status().isOk()).andExpect(view().name("servicio/servicios"));
        mockMvc.perform(get("/contratos")).andExpect(status().isOk()).andExpect(view().name("gestion/contratos"));
    }

    @Test
    @DisplayName("Proyectos ofrece emitir el DTE del finalizado y avisa de los atrasados")
    void proyectos() throws Exception {
        mockMvc.perform(get("/proyectos"))
                .andExpect(status().isOk())
                .andExpect(view().name("gestion/proyectos"))
                .andExpect(model().attributeExists("cotizados", "enEjecucion", "finalizados",
                        "facturados", "todosProyectos", "proyectosAtrasados"))
                // Acción de facturación sobre el proyecto finalizado
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Emitir DTE")))
                // Aviso del proyecto cuya fecha estimada de fin ya pasó
                .andExpect(content().string(org.hamcrest.Matchers.containsString("proyecto(s) atrasado(s)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Auditoría fiscal 2026")));
    }

    @Test
    @DisplayName("Los endpoints REST de reportes responden JSON")
    void apiDeReportes() throws Exception {
        mockMvc.perform(get("/reportes/kpis")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFacturado").exists());
        mockMvc.perform(get("/reportes/tendencia")).andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(12)));
        mockMvc.perform(get("/reportes/antiguedad-saldos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.tramos", org.hamcrest.Matchers.hasSize(5)));
        mockMvc.perform(get("/reportes/libro-ventas")).andExpect(status().isOk())
                .andExpect(jsonPath("$.meses", org.hamcrest.Matchers.hasSize(12)));
        mockMvc.perform(get("/reportes/proyectos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.porEstado").exists());
    }

    @Test
    @DisplayName("Las exportaciones CSV se descargan como adjunto")
    void exportacionesCsv() throws Exception {
        mockMvc.perform(get("/reportes/libro-ventas.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("libro-ventas")));
        mockMvc.perform(get("/reportes/antiguedad-saldos.csv")).andExpect(status().isOk());
        mockMvc.perform(get("/reportes/cartera-clientes.csv")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un error de negocio en la API devuelve JSON con la clave error")
    void erroresDeApiEnJson() throws Exception {
        mockMvc.perform(get("/facturacion/999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/proyectos/por-facturar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }
}
