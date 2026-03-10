package com.fiscore.core.services;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.repositories.ContratoRepository;
import com.fiscore.core.repositories.FacturaRepository;
import com.fiscore.core.repositories.ProyectoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ReportesService {

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ProyectoRepository proyectoRepository;

    /** KPIs globales */
    public Map<String, Object> getKpis() {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("honorariosRecurrentes", contratoRepository.sumHonorariosActivos());
        kpis.put("totalFacturado", facturaRepository.sumTotalFacturado());
        kpis.put("montoPendiente", facturaRepository.sumMontoPendiente());
        kpis.put("contratosActivos", contratoRepository.countByEstado("ACTIVO"));
        kpis.put("proyectosActivos", proyectoRepository.countByEstado("EN_EJECUCION"));
        return kpis;
    }

    /** Ingresos por categoría de servicio (contratos activos) */
    public Map<String, BigDecimal> getIngresosPorCategoria() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : contratoRepository.ingresosPorCategoria()) {
            result.put(row[0].toString(), ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    /** Top clientes por honorarios (incluye % relativo al máximo) */
    public List<Map<String, Object>> getTopClientesPorHonorarios(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Object[]> rows = contratoRepository.topClientesPorHonorarios();
        BigDecimal maxVal = BigDecimal.ONE; // evitar division by zero
        if (!rows.isEmpty()) {
            BigDecimal first = (BigDecimal) rows.get(0)[1];
            if (first.compareTo(BigDecimal.ZERO) > 0) maxVal = first;
        }
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            Object[] row = rows.get(i);
            BigDecimal hon = ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", row[0]);
            m.put("honorarios", hon);
            // Porcentaje relativo para la barra de progreso
            int pct = hon.multiply(new BigDecimal("100")).divide(maxVal, 0, RoundingMode.HALF_UP).intValue();
            m.put("porcentaje", pct);
            result.add(m);
        }
        return result;
    }

    /** Top clientes por facturado */
    public List<Map<String, Object>> getTopClientesPorFacturado(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Object[]> rows = facturaRepository.topClientesPorFacturado();
        for (int i = 0; i < Math.min(limit, rows.size()); i++) {
            Object[] row = rows.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nombre", row[0]);
            m.put("monto", ((BigDecimal) row[1]).setScale(2, RoundingMode.HALF_UP));
            result.add(m);
        }
        return result;
    }

    /** Distribución de contratos por tipo */
    public Map<String, Long> getDistribucionPorTipo() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : contratoRepository.distribucionPorTipo()) {
            result.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return result;
    }

    /** Distribución de facturas por estado */
    public Map<String, Long> getDistribucionFacturasPorEstado() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : facturaRepository.distribucionPorEstado()) {
            result.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return result;
    }

    /**
     * Cartera agrupada por cliente: mapa de cliente → lista de contratos activos
     * Permite ver todos los servicios (recurrentes + eventuales) de cada cliente
     */
    public List<Map<String, Object>> getCarteraPorCliente() {
        List<Contrato> contratos = contratoRepository.findActivosConDetalle();

        // Agrupar por cliente manteniendo orden
        Map<Long, Map<String, Object>> porCliente = new LinkedHashMap<>();
        for (Contrato c : contratos) {
            Cliente cl = c.getCliente();
            porCliente.computeIfAbsent(cl.getId(), id -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("cliente", cl);
                entry.put("contratos", new ArrayList<Contrato>());
                entry.put("totalMensual", BigDecimal.ZERO);
                entry.put("totalEventual", BigDecimal.ZERO);
                entry.put("cantRecurrentes", 0);
                entry.put("cantEventuales", 0);
                return entry;
            });

            Map<String, Object> entry = porCliente.get(cl.getId());
            ((List<Contrato>) entry.get("contratos")).add(c);

            if ("RECURRENTE".equals(c.getTipoFacturacion())) {
                BigDecimal total = (BigDecimal) entry.get("totalMensual");
                BigDecimal hon = c.getHonorariosPactados() != null ? c.getHonorariosPactados() : BigDecimal.ZERO;
                entry.put("totalMensual", total.add(hon));
                entry.put("cantRecurrentes", (int) entry.get("cantRecurrentes") + 1);
            } else {
                BigDecimal total = (BigDecimal) entry.get("totalEventual");
                BigDecimal hon = c.getHonorariosPactados() != null ? c.getHonorariosPactados() : BigDecimal.ZERO;
                entry.put("totalEventual", total.add(hon));
                entry.put("cantEventuales", (int) entry.get("cantEventuales") + 1);
            }
        }

        return new ArrayList<>(porCliente.values());
    }
}
