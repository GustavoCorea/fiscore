package com.fiscore.core.controller;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.Factura;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.services.ConfiguracionDteService;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
import com.fiscore.core.services.ProyectoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/facturacion")
public class FacturacionController {

    private final FacturacionService facturacionService;
    private final ContratoService contratoService;
    private final ProyectoService proyectoService;
    private final ConfiguracionDteService configuracion;

    public FacturacionController(FacturacionService facturacionService,
                                 ContratoService contratoService,
                                 ProyectoService proyectoService,
                                 ConfiguracionDteService configuracion) {
        this.facturacionService = facturacionService;
        this.contratoService = contratoService;
        this.proyectoService = proyectoService;
        this.configuracion = configuracion;
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

    /** Genera una factura a partir de un contrato existente. */
    @PostMapping("/generar/{contratoId}")
    @ResponseBody
    public ResponseEntity<?> generarDesdeContrato(@PathVariable Long contratoId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        Contrato contrato = contratoService.findById(contratoId)
                .orElseThrow(() -> new IllegalArgumentException("Contrato no encontrado."));

        Map<String, Object> datos = body != null ? body : Map.of();
        Factura factura = facturacionService.generarDesdeContrato(
                contrato,
                texto(datos.get("periodoFacturado")),
                texto(datos.get("condicionPago")),
                entero(datos.get("plazoCredito")));

        return ResponseEntity.ok(Map.of(
                "message", "Factura generada exitosamente",
                "id", factura.getId(),
                "numeroFactura", factura.getNumeroFactura(),
                "montoTotal", factura.getMontoTotal()));
    }

    /** Genera la factura de un proyecto/caso finalizado. */
    @PostMapping("/generar-proyecto/{proyectoId}")
    @ResponseBody
    public ResponseEntity<?> generarDesdeProyecto(@PathVariable Long proyectoId,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        Proyecto proyecto = proyectoService.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        Map<String, Object> datos = body != null ? body : Map.of();
        Factura factura = facturacionService.generarDesdeProyecto(
                proyecto,
                texto(datos.get("condicionPago")),
                entero(datos.get("plazoCredito")));

        return ResponseEntity.ok(Map.of(
                "message", "Factura del proyecto generada exitosamente",
                "id", factura.getId(),
                "numeroFactura", factura.getNumeroFactura(),
                "montoTotal", factura.getMontoTotal()));
    }

    /** Facturación masiva de los contratos recurrentes con ciclo vencido. */
    @PostMapping("/generar-lote")
    @ResponseBody
    public ResponseEntity<?> generarLote(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> datos = body != null ? body : Map.of();
        return ResponseEntity.ok(facturacionService.generarLoteRecurrente(
                texto(datos.get("periodoFacturado")),
                texto(datos.get("condicionPago")),
                entero(datos.get("plazoCredito"))));
    }

    /** Crea o actualiza una factura capturada manualmente. */
    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarFactura(@RequestBody Factura factura) {
        Factura saved = facturacionService.save(factura);
        return ResponseEntity.ok(Map.of(
                "message", "Factura guardada exitosamente",
                "id", saved.getId(),
                "numeroFactura", saved.getNumeroFactura(),
                "montoTotal", saved.getMontoTotal()));
    }

    /** Cambia el estado de una factura (PAGADA, ANULADA, EMITIDA...). */
    @PatchMapping("/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Factura factura = facturacionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada."));
        Factura actualizada = facturacionService.cambiarEstado(factura, body.get("estado"), body.get("motivo"));
        return ResponseEntity.ok(Map.of(
                "message", "Factura " + actualizada.getEstado().toLowerCase(),
                "estado", actualizada.getEstado()));
    }

    /** Anula una factura conservando el documento. */
    @PostMapping("/{id}/anular")
    @ResponseBody
    public ResponseEntity<?> anular(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Factura factura = facturacionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada."));
        facturacionService.anular(factura, body != null ? body.get("motivo") : null);
        return ResponseEntity.ok(Map.of("message", "Factura anulada"));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarFactura(@PathVariable Long id) {
        facturacionService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Borrador eliminado exitosamente"));
    }

    /** Vista imprimible del documento tributario (una sola hoja, sin menús). */
    @GetMapping("/{id}/imprimir")
    public String imprimir(@PathVariable Long id, Model model) {
        Factura factura = facturacionService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada."));
        model.addAttribute("pageTitle", "DTE " + factura.getNumeroFactura());
        model.addAttribute("factura", factura);
        model.addAttribute("emisor", emisorDelDocumento());
        model.addAttribute("ambiente", configuracion.getAmbienteDescripcion());
        return "facturacion/documento";
    }

    @GetMapping("/pendiente-cobro")
    @ResponseBody
    public ResponseEntity<?> getMontoPendiente() {
        BigDecimal monto = facturacionService.getMontoPendiente();
        return ResponseEntity.ok(Map.of("monto", monto));
    }

    /** Datos del emisor para la hoja imprimible, tomados de la configuración editable. */
    private Map<String, String> emisorDelDocumento() {
        Map<String, String> emisor = new LinkedHashMap<>();
        emisor.put("nombre", configuracion.get(ParametroDte.EMISOR_NOMBRE));
        emisor.put("nombreComercial", configuracion.get(ParametroDte.EMISOR_NOMBRE_COMERCIAL));
        emisor.put("nit", configuracion.get(ParametroDte.EMISOR_NIT));
        emisor.put("nrc", configuracion.get(ParametroDte.EMISOR_NRC));
        emisor.put("giro", configuracion.get(ParametroDte.EMISOR_GIRO));
        emisor.put("direccion", configuracion.get(ParametroDte.EMISOR_DIRECCION));
        emisor.put("telefono", configuracion.get(ParametroDte.EMISOR_TELEFONO));
        emisor.put("correo", configuracion.get(ParametroDte.EMISOR_CORREO));
        return emisor;
    }

    // ---- helpers de lectura del body ----

    private static String texto(Object valor) {
        return valor != null && !valor.toString().isBlank() ? valor.toString().trim() : null;
    }

    private static Integer entero(Object valor) {
        if (valor == null || valor.toString().isBlank()) return null;
        try {
            return Integer.valueOf(valor.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
