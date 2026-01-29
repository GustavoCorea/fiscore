package com.fiscore.core.entities;
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "\"ADM_USUARIOS\"")
public class AdmUsuario {
@jakarta.persistence.Id
@jakarta.persistence.Column(name = "\"USER_ID\"", nullable = false)
private java.lang.Long id;

@jakarta.persistence.Column(name = "\"USER_USERNAME\"", nullable = false, length = 100)
private java.lang.String userUsername;

@jakarta.persistence.Column(name = "\"USER_PASSWORD\"", nullable = false, length = 250)
private java.lang.String userPassword;

@jakarta.persistence.Column(name = "\"USER_DUI\"", length = 9)
private java.lang.String userDui;

@jakarta.persistence.Column(name = "\"USER_NOMBRES\"", length = 150)
private java.lang.String userNombres;

@jakarta.persistence.Column(name = "\"USER_APELLIDOS\"", length = 150)
private java.lang.String userApellidos;

@jakarta.persistence.Column(name = "\"USER_SEXO\"", length = Integer.MAX_VALUE)
private java.lang.String userSexo;

@jakarta.persistence.Column(name = "\"USER_FCH_NAC\"")
private java.time.LocalDate userFchNac;

@jakarta.persistence.Column(name = "\"USER_TELEFONO\"", length = 12)
private java.lang.String userTelefono;

@jakarta.persistence.Column(name = "\"USER_CORREO\"", length = 150)
private java.lang.String userCorreo;

@org.hibernate.annotations.ColumnDefault("1")
@jakarta.persistence.Column(name = "\"USER_ESTADO\"", precision = 1)
private java.math.BigDecimal userEstado;

@jakarta.persistence.Column(name = "\"USER_USUARIO_REGISTRA\"", length = 100)
private java.lang.String userUsuarioRegistra;

@jakarta.persistence.Column(name = "\"USER_FCH_REGISTRO\"")
private java.time.LocalDate userFchRegistro;

@jakarta.persistence.Column(name = "\"USER_USUARIO_MODIFICA\"", length = 100)
private java.lang.String userUsuarioModifica;

@jakarta.persistence.Column(name = "\"USER_FCH_MODIFICA\"")
private java.time.LocalDate userFchModifica;

public java.lang.Long getId() {
  return id;
}public void setId(java.lang.Long id) {
  this.id = id;
}

public java.lang.String getUserUsername() {
  return userUsername;
}public void setUserUsername(java.lang.String userUsername) {
  this.userUsername = userUsername;
}

public java.lang.String getUserPassword() {
  return userPassword;
}public void setUserPassword(java.lang.String userPassword) {
  this.userPassword = userPassword;
}

public java.lang.String getUserDui() {
  return userDui;
}public void setUserDui(java.lang.String userDui) {
  this.userDui = userDui;
}

public java.lang.String getUserNombres() {
  return userNombres;
}public void setUserNombres(java.lang.String userNombres) {
  this.userNombres = userNombres;
}

public java.lang.String getUserApellidos() {
  return userApellidos;
}public void setUserApellidos(java.lang.String userApellidos) {
  this.userApellidos = userApellidos;
}

public java.lang.String getUserSexo() {
  return userSexo;
}public void setUserSexo(java.lang.String userSexo) {
  this.userSexo = userSexo;
}

public java.time.LocalDate getUserFchNac() {
  return userFchNac;
}public void setUserFchNac(java.time.LocalDate userFchNac) {
  this.userFchNac = userFchNac;
}

public java.lang.String getUserTelefono() {
  return userTelefono;
}public void setUserTelefono(java.lang.String userTelefono) {
  this.userTelefono = userTelefono;
}

public java.lang.String getUserCorreo() {
  return userCorreo;
}public void setUserCorreo(java.lang.String userCorreo) {
  this.userCorreo = userCorreo;
}

public java.math.BigDecimal getUserEstado() {
  return userEstado;
}public void setUserEstado(java.math.BigDecimal userEstado) {
  this.userEstado = userEstado;
}

public java.lang.String getUserUsuarioRegistra() {
  return userUsuarioRegistra;
}public void setUserUsuarioRegistra(java.lang.String userUsuarioRegistra) {
  this.userUsuarioRegistra = userUsuarioRegistra;
}

public java.time.LocalDate getUserFchRegistro() {
  return userFchRegistro;
}public void setUserFchRegistro(java.time.LocalDate userFchRegistro) {
  this.userFchRegistro = userFchRegistro;
}

public java.lang.String getUserUsuarioModifica() {
  return userUsuarioModifica;
}public void setUserUsuarioModifica(java.lang.String userUsuarioModifica) {
  this.userUsuarioModifica = userUsuarioModifica;
}

public java.time.LocalDate getUserFchModifica() {
  return userFchModifica;
}public void setUserFchModifica(java.time.LocalDate userFchModifica) {
  this.userFchModifica = userFchModifica;
}

}