package com.fiscore.core.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FacturacionController {

    private final LoginController loginController;

    public FacturacionController(LoginController loginController) {
        this.loginController = loginController;
    }


}