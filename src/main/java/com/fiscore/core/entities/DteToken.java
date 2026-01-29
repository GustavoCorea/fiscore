package com.fiscore.core.entities;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "DTE_TOKEN")
public class DteToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DTTO_ID", nullable = false)
    private Long id;

    @Column(name = "DTTO_TOKEN", length = 500)
    private String dttoToken;

    @Column(name = "DTTO_STATUS", length = 20)
    private String dttoStatus;

    @Column(name = "DTTO_MESSAGE", length = 20)
    private String dttoMessage;

    @Column(name = "DTTO_RES", length = 600)
    private String dttoRes;

    @Column(name = "DTTO_FEC")
    private Instant dttoFec;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDttoToken() {
        return dttoToken;
    }

    public void setDttoToken(String dttoToken) {
        this.dttoToken = dttoToken;
    }

    public String getDttoStatus() {
        return dttoStatus;
    }

    public void setDttoStatus(String dttoStatus) {
        this.dttoStatus = dttoStatus;
    }

    public String getDttoMessage() {
        return dttoMessage;
    }

    public void setDttoMessage(String dttoMessage) {
        this.dttoMessage = dttoMessage;
    }

    public String getDttoRes() {
        return dttoRes;
    }

    public void setDttoRes(String dttoRes) {
        this.dttoRes = dttoRes;
    }

    public Instant getDttoFec() {
        return dttoFec;
    }

    public void setDttoFec(Instant dttoFec) {
        this.dttoFec = dttoFec;
    }

}