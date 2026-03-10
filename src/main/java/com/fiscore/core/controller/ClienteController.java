package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.services.ClienteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
    @ResponseBody
    public ResponseEntity<?> listClientes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        return ResponseEntity.ok(clienteService.findAll());
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getCliente(@PathVariable Long id) {
        Optional<Cliente> c = clienteService.findById(id);
        return c.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarCliente(@RequestBody Cliente cliente) {
        Cliente saved = clienteService.save(cliente);
        return ResponseEntity.ok(Map.of("message", "Cliente guardado exitosamente", "id", saved.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizarCliente(@PathVariable Long id, @RequestBody Cliente cliente) {
        if (!clienteService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        cliente.setId(id);
        Cliente saved = clienteService.save(cliente);
        return ResponseEntity.ok(Map.of("message", "Cliente actualizado exitosamente", "id", saved.getId()));
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarCliente(@PathVariable Long id) {
        if (!clienteService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        clienteService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Cliente eliminado exitosamente"));
    }
}