package com.fiscore.core.models;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** Lista de servicios incluidos en este contrato */
    @OneToMany(mappedBy = "contrato", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ContratoServicio> servicios = new ArrayList<>();

    private String tipoFacturacion;         // RECURRENTE, PAGO_UNICO, POR_HORA
    private BigDecimal honorariosPactados;  // total pactado (suma de servicios o valor acordado)
    private LocalDate fechaInicio;
    private LocalDate fechaProximaFacturacion;
    private String notas;
    private String estado;                  // ACTIVO, SUSPENDIDO, CANCELADO
    private LocalDate fechaCreacion;
}