package com.fiscore.core.controller;

import com.fiscore.core.services.ReportesService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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
}
