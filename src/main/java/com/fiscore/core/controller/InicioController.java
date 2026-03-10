package com.fiscore.core.controller;

import com.fiscore.core.services.ClienteService;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
import com.fiscore.core.services.ProyectoService;
import com.fiscore.core.services.ReportesService;
import com.fiscore.core.services.ServicioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;

@SessionScope
@Controller
public class InicioController implements Serializable {

    private final LoginController loginController;
    private final ServicioService servicioService;
    private final ClienteService clienteService;
    private final ContratoService contratoService;
    private final ProyectoService proyectoService;
    private final FacturacionService facturacionService;
    private final ReportesService reportesService;

    public InicioController(LoginController loginController, ServicioService servicioService,
                            ClienteService clienteService, ContratoService contratoService,
                            ProyectoService proyectoService, FacturacionService facturacionService,
                            ReportesService reportesService) {
        this.loginController = loginController;
        this.servicioService = servicioService;
        this.clienteService = clienteService;
        this.contratoService = contratoService;
        this.proyectoService = proyectoService;
        this.facturacionService = facturacionService;
        this.reportesService = reportesService;
    }

    @GetMapping("/inicio")
    public String inicio(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("totalClientes", clienteService.count());
        model.addAttribute("totalContratos", contratoService.countActivos());
        model.addAttribute("proyectosActivos", proyectoService.countEnEjecucion());
        java.math.BigDecimal monto = facturacionService.getMontoPendiente();
        model.addAttribute("montoPorCobrar", String.format("$%.2f", monto));
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
        model.addAttribute("contratosList", contratoService.findAll());
        model.addAttribute("clientesList", clienteService.findAll());
        model.addAttribute("serviciosList", servicioService.findAll());
        return "gestion/contratos";
    }

    @GetMapping("/proyectos")
    public String proyectos(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("clientesList", clienteService.findAll());
        java.util.List<com.fiscore.core.models.Proyecto> cotizados = proyectoService.findByEstado("COTIZADO");
        java.util.List<com.fiscore.core.models.Proyecto> enEjecucion = proyectoService.findByEstado("EN_EJECUCION");
        java.util.List<com.fiscore.core.models.Proyecto> finalizados = proyectoService.findByEstado("FINALIZADO");
        model.addAttribute("cotizados", cotizados);
        model.addAttribute("enEjecucion", enEjecucion);
        model.addAttribute("finalizados", finalizados);
        java.util.List<com.fiscore.core.models.Proyecto> todos = new java.util.ArrayList<>();
        todos.addAll(cotizados);
        todos.addAll(enEjecucion);
        todos.addAll(finalizados);
        model.addAttribute("todosProyectos", todos);
        return "gestion/proyectos";
    }

    @GetMapping("/facturacion")
    public String facturacion(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("contratosActivos", contratoService.findActivos());
        model.addAttribute("historialFacturas", facturacionService.findAll());
        model.addAttribute("clientesList", clienteService.findAll());
        return "facturacion/facturacion";
    }

    @GetMapping("/reportes")
    public String reportes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("kpis", reportesService.getKpis());
        model.addAttribute("ingresosPorCategoria", reportesService.getIngresosPorCategoria());
        model.addAttribute("topClientes", reportesService.getTopClientesPorHonorarios(8));
        model.addAttribute("distribucionTipo", reportesService.getDistribucionPorTipo());
        model.addAttribute("distribucionFacturas", reportesService.getDistribucionFacturasPorEstado());
        model.addAttribute("carteraClientes", reportesService.getCarteraPorCliente());
        return "reportes/reportes";
    }
}
