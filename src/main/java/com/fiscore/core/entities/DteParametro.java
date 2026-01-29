package com.fiscore.core.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "DTE_PARAMETRO")
public class DteParametro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DTPA_ID", nullable = false)
    private Long id;

    @Column(name = "DTPA_NOMBRE", nullable = false, length = 60)
    private String dtpaNombre;

    @Column(name = "DTPA_DESCRIPCION", nullable = false, length = 70)
    private String dtpaDescripcion;

    @Column(name = "DTPA_VAL", nullable = false, length = 500)
    private String dtpaVal;

    @Column(name = "DTPA_ESTADO")
    private Short dtpaEstado;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDtpaNombre() {
        return dtpaNombre;
    }

    public void setDtpaNombre(String dtpaNombre) {
        this.dtpaNombre = dtpaNombre;
    }

    public String getDtpaDescripcion() {
        return dtpaDescripcion;
    }

    public void setDtpaDescripcion(String dtpaDescripcion) {
        this.dtpaDescripcion = dtpaDescripcion;
    }

    public String getDtpaVal() {
        return dtpaVal;
    }

    public void setDtpaVal(String dtpaVal) {
        this.dtpaVal = dtpaVal;
    }

    public Short getDtpaEstado() {
        return dtpaEstado;
    }

    public void setDtpaEstado(Short dtpaEstado) {
        this.dtpaEstado = dtpaEstado;
    }

}