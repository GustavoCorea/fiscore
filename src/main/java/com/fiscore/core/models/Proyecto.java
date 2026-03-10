package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Table(name = "proyecto")
public class Proyecto {

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