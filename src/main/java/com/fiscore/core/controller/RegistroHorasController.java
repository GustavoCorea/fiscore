package com.fiscore.core.controller;

import com.fiscore.core.models.RegistroHoras;
import com.fiscore.core.services.RegistroHorasService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horas dedicadas a un caso.
 *
 * Devuelve una vista y no la entidad: RegistroHoras arrastra el proyecto y este
 * su cliente, de modo que serializarla entera mandaría media base de datos en
 * cada fila de una tabla que solo necesita seis columnas.
 */
@Controller
@RequestMapping("/horas")
public class RegistroHorasController {

    private final RegistroHorasService registroService;

    public RegistroHorasController(RegistroHorasService registroService) {
        this.registroService = registroService;
    }

    public record FormularioHoras(Long proyectoId, String fecha, BigDecimal horas,
                                  String descripcion, String usuario,
                                  BigDecimal tarifaHora, Boolean facturable) {

        LocalDate fechaComoFecha() {
            return (fecha == null || fecha.isBlank()) ? null : LocalDate.parse(fecha);
        }
    }

    @GetMapping("/proyecto/{proyectoId}")
    @ResponseBody
    public ResponseEntity<?> listar(@PathVariable Long proyectoId) {
        List<Map<String, Object>> vista = registroService.listarPorProyecto(proyectoId).stream()
                .map(RegistroHorasController::vista)
                .toList();
        return ResponseEntity.ok(vista);
    }

    @GetMapping("/proyecto/{proyectoId}/resumen")
    @ResponseBody
    public ResponseEntity<?> resumen(@PathVariable Long proyectoId) {
        return ResponseEntity.ok(registroService.resumen(proyectoId));
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> registrar(@RequestBody FormularioHoras form) {
        RegistroHoras creado = registroService.registrar(
                form.proyectoId(), form.fechaComoFecha(), form.horas(), form.descripcion(),
                form.usuario(), form.tarifaHora(), form.facturable());

        return ResponseEntity.ok(Map.of("message", "Horas registradas", "id", creado.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody FormularioHoras form) {
        registroService.actualizar(id, form.fechaComoFecha(), form.horas(), form.descripcion(),
                form.usuario(), form.tarifaHora(), form.facturable());
        return ResponseEntity.ok(Map.of("message", "Registro actualizado", "id", id));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        registroService.eliminar(id);
        return ResponseEntity.ok(Map.of("message", "Registro eliminado"));
    }

    // -----------------------------------------------------------------

    private static Map<String, Object> vista(RegistroHoras r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("fecha", r.getFecha());
        m.put("horas", r.getHoras());
        m.put("descripcion", r.getDescripcion());
        m.put("usuario", r.getUsuario());
        m.put("tarifaHora", r.getTarifaHora());
        m.put("importe", r.getImporte());
        m.put("facturable", r.getFacturable());
        m.put("facturado", r.estaFacturado());
        m.put("numeroFactura", r.getNumeroFactura());
        return m;
    }
}
