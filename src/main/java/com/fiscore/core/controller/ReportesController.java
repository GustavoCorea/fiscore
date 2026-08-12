package com.fiscore.core.controller;

import com.fiscore.core.services.ReportesService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reportes")
public class ReportesController {

    private final ReportesService reportesService;

    public ReportesController(ReportesService reportesService) {
        this.reportesService = reportesService;
    }

    @GetMapping("/kpis")
    @ResponseBody
    public ResponseEntity<?> getKpis() {
        return ResponseEntity.ok(reportesService.getKpis());
    }

    @GetMapping("/por-categoria")
    @ResponseBody
    public ResponseEntity<?> getPorCategoria() {
        return ResponseEntity.ok(reportesService.getIngresosPorCategoria());
    }

    @GetMapping("/top-clientes-honorarios")
    @ResponseBody
    public ResponseEntity<?> getTopClientesHonorarios() {
        return ResponseEntity.ok(reportesService.getTopClientesPorHonorarios(10));
    }

    @GetMapping("/top-clientes-facturado")
    @ResponseBody
    public ResponseEntity<?> getTopClientesFacturado() {
        return ResponseEntity.ok(reportesService.getTopClientesPorFacturado(10));
    }

    @GetMapping("/distribucion-tipo")
    @ResponseBody
    public ResponseEntity<?> getDistribucionTipo() {
        return ResponseEntity.ok(reportesService.getDistribucionPorTipo());
    }

    @GetMapping("/distribucion-facturas")
    @ResponseBody
    public ResponseEntity<?> getDistribucionFacturas() {
        return ResponseEntity.ok(reportesService.getDistribucionFacturasPorEstado());
    }

    @GetMapping("/cartera-clientes")
    @ResponseBody
    public ResponseEntity<?> getCarteraClientes() {
        return ResponseEntity.ok(reportesService.getCarteraPorCliente());
    }

    @GetMapping("/tendencia")
    @ResponseBody
    public ResponseEntity<?> getTendencia(@RequestParam(defaultValue = "12") int meses) {
        return ResponseEntity.ok(reportesService.getTendenciaMensual(meses));
    }

    @GetMapping("/antiguedad-saldos")
    @ResponseBody
    public ResponseEntity<?> getAntiguedadSaldos() {
        return ResponseEntity.ok(reportesService.getAntiguedadSaldos());
    }

    @GetMapping("/libro-ventas")
    @ResponseBody
    public ResponseEntity<?> getLibroVentas(@RequestParam(required = false) Integer anio) {
        int periodo = anio != null ? anio : LocalDate.now().getYear();
        return ResponseEntity.ok(Map.of(
                "anio", periodo,
                "meses", reportesService.getLibroVentas(periodo),
                "totales", reportesService.getTotalesLibroVentas(periodo)));
    }

    @GetMapping("/proyectos")
    @ResponseBody
    public ResponseEntity<?> getResumenProyectos() {
        return ResponseEntity.ok(reportesService.getResumenProyectos());
    }

    // =================================================================
    // Exportaciones CSV
    // =================================================================

    /** Libro de ventas del año en CSV, listo para abrir en Excel. */
    @GetMapping("/libro-ventas.csv")
    @ResponseBody
    public ResponseEntity<byte[]> exportarLibroVentas(@RequestParam(required = false) Integer anio) {
        int periodo = anio != null ? anio : LocalDate.now().getYear();
        StringBuilder csv = new StringBuilder(
                "Mes;Gravado contribuyentes (CCF);Gravado consumidor final;Exento;No sujeto;IVA debito;Total;Documentos\n");

        for (Map<String, Object> fila : reportesService.getLibroVentas(periodo)) {
            csv.append(fila.get("label")).append(';')
               .append(fila.get("gravadoContribuyente")).append(';')
               .append(fila.get("gravadoConsumidor")).append(';')
               .append(fila.get("exento")).append(';')
               .append(fila.get("noSujeto")).append(';')
               .append(fila.get("iva")).append(';')
               .append(fila.get("total")).append(';')
               .append(fila.get("documentos")).append('\n');
        }

        Map<String, Object> t = reportesService.getTotalesLibroVentas(periodo);
        csv.append("TOTAL ").append(periodo).append(';')
           .append(t.get("gravadoContribuyente")).append(';')
           .append(t.get("gravadoConsumidor")).append(';')
           .append(t.get("exento")).append(';')
           .append(t.get("noSujeto")).append(';')
           .append(t.get("iva")).append(';')
           .append(t.get("total")).append(';')
           .append(t.get("documentos")).append('\n');

        return csvResponse(csv.toString(), "libro-ventas-" + periodo + ".csv");
    }

