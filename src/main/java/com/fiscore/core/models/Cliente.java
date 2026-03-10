package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
@Table(name = "cliente")
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String direccion;
    private String telefono;
    private String email;
    private String nit;
    private String nrc;
    private String giro;
    private String tipoCliente;   // JURIDICA, NATURAL
    private String tipoDocumento; // NIT, DUI, PASAPORTE, OTRO
    private String municipio;
    private String departamento;
    private String estado;        // ACTIVO, INACTIVO
    private LocalDate fechaRegistro;
}