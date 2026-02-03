package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.services.ClienteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ClienteController {

    @Autowired
    private ClienteService clienteService;

    @GetMapping("/clientes")
    public String listarClientes(Model model) {
        model.addAttribute("clientesList", clienteService.findAll());
        return "cliente/clientes";
    }

    @GetMapping("/registroClientes")
    public String mostrarFormularioRegistro(Model model) {
        model.addAttribute("cliente", new Cliente());
        return "cliente/registroClientes";
    }

    @PostMapping("/clientes")
    public String registrarCliente(@ModelAttribute Cliente cliente) {
        clienteService.save(cliente);
        return "redirect:/clientes";
    }
}