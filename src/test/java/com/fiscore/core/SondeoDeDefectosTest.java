package com.fiscore.core;

import com.fiscore.core.models.*;
import com.fiscore.core.repositories.*;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
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
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sondeos dirigidos a los puntos donde se sospecha que el sistema falla.
 * A diferencia del resto de la suite, estas pruebas están escritas para
 * intentar romper el sistema, no para confirmar que funciona.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SondeoDeDefectosTest {

    @Autowired private FacturacionService facturacionService;
    @Autowired private ContratoService contratoService;
    @Autowired private MockMvc mockMvc;

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private DteCorrelativoRepository correlativoRepository;
    @Autowired private com.fiscore.core.services.CorrelativoService correlativoService;
    @Autowired private ProyectoRepository proyectoRepository;

    private Cliente cliente;
    private Servicio servicio;

    @BeforeEach
    void preparar() {
        facturaRepository.deleteAll();
        // Los correlativos no se reutilizan: para partir de 1 hay que reiniciarlos
        correlativoRepository.deleteAll();
        contratoRepository.deleteAll();
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        servicioRepository.deleteAll();
        correlativoService.inicializar();  // como al arrancar la aplicación

        cliente = new Cliente();
        cliente.setNombre("Inversiones ACME, S.A. de C.V.");
        cliente.setNit("0614-010101-001-0");
        cliente.setNrc("123456-7");
        cliente.setEstado("ACTIVO");
        cliente = clienteRepository.save(cliente);

        servicio = new Servicio();
        servicio.setNombre("Contabilidad mensual");
        servicio.setCategoria("CONTABILIDAD");
        servicio.setPeriodicidad("MENSUAL");
        servicio.setPrecio(new BigDecimal("250.00"));
        servicio = servicioRepository.save(servicio);
    }

    private Contrato contrato(String monto, LocalDate inicio) {
        Contrato c = new Contrato();
        c.setCliente(cliente);
        c.setTipoFacturacion("RECURRENTE");
        c.setHonorariosPactados(new BigDecimal(monto));
        c.setFechaInicio(inicio);
        c.setEstado("ACTIVO");
        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(servicio);
        cs.setPrecioAcordado(new BigDecimal(monto));
        c.getServicios().add(cs);
        return contratoService.save(c);
    }

    // =================================================================

    @Test
    @DisplayName("SONDEO: emisión concurrente no debe repetir correlativos")
    void correlativosBajoConcurrencia() throws Exception {
        int hilos = 8;
        List<Contrato> contratos = new java.util.ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            contratos.add(contrato("113.00", LocalDate.now().minusDays(1)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<String>> resultados = new java.util.ArrayList<>();

        for (Contrato c : contratos) {
            resultados.add(pool.submit(() -> {
                salida.await();   // todos arrancan a la vez
                Contrato recargado = contratoRepository.findById(c.getId()).orElseThrow();
                return facturacionService
                        .generarDesdeContrato(recargado, "Marzo 2026", "CONTADO", null)
                        .getNumeroFactura();
            }));
        }
        salida.countDown();

        List<String> numeros = new java.util.ArrayList<>();
        List<String> fallos = new java.util.ArrayList<>();
        for (Future<String> f : resultados) {
            try {
                numeros.add(f.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                Throwable causa = e.getCause();
                fallos.add(causa.getClass().getSimpleName() + ": " + causa.getMessage());
            }
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
                .as("quedaron emisiones colgadas: posible bloqueo o pool agotado")
                .isTrue();

        assertThat(numeros)
                .as("se perdieron emisiones. Motivos: %s", fallos)
                .hasSize(hilos);

        assertThat(numeros)
                .as("se emitieron correlativos repetidos: %s", numeros)
                .doesNotHaveDuplicates();

        // Y en la base tampoco debe haber duplicados
        List<String> enBase = facturaRepository.findAll().stream()
                .map(Factura::getNumeroFactura).toList();
        assertThat(enBase).as("hay correlativos duplicados almacenados").doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("SONDEO: anular la factura de un contrato debe permitir reemitir el periodo")
    void anularDevuelveElContratoALaBandeja() {
        Contrato c = contrato("226.00", LocalDate.now().minusMonths(1));

        Factura f = facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);
        assertThat(contratoService.findPendientesDeFacturar()).isEmpty();

        facturacionService.anular(f, "error de digitación");

        // Tras anular, el periodo quedó sin facturar: el contrato debe poder volver a emitirse
        assertThat(contratoService.findPendientesDeFacturar())
                .as("el contrato quedó fuera de la bandeja pese a que su factura fue anulada")
                .extracting(Contrato::getId)
                .contains(c.getId());
    }

    @Test
    @DisplayName("SONDEO: los CSV no deben permitir inyección de fórmulas en Excel")
    void csvSinInyeccionDeFormulas() throws Exception {
        Cliente malicioso = new Cliente();
        malicioso.setNombre("=1+1+cmd|' /c calc'!A0");
        malicioso.setNit("0614-030303-003-0");
        malicioso.setNrc("999999-9");
        malicioso.setEstado("ACTIVO");
        clienteRepository.save(malicioso);

        Contrato c = new Contrato();
        c.setCliente(malicioso);
        c.setTipoFacturacion("RECURRENTE");
        c.setHonorariosPactados(new BigDecimal("100.00"));
        c.setFechaInicio(LocalDate.now().minusMonths(1));
        c.setEstado("ACTIVO");
        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(servicio);
        cs.setPrecioAcordado(new BigDecimal("100.00"));
        c.getServicios().add(cs);
        contratoService.save(c);

        String csv = mockMvc.perform(get("/reportes/cartera-clientes.csv"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Ninguna celda debe empezar por un carácter que Excel interprete como fórmula
        csv.lines().skip(1).forEach(linea -> {
            for (String celda : linea.split(";")) {
                String limpia = celda.replace("﻿", "");
                assertThat(limpia.isEmpty() || "=+-@".indexOf(limpia.charAt(0)) < 0)
                        .as("celda ejecutable en Excel: %s", celda)
                        .isTrue();
            }
        });
    }

    @Test
    @DisplayName("SONDEO: cargar el panel no debe disparar decenas de consultas repetidas")
    void panelNoRepiteConsultas() throws Exception {
        for (int i = 0; i < 3; i++) {
            contrato("226.00", LocalDate.now().minusMonths(1));
        }
        facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

        org.hibernate.stat.Statistics stats = estadisticas();
        stats.clear();

        mockMvc.perform(get("/inicio")).andExpect(status().isOk());

        long consultas = stats.getPrepareStatementCount();
        assertThat(consultas)
                .as("el panel ejecutó %d sentencias: revisar consultas duplicadas", consultas)
                .isLessThan(60);
    }

    @Test
    @DisplayName("SONDEO: el importe cero y los negativos no deben colarse en un DTE")
    void importesInvalidos() {
        Contrato cero = new Contrato();
        cero.setCliente(cliente);
        cero.setTipoFacturacion("PAGO_UNICO");
        cero.setHonorariosPactados(BigDecimal.ZERO);
        cero.setFechaInicio(LocalDate.now());
        cero.setEstado("ACTIVO");

        assertThat(catchException(() -> facturacionService.generarDesdeContrato(
                cero, "Marzo 2026", "CONTADO", null)))
                .as("se permitió emitir un DTE por importe cero")
                .isNotNull();

        Contrato negativo = new Contrato();
        negativo.setCliente(cliente);
        negativo.setTipoFacturacion("PAGO_UNICO");
        negativo.setHonorariosPactados(new BigDecimal("-50.00"));
        negativo.setFechaInicio(LocalDate.now());
        negativo.setEstado("ACTIVO");

        assertThat(catchException(() -> facturacionService.generarDesdeContrato(
                negativo, "Marzo 2026", "CONTADO", null)))
                .as("se permitió emitir un DTE por importe negativo")
                .isNotNull();
    }

    @Test
    @DisplayName("SONDEO: el periodo facturado no debe aceptar valores absurdos")
    void periodoFacturadoRazonable() {
        Contrato c = contrato("226.00", LocalDate.now().minusMonths(1));

        // Un periodo larguísimo no debe romper la persistencia
        String periodoLargo = "x".repeat(400);
        Exception error = catchException(() ->
                facturacionService.generarDesdeContrato(c, periodoLargo, "CONTADO", null));

        assertThat(error)
                .as("un periodo de 400 caracteres se aceptó sin control")
                .isNotNull();
        // El rechazo debe venir de la aplicación con un mensaje entendible,
        // no de la base de datos por columna desbordada.
        assertThat(error)
                .as("el error lo produjo la base de datos, no la validación")
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(error.getMessage()).contains("periodo facturado");
    }

    // =================================================================

    @Autowired private org.hibernate.SessionFactory sessionFactory;

    private org.hibernate.stat.Statistics estadisticas() {
        return sessionFactory.getStatistics();
    }

    private static Exception catchException(Runnable accion) {
        try {
            accion.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
