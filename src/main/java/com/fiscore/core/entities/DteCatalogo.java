package com.fiscore.core.entities;
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "\"DTE_CATALOGO\"")
public class DteCatalogo {
@jakarta.persistence.Id
@jakarta.persistence.Column(name = "\"DTCA_ID\"", nullable = false)
private java.lang.Long id;

@jakarta.persistence.Column(name = "\"DTCA_COD\"", length = 18)
private java.lang.String dtcaCod;

@jakarta.persistence.Column(name = "\"DTCA_DES\"", length = 250)
private java.lang.String dtcaDes;

@jakarta.persistence.Column(name = "\"DTCA_CAP\"")
private java.lang.Long dtcaCap;

@jakarta.persistence.Column(name = "\"DTCA_EST\"")
private java.lang.Short dtcaEst;

@jakarta.persistence.Column(name = "\"DTCA_CAX\"", length = 10)
private java.lang.String dtcaCax;

@jakarta.persistence.Column(name = "\"DTCA_USR\"", length = 10)
private java.lang.String dtcaUsr;

@jakarta.persistence.Column(name = "\"DTCA_FEC\"")
private java.time.Instant dtcaFec;

public java.lang.Long getId() {
  return id;
}public void setId(java.lang.Long id) {
  this.id = id;
}

public java.lang.String getDtcaCod() {
  return dtcaCod;
}public void setDtcaCod(java.lang.String dtcaCod) {
  this.dtcaCod = dtcaCod;
}

public java.lang.String getDtcaDes() {
  return dtcaDes;
}public void setDtcaDes(java.lang.String dtcaDes) {
  this.dtcaDes = dtcaDes;
}

public java.lang.Long getDtcaCap() {
  return dtcaCap;
}public void setDtcaCap(java.lang.Long dtcaCap) {
  this.dtcaCap = dtcaCap;
}

public java.lang.Short getDtcaEst() {
  return dtcaEst;
}public void setDtcaEst(java.lang.Short dtcaEst) {
  this.dtcaEst = dtcaEst;
}

public java.lang.String getDtcaCax() {
  return dtcaCax;
}public void setDtcaCax(java.lang.String dtcaCax) {
  this.dtcaCax = dtcaCax;
}

public java.lang.String getDtcaUsr() {
  return dtcaUsr;
}public void setDtcaUsr(java.lang.String dtcaUsr) {
  this.dtcaUsr = dtcaUsr;
}

public java.time.Instant getDtcaFec() {
  return dtcaFec;
}public void setDtcaFec(java.time.Instant dtcaFec) {
  this.dtcaFec = dtcaFec;
}

}