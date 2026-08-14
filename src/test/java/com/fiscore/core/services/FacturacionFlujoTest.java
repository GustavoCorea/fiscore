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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre los flujos de trabajo de facturación: emisión desde contrato,
 * avance del ciclo, control de duplicados, cobro, anulación y proyectos.
 */
@SpringBootTest
@ActiveProfiles("test")
class FacturacionFlujoTest {

    @Autowired private FacturacionService facturacionService;
    @Autowired private ContratoService contratoService;
    @Autowired private ProyectoService proyectoService;
    @Autowired private ReportesService reportesService;

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private DteCorrelativoRepository correlativoRepository;
    @Autowired private com.fiscore.core.services.CorrelativoService correlativoService;
    @Autowired private com.fiscore.core.repositories.RegistroHorasRepository registroHorasRepository;
    @Autowired private ProyectoRepository proyectoRepository;

    private Cliente contribuyente;
    private Cliente consumidorFinal;
    private Servicio contabilidad;

    @BeforeEach
    void limpiarYSembrar() {
        facturaRepository.deleteAll();
        // Los correlativos no se reutilizan: para partir de 1 hay que reiniciarlos
        correlativoRepository.deleteAll();
        contratoRepository.deleteAll();
        registroHorasRepository.deleteAll();   // apuntan a proyecto
        proyectoRepository.deleteAll();
        clienteRepository.deleteAll();
        servicioRepository.deleteAll();
        correlativoService.inicializar();  // como al arrancar la aplicación

        contribuyente = clienteRepository.save(cliente("Inversiones ACME, S.A. de C.V.", "0614-010101-001-0", "123456-7"));
        consumidorFinal = clienteRepository.save(cliente("Juan Pérez", "01234567-8", null));

        contabilidad = new Servicio();
        contabilidad.setNombre("Contabilidad mensual");
        contabilidad.setCategoria("CONTABILIDAD");
        contabilidad.setPeriodicidad("MENSUAL");
        contabilidad.setPrecio(new BigDecimal("250.00"));
        contabilidad = servicioRepository.save(contabilidad);
    }

    private Cliente cliente(String nombre, String nit, String nrc) {
        Cliente c = new Cliente();
        c.setNombre(nombre);
        c.setNit(nit);
        c.setNrc(nrc);
        c.setEstado("ACTIVO");
        c.setEmail("contacto@ejemplo.sv");
        return c;
    }

    private Contrato contratoDe(Cliente cliente, String tipo, String monto, LocalDate inicio) {
        Contrato contrato = new Contrato();
        contrato.setCliente(cliente);
        contrato.setTipoFacturacion(tipo);
        contrato.setHonorariosPactados(new BigDecimal(monto));
        contrato.setFechaInicio(inicio);
        contrato.setEstado("ACTIVO");

        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(contabilidad);
        cs.setPrecioAcordado(new BigDecimal(monto));
        contrato.getServicios().add(cs);

        return contratoService.save(contrato);
    }

    // =================================================================

