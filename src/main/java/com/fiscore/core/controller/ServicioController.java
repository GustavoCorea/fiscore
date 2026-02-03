package com.fiscore.core.controller;

import com.fiscore.core.models.Servicio;
import com.fiscore.core.services.ServicioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ServicioController {

    private final ServicioService servicioService;
    private final LoginController loginController;

    public ServicioController(ServicioService servicioService, LoginController loginController) {
        this.servicioService = servicioService;
        this.loginController = loginController;
    }

    @GetMapping("/servicios")
    public String listarServicios(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("serviciosList", servicioService.findAll());
        return "servicio/servicios";
    }

    @GetMapping("/registroServicios")
    public String mostrarFormularioRegistro(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("servicio", new Servicio());
        return "servicio/registroServicios";
    }

    @PostMapping("/servicios")
    public String registrarServicio(@ModelAttribute Servicio servicio) {
        servicioService.save(servicio);
        return "redirect:/servicios";
    }
}