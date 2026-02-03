package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.services.ClienteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ClienteController {

    private final LoginController loginController;
    private final ClienteService clienteService;

    public ClienteController(LoginController loginController, ClienteService clienteService) {
        this.loginController = loginController;
        this.clienteService = clienteService;
    }

    @GetMapping("/clientes")
    public String listarClientes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("clientesList", clienteService.findAll());
        return "cliente/clientes";
    }

    @GetMapping("/registroClientes")
    public String mostrarFormularioRegistro(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("cliente", new Cliente());
        return "cliente/registroClientes";
    }

    @PostMapping("/clientes")
    public String registrarCliente(@ModelAttribute Cliente cliente) {
        clienteService.save(cliente);
        return "redirect:/clientes";
    }
}