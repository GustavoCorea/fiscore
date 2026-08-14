package com.fiscore.core.models;

import java.util.List;
import java.util.Map;

/**
 * Ciclo de vida del documento frente al Ministerio de Hacienda.
 *
 * Hasta ahora estos nombres existían solo como texto suelto en un comentario y
 * nadie los asignaba: toda factura nacía en PENDIENTE_ENVIO y ahí se quedaba.
 * Al declararlos aquí con sus transiciones válidas, el resto de la integración
 * tiene dónde encajar y un cambio imposible falla en el acto en lugar de dejar
 * un documento en un estado que no significa nada.
 *
 * Es deliberadamente distinto de {@code Factura.estado}, que describe el
 * documento en la contabilidad interna (BORRADOR, EMITIDA, PAGADA, ANULADA).
 * Una factura puede estar PAGADA para el despacho y seguir RECHAZADA por
 * Hacienda: son dos verdades independientes y confundirlas oculta la segunda.
 */
public enum EstadoDte {

    /** Emitido en el sistema, todavía no transmitido. */
    PENDIENTE_ENVIO,

    /** Enviado a Hacienda, sin respuesta aún. */
    ENVIADO,

    /** Hacienda devolvió el sello de recepción. El documento es válido. */
    ACEPTADO,

    /** Hacienda lo rechazó. Se corrige y se reenvía. */
    RECHAZADO,

    /** Hacienda no estaba disponible: queda en cola para el envío diferido. */
    CONTINGENCIA,

    /** Invalidado ante Hacienda mediante el evento de anulación. */
    INVALIDADO;

    /**
     * Qué puede venir después de cada estado.
     *
     * ACEPTADO solo lleva a INVALIDADO: un documento con sello no se corrige,
     * se invalida y se emite otro. INVALIDADO es terminal.
     */
    private static final Map<EstadoDte, List<EstadoDte>> SIGUIENTES = Map.of(
            PENDIENTE_ENVIO, List.of(ENVIADO, CONTINGENCIA),
            ENVIADO,         List.of(ACEPTADO, RECHAZADO, CONTINGENCIA),
            RECHAZADO,       List.of(PENDIENTE_ENVIO, ENVIADO),
            CONTINGENCIA,    List.of(PENDIENTE_ENVIO, ENVIADO),
            ACEPTADO,        List.of(INVALIDADO),
            INVALIDADO,      List.of()
    );

    public boolean puedePasarA(EstadoDte destino) {
        return destino != null && SIGUIENTES.get(this).contains(destino);
    }

    public List<EstadoDte> siguientesPosibles() {
        return SIGUIENTES.get(this);
    }

    /** El documento ya tiene valor fiscal y no admite correcciones locales. */
    public boolean tieneValidezFiscal() {
        return this == ACEPTADO;
    }

    /**
     * Traduce el valor guardado en la columna. Un texto desconocido —de datos
     * anteriores a este enum— se interpreta como pendiente de envío, que es lo
     * único que puede ser un documento que nunca se transmitió.
     */
    public static EstadoDte desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return PENDIENTE_ENVIO;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDIENTE_ENVIO;
        }
    }
}
