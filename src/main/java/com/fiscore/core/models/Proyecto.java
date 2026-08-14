package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "proyecto")
public class Proyecto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    private String categoria;           // CONTABILIDAD, LEGAL, AUDITORIA, CONSULTORIA, TRAMITES
    private BigDecimal presupuesto;

    /**
     * Tarifa por hora del caso. Sirve de valor por defecto al registrar horas;
     * cada registro guarda la suya, de modo que cambiarla aquí no reescribe el
     * precio del trabajo ya hecho.
     */
    private BigDecimal tarifaHora;
    private Integer porcentajeAvance;
    // COTIZADO, EN_EJECUCION, FINALIZADO, FACTURADO, CANCELADO
    private String estado;
    private LocalDate fechaInicio;
    private LocalDate fechaEstimadaFin;
    private LocalDate fechaFin;
    private String notas;
    private LocalDate fechaCreacion;
    private Boolean facturado;
}