package com.fiscore.core.entities;
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "\"DTE_PARAMETRO\"")
public class DteParametro {
@jakarta.persistence.Id
@jakarta.persistence.GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
@jakarta.persistence.Column(name = "\"DTPA_ID\"", nullable = false)
private java.lang.Long id;

@jakarta.persistence.Column(name = "\"DTPA_NOMBRE\"", nullable = false, length = 60)
private java.lang.String dtpaNombre;

@jakarta.persistence.Column(name = "\"DTPA_DESCRIPCION\"", nullable = false, length = 70)
private java.lang.String dtpaDescripcion;

@jakarta.persistence.Column(name = "\"DTPA_VAL\"", nullable = false, length = 500)
private java.lang.String dtpaVal;

@jakarta.persistence.Column(name = "\"DTPA_ESTADO\"")
private java.lang.Short dtpaEstado;

public java.lang.Long getId() {
  return id;
}public void setId(java.lang.Long id) {
  this.id = id;
}

public java.lang.String getDtpaNombre() {
  return dtpaNombre;
}public void setDtpaNombre(java.lang.String dtpaNombre) {
  this.dtpaNombre = dtpaNombre;
}

public java.lang.String getDtpaDescripcion() {
  return dtpaDescripcion;
}public void setDtpaDescripcion(java.lang.String dtpaDescripcion) {
  this.dtpaDescripcion = dtpaDescripcion;
}

public java.lang.String getDtpaVal() {
  return dtpaVal;
}public void setDtpaVal(java.lang.String dtpaVal) {
  this.dtpaVal = dtpaVal;
}

public java.lang.Short getDtpaEstado() {
  return dtpaEstado;
}public void setDtpaEstado(java.lang.Short dtpaEstado) {
  this.dtpaEstado = dtpaEstado;
}

}