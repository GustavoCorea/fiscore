package com.fiscore.core.controller;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.Factura;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.services.ContratoService;
import com.fiscore.core.services.FacturacionService;
import com.fiscore.core.services.ProyectoService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alimenta la campana de notificaciones de la barra superior con avisos reales
 * (facturas vencidas, ciclos de facturación por emitir, proyectos por cobrar),
 * en lugar de los datos de ejemplo que traía la plantilla.
 *
 * Solo aplica a las vistas: se limita al controlador que sirve las páginas.
 */
@ControllerAdvice(assignableTypes = InicioController.class)
public class LayoutAdvice {

    /** Máximo de avisos mostrados en el desplegable. */
    private static final int MAX_AVISOS = 8;

    private final FacturacionService facturacionService;
    private final ContratoService contratoService;
    private final ProyectoService proyectoService;

    public LayoutAdvice(FacturacionService facturacionService,
                        ContratoService contratoService,
                        ProyectoService proyectoService) {
        this.facturacionService = facturacionService;
        this.contratoService = contratoService;
        this.proyectoService = proyectoService;
    }

    @ModelAttribute("avisos")
    public List<Map<String, Object>> avisos() {
        List<Map<String, Object>> avisos = new ArrayList<>();
        LocalDate hoy = LocalDate.now();

        for (Factura f : facturacionService.findVencidas()) {
            long dias = ChronoUnit.DAYS.between(f.getFechaVencimiento(), hoy);
            avisos.add(aviso("danger", "mdi-cash-remove",
                    "Factura " + f.getNumeroFactura() + " vencida",
                    (f.getCliente() != null ? f.getCliente().getNombre() : "Cliente") + " · " + dias + " día(s) de mora",
                    "/facturacion"));
        }

        for (Contrato c : contratoService.findPendientesDeFacturar()) {
            avisos.add(aviso("warning", "mdi-file-clock",
                    "Contrato por facturar",
                    (c.getCliente() != null ? c.getCliente().getNombre() : "Cliente")
                            + " · ciclo del " + c.getFechaProximaFacturacion(),
                    "/facturacion"));
        }

        for (Proyecto p : proyectoService.findFinalizadosSinFacturar()) {
            avisos.add(aviso("info", "mdi-briefcase-check",
                    "Proyecto finalizado sin facturar",
                    p.getNombre(),
                    "/proyectos"));
        }

        for (Proyecto p : proyectoService.findAtrasados()) {
            avisos.add(aviso("secondary", "mdi-calendar-alert",
                    "Proyecto atrasado",
                    p.getNombre() + " · vencía el " + p.getFechaEstimadaFin(),
                    "/proyectos"));
        }

        return avisos.size() > MAX_AVISOS ? avisos.subList(0, MAX_AVISOS) : avisos;
    }

    /** Total real de avisos, aunque el desplegable solo muestre los primeros. */
    @ModelAttribute("totalAvisos")
    public int totalAvisos() {
        return facturacionService.findVencidas().size()
                + contratoService.findPendientesDeFacturar().size()
                + proyectoService.findFinalizadosSinFacturar().size()
                + proyectoService.findAtrasados().size();
    }

    private Map<String, Object> aviso(String color, String icono, String titulo, String detalle, String enlace) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("color", color);
        m.put("icono", icono);
        m.put("titulo", titulo);
        m.put("detalle", detalle);
        m.put("enlace", enlace);
        return m;
    }
}
