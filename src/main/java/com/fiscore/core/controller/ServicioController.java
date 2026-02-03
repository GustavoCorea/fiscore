package com.fiscore.core.controller;

import com.fiscore.core.models.Servicio;
import com.fiscore.core.services.ServicioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ServicioController {

    @Autowired
    private ServicioService servicioService;

    @GetMapping("/servicios")
    public String listarServicios(Model model) {
        model.addAttribute("serviciosList", servicioService.findAll());
        return "servicio/servicios";
    }

    @GetMapping("/registroServicios")
    public String mostrarFormularioRegistro(Model model) {
        model.addAttribute("servicio", new Servicio());
        return "servicio/registroServicios";
    }

    @PostMapping("/servicios")
    public String registrarServicio(@ModelAttribute Servicio servicio) {
        servicioService.save(servicio);
        return "redirect:/servicios";
    }
}