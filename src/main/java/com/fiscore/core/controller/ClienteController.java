package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.services.ClienteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final LoginController loginController;

    public ClienteController(ClienteService clienteService, LoginController loginController) {
        this.clienteService = clienteService;
        this.loginController = loginController;
    }

    @GetMapping("/listar")
    public ResponseEntity<?> listClientes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return ResponseEntity.ok(clienteService.findAll());
    }

    @PostMapping("/guardar")
    public ResponseEntity<?> guardarCliente(@ModelAttribute Cliente cliente) {
        clienteService.save(cliente);
        return ResponseEntity.ok("Cliente guardado exitosamente");
    }
}