package com.fiscore.core.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FacturacionController {

    @GetMapping("/facturacion")
    public String facturacion() {
        return "facturacion/facturacion";
    }
}