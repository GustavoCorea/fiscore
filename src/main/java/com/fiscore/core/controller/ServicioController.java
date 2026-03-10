package com.fiscore.core.controller;

import com.fiscore.core.models.Servicio;
import com.fiscore.core.services.ServicioService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/servicios")
public class ServicioController {

    private final ServicioService servicioService;

    public ServicioController(ServicioService servicioService) {
        this.servicioService = servicioService;
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listServicios() {
        return ResponseEntity.ok(servicioService.findAll());
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getServicio(@PathVariable Long id) {
        Optional<Servicio> s = servicioService.findById(id);
        return s.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> registrarServicio(@RequestBody Servicio servicio) {
        Servicio saved = servicioService.save(servicio);
        return ResponseEntity.ok(Map.of("message", "Servicio guardado exitosamente", "id", saved.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizarServicio(@PathVariable Long id, @RequestBody Servicio servicio) {
        if (!servicioService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        servicio.setId(id);
        Servicio saved = servicioService.save(servicio);
        return ResponseEntity.ok(Map.of("message", "Servicio actualizado exitosamente", "id", saved.getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarServicio(@PathVariable Long id) {
        if (!servicioService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        servicioService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Servicio eliminado exitosamente"));
    }
}