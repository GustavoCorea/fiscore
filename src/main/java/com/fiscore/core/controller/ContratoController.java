package com.fiscore.core.controller;

import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.ContratoServicio;
import com.fiscore.core.models.Servicio;
import com.fiscore.core.services.ClienteService;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.ServicioService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/contratos")
public class ContratoController {

    private final ContratoService contratoService;
    private final ClienteService clienteService;
    private final ServicioService servicioService;

    public ContratoController(ContratoService contratoService, ClienteService clienteService, ServicioService servicioService) {
        this.contratoService = contratoService;
        this.clienteService = clienteService;
        this.servicioService = servicioService;
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listarContratos() {
        return ResponseEntity.ok(contratoService.findAll());
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> getContrato(@PathVariable Long id) {
        Optional<Contrato> c = contratoService.findById(id);
        return c.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarContrato(@RequestBody Map<String, Object> body) {
        try {
            Contrato contrato = buildContrato(null, body);
            Contrato saved = contratoService.save(contrato);
            return ResponseEntity.ok(Map.of("message", "Contrato creado exitosamente", "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizarContrato(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        if (!contratoService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        try {
            Contrato contrato = buildContrato(id, body);
            Contrato saved = contratoService.save(contrato);
            return ResponseEntity.ok(Map.of("message", "Contrato actualizado exitosamente", "id", saved.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarContrato(@PathVariable Long id) {
        if (!contratoService.findById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        contratoService.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Contrato eliminado exitosamente"));
    }

    @PatchMapping("/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return contratoService.findById(id).map(c -> {
            c.setEstado(body.get("estado"));
            contratoService.save(c);
            return ResponseEntity.ok(Map.of("message", "Estado actualizado"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @SuppressWarnings("unchecked")
    private Contrato buildContrato(Long id, Map<String, Object> body) {
        Contrato contrato = new Contrato();
        if (id != null) contrato.setId(id);

        Long clienteId = Long.valueOf(body.get("clienteId").toString());
        Cliente cliente = clienteService.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        contrato.setCliente(cliente);

        // Procesar lista de servicios
        List<Map<String, Object>> serviciosData = (List<Map<String, Object>>) body.get("servicios");
        if (serviciosData == null || serviciosData.isEmpty()) {
            throw new RuntimeException("Debe agregar al menos un servicio al contrato");
        }

        BigDecimal totalCalculado = BigDecimal.ZERO;
        for (Map<String, Object> sData : serviciosData) {
            Long servicioId = Long.valueOf(sData.get("servicioId").toString());
            Servicio servicio = servicioService.findById(servicioId)
                    .orElseThrow(() -> new RuntimeException("Servicio no encontrado: " + servicioId));
            BigDecimal precio = new BigDecimal(sData.getOrDefault("precioAcordado", "0").toString());

            ContratoServicio cs = new ContratoServicio();
            cs.setServicio(servicio);
            cs.setPrecioAcordado(precio);
            contrato.getServicios().add(cs);
            totalCalculado = totalCalculado.add(precio);
        }

        contrato.setTipoFacturacion(body.getOrDefault("tipoFacturacion", "RECURRENTE").toString());

        // Honorarios: usar valor explícito o suma de servicios
        Object hon = body.get("honorariosPactados");
        if (hon != null && !hon.toString().isBlank()) {
            contrato.setHonorariosPactados(new BigDecimal(hon.toString()));
        } else {
            contrato.setHonorariosPactados(totalCalculado);
        }

        Object fi = body.get("fechaInicio");
        if (fi != null && !fi.toString().isBlank()) contrato.setFechaInicio(LocalDate.parse(fi.toString()));

        Object notas = body.get("notas");
        if (notas != null) contrato.setNotas(notas.toString());

        Object estado = body.get("estado");
        if (estado != null) contrato.setEstado(estado.toString());

        return contrato;
    }
}