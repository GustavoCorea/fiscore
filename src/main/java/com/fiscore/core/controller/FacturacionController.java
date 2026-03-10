package com.fiscore.core.controller;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.Factura;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/facturacion")
public class FacturacionController {

    private final LoginController loginController;
    private final FacturacionService facturacionService;
    private final ContratoService contratoService;

    public FacturacionController(LoginController loginController, FacturacionService facturacionService, ContratoService contratoService) {
        this.loginController = loginController;
        this.facturacionService = facturacionService;
        this.contratoService = contratoService;
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listarFacturas(@RequestParam(required = false) String estado) {
        List<Factura> facturas = estado != null && !estado.isBlank()
                ? facturacionService.findByEstado(estado)
                : facturacionService.findAll();
        return ResponseEntity.ok(facturas);
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getFactura(@PathVariable Long id) {
        Optional<Factura> f = facturacionService.findById(id);
        return f.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Genera factura desde un contrato existente
     */
    @PostMapping("/generar/{contratoId}")
    @ResponseBody
    public ResponseEntity<?> generarDesdeContrato(
            @PathVariable Long contratoId,
            @RequestBody Map<String, Object> body) {
        Optional<Contrato> contratoOpt = contratoService.findById(contratoId);
        if (contratoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            String periodo = body.getOrDefault("periodoFacturado", "").toString();
            String condicion = body.getOrDefault("condicionPago", "CONTADO").toString();
            Object plazo = body.get("plazoCredito");
            Integer plazoDias = plazo != null && !plazo.toString().isBlank() ? Integer.valueOf(plazo.toString()) : null;

            Factura factura = facturacionService.generarDesdContrato(contratoOpt.get(), periodo, condicion, plazoDias);
            return ResponseEntity.ok(Map.of(
                    "message", "Factura generada exitosamente",
                    "id", factura.getId(),
                    "numeroFactura", factura.getNumeroFactura()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Crea una factura manual
     */
    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarFactura(@RequestBody Factura factura) {
        try {
            Factura saved = facturacionService.save(factura);
            return ResponseEntity.ok(Map.of(
                    "message", "Factura guardada exitosamente",
                    "id", saved.getId(),
                    "numeroFactura", saved.getNumeroFactura()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cambia el estado de una factura (PAGADA, ANULADA, etc.)
     */
    @PatchMapping("/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return facturacionService.findById(id).map(f -> {
            f.setEstado(body.get("estado"));
            facturacionService.save(f);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarFactura(@PathVariable Long id) {
        if (!facturacionService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        facturacionService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Factura eliminada exitosamente"));
    }

    @GetMapping("/pendiente-cobro")
    @ResponseBody
    public ResponseEntity<?> getMontoPendiente() {
        BigDecimal monto = facturacionService.getMontoPendiente();
        return ResponseEntity.ok(Map.of("monto", monto));
    }
}