package com.fiscore.core.controller;

import com.fiscore.core.entities.AdmUsuario;
import com.fiscore.core.services.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alta y mantenimiento de usuarios.
 *
 * Toda la ruta está reservada al administrador en SecurityConfig: quien
 * gestiona usuarios puede concederse cualquier permiso, así que el control de
 * acceso a esta pantalla es el mismo que protege la configuración fiscal.
 *
 * Las respuestas nunca devuelven la entidad tal cual. AdmUsuario lleva el hash
 * BCrypt en USER_PASSWORD y serializarla entera lo enviaría al navegador en
 * cada listado; se arma una vista explícita con los campos que la pantalla
 * necesita, y el hash no es uno de ellos.
 */
@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Datos que llegan del formulario. La contraseña se trata aparte. */
    public record FormularioUsuario(String username, String password, String nombres,
                                    String apellidos, String correo, String telefono,
                                    String rol) {
    }

    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<?> listar() {
        List<Map<String, Object>> vista = usuarioService.listar().stream()
                .map(UsuarioController::vista)
                .toList();
        return ResponseEntity.ok(vista);
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return usuarioService.buscarPorId(id)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(vista(u)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/guardar")
    @ResponseBody
    public ResponseEntity<?> crear(@RequestBody FormularioUsuario form) {
        AdmUsuario creado = usuarioService.crear(
                form.username(), form.password(), form.nombres(),
                form.apellidos(), form.correo(), form.rol());

        // El teléfono no forma parte de la firma de crear(); se guarda aparte
        // para no cambiar una API que usan la siembra de arranque y las pruebas.
        if (form.telefono() != null && !form.telefono().isBlank()) {
            usuarioService.actualizar(creado.getId(), form.nombres(), form.apellidos(),
                    form.correo(), form.telefono(), form.rol());
        }

        return ResponseEntity.ok(Map.of("message", "Usuario creado", "id", creado.getId()));
    }

    @PutMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody FormularioUsuario form) {
        usuarioService.actualizar(id, form.nombres(), form.apellidos(),
                form.correo(), form.telefono(), form.rol());
        return ResponseEntity.ok(Map.of("message", "Usuario actualizado", "id", id));
    }

    @PatchMapping("/{id}/estado")
    @ResponseBody
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, Object> cuerpo) {
        boolean activo = Boolean.parseBoolean(String.valueOf(cuerpo.get("activo")));
        usuarioService.cambiarEstado(id, activo);
        return ResponseEntity.ok(Map.of("message", activo ? "Usuario activado" : "Usuario desactivado"));
    }

    @PostMapping("/{id}/password")
    @ResponseBody
    public ResponseEntity<?> restablecerPassword(@PathVariable Long id, @RequestBody Map<String, String> cuerpo) {
        usuarioService.restablecerPassword(id, cuerpo.get("password"));
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida"));
    }

    // -----------------------------------------------------------------

    /** Proyección segura: todo lo que la pantalla usa, y nada del hash. */
    private static Map<String, Object> vista(AdmUsuario u) {
        boolean activo = u.getUserEstado() == null
                || u.getUserEstado().compareTo(BigDecimal.ONE) == 0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUserUsername());
        m.put("nombres", u.getUserNombres());
        m.put("apellidos", u.getUserApellidos());
        m.put("correo", u.getUserCorreo());
        m.put("telefono", u.getUserTelefono());
        m.put("rol", u.getUserRol());
        m.put("activo", activo);
        m.put("fechaRegistro", u.getUserFchRegistro());
        m.put("usuarioRegistra", u.getUserUsuarioRegistra());
        m.put("fechaModifica", u.getUserFchModifica());
        m.put("usuarioModifica", u.getUserUsuarioModifica());
        return m;
    }
}