    @Test
    @DisplayName("Un contrato con NRC genera CCF (03) y desagrega el IVA cuadrando el total")
    void generaCcfConIvaCuadrado() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));

        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        assertThat(factura.getTipoDte()).isEqualTo("03");
        assertThat(factura.getNumeroFactura()).startsWith("CCF-");
        assertThat(factura.getNumeroControl()).matches("DTE-03-[A-Z0-9]+-\\d{15}");
        assertThat(factura.getCodigoGeneracion()).isNotBlank();
        assertThat(factura.getEstado()).isEqualTo("EMITIDA");

        // 226.00 con IVA incluido => base 200.00 + IVA 26.00
        assertThat(factura.getSubtotalGravado()).isEqualByComparingTo("200.00");
        assertThat(factura.getIvaPercibido()).isEqualByComparingTo("26.00");
        assertThat(factura.getMontoTotal()).isEqualByComparingTo("226.00");
    }

    @Test
    @DisplayName("Un contrato sin NRC genera Factura de consumidor final (01)")
    void generaFacturaConsumidorFinal() {
        Contrato contrato = contratoDe(consumidorFinal, "RECURRENTE", "113.00", LocalDate.now().minusMonths(1));

        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        assertThat(factura.getTipoDte()).isEqualTo("01");
        assertThat(factura.getNumeroFactura()).startsWith("FAC-");
    }

    @Test
    @DisplayName("Emitir la factura adelanta el ciclo del contrato recurrente a una fecha futura")
    void emitirAvanzaElCiclo() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(2));
        assertThat(contratoService.findPendientesDeFacturar()).hasSize(1);

        facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        Contrato actualizado = contratoRepository.findById(contrato.getId()).orElseThrow();
        assertThat(actualizado.getFechaProximaFacturacion()).isAfter(LocalDate.now());
        // Ya no aparece en la bandeja "por emitir"
        assertThat(contratoService.findPendientesDeFacturar()).isEmpty();
    }

    @Test
    @DisplayName("No se puede facturar dos veces el mismo contrato en el mismo periodo")
    void bloqueaFacturacionDuplicada() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        Contrato recargado = contratoRepository.findById(contrato.getId()).orElseThrow();
        assertThatThrownBy(() -> facturacionService.generarDesdeContrato(recargado, "Marzo 2026", "CONTADO", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya existe una factura vigente");
    }

    @Test
    @DisplayName("La condición de crédito fija la fecha de vencimiento")
    void creditoFijaVencimiento() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));

        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CREDITO", 45);

        assertThat(factura.getPlazoCredito()).isEqualTo(45);
        assertThat(factura.getFechaVencimiento()).isEqualTo(LocalDate.now().plusDays(45));
    }

    @Test
    @DisplayName("La facturación masiva emite los ciclos vencidos y reporta los omitidos")
    void facturacionMasiva() {
        contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        contratoDe(consumidorFinal, "RECURRENTE", "113.00", LocalDate.now().minusMonths(1));

        Map<String, Object> resumen = facturacionService.generarLoteRecurrente("Marzo 2026", "CONTADO", null);

        assertThat(resumen.get("cantidadEmitidas")).isEqualTo(2);
        assertThat(resumen.get("cantidadOmitidas")).isEqualTo(0);
        assertThat((BigDecimal) resumen.get("montoTotal")).isEqualByComparingTo("339.00");
    }

    @Test
    @DisplayName("Registrar el pago marca la factura como pagada con fecha")
    void registrarPago() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        Factura pagada = facturacionService.cambiarEstado(factura, "PAGADA", null);

        assertThat(pagada.getEstado()).isEqualTo("PAGADA");
        assertThat(pagada.getFechaPago()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Anular conserva el documento y deja de contar como venta")
    void anularConservaDocumento() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        facturacionService.anular(factura, "error en el monto");

        Factura anulada = facturaRepository.findById(factura.getId()).orElseThrow();
        assertThat(anulada.getEstado()).isEqualTo("ANULADA");
        assertThat(anulada.getNotas()).contains("error en el monto");
        assertThat(facturaRepository.sumTotalFacturado()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Una factura emitida no se puede borrar: solo anular")
    void noSeBorranFacturasEmitidas() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        assertThatThrownBy(() -> facturacionService.deleteById(factura.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debe anularse");
    }

    @Test
    @DisplayName("Facturar un proyecto lo marca como FACTURADO y anularlo lo devuelve a FINALIZADO")
    void flujoDeProyecto() {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Constitución de sociedad");
        proyecto.setCategoria("LEGAL");
        proyecto.setCliente(contribuyente);
        proyecto.setPresupuesto(new BigDecimal("565.00"));
        proyecto.setEstado("FINALIZADO");
        proyecto = proyectoService.save(proyecto);

        assertThat(proyectoService.findFinalizadosSinFacturar()).hasSize(1);

        Factura factura = facturacionService.generarDesdeProyecto(proyecto, "CONTADO", null);
        assertThat(factura.getMontoTotal()).isEqualByComparingTo("565.00");
        assertThat(proyectoRepository.findById(proyecto.getId()).orElseThrow().getEstado()).isEqualTo("FACTURADO");
        assertThat(proyectoService.findFinalizadosSinFacturar()).isEmpty();

        facturacionService.anular(factura, "cliente desistió");
        Proyecto reabierto = proyectoRepository.findById(proyecto.getId()).orElseThrow();
        assertThat(reabierto.getEstado()).isEqualTo("FINALIZADO");
        assertThat(reabierto.getFacturado()).isFalse();
    }

    @Test
    @DisplayName("Los correlativos son independientes por tipo de DTE")
    void correlativosPorTipo() {
        Contrato ccf = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Contrato cf = contratoDe(consumidorFinal, "RECURRENTE", "113.00", LocalDate.now().minusMonths(1));

        Factura f1 = facturacionService.generarDesdeContrato(ccf, "Enero 2026", "CONTADO", null);
        Factura f2 = facturacionService.generarDesdeContrato(cf, "Enero 2026", "CONTADO", null);

        assertThat(f1.getNumeroFactura()).isEqualTo("CCF-00001");
        assertThat(f2.getNumeroFactura()).isEqualTo("FAC-00001");
    }

    @Test
    @DisplayName("El CCF exige NRC del receptor en la captura manual")
    void ccfExigeNrc() {
        Factura factura = new Factura();
        factura.setTipoDte("03");
        factura.setCliente(consumidorFinal); // sin NRC
        DetalleFactura d = new DetalleFactura();
        d.setDescripcion("Asesoría");
        d.setCantidad(BigDecimal.ONE);
        d.setPrecioUnitario(new BigDecimal("100.00"));
        d.setVentaGravada(new BigDecimal("100.00"));
        factura.setDetalles(List.of(d));

        assertThatThrownBy(() -> facturacionService.save(factura))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NRC");
    }

    @Test
    @DisplayName("La captura manual calcula IVA sobre la base gravada menos descuento")
    void capturaManualCalculaTotales() {
        Factura factura = new Factura();
        factura.setTipoDte("01");
        factura.setCliente(consumidorFinal);
        factura.setDescuento(new BigDecimal("100.00"));

        DetalleFactura gravado = new DetalleFactura();
        gravado.setDescripcion("Consultoría");
        gravado.setCantidad(BigDecimal.ONE);
        gravado.setPrecioUnitario(new BigDecimal("1000.00"));
        gravado.setVentaGravada(new BigDecimal("1000.00"));

        DetalleFactura exento = new DetalleFactura();
        exento.setDescripcion("Servicio exento");
        exento.setCantidad(BigDecimal.ONE);
        exento.setPrecioUnitario(new BigDecimal("50.00"));
        exento.setVentaExenta(new BigDecimal("50.00"));

        factura.setDetalles(List.of(gravado, exento));
        Factura guardada = facturacionService.save(factura);

        assertThat(guardada.getSubtotalGravado()).isEqualByComparingTo("1000.00");
        assertThat(guardada.getSubtotalExento()).isEqualByComparingTo("50.00");
        // IVA sobre (1000 - 100) = 117.00 ; total = 1000 + 50 - 100 + 117
        assertThat(guardada.getIvaPercibido()).isEqualByComparingTo("117.00");
        assertThat(guardada.getMontoTotal()).isEqualByComparingTo("1067.00");
    }

    // =================================================================
    // Reportes
    // =================================================================

    @Test
    @DisplayName("La antigüedad de saldos clasifica los documentos según su mora")
    void antiguedadDeSaldos() {
        Contrato contrato = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Factura factura = facturacionService.generarDesdeContrato(contrato, "Marzo 2026", "CONTADO", null);

        // Se fuerza un vencimiento de 45 días atrás
        factura.setFechaVencimiento(LocalDate.now().minusDays(45));
        facturaRepository.save(factura);

        Map<String, Object> antiguedad = reportesService.getAntiguedadSaldos();

        assertThat(antiguedad.get("cantidad")).isEqualTo(1);
        assertThat((BigDecimal) antiguedad.get("total")).isEqualByComparingTo("226.00");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detalle = (List<Map<String, Object>>) antiguedad.get("detalle");
        assertThat(detalle.get(0).get("tramo")).isEqualTo("31 - 60 días");
        assertThat(detalle.get(0).get("diasMora")).isEqualTo(45L);
    }

    @Test
    @DisplayName("El libro de ventas separa contribuyentes de consumidor final y suma el IVA")
    void libroDeVentas() {
        Contrato ccf = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Contrato cf = contratoDe(consumidorFinal, "RECURRENTE", "113.00", LocalDate.now().minusMonths(1));
        facturacionService.generarDesdeContrato(ccf, "Marzo 2026", "CONTADO", null);
        facturacionService.generarDesdeContrato(cf, "Marzo 2026", "CONTADO", null);

        Map<String, Object> totales = reportesService.getTotalesLibroVentas(LocalDate.now().getYear());

        assertThat((BigDecimal) totales.get("gravadoContribuyente")).isEqualByComparingTo("200.00");
        assertThat((BigDecimal) totales.get("gravadoConsumidor")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) totales.get("iva")).isEqualByComparingTo("39.00");
        assertThat((BigDecimal) totales.get("total")).isEqualByComparingTo("339.00");
        assertThat(totales.get("documentos")).isEqualTo(2L);
    }

    @Test
    @DisplayName("La tendencia mensual devuelve la ventana completa sin huecos")
    void tendenciaMensualSinHuecos() {
        List<Map<String, Object>> tendencia = reportesService.getTendenciaMensual(12);

        assertThat(tendencia).hasSize(12);
        assertThat(tendencia.get(11).get("mes")).isEqualTo(
                String.format("%04d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue()));
        assertThat(tendencia).allSatisfy(punto -> assertThat(punto.get("facturado")).isNotNull());
    }

    @Test
    @DisplayName("Los KPIs reflejan lo facturado, lo cobrado y la tasa de cobro")
    void kpisGlobales() {
        Contrato ccf = contratoDe(contribuyente, "RECURRENTE", "226.00", LocalDate.now().minusMonths(1));
        Contrato cf = contratoDe(consumidorFinal, "RECURRENTE", "113.00", LocalDate.now().minusMonths(1));
        Factura pagada = facturacionService.generarDesdeContrato(ccf, "Marzo 2026", "CONTADO", null);
        facturacionService.generarDesdeContrato(cf, "Marzo 2026", "CONTADO", null);
        facturacionService.registrarPago(pagada, null);

        Map<String, Object> kpis = reportesService.getKpis();

        assertThat((BigDecimal) kpis.get("totalFacturado")).isEqualByComparingTo("339.00");
        assertThat((BigDecimal) kpis.get("totalCobrado")).isEqualByComparingTo("226.00");
        assertThat((BigDecimal) kpis.get("montoPendiente")).isEqualByComparingTo("113.00");
        assertThat((BigDecimal) kpis.get("tasaCobro")).isEqualByComparingTo("66.7");
    }
}
