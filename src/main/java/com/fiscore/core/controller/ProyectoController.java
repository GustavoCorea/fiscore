package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.services.ClienteService;
import com.fiscore.core.services.ProyectoService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/proyectos")
public class ProyectoController {

    private final ProyectoService proyectoService;
    private final ClienteService clienteService;

    public ProyectoController(ProyectoService proyectoService, ClienteService clienteService) {
        this.proyectoService = proyectoService;
        this.clienteService = clienteService;
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listarProyectos() {
        return ResponseEntity.ok(proyectoService.findAll());
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getProyecto(@PathVariable Long id) {
        Optional<Proyecto> p = proyectoService.findById(id);
        return p.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/por-facturar")
    @ResponseBody
    public ResponseEntity<?> listarPorFacturar() {
        return ResponseEntity.ok(proyectoService.findFinalizadosSinFacturar());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarProyecto(@RequestBody Map<String, Object> body) {
        Proyecto saved = proyectoService.save(buildProyecto(null, body));
        return ResponseEntity.ok(Map.of("message", "Proyecto creado exitosamente", "id", saved.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizarProyecto(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (proyectoService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Proyecto saved = proyectoService.save(buildProyecto(id, body));
        return ResponseEntity.ok(Map.of("message", "Proyecto actualizado exitosamente", "id", saved.getId()));
    }

    @PatchMapping("/{id}/avance")
    @ResponseBody
    public ResponseEntity<?> actualizarAvance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return proyectoService.findById(id).map(p -> {
            Object avance = body.get("porcentajeAvance");
            if (avance != null) p.setPorcentajeAvance(Integer.valueOf(avance.toString()));
            Object estado = body.get("estado");
            if (estado != null) p.setEstado(estado.toString());
            proyectoService.save(p);
            return ResponseEntity.ok(Map.of("message", "Proyecto actualizado"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarProyecto(@PathVariable Long id) {
        if (!proyectoService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        proyectoService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Proyecto eliminado exitosamente"));
    }

    /**
     * Al editar se parte de la entidad almacenada para no perder los campos que
     * el formulario no envía (fecha de creación, marca de facturado, fecha de cierre).
     */
    private Proyecto buildProyecto(Long id, Map<String, Object> body) {
        Proyecto proyecto = (id != null)
                ? proyectoService.findById(id).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."))
                : new Proyecto();

        Object clienteId = body.get("clienteId");
        if (clienteId != null && !clienteId.toString().isBlank()) {
            Cliente cliente = clienteService.findById(Long.valueOf(clienteId.toString()))
                    .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado."));
            proyecto.setCliente(cliente);
        } else {
            proyecto.setCliente(null);
        }

        String nombre = body.getOrDefault("nombre", "").toString().trim();
        if (nombre.isEmpty()) {
            throw new IllegalArgumentException("El nombre del proyecto es obligatorio.");
        }
        proyecto.setNombre(nombre);
        proyecto.setDescripcion(body.getOrDefault("descripcion", "").toString());
        proyecto.setCategoria(body.getOrDefault("categoria", "CONSULTORIA").toString());

        Object presupuesto = body.get("presupuesto");
        if (presupuesto != null && !presupuesto.toString().isBlank()) {
            proyecto.setPresupuesto(new BigDecimal(presupuesto.toString()));
        }

        Object fi = body.get("fechaInicio");
        if (fi != null && !fi.toString().isBlank()) proyecto.setFechaInicio(LocalDate.parse(fi.toString()));

        Object ff = body.get("fechaEstimadaFin");
        if (ff != null && !ff.toString().isBlank()) proyecto.setFechaEstimadaFin(LocalDate.parse(ff.toString()));

        Object estado = body.get("estado");
        if (estado != null) proyecto.setEstado(estado.toString());

        Object notas = body.get("notas");
        if (notas != null) proyecto.setNotas(notas.toString());

        Object avance = body.get("porcentajeAvance");
        if (avance != null && !avance.toString().isBlank()) {
            proyecto.setPorcentajeAvance(Integer.valueOf(avance.toString()));
        }

        return proyecto;
    }
}