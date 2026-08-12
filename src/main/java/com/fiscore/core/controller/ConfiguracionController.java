package com.fiscore.core.controller;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.services.ConfiguracionDteService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pantalla y API de la configuración de emisión de DTE.
 * Las credenciales se tratan como secretos: nunca se devuelven al navegador.
 */
@Controller
@RequestMapping("/configuracion")
public class ConfiguracionController {

    private final ConfiguracionDteService configuracionService;

    public ConfiguracionController(ConfiguracionDteService configuracionService) {
        this.configuracionService = configuracionService;
    }

    /** Devuelve todos los parámetros agrupados por sección (sin secretos). */
    @GetMapping("/dte/parametros")
    @ResponseBody
    public ResponseEntity<?> listarParametros() {
        return ResponseEntity.ok(configuracionService.getFormulario());
    }

    /** Guarda los cambios enviados desde el formulario. */
    @PostMapping("/dte/guardar")
    @ResponseBody
    public ResponseEntity<?> guardar(@RequestBody Map<String, String> valores) {
        int cambios = configuracionService.guardar(valores);
        return ResponseEntity.ok(Map.of(
                "message", cambios == 0
                        ? "No había cambios que guardar."
                        : cambios + " parámetro(s) actualizado(s).",
                "cambios", cambios));
    }

    /** Restaura un parámetro a su valor de fábrica. */
    @PostMapping("/dte/restaurar/{clave}")
    @ResponseBody
    public ResponseEntity<?> restaurar(@PathVariable String clave) {
        configuracionService.restaurarPorDefecto(clave);
        ParametroDte definicion = ParametroDte.porClave(clave);
        return ResponseEntity.ok(Map.of(
                "message", "Se restauró el valor por defecto.",
                "valor", definicion != null ? definicion.getValorPorDefecto() : ""));
    }

    /** Resumen del estado de la conexión con Hacienda. */
    @GetMapping("/dte/estado")
    @ResponseBody
    public ResponseEntity<?> estado() {
        return ResponseEntity.ok(Map.of(
                "ambiente", configuracionService.get(ParametroDte.MH_AMBIENTE),
                "ambienteDescripcion", configuracionService.getAmbienteDescripcion(),
                "conexionConfigurada", configuracionService.isConexionConfigurada(),
                "emisor", configuracionService.get(ParametroDte.EMISOR_NOMBRE),
                "nit", configuracionService.get(ParametroDte.EMISOR_NIT),
                "nrc", configuracionService.get(ParametroDte.EMISOR_NRC)));
    }
}
