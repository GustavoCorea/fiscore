package com.fiscore.core.entities;
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "\"DTE_TOKEN\"")
public class DteToken {
@jakarta.persistence.Id
@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
@jakarta.persistence.Column(name = "\"DTTO_ID\"", nullable = false)
private java.lang.Long id;

@jakarta.persistence.Column(name = "\"DTTO_TOKEN\"", length = 500)
private java.lang.String dttoToken;

@jakarta.persistence.Column(name = "\"DTTO_STATUS\"", length = 20)
private java.lang.String dttoStatus;

@jakarta.persistence.Column(name = "\"DTTO_MESSAGE\"", length = 20)
private java.lang.String dttoMessage;

@jakarta.persistence.Column(name = "\"DTTO_RES\"", length = 600)
private java.lang.String dttoRes;

@jakarta.persistence.Column(name = "\"DTTO_FEC\"")
private java.time.Instant dttoFec;

public java.lang.Long getId() {
  return id;
}public void setId(java.lang.Long id) {
  this.id = id;
}

public java.lang.String getDttoToken() {
  return dttoToken;
}public void setDttoToken(java.lang.String dttoToken) {
  this.dttoToken = dttoToken;
}

public java.lang.String getDttoStatus() {
  return dttoStatus;
}public void setDttoStatus(java.lang.String dttoStatus) {
  this.dttoStatus = dttoStatus;
}

public java.lang.String getDttoMessage() {
  return dttoMessage;
}public void setDttoMessage(java.lang.String dttoMessage) {
  this.dttoMessage = dttoMessage;
}

public java.lang.String getDttoRes() {
  return dttoRes;
}public void setDttoRes(java.lang.String dttoRes) {
  this.dttoRes = dttoRes;
}

public java.time.Instant getDttoFec() {
  return dttoFec;
}public void setDttoFec(java.time.Instant dttoFec) {
  this.dttoFec = dttoFec;
}

}