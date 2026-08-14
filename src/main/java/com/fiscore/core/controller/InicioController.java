package com.fiscore.core.controller;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.services.ClienteService;
import com.fiscore.core.services.ConfiguracionDteService;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@SessionScope
@Controller
public class InicioController implements Serializable {

    /** Horizonte en días de la agenda de facturación mostrada en el panel. */
    private static final int DIAS_AGENDA = 30;

    private final LoginController loginController;
    private final ServicioService servicioService;
    private final ClienteService clienteService;
    private final ContratoService contratoService;
    private final ProyectoService proyectoService;
    private final FacturacionService facturacionService;
    private final ReportesService reportesService;
    private final ConfiguracionDteService configuracionDteService;

    public InicioController(LoginController loginController, ServicioService servicioService,
                            ClienteService clienteService, ContratoService contratoService,
                            ProyectoService proyectoService, FacturacionService facturacionService,
                            ReportesService reportesService, ConfiguracionDteService configuracionDteService) {
        this.loginController = loginController;
        this.servicioService = servicioService;
        this.clienteService = clienteService;
        this.contratoService = contratoService;
        this.proyectoService = proyectoService;
        this.facturacionService = facturacionService;
        this.reportesService = reportesService;
        this.configuracionDteService = configuracionDteService;
    }

    @GetMapping("/inicio")
    public String inicio(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Panel de control");
        model.addAttribute("menuActivo", "inicio");

        model.addAttribute("kpis", reportesService.getKpis());
        model.addAttribute("totalClientes", clienteService.count());
        model.addAttribute("totalContratos", contratoService.countActivos());
        model.addAttribute("proyectosActivos", proyectoService.countEnEjecucion());

        // Bandejas de trabajo del día
        model.addAttribute("agendaFacturacion", contratoService.findAgendaFacturacion(DIAS_AGENDA));
        model.addAttribute("contratosPorFacturar", contratoService.findPendientesDeFacturar());
        model.addAttribute("proyectosPorFacturar", proyectoService.findFinalizadosSinFacturar());
        model.addAttribute("proyectosAtrasados", proyectoService.findAtrasados());
        model.addAttribute("facturasVencidas", facturacionService.findVencidas());
        model.addAttribute("facturasRecientes", facturacionService.findRecientes());

        model.addAttribute("tendencia", reportesService.getTendenciaMensual(6));
        model.addAttribute("ingresosPorCategoria", reportesService.getIngresosPorCategoria());
        model.addAttribute("periodoActual", FacturacionService.periodoDe(LocalDate.now()));
        return "index";
    }

    @GetMapping("/clientes")
    public String clientes(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Clientes");
        model.addAttribute("menuActivo", "clientes");
        model.addAttribute("clientesList", clienteService.findAll());
        return "cliente/clientes";
    }

    /**
     * Gestión de usuarios. La página no recibe la lista desde el modelo: la pide
     * la propia pantalla a /usuarios/listar, que devuelve una vista sin el hash
     * de la contraseña. Cargarla aquí obligaría a repetir esa proyección.
     */
    @GetMapping("/usuarios")
    public String usuarios(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Usuarios");
        model.addAttribute("menuActivo", "usuarios");
        return "seguridad/usuarios";
    }

    @GetMapping("/servicios")
    public String servicios(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Servicios");
        model.addAttribute("menuActivo", "servicios");
        model.addAttribute("serviciosList", servicioService.findAll());
        return "servicio/servicios";
    }

    @GetMapping("/contratos")
    public String contratos(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Contratos");
        model.addAttribute("menuActivo", "contratos");
        model.addAttribute("contratosList", contratoService.findAll());
        model.addAttribute("clientesList", clienteService.findAll());
        model.addAttribute("serviciosList", servicioService.findAll());
        return "gestion/contratos";
    }

