package com.fiscore.core.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce las excepciones de la aplicación a respuestas útiles:
 * JSON con la clave {@code error} para los endpoints REST y una página
 * de error para la navegación normal. Sin esto, cualquier fallo llegaba
 * al navegador como una traza de Whitelabel.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Datos incorrectos enviados por el cliente → 400. */
    @ExceptionHandler({IllegalArgumentException.class, NumberFormatException.class})
    public Object handleBadRequest(RuntimeException ex, HttpServletRequest request, HandlerMethod handler) {
        return responder(HttpStatus.BAD_REQUEST, mensaje(ex, "Los datos enviados no son válidos."), ex, request, handler);
    }

    /** Operación no permitida en el estado actual → 409. */
    @ExceptionHandler(IllegalStateException.class)
    public Object handleConflict(IllegalStateException ex, HttpServletRequest request, HandlerMethod handler) {
        return responder(HttpStatus.CONFLICT, mensaje(ex, "La operación no es posible en este momento."), ex, request, handler);
    }

    /** Violación de integridad referencial (borrar un cliente con contratos, etc.). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Object handleIntegridad(DataIntegrityViolationException ex, HttpServletRequest request, HandlerMethod handler) {
        String msg = "No se puede completar la operación porque el registro tiene información asociada "
                + "(contratos, facturas o proyectos). Desactívelo en lugar de eliminarlo.";
        return responder(HttpStatus.CONFLICT, msg, ex, request, handler);
    }

    /** Cualquier otro fallo → 500 con mensaje genérico; el detalle va al log. */
    @ExceptionHandler(Exception.class)
    public Object handleGeneric(Exception ex, HttpServletRequest request, HandlerMethod handler) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado al procesar la solicitud.", ex, request, handler);
    }

    // -----------------------------------------------------------------

    private Object responder(HttpStatus status, String mensaje, Exception ex,
                             HttpServletRequest request, HandlerMethod handler) {

        if (status.is5xxServerError()) {
            log.error("Error procesando {} {}", request.getMethod(), request.getRequestURI(), ex);
        } else {
            log.warn("{} en {} {}: {}", status.value(), request.getMethod(), request.getRequestURI(), mensaje);
        }

        if (esRespuestaJson(handler)) {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("error", mensaje);
            cuerpo.put("status", status.value());
            cuerpo.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(status).body(cuerpo);
        }

        ModelAndView mv = new ModelAndView("error");
        mv.setStatus(status);
        mv.addObject("pageTitle", "Error");
        mv.addObject("status", status.value());
        mv.addObject("mensaje", mensaje);
        return mv;
    }

    /** Los endpoints REST se identifican por @ResponseBody o @RestController. */
    private boolean esRespuestaJson(HandlerMethod handler) {
        if (handler == null) return true;
        return handler.hasMethodAnnotation(ResponseBody.class)
                || handler.getBeanType().isAnnotationPresent(ResponseBody.class)
                || handler.getBeanType().isAnnotationPresent(RestController.class);
    }

    private String mensaje(Exception ex, String porDefecto) {
        return (ex.getMessage() != null && !ex.getMessage().isBlank()) ? ex.getMessage() : porDefecto;
    }
}
