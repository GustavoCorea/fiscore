package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Table(name = "contrato")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "servicio_id")
    private Servicio servicio;

    private String tipoFacturacion;     // RECURRENTE, PAGO_UNICO, POR_HORA
    private BigDecimal honorariosPactados;
    private LocalDate fechaInicio;
    private LocalDate fechaProximaFacturacion;
    private String notas;
    private String estado;              // ACTIVO, SUSPENDIDO, CANCELADO
    private LocalDate fechaCreacion;
}