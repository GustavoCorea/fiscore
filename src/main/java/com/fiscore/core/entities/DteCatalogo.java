package com.fiscore.core.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "DTE_CATALOGO")
public class DteCatalogo {
    @Id
    @Column(name = "DTCA_ID", nullable = false)
    private Long id;

    @Column(name = "DTCA_COD", length = 18)
    private String dtcaCod;

    @Column(name = "DTCA_DES", length = 250)
    private String dtcaDes;

    @Column(name = "DTCA_CAP")
    private Long dtcaCap;

    @Column(name = "DTCA_EST")
    private Short dtcaEst;

    @Column(name = "DTCA_CAX", length = 10)
    private String dtcaCax;

    @Column(name = "DTCA_USR", length = 10)
    private String dtcaUsr;

    @Column(name = "DTCA_FEC")
    private Instant dtcaFec;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDtcaCod() {
        return dtcaCod;
    }

    public void setDtcaCod(String dtcaCod) {
        this.dtcaCod = dtcaCod;
    }

    public String getDtcaDes() {
        return dtcaDes;
    }

    public void setDtcaDes(String dtcaDes) {
        this.dtcaDes = dtcaDes;
    }

    public Long getDtcaCap() {
        return dtcaCap;
    }

    public void setDtcaCap(Long dtcaCap) {
        this.dtcaCap = dtcaCap;
    }

    public Short getDtcaEst() {
        return dtcaEst;
    }

    public void setDtcaEst(Short dtcaEst) {
        this.dtcaEst = dtcaEst;
    }

    public String getDtcaCax() {
        return dtcaCax;
    }

    public void setDtcaCax(String dtcaCax) {
        this.dtcaCax = dtcaCax;
    }

    public String getDtcaUsr() {
        return dtcaUsr;
    }

    public void setDtcaUsr(String dtcaUsr) {
        this.dtcaUsr = dtcaUsr;
    }

    public Instant getDtcaFec() {
        return dtcaFec;
    }

    public void setDtcaFec(Instant dtcaFec) {
        this.dtcaFec = dtcaFec;
    }

}