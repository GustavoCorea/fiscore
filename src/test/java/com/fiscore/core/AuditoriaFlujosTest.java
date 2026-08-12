package com.fiscore.core;

import com.fiscore.core.models.*;
import com.fiscore.core.repositories.*;
import com.fiscore.core.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Auditoría de extremo a extremo: recorre los flujos de trabajo completos y
 * comprueba que los reportes y el panel reflejan exactamente lo ocurrido.
 *
 * A diferencia de las pruebas por servicio, aquí se monta un escenario con
 * historia (varios meses, morosidad, anulaciones) y se verifica la coherencia
 * entre módulos, que es donde suelen esconderse los errores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditoriaFlujosTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FacturacionService facturacionService;
    @Autowired private ContratoService contratoService;
    @Autowired private ProyectoService proyectoService;
    @Autowired private ReportesService reportesService;

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private DteCorrelativoRepository correlativoRepository;
    @Autowired private com.fiscore.core.services.CorrelativoService correlativoService;

    private Cliente acme;        // contribuyente con NRC  -> CCF
    private Cliente elRoble;     // contribuyente con NRC  -> CCF
    private Cliente natural;     // sin NRC                -> Factura CF
    private Servicio contabilidad;

    @BeforeEach
    void limpiar() {
        facturaRepository.deleteAll();
        // Los correlativos no se reutilizan: para partir de 1 hay que reiniciarlos
        correlativoRepository.deleteAll();
        contratoRepository.deleteAll();
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        servicioRepository.deleteAll();
        correlativoService.inicializar();  // como al arrancar la aplicación

        acme = guardarCliente("Inversiones ACME, S.A. de C.V.", "0614-010101-001-0", "123456-7");
        elRoble = guardarCliente("Distribuidora El Roble", "0614-020202-002-1", "654321-2");
        natural = guardarCliente("María Elena Portillo", "01234567-8", null);

        contabilidad = new Servicio();
        contabilidad.setNombre("Contabilidad mensual");
        contabilidad.setCategoria("CONTABILIDAD");
        contabilidad.setPeriodicidad("MENSUAL");
        contabilidad.setPrecio(new BigDecimal("250.00"));
        contabilidad = servicioRepository.save(contabilidad);
    }

    private Cliente guardarCliente(String nombre, String nit, String nrc) {
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setNit(nit);
        c.setNrc(nrc);
        c.setEstado("ACTIVO");
        c.setTipoCliente(nrc != null ? "JURIDICA" : "NATURAL");
        return clienteRepository.save(c);
    }

    private Contrato contrato(Cliente cliente, String tipo, String monto, LocalDate inicio) {
        Contrato c = new Contrato();
        c.setCliente(cliente);
        c.setTipoFacturacion(tipo);
        c.setHonorariosPactados(new BigDecimal(monto));
        c.setFechaInicio(inicio);
        c.setEstado("ACTIVO");
        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(contabilidad);
        cs.setPrecioAcordado(new BigDecimal(monto));
        c.getServicios().add(cs);
        return contratoService.save(c);
    }

    /** Reubica una factura en el pasado para poder probar tendencias y mora. */
    private Factura envejecer(Factura factura, int mesesAtras, Integer diasMora) {
        factura.setFechaEmision(LocalDateTime.now().minusMonths(mesesAtras));
        if (diasMora != null) {
            factura.setFechaVencimiento(LocalDate.now().minusDays(diasMora));
        }
        return facturaRepository.save(factura);
    }

    // =================================================================
    @Nested
    @DisplayName("Ciclo de vida del contrato recurrente")
    class CicloContrato {

        @Test
        @DisplayName("Alta → bandeja → emisión → salida de bandeja → siguiente ciclo")
        void cicloCompleto() {
            Contrato c = contrato(acme, "RECURRENTE", "226.00", LocalDate.now().minusMonths(3));

            // Nace con la facturación vencida, así que entra en la bandeja
            assertThat(contratoService.findPendientesDeFacturar())
                    .extracting(Contrato::getId).containsExactly(c.getId());

            Factura f = facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);
            assertThat(f.getEstado()).isEqualTo("EMITIDA");

            // Sale de la bandeja y queda programado a futuro
            assertThat(contratoService.findPendientesDeFacturar()).isEmpty();
            LocalDate proxima = contratoRepository.findById(c.getId()).orElseThrow()
                    .getFechaProximaFacturacion();
            assertThat(proxima).isAfter(LocalDate.now());

            // Y aparece en la agenda de los próximos 30 días si cae dentro
            long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), proxima);
            if (dias <= 30) {
                assertThat(contratoService.findAgendaFacturacion(30))
                        .extracting(Contrato::getId).contains(c.getId());
            }
        }

        @Test
        @DisplayName("Un contrato de pago único no vuelve a programarse")
        void pagoUnicoNoSeReprograma() {
            Contrato c = contrato(natural, "PAGO_UNICO", "500.00", LocalDate.now().minusDays(5));

            facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);

            assertThat(contratoRepository.findById(c.getId()).orElseThrow()
                    .getFechaProximaFacturacion()).isNull();
            assertThat(contratoService.findPendientesDeFacturar()).isEmpty();
        }

        @Test
        @DisplayName("Un contrato muy atrasado se pone al día en un solo ciclo futuro")
        void contratoAtrasadoSeRecupera() {
            Contrato c = contrato(acme, "RECURRENTE", "226.00", LocalDate.now().minusMonths(8));

            facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);

            LocalDate proxima = contratoRepository.findById(c.getId()).orElseThrow()
                    .getFechaProximaFacturacion();
            assertThat(proxima).isAfter(LocalDate.now());
            // No se queda a mitad de camino en el pasado
            assertThat(proxima).isBefore(LocalDate.now().plusMonths(2));
        }

        @Test
        @DisplayName("Editar el contrato no duplica ni pierde los servicios")
        void edicionConservaServicios() {
            Contrato c = contrato(acme, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
            assertThat(c.getServicios()).hasSize(1);

            Contrato edicion = new Contrato();
            edicion.setId(c.getId());
            edicion.setCliente(acme);
            edicion.setTipoFacturacion("RECURRENTE");
            edicion.setHonorariosPactados(new BigDecimal("339.00"));
            edicion.setFechaInicio(c.getFechaInicio());
            edicion.setEstado("ACTIVO");
            ContratoServicio nuevo = new ContratoServicio();
            nuevo.setServicio(contabilidad);
            nuevo.setPrecioAcordado(new BigDecimal("339.00"));
            edicion.getServicios().add(nuevo);

            Contrato guardado = contratoService.save(edicion);

            assertThat(guardado.getServicios()).hasSize(1);
            assertThat(guardado.getHonorariosPactados()).isEqualByComparingTo("339.00");
            assertThat(contratoRepository.findById(c.getId()).orElseThrow().getServicios()).hasSize(1);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Integridad de los documentos tributarios")
    class IntegridadDte {

        @Test
        @DisplayName("Los correlativos no se repiten aunque se emita en ráfaga")
        void correlativosSinHuecosNiRepetidos() {
            for (int i = 0; i < 5; i++) {
                contrato(acme, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            }
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            List<String> numeros = facturaRepository.findAll().stream()
                    .map(Factura::getNumeroFactura).sorted().toList();

            assertThat(numeros).hasSize(5);
            assertThat(numeros).doesNotHaveDuplicates();
            assertThat(numeros).containsExactly(
                    "CCF-00001", "CCF-00002", "CCF-00003", "CCF-00004", "CCF-00005");
        }

        @Test
        @DisplayName("Los números de control tampoco se repiten")
        void numerosDeControlUnicos() {
            for (int i = 0; i < 4; i++) {
                contrato(acme, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            }
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            assertThat(facturaRepository.findAll().stream().map(Factura::getNumeroControl).toList())
                    .doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("Cada factura cuadra: gravado + exento + no sujeto - descuento + IVA = total")
        void totalesCuadranSiempre() {
            contrato(acme, "PAGO_UNICO", "1234.57", LocalDate.now().minusDays(1));
            contrato(elRoble, "PAGO_UNICO", "0.03", LocalDate.now().minusDays(1));
            contrato(natural, "PAGO_UNICO", "99999.99", LocalDate.now().minusDays(1));
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            for (Factura f : facturaRepository.findAll()) {
                BigDecimal esperado = f.getSubtotalGravado()
                        .add(f.getSubtotalExento())
                        .add(f.getSubtotalNoSujeto())
                        .subtract(f.getDescuento())
                        .add(f.getIvaPercibido())
                        .subtract(f.getIvaRetenido());

                assertThat(f.getMontoTotal())
                        .as("la factura %s no cuadra", f.getNumeroFactura())
                        .isEqualByComparingTo(esperado);
            }
        }

        @Test
        @DisplayName("El total facturado coincide exactamente con lo pactado, sin céntimos perdidos")
        void sinDesviacionPorRedondeo() {
            // 1234.57 no divide exacto entre 1.13: el ajuste va contra el IVA
            Contrato c = contrato(acme, "PAGO_UNICO", "1234.57", LocalDate.now().minusDays(1));
            Factura f = facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);

            assertThat(f.getMontoTotal()).isEqualByComparingTo("1234.57");
        }

        @Test
        @DisplayName("Una factura con varios servicios genera una línea por servicio")
        void unaLineaPorServicio() {
            Servicio iva = new Servicio();
            iva.setNombre("Declaración de IVA");
            iva.setCategoria("CONTABILIDAD");
            iva.setPeriodicidad("MENSUAL");
            iva.setPrecio(new BigDecimal("75.00"));
            Servicio guardado = servicioRepository.save(iva);

            Contrato c = new Contrato();
            c.setCliente(acme);
            c.setTipoFacturacion("RECURRENTE");
            c.setHonorariosPactados(new BigDecimal("367.25"));
            c.setFechaInicio(LocalDate.now().minusMonths(1));
            c.setEstado("ACTIVO");
            ContratoServicio cs1 = new ContratoServicio();
            cs1.setServicio(contabilidad);
            cs1.setPrecioAcordado(new BigDecimal("250.00"));
            ContratoServicio cs2 = new ContratoServicio();
            cs2.setServicio(guardado);
            cs2.setPrecioAcordado(new BigDecimal("75.00"));
            c.getServicios().add(cs1);
            c.getServicios().add(cs2);
            Contrato persistido = contratoService.save(c);

            Factura f = facturacionService.generarDesdeContrato(persistido, "Marzo 2026", "CONTADO", null);

            assertThat(f.getDetalles()).hasSize(2);
            assertThat(f.getDetalles()).extracting(DetalleFactura::getNumItem).containsExactly(1, 2);
            assertThat(f.getMontoTotal()).isEqualByComparingTo("367.25");
        }

        @Test
        @DisplayName("Un contrato sin cliente o sin honorarios no puede facturarse")
        void datosMinimosParaFacturar() {
            Contrato sinHonorarios = new Contrato();
            sinHonorarios.setCliente(acme);
            sinHonorarios.setHonorariosPactados(BigDecimal.ZERO);

            assertThatThrownBy(() -> facturacionService.generarDesdeContrato(
                    sinHonorarios, "Marzo 2026", "CONTADO", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("honorarios");

            Contrato sinCliente = new Contrato();
            sinCliente.setHonorariosPactados(new BigDecimal("100.00"));

            assertThatThrownBy(() -> facturacionService.generarDesdeContrato(
                    sinCliente, "Marzo 2026", "CONTADO", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cliente");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Coherencia entre facturación y reportes")
    class CoherenciaReportes {

        @Test
        @DisplayName("Anular una factura la descuenta de KPIs, libro de ventas y cobranza")
        void anularSeReflejaEnTodosLosReportes() {
            Contrato c1 = contrato(acme, "PAGO_UNICO", "226.00", LocalDate.now().minusDays(1));
            Contrato c2 = contrato(elRoble, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            Factura aAnular = facturacionService.generarDesdeContrato(c1, "Marzo 2026", "CONTADO", null);
            facturacionService.generarDesdeContrato(c2, "Marzo 2026", "CONTADO", null);

            assertThat((BigDecimal) reportesService.getKpis().get("totalFacturado"))
                    .isEqualByComparingTo("339.00");

            facturacionService.anular(aAnular, "prueba");

            Map<String, Object> kpis = reportesService.getKpis();
            assertThat((BigDecimal) kpis.get("totalFacturado")).isEqualByComparingTo("113.00");
            assertThat((BigDecimal) kpis.get("montoPendiente")).isEqualByComparingTo("113.00");

            // El libro de ventas tampoco la cuenta
            Map<String, Object> libro = reportesService.getTotalesLibroVentas(LocalDate.now().getYear());
            assertThat((BigDecimal) libro.get("total")).isEqualByComparingTo("113.00");
            assertThat(libro.get("documentos")).isEqualTo(1L);

            // Ni la cobranza
            assertThat(reportesService.getAntiguedadSaldos().get("cantidad")).isEqualTo(1);
        }

        @Test
        @DisplayName("Cobrar mueve el importe de pendiente a cobrado sin alterar el total")
        void cobrarNoAlteraElTotalFacturado() {
            Contrato c = contrato(acme, "PAGO_UNICO", "226.00", LocalDate.now().minusDays(1));
            Factura f = facturacionService.generarDesdeContrato(c, "Marzo 2026", "CONTADO", null);

            facturacionService.registrarPago(f, null);

            Map<String, Object> kpis = reportesService.getKpis();
            assertThat((BigDecimal) kpis.get("totalFacturado")).isEqualByComparingTo("226.00");
            assertThat((BigDecimal) kpis.get("totalCobrado")).isEqualByComparingTo("226.00");
            assertThat((BigDecimal) kpis.get("montoPendiente")).isEqualByComparingTo("0.00");
            assertThat((BigDecimal) kpis.get("tasaCobro")).isEqualByComparingTo("100.0");
            assertThat(reportesService.getAntiguedadSaldos().get("cantidad")).isEqualTo(0);
        }

        @Test
        @DisplayName("La suma de los tramos de mora es el total pendiente")
        void tramosSumanElTotal() {
            for (int i = 0; i < 4; i++) {
                contrato(acme, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            }
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            List<Factura> facturas = facturaRepository.findAll();
            envejecer(facturas.get(0), 0, -10);  // aún por vencer
            envejecer(facturas.get(1), 1, 20);   // 1-30
            envejecer(facturas.get(2), 2, 50);   // 31-60
            envejecer(facturas.get(3), 4, 120);  // +90

            Map<String, Object> antiguedad = reportesService.getAntiguedadSaldos();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tramos = (List<Map<String, Object>>) antiguedad.get("tramos");

            BigDecimal suma = tramos.stream()
                    .map(t -> (BigDecimal) t.get("monto"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(suma).isEqualByComparingTo((BigDecimal) antiguedad.get("total"));
            assertThat(suma).isEqualByComparingTo("452.00");

            // Cada documento cayó en un tramo distinto
            assertThat(tramos).filteredOn(t -> ((Integer) t.get("cantidad")) == 1).hasSize(4);
        }

        @Test
        @DisplayName("El libro de ventas separa CCF de consumidor final y el IVA cuadra")
        void libroDeVentasCuadra() {
            contrato(acme, "PAGO_UNICO", "226.00", LocalDate.now().minusDays(1));      // CCF
            contrato(natural, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));   // Factura CF
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            Map<String, Object> t = reportesService.getTotalesLibroVentas(LocalDate.now().getYear());

            assertThat((BigDecimal) t.get("gravadoContribuyente")).isEqualByComparingTo("200.00");
            assertThat((BigDecimal) t.get("gravadoConsumidor")).isEqualByComparingTo("100.00");
            assertThat((BigDecimal) t.get("iva")).isEqualByComparingTo("39.00");

            BigDecimal gravadoTotal = ((BigDecimal) t.get("gravadoContribuyente"))
                    .add((BigDecimal) t.get("gravadoConsumidor"));
            assertThat(gravadoTotal.add((BigDecimal) t.get("iva")))
                    .isEqualByComparingTo((BigDecimal) t.get("total"));
        }

        @Test
        @DisplayName("La tendencia mensual ubica cada factura en su mes")
        void tendenciaUbicaPorMes() {
            for (int i = 0; i < 3; i++) {
                contrato(acme, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            }
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            List<Factura> facturas = facturaRepository.findAll();
            envejecer(facturas.get(0), 0, null);
            envejecer(facturas.get(1), 1, null);
            envejecer(facturas.get(2), 2, null);

            List<Map<String, Object>> serie = reportesService.getTendenciaMensual(12);
            assertThat(serie).hasSize(12);

            BigDecimal total = serie.stream()
                    .map(p -> (BigDecimal) p.get("facturado"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(total).isEqualByComparingTo("339.00");

            // Los tres últimos meses tienen un documento cada uno
            assertThat(serie.subList(9, 12)).allSatisfy(p ->
                    assertThat((Long) p.get("cantidad")).isEqualTo(1L));
        }

        @Test
        @DisplayName("Los rankings suman lo mismo que el total facturado")
        void rankingsCoherentes() {
            contrato(acme, "PAGO_UNICO", "226.00", LocalDate.now().minusDays(1));
            contrato(elRoble, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(1));
            facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

            BigDecimal sumaRanking = reportesService.getTopClientesPorFacturado(10).stream()
                    .map(m -> (BigDecimal) m.get("monto"))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(sumaRanking)
                    .isEqualByComparingTo((BigDecimal) reportesService.getKpis().get("totalFacturado"));
        }

        @Test
        @DisplayName("Con la base vacía, ningún reporte falla ni devuelve nulos")
        void reportesConBaseVacia() {
            Map<String, Object> kpis = reportesService.getKpis();
            assertThat(kpis.values()).doesNotContainNull();
            assertThat((BigDecimal) kpis.get("totalFacturado")).isEqualByComparingTo("0.00");
            assertThat((BigDecimal) kpis.get("tasaCobro")).isEqualByComparingTo("0.0");

            assertThat(reportesService.getTendenciaMensual(12)).hasSize(12);
            assertThat(reportesService.getAntiguedadSaldos().get("cantidad")).isEqualTo(0);
            assertThat(reportesService.getLibroVentas(LocalDate.now().getYear())).hasSize(12);
            assertThat(reportesService.getCarteraPorCliente()).isEmpty();
            assertThat(reportesService.getTopClientesPorHonorarios(5)).isEmpty();
            assertThat(reportesService.getIngresosPorCategoria()).isEmpty();
            assertThat(reportesService.getResumenProyectos().get("porEstado")).isNotNull();
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Flujo de proyectos")
    class FlujoProyectos {

        @Test
        @DisplayName("Cotizado → ejecución → finalizado → facturado, con el reporte al día")
        void cicloDeProyecto() {
            Proyecto p = new Proyecto();
            p.setNombre("Constitución de sociedad");
            p.setCategoria("LEGAL");
            p.setCliente(acme);
            p.setPresupuesto(new BigDecimal("565.00"));
            p.setEstado("COTIZADO");
            p = proyectoService.save(p);

            p.setEstado("EN_EJECUCION");
            p.setPorcentajeAvance(50);
            p = proyectoService.save(p);
            assertThat(proyectoService.countEnEjecucion()).isEqualTo(1);

            p.setEstado("FINALIZADO");
            p = proyectoService.save(p);
            // Al finalizar se cierra el avance y se fecha el cierre
            assertThat(p.getPorcentajeAvance()).isEqualTo(100);
            assertThat(p.getFechaFin()).isNotNull();
            assertThat(proyectoService.findFinalizadosSinFacturar()).hasSize(1);

            Factura f = facturacionService.generarDesdeProyecto(p, "CONTADO", null);
            assertThat(f.getMontoTotal()).isEqualByComparingTo("565.00");
            assertThat(f.getProyecto().getId()).isEqualTo(p.getId());

            assertThat(proyectoRepository.findById(p.getId()).orElseThrow().getEstado())
                    .isEqualTo("FACTURADO");
            assertThat(proyectoService.findFinalizadosSinFacturar()).isEmpty();

            // El presupuesto deja de estar pendiente de cobro
            assertThat((BigDecimal) reportesService.getKpis().get("presupuestoPendiente"))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Un proyecto no se puede facturar dos veces")
        void proyectoNoSeFacturaDosVeces() {
            Proyecto p = new Proyecto();
            p.setNombre("Auditoría");
            p.setCategoria("AUDITORIA");
            p.setCliente(acme);
            p.setPresupuesto(new BigDecimal("1000.00"));
            p.setEstado("FINALIZADO");
            Proyecto guardado = proyectoService.save(p);

            facturacionService.generarDesdeProyecto(guardado, "CONTADO", null);
            Proyecto recargado = proyectoRepository.findById(guardado.getId()).orElseThrow();

            assertThatThrownBy(() -> facturacionService.generarDesdeProyecto(recargado, "CONTADO", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ya fue facturado");
        }

        @Test
        @DisplayName("Los proyectos atrasados se detectan por su fecha estimada de fin")
        void deteccionDeAtrasados() {
            Proyecto aTiempo = new Proyecto();
            aTiempo.setNombre("En plazo");
            aTiempo.setCategoria("CONSULTORIA");
            aTiempo.setCliente(acme);
            aTiempo.setEstado("EN_EJECUCION");
            aTiempo.setFechaEstimadaFin(LocalDate.now().plusDays(10));
            proyectoService.save(aTiempo);

            Proyecto tarde = new Proyecto();
            tarde.setNombre("Fuera de plazo");
            tarde.setCategoria("LEGAL");
            tarde.setCliente(acme);
            tarde.setEstado("EN_EJECUCION");
            tarde.setFechaEstimadaFin(LocalDate.now().minusDays(3));
            proyectoService.save(tarde);

            assertThat(proyectoService.findAtrasados())
                    .extracting(Proyecto::getNombre).containsExactly("Fuera de plazo");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Panel y pantallas con un escenario realista")
    class PantallasConDatos {

        @BeforeEach
        void escenario() {
            // Historia de 3 meses con cobros, mora y un documento anulado
            contrato(acme, "RECURRENTE", "226.00", LocalDate.now().minusMonths(3));
            contrato(elRoble, "RECURRENTE", "339.00", LocalDate.now().minusMonths(2));
            contrato(natural, "PAGO_UNICO", "113.00", LocalDate.now().minusDays(2));
            facturacionService.generarLoteRecurrente("Marzo 2026", "CREDITO", 30);

            List<Factura> facturas = facturaRepository.findAll();
            facturacionService.registrarPago(facturas.get(0), null);
            envejecer(facturas.get(1), 2, 65);
            facturacionService.anular(facturas.get(2), "duplicado");

            Proyecto p = new Proyecto();
            p.setNombre("Reestructuración contable");
            p.setCategoria("CONSULTORIA");
            p.setCliente(elRoble);
            p.setPresupuesto(new BigDecimal("1800.00"));
            p.setEstado("FINALIZADO");
            proyectoService.save(p);
        }

        @Test
        @DisplayName("El panel muestra las bandejas coherentes con el escenario")
        void panelCoherente() throws Exception {
            mockMvc.perform(get("/inicio"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("kpis", "tendencia", "facturasVencidas",
                            "proyectosPorFacturar", "facturasRecientes"))
                    // La factura vencida a 65 días aparece
                    .andExpect(model().attribute("facturasVencidas",
                            org.hamcrest.Matchers.hasSize(1)))
                    // El proyecto finalizado espera factura
                    .andExpect(model().attribute("proyectosPorFacturar",
                            org.hamcrest.Matchers.hasSize(1)));
        }

        @Test
        @DisplayName("Todas las pantallas renderizan con datos reales")
        void todasLasPantallas() throws Exception {
            for (String ruta : List.of("/inicio", "/clientes", "/servicios", "/contratos",
                    "/proyectos", "/facturacion", "/reportes", "/configuracion/dte")) {
                mockMvc.perform(get(ruta))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Las cinco pestañas de reportes traen sus datos")
        void reportesCompletos() throws Exception {
            mockMvc.perform(get("/reportes"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("kpis", "tendencia", "antiguedad",
                            "libroVentas", "totalesLibro", "resumenProyectos",
                            "ingresosPorCategoria", "topClientes", "topFacturado",
                            "distribucionTipo", "distribucionFacturas", "carteraClientes"));
        }

        @Test
        @DisplayName("La hoja imprimible sale bien para cada documento emitido")
        void documentosImprimibles() throws Exception {
            for (Factura f : facturaRepository.findAll()) {
                mockMvc.perform(get("/facturacion/" + f.getId() + "/imprimir"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(
                                org.hamcrest.Matchers.containsString(f.getNumeroFactura())));
            }
        }

        @Test
        @DisplayName("Las exportaciones CSV salen con contenido, no vacías")
        void exportacionesConContenido() throws Exception {
            for (String ruta : List.of("/reportes/libro-ventas.csv",
                    "/reportes/antiguedad-saldos.csv", "/reportes/cartera-clientes.csv")) {
                String csv = mockMvc.perform(get(ruta))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();

                assertThat(csv.lines().count())
                        .as("el CSV %s solo trae la cabecera", ruta)
                        .isGreaterThan(1);
            }
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Robustez ante datos incompletos")
    class Robustez {

        @Test
        @DisplayName("Un contrato sin servicios no rompe la cartera ni los reportes")
        void contratoSinServicios() {
            Contrato c = new Contrato();
            c.setCliente(acme);
            c.setTipoFacturacion("RECURRENTE");
            c.setHonorariosPactados(new BigDecimal("100.00"));
            c.setFechaInicio(LocalDate.now().minusMonths(1));
            c.setEstado("ACTIVO");
            contratoRepository.save(c);

            assertThat(reportesService.getCarteraPorCliente()).hasSize(1);
            assertThat(reportesService.getKpis()).isNotNull();

            // Y aun así puede facturarse con una línea global
            Factura f = facturacionService.generarDesdeContrato(
                    contratoRepository.findById(c.getId()).orElseThrow(), "Marzo 2026", "CONTADO", null);
            assertThat(f.getDetalles()).hasSize(1);
            assertThat(f.getDetalles().get(0).getDescripcion()).contains("Servicios contratados");
        }

        @Test
        @DisplayName("Un proyecto sin cliente o sin presupuesto se rechaza al facturar")
        void proyectoIncompleto() {
            Proyecto sinCliente = new Proyecto();
            sinCliente.setNombre("Sin cliente");
            sinCliente.setPresupuesto(new BigDecimal("100.00"));
            sinCliente.setEstado("FINALIZADO");
            Proyecto p1 = proyectoService.save(sinCliente);

            assertThatThrownBy(() -> facturacionService.generarDesdeProyecto(p1, "CONTADO", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cliente");

            Proyecto sinPresupuesto = new Proyecto();
            sinPresupuesto.setNombre("Sin presupuesto");
            sinPresupuesto.setCliente(acme);
            sinPresupuesto.setEstado("FINALIZADO");
            Proyecto p2 = proyectoService.save(sinPresupuesto);

            assertThatThrownBy(() -> facturacionService.generarDesdeProyecto(p2, "CONTADO", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("presupuesto");
        }

        @Test
        @DisplayName("Una factura sin líneas o sin cliente se rechaza con mensaje claro")
        void facturaManualIncompleta() {
            Factura sinDetalles = new Factura();
            sinDetalles.setTipoDte("01");
            sinDetalles.setCliente(natural);

            assertThatThrownBy(() -> facturacionService.save(sinDetalles))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("detalle");
        }

        @Test
        @DisplayName("Un cliente con contratos no se puede borrar sin avisar")
        void borradoConDependencias() {
            Contrato c = contrato(acme, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
            assertThat(c.getId()).isNotNull();

            // El borrado del cliente debe fallar por integridad referencial
            assertThatThrownBy(() -> {
                clienteRepository.deleteById(acme.getId());
                clienteRepository.flush();
            }).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("La API responde 404 ante identificadores inexistentes")
        void identificadoresInexistentes() throws Exception {
            mockMvc.perform(get("/facturacion/999999")).andExpect(status().isNotFound());
            mockMvc.perform(get("/clientes/999999")).andExpect(status().isNotFound());
            mockMvc.perform(get("/contratos/999999")).andExpect(status().isNotFound());
            mockMvc.perform(get("/proyectos/999999")).andExpect(status().isNotFound());
            mockMvc.perform(get("/servicios/999999")).andExpect(status().isNotFound());
        }
    }
}
