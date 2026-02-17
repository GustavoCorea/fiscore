package com.fiscore.core.controller;

import com.fiscore.core.models.Servicio;
import com.fiscore.core.services.ServicioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/servicios")
public class ServicioController {

    private final ServicioService servicioService;
    private final LoginController loginController;

    public ServicioController(ServicioService servicioService, LoginController loginController) {
        this.servicioService = servicioService;
        this.loginController = loginController;
    }

    @GetMapping("/listar")
    public ResponseEntity<?> listServicios(HttpSession session, Model model) {
        return ResponseEntity.ok(servicioService.findAll());
    }

    @PostMapping("/guardar")
    public ResponseEntity<?> registrarServicio(@ModelAttribute Servicio servicio) {
        servicioService.save(servicio);
        return ResponseEntity.ok("Servicio guardado exitosamente");
    }
}