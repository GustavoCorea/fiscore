package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Trabajo dedicado a un caso, en horas.
 *
 * Es la unidad con la que factura un bufete, y hasta ahora no existía: un
 * proyecto tenía presupuesto y un booleano de facturado, lo que solo permite
 * cobrar precio cerrado.
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "registro_horas")
public class RegistroHoras extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proyecto_id", nullable = false)
    @ToString.Exclude
    private Proyecto proyecto;

    /** Día en que se hizo el trabajo, que no tiene por qué ser el del registro. */
    private LocalDate fecha;

    private BigDecimal horas;

    private String descripcion;

    /**
     * Quién hizo el trabajo. Se guarda el nombre de usuario y no una clave
     * ajena, igual que en las columnas de auditoría: una cuenta desactivada
     * sigue siendo la autora de lo que hizo.
     *
     * Es distinto de {@code creadoPor}: un socio puede registrar horas de un
     * pasante, y ambas cosas conviene poder responderlas.
     */
    private String usuario;

    /**
     * Tarifa aplicada, copiada del caso al registrar.
     *
     * Se guarda aquí a propósito y no se lee del proyecto al calcular: las
     * tarifas suben, y el trabajo de marzo no puede reprecificarse porque en
     * septiembre se acordara otra cosa.
     */
    private BigDecimal tarifaHora;

    /** Hay trabajo que se hace y no se cobra; conviene registrarlo igual. */
    private Boolean facturable;

    /**
     * Factura en la que se cobró. Mientras sea nula el registro está pendiente
     * de facturar; una vez asignada, el registro ya no se toca.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "factura_id")
    @ToString.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Factura factura;

    /** Importe del registro: horas por tarifa, a dos decimales. */
    public BigDecimal getImporte() {
        if (horas == null || tarifaHora == null) {
            return BigDecimal.ZERO;
        }
        return horas.multiply(tarifaHora).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean estaFacturado() {
        return factura != null;
    }

    /** Número de la factura donde se cobró, para las pantallas. */
    public String getNumeroFactura() {
        return factura != null ? factura.getNumeroFactura() : null;
    }
}
