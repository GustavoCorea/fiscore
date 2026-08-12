package com.fiscore.core.services;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.Factura;
import com.fiscore.core.repositories.ClienteRepository;
import com.fiscore.core.repositories.ContratoRepository;
import com.fiscore.core.repositories.FacturaRepository;
import com.fiscore.core.repositories.ProyectoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReportesService {

    private static final Locale ES_SV = Locale.forLanguageTag("es-SV");
    private static final BigDecimal CIEN = new BigDecimal("100");

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    // =================================================================
    // KPIs
    // =================================================================

    /** Indicadores globales del despacho. */
    public Map<String, Object> getKpis() {
        LocalDate hoy = LocalDate.now();
        YearMonth mesActual = YearMonth.from(hoy);

        BigDecimal facturado = facturaRepository.sumTotalFacturado();
        BigDecimal cobrado = facturaRepository.sumTotalCobrado();
        BigDecimal pendiente = facturaRepository.sumMontoPendiente();

        BigDecimal facturadoMes = facturaRepository.sumFacturadoEntre(
                mesActual.atDay(1).atStartOfDay(), mesActual.atEndOfMonth().atTime(23, 59, 59));
        BigDecimal ivaMes = facturaRepository.sumIvaEntre(
                mesActual.atDay(1).atStartOfDay(), mesActual.atEndOfMonth().atTime(23, 59, 59));

        BigDecimal vencido = BigDecimal.ZERO;
        List<Factura> vencidas = facturaRepository.findVencidas(hoy);
        for (Factura f : vencidas) {
            vencido = vencido.add(nvl(f.getMontoTotal()));
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("honorariosRecurrentes", escala(contratoRepository.sumHonorariosActivos()));
        kpis.put("totalFacturado", escala(facturado));
        kpis.put("totalCobrado", escala(cobrado));
        kpis.put("montoPendiente", escala(pendiente));
        kpis.put("montoVencido", escala(vencido));
        kpis.put("facturasVencidas", vencidas.size());
        kpis.put("facturadoMes", escala(facturadoMes));
        kpis.put("ivaMes", escala(ivaMes));
        kpis.put("contratosActivos", contratoRepository.countByEstado("ACTIVO"));
        kpis.put("proyectosActivos", proyectoRepository.countByEstado("EN_EJECUCION"));
        kpis.put("clientes", clienteRepository.count());
        kpis.put("presupuestoPendiente", escala(proyectoRepository.sumPresupuestoPendiente()));
        // Tasa de cobro = cobrado / facturado
        kpis.put("tasaCobro", porcentaje(cobrado, facturado));
        return kpis;
    }

    // =================================================================
    // Tendencia mensual
    // =================================================================

    /**
     * Serie mensual de facturación de los últimos {@code meses} meses (incluido el actual).
     * Los meses sin movimiento se devuelven en cero para que la gráfica no tenga huecos.
     */
    public List<Map<String, Object>> getTendenciaMensual(int meses) {
        int ventana = Math.max(1, Math.min(meses, 36));
        YearMonth fin = YearMonth.from(LocalDate.now());
        YearMonth inicio = fin.minusMonths(ventana - 1L);

        Map<String, Object[]> porMes = new HashMap<>();
        for (Object[] row : facturaRepository.resumenPorMes(
                inicio.atDay(1).atStartOfDay(), fin.plusMonths(1).atDay(1).atStartOfDay())) {
            porMes.put(String.valueOf(row[0]), row);
        }

        List<Map<String, Object>> serie = new ArrayList<>();
        for (int i = 0; i < ventana; i++) {
            YearMonth ym = inicio.plusMonths(i);
            String clave = String.format("%04d-%02d", ym.getYear(), ym.getMonthValue());
            Object[] row = porMes.get(clave);

            Map<String, Object> punto = new LinkedHashMap<>();
            punto.put("mes", clave);
            punto.put("label", etiquetaMes(ym));
            punto.put("facturado", row != null ? escala(toBigDecimal(row[1])) : BigDecimal.ZERO);
            punto.put("cobrado", row != null ? escala(toBigDecimal(row[2])) : BigDecimal.ZERO);
            punto.put("iva", row != null ? escala(toBigDecimal(row[3])) : BigDecimal.ZERO);
            punto.put("cantidad", row != null ? ((Number) row[4]).longValue() : 0L);
            serie.add(punto);
        }
        return serie;
    }

    // =================================================================
    // Antigüedad de saldos (cuentas por cobrar)
    // =================================================================

    /**
     * Clasifica las facturas emitidas pendientes de cobro en tramos de mora.
     * Devuelve los tramos con sus totales y el detalle documento a documento.
     */
    public Map<String, Object> getAntiguedadSaldos() {
        LocalDate hoy = LocalDate.now();

        // Orden significativo: de "sano" a "crítico".
        String[] tramos = {"Por vencer", "1 - 30 días", "31 - 60 días", "61 - 90 días", "Más de 90 días"};
        Map<String, BigDecimal> montos = new LinkedHashMap<>();
        Map<String, Integer> conteos = new LinkedHashMap<>();
        for (String t : tramos) {
            montos.put(t, BigDecimal.ZERO);
            conteos.put(t, 0);
        }

        List<Map<String, Object>> detalle = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Factura f : facturaRepository.findPendientesDeCobro()) {
            LocalDate vence = f.getFechaVencimiento() != null
                    ? f.getFechaVencimiento()
                    : (f.getFechaEmision() != null ? f.getFechaEmision().toLocalDate() : hoy);
            long diasMora = ChronoUnit.DAYS.between(vence, hoy);
            String tramo = tramoDe(diasMora);
            BigDecimal monto = escala(nvl(f.getMontoTotal()));

            montos.put(tramo, montos.get(tramo).add(monto));
            conteos.put(tramo, conteos.get(tramo) + 1);
            total = total.add(monto);

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", f.getId());
            fila.put("numeroFactura", f.getNumeroFactura());
            fila.put("tipoDte", f.getTipoDte());
            fila.put("cliente", f.getCliente() != null ? f.getCliente().getNombre() : "—");
            fila.put("fechaEmision", f.getFechaEmision() != null ? f.getFechaEmision().toLocalDate() : null);
            fila.put("fechaVencimiento", vence);
            fila.put("diasMora", Math.max(diasMora, 0));
            fila.put("monto", monto);
            fila.put("tramo", tramo);
            detalle.add(fila);
        }

        // Los más vencidos primero
        detalle.sort((a, b) -> Long.compare((Long) b.get("diasMora"), (Long) a.get("diasMora")));

        List<Map<String, Object>> resumen = new ArrayList<>();
        for (String t : tramos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tramo", t);
            item.put("monto", escala(montos.get(t)));
            item.put("cantidad", conteos.get(t));
            item.put("porcentaje", porcentaje(montos.get(t), total));
            resumen.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tramos", resumen);
        result.put("detalle", detalle);
        result.put("total", escala(total));
        result.put("cantidad", detalle.size());
        return result;
    }

    private String tramoDe(long diasMora) {
        if (diasMora <= 0) return "Por vencer";
        if (diasMora <= 30) return "1 - 30 días";
        if (diasMora <= 60) return "31 - 60 días";
        if (diasMora <= 90) return "61 - 90 días";
        return "Más de 90 días";
    }

    // =================================================================
    // Libro de ventas (resumen fiscal)
    // =================================================================

    /**
     * Resumen del libro de ventas del año indicado, separando contribuyentes
     * (CCF, tipo 03) de consumidor final (tipos 01/14), como exige el
     * Ministerio de Hacienda de El Salvador.
     */
    public List<Map<String, Object>> getLibroVentas(int anio) {
        LocalDateTime desde = LocalDate.of(anio, 1, 1).atStartOfDay();
        LocalDateTime hasta = LocalDate.of(anio + 1, 1, 1).atStartOfDay();

        Map<String, Map<String, Object>> porMes = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(anio, m);
            String clave = String.format("%04d-%02d", anio, m);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("mes", clave);
            fila.put("label", etiquetaMes(ym));
            fila.put("gravadoContribuyente", BigDecimal.ZERO);
            fila.put("gravadoConsumidor", BigDecimal.ZERO);
            fila.put("exento", BigDecimal.ZERO);
            fila.put("noSujeto", BigDecimal.ZERO);
            fila.put("iva", BigDecimal.ZERO);
            fila.put("total", BigDecimal.ZERO);
            fila.put("documentos", 0L);
            porMes.put(clave, fila);
        }

        for (Object[] row : facturaRepository.libroVentas(desde, hasta)) {
            Map<String, Object> fila = porMes.get(String.valueOf(row[0]));
            if (fila == null) continue;

            String tipoDte = String.valueOf(row[1]);
            BigDecimal gravado = toBigDecimal(row[2]);
            String campoGravado = "03".equals(tipoDte) ? "gravadoContribuyente" : "gravadoConsumidor";

            fila.put(campoGravado, ((BigDecimal) fila.get(campoGravado)).add(gravado));
            fila.put("exento", ((BigDecimal) fila.get("exento")).add(toBigDecimal(row[3])));
            fila.put("noSujeto", ((BigDecimal) fila.get("noSujeto")).add(toBigDecimal(row[4])));
            fila.put("iva", ((BigDecimal) fila.get("iva")).add(toBigDecimal(row[5])));
            fila.put("total", ((BigDecimal) fila.get("total")).add(toBigDecimal(row[6])));
            fila.put("documentos", (Long) fila.get("documentos") + ((Number) row[7]).longValue());
        }

        List<Map<String, Object>> libro = new ArrayList<>();
        for (Map<String, Object> fila : porMes.values()) {
            fila.replaceAll((k, v) -> v instanceof BigDecimal ? escala((BigDecimal) v) : v);
            libro.add(fila);
        }
        return libro;
    }

    /** Totales anuales del libro de ventas, para el pie de la tabla. */
    public Map<String, Object> getTotalesLibroVentas(int anio) {
        BigDecimal gravadoContribuyente = BigDecimal.ZERO;
        BigDecimal gravadoConsumidor = BigDecimal.ZERO;
        BigDecimal exento = BigDecimal.ZERO;
        BigDecimal noSujeto = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        long documentos = 0;

        for (Map<String, Object> fila : getLibroVentas(anio)) {
            gravadoContribuyente = gravadoContribuyente.add((BigDecimal) fila.get("gravadoContribuyente"));
            gravadoConsumidor = gravadoConsumidor.add((BigDecimal) fila.get("gravadoConsumidor"));
            exento = exento.add((BigDecimal) fila.get("exento"));
            noSujeto = noSujeto.add((BigDecimal) fila.get("noSujeto"));
            iva = iva.add((BigDecimal) fila.get("iva"));
            total = total.add((BigDecimal) fila.get("total"));
            documentos += (Long) fila.get("documentos");
        }

        Map<String, Object> totales = new LinkedHashMap<>();
        totales.put("anio", anio);
        totales.put("gravadoContribuyente", escala(gravadoContribuyente));
        totales.put("gravadoConsumidor", escala(gravadoConsumidor));
        totales.put("exento", escala(exento));
        totales.put("noSujeto", escala(noSujeto));
        totales.put("iva", escala(iva));
        totales.put("total", escala(total));
        totales.put("documentos", documentos);
        return totales;
    }

    // =================================================================
    // Proyectos
    // =================================================================

    /** Conteo y presupuesto de proyectos agrupados por estado y por categoría. */
    public Map<String, Object> getResumenProyectos() {
        List<Map<String, Object>> porEstado = new ArrayList<>();
        for (Object[] row : proyectoRepository.resumenPorEstado()) {
            porEstado.add(filaAgrupada(row));
        }
        List<Map<String, Object>> porCategoria = new ArrayList<>();
        for (Object[] row : proyectoRepository.resumenPorCategoria()) {
            porCategoria.add(filaAgrupada(row));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("porEstado", porEstado);
        result.put("porCategoria", porCategoria);
        result.put("presupuestoPendiente", escala(proyectoRepository.sumPresupuestoPendiente()));
        return result;
    }

    private Map<String, Object> filaAgrupada(Object[] row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("clave", row[0] != null ? row[0].toString() : "SIN CLASIFICAR");
        m.put("cantidad", ((Number) row[1]).longValue());
        m.put("monto", escala(toBigDecimal(row[2])));
        return m;
    }

    // =================================================================
    // Distribuciones y rankings
    // =================================================================

    /** Ingresos por categoría de servicio (contratos activos) */
    public Map<String, BigDecimal> getIngresosPorCategoria() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : contratoRepository.ingresosPorCategoria()) {
            String categoria = row[0] != null ? row[0].toString() : "SIN CATEGORÍA";
            result.merge(categoria, escala(toBigDecimal(row[1])), BigDecimal::add);
        }
        return result;
    }

    /** Top clientes por honorarios (incluye % relativo al máximo) */
    public List<Map<String, Object>> getTopClientesPorHonorarios(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Object[]> rows = contratoRepository.topClientesPorHonorarios();
        BigDecimal maxVal = BigDecimal.ONE; // evitar division by zero
        if (!rows.isEmpty()) {
            BigDecimal first = toBigDecimal(rows.get(0)[1]);
            if (first.compareTo(BigDecimal.ZERO) > 0) maxVal = first;
        }
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            Object[] row = rows.get(i);
            BigDecimal hon = escala(toBigDecimal(row[1]));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", row[0]);
            m.put("honorarios", hon);
            // Porcentaje relativo para la barra de progreso
            m.put("porcentaje", hon.multiply(CIEN).divide(maxVal, 0, RoundingMode.HALF_UP).intValue());
            result.add(m);
        }
        return result;
    }

    /** Top clientes por facturado */
    public List<Map<String, Object>> getTopClientesPorFacturado(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Object[]> rows = facturaRepository.topClientesPorFacturado();
        BigDecimal maxVal = BigDecimal.ONE;
        if (!rows.isEmpty()) {
            BigDecimal first = toBigDecimal(rows.get(0)[1]);
            if (first.compareTo(BigDecimal.ZERO) > 0) maxVal = first;
        }
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            Object[] row = rows.get(i);
            BigDecimal monto = escala(toBigDecimal(row[1]));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", row[0]);
            m.put("monto", monto);
            m.put("porcentaje", monto.multiply(CIEN).divide(maxVal, 0, RoundingMode.HALF_UP).intValue());
            result.add(m);
        }
        return result;
    }

    /** Distribución de contratos por tipo */
    public Map<String, Long> getDistribucionPorTipo() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : contratoRepository.distribucionPorTipo()) {
            String clave = row[0] != null ? row[0].toString() : "SIN TIPO";
            result.merge(clave, ((Number) row[1]).longValue(), Long::sum);
        }
        return result;
    }

    /** Distribución de facturas por estado */
    public Map<String, Long> getDistribucionFacturasPorEstado() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : facturaRepository.distribucionPorEstado()) {
            String clave = row[0] != null ? row[0].toString() : "SIN ESTADO";
            result.merge(clave, ((Number) row[1]).longValue(), Long::sum);
        }
        return result;
    }

    /**
     * Cartera agrupada por cliente: mapa de cliente → lista de contratos activos.
     * Permite ver todos los servicios (recurrentes + eventuales) de cada cliente.
     */
    public List<Map<String, Object>> getCarteraPorCliente() {
        List<Contrato> contratos = contratoRepository.findActivosConDetalle();

        // Agrupar por cliente manteniendo orden
        Map<Long, Map<String, Object>> porCliente = new LinkedHashMap<>();
        for (Contrato c : contratos) {
            Cliente cl = c.getCliente();
            if (cl == null) continue;

            Map<String, Object> entry = porCliente.computeIfAbsent(cl.getId(), id -> {
                Map<String, Object> nuevo = new LinkedHashMap<>();
                nuevo.put("cliente", cl);
                nuevo.put("contratos", new ArrayList<Contrato>());
                nuevo.put("totalMensual", BigDecimal.ZERO);
                nuevo.put("totalEventual", BigDecimal.ZERO);
                nuevo.put("cantRecurrentes", 0);
                nuevo.put("cantEventuales", 0);
                return nuevo;
            });

            @SuppressWarnings("unchecked")
            List<Contrato> lista = (List<Contrato>) entry.get("contratos");
            lista.add(c);

            BigDecimal hon = nvl(c.getHonorariosPactados());
            if ("RECURRENTE".equals(c.getTipoFacturacion())) {
                entry.put("totalMensual", ((BigDecimal) entry.get("totalMensual")).add(hon));
                entry.put("cantRecurrentes", (int) entry.get("cantRecurrentes") + 1);
            } else {
                entry.put("totalEventual", ((BigDecimal) entry.get("totalEventual")).add(hon));
                entry.put("cantEventuales", (int) entry.get("cantEventuales") + 1);
            }
        }

        return new ArrayList<>(porCliente.values());
    }

    // =================================================================
    // Utilidades
    // =================================================================

    private static String etiquetaMes(YearMonth ym) {
        String mes = ym.getMonth().getDisplayName(TextStyle.SHORT, ES_SV).replace(".", "");
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + ym.getYear();
    }

    private static BigDecimal toBigDecimal(Object valor) {
        if (valor == null) return BigDecimal.ZERO;
        if (valor instanceof BigDecimal bd) return bd;
        return new BigDecimal(valor.toString());
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static BigDecimal escala(BigDecimal valor) {
        return nvl(valor).setScale(2, RoundingMode.HALF_UP);
    }

    /** Porcentaje de {@code parte} sobre {@code total}, con un decimal. */
    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO.setScale(1);
        return nvl(parte).multiply(CIEN).divide(total, 1, RoundingMode.HALF_UP);
    }
}