    /** Antigüedad de saldos en CSV. */
    @GetMapping("/antiguedad-saldos.csv")
    @ResponseBody
    public ResponseEntity<byte[]> exportarAntiguedadSaldos() {
        Map<String, Object> datos = reportesService.getAntiguedadSaldos();
        StringBuilder csv = new StringBuilder("N Factura;Tipo DTE;Cliente;Emision;Vencimiento;Dias mora;Tramo;Monto\n");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detalle = (List<Map<String, Object>>) datos.get("detalle");
        for (Map<String, Object> fila : detalle) {
            csv.append(texto(fila.get("numeroFactura"))).append(';')
               .append(texto(fila.get("tipoDte"))).append(';')
               .append(texto(fila.get("cliente"))).append(';')
               .append(nvl(fila.get("fechaEmision"))).append(';')
               .append(nvl(fila.get("fechaVencimiento"))).append(';')
               .append(nvl(fila.get("diasMora"))).append(';')
               .append(texto(fila.get("tramo"))).append(';')
               .append(nvl(fila.get("monto"))).append('\n');
        }
        csv.append("TOTAL;;;;;;;").append(datos.getOrDefault("total", BigDecimal.ZERO)).append('\n');

        return csvResponse(csv.toString(), "antiguedad-saldos.csv");
    }

    /** Cartera de servicios por cliente en CSV. */
    @GetMapping("/cartera-clientes.csv")
    @ResponseBody
    public ResponseEntity<byte[]> exportarCartera() {
        StringBuilder csv = new StringBuilder("Cliente;NIT;Contratos recurrentes;Contratos eventuales;Total recurrente;Total eventual\n");

        for (Map<String, Object> entry : reportesService.getCarteraPorCliente()) {
            com.fiscore.core.models.Cliente cliente = (com.fiscore.core.models.Cliente) entry.get("cliente");
            csv.append(texto(cliente.getNombre())).append(';')
               .append(texto(cliente.getNit())).append(';')
               .append(entry.get("cantRecurrentes")).append(';')
               .append(entry.get("cantEventuales")).append(';')
               .append(entry.get("totalMensual")).append(';')
               .append(entry.get("totalEventual")).append('\n');
        }
        return csvResponse(csv.toString(), "cartera-clientes.csv");
    }

    private ResponseEntity<byte[]> csvResponse(String contenido, String nombreArchivo) {
        // BOM UTF-8 para que Excel respete los acentos
        byte[] cuerpo = ("﻿" + contenido).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(cuerpo);
    }

    /** Valores numéricos y fechas: se emiten tal cual, sin escapar. */
    private static String nvl(Object valor) {
        return valor != null ? valor.toString() : "";
    }

    /** Valores de texto que provienen de datos capturados por el usuario. */
    private static String texto(Object valor) {
        return escapar(valor != null ? valor.toString() : "");
    }

    /**
     * Deja el texto seguro para un CSV: quita el separador y los saltos de línea
     * y neutraliza la inyección de fórmulas. Un nombre que empiece por
     * {@code = + - @} lo ejecutaría Excel al abrir el archivo, así que se
     * antepone un apóstrofo, que Excel interpreta como "esto es texto".
     */
    private static String escapar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String limpio = texto.replace(';', ',')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');

        if ("=+-@".indexOf(limpio.charAt(0)) >= 0) {
            return "'" + limpio;
        }
        return limpio;
    }
}
