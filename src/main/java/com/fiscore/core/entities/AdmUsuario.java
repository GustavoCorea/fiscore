package com.fiscore.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@Table(name = "ADM_USUARIOS")
public class AdmUsuario {
    @Id
    @Column(name = "USER_ID", nullable = false)
    private Long id;

    @Column(name = "USER_USERNAME", nullable = false, length = 100)
    private String userUsername;

    @Column(name = "USER_PASSWORD", nullable = false, length = 250)
    private String userPassword;

    @Column(name = "USER_DUI", length = 9)
    private String userDui;

    @Column(name = "USER_NOMBRES", length = 150)
    private String userNombres;

    @Column(name = "USER_APELLIDOS", length = 150)
    private String userApellidos;

    @Column(name = "USER_SEXO", length = Integer.MAX_VALUE)
    private String userSexo;

    @Column(name = "USER_FCH_NAC")
    private LocalDate userFchNac;

    @Column(name = "USER_TELEFONO", length = 12)
    private String userTelefono;

    @Column(name = "USER_CORREO", length = 150)
    private String userCorreo;

    @ColumnDefault("1")
    @Column(name = "USER_ESTADO", precision = 1)
    private BigDecimal userEstado;

    @Column(name = "USER_USUARIO_REGISTRA", length = 100)
    private String userUsuarioRegistra;

    @Column(name = "USER_FCH_REGISTRO")
    private LocalDate userFchRegistro;

    @Column(name = "USER_USUARIO_MODIFICA", length = 100)
    private String userUsuarioModifica;

    @Column(name = "USER_FCH_MODIFICA")
    private LocalDate userFchModifica;

}