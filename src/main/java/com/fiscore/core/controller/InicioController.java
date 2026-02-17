package com.fiscore.core.controller;

import com.fiscore.core.services.ClienteService;
import com.fiscore.core.services.ServicioService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;

@SessionScope
@Controller
public class InicioController {

	private final LoginController loginController;
    private final ServicioService servicioService;
    private final ClienteService clienteService;

    public InicioController(LoginController loginController, ServicioService servicioService, ClienteService clienteService) {
        this.loginController = loginController;
        this.servicioService = servicioService;
        this.clienteService = clienteService;
    }

    @GetMapping("/inicio")
	public String inicio(HttpSession session, Model model) {
		loginController.initAuthentication(session, model);
		return "index";
	}

    @GetMapping("/clientes")
    public String clientes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("clientesList", clienteService.findAll());
        return "cliente/clientes";
    }

    @GetMapping("/servicios")
    public String servicios(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("serviciosList", servicioService.findAll());
        return "servicio/servicios";
    }

    @GetMapping("/contratos")
    public String contratos(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return "gestion/contratos";
    }

    @GetMapping("/proyectos")
    public String proyectos(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return "gestion/proyectos";
    }

    @GetMapping("/facturacion")
    public String facturacion(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return "facturacion/facturacion";
    }

}