    @GetMapping("/proyectos")
    public String proyectos(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Proyectos y casos");
        model.addAttribute("menuActivo", "proyectos");
        model.addAttribute("clientesList", clienteService.findAll());

        List<Proyecto> cotizados = proyectoService.findByEstado("COTIZADO");
        List<Proyecto> enEjecucion = proyectoService.findByEstado("EN_EJECUCION");
        List<Proyecto> finalizados = proyectoService.findByEstado("FINALIZADO");
        List<Proyecto> facturados = proyectoService.findByEstado("FACTURADO");

        model.addAttribute("cotizados", cotizados);
        model.addAttribute("enEjecucion", enEjecucion);
        model.addAttribute("finalizados", finalizados);
        model.addAttribute("facturados", facturados);

        List<Proyecto> todos = new ArrayList<>();
        todos.addAll(cotizados);
        todos.addAll(enEjecucion);
        todos.addAll(finalizados);
        todos.addAll(facturados);
        model.addAttribute("todosProyectos", todos);
        model.addAttribute("proyectosAtrasados", proyectoService.findAtrasados());
        return "gestion/proyectos";
    }

    @GetMapping("/facturacion")
    public String facturacion(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Facturación DTE");
        model.addAttribute("menuActivo", "facturacion");

        List<Contrato> porFacturar = contratoService.findPendientesDeFacturar();
        model.addAttribute("contratosPorFacturar", porFacturar);
        model.addAttribute("agendaFacturacion", contratoService.findAgendaFacturacion(DIAS_AGENDA));
        model.addAttribute("proyectosPorFacturar", proyectoService.findFinalizadosSinFacturar());
        model.addAttribute("historialFacturas", facturacionService.findAll());
        model.addAttribute("clientesList", clienteService.findAll());
        model.addAttribute("periodoActual", FacturacionService.periodoDe(LocalDate.now()));
        return "facturacion/facturacion";
    }

    @GetMapping("/reportes")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(required = false) Integer anio) {
        loginController.initAuthentication(session, model);
        int periodo = anio != null ? anio : LocalDate.now().getYear();

        model.addAttribute("pageTitle", "Reportes");
        model.addAttribute("menuActivo", "reportes");
        model.addAttribute("anio", periodo);
        model.addAttribute("aniosDisponibles", aniosDisponibles(periodo));

        model.addAttribute("kpis", reportesService.getKpis());
        model.addAttribute("tendencia", reportesService.getTendenciaMensual(12));
        model.addAttribute("antiguedad", reportesService.getAntiguedadSaldos());
        model.addAttribute("libroVentas", reportesService.getLibroVentas(periodo));
        model.addAttribute("totalesLibro", reportesService.getTotalesLibroVentas(periodo));
        model.addAttribute("resumenProyectos", reportesService.getResumenProyectos());
        model.addAttribute("ingresosPorCategoria", reportesService.getIngresosPorCategoria());
        model.addAttribute("topClientes", reportesService.getTopClientesPorHonorarios(8));
        model.addAttribute("topFacturado", reportesService.getTopClientesPorFacturado(8));
        model.addAttribute("distribucionTipo", reportesService.getDistribucionPorTipo());
        model.addAttribute("distribucionFacturas", reportesService.getDistribucionFacturasPorEstado());
        model.addAttribute("carteraClientes", reportesService.getCarteraPorCliente());
        return "reportes/reportes";
    }

    @GetMapping("/configuracion/dte")
    public String configuracionDte(HttpSession session, Model model) {
        loginController.initAuthentication(session, model);
        model.addAttribute("pageTitle", "Parámetros DTE");
        model.addAttribute("menuActivo", "configuracion");
        model.addAttribute("grupos", configuracionDteService.getFormulario());
        model.addAttribute("ambiente", configuracionDteService.getAmbienteDescripcion());
        model.addAttribute("conexionConfigurada", configuracionDteService.isConexionConfigurada());
        return "configuracion/dte";
    }

    /** Años ofrecidos en el selector del libro de ventas. */
    private List<Integer> aniosDisponibles(int seleccionado) {
        int actual = LocalDate.now().getYear();
        List<Integer> anios = new ArrayList<>();
        for (int a = actual; a >= actual - 4; a--) {
            anios.add(a);
        }
        if (!anios.contains(seleccionado)) {
            anios.add(0, seleccionado);
        }
        return anios;
    }
}
