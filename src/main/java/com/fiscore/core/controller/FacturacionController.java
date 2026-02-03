package com.fiscore.core.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FacturacionController {

    private final LoginController loginController;

    public FacturacionController(LoginController loginController) {
        this.loginController = loginController;
    }

    @GetMapping("/facturacion")
    public String facturacion(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return "facturacion/facturacion";
    }
}