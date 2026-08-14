package com.fiscore.core.security;

import com.fiscore.core.entities.AdmUsuario;
import com.fiscore.core.repositories.AdmUsuarioRepository;
import com.fiscore.core.services.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pantalla de gestión de usuarios: control de acceso, lo que sale por el JSON
 * y las dos salvaguardas que impiden dejar el sistema sin quien lo administre.
 */
@SpringBootTest(properties = "authentication.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestionUsuariosTest {

    private static final String ADMIN = "admin.pruebas";
    private static final String CLAVE = "Prueba2026*";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioService usuarioService;
    @Autowired private AdmUsuarioRepository usuarioRepository;

    private AdmUsuario administrador;

    @BeforeEach
    void prepararUsuarios() {
        usuarioRepository.deleteAll();
        administrador = usuarioService.crear(ADMIN, CLAVE, "Ada", "Ramírez",
                "ada@fiscore.sv", UsuarioService.ROL_ADMIN);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor comoAdmin() {
        return user(ADMIN).authorities(
                new SimpleGrantedAuthority(UsuarioService.ROL_ADMIN),
                new SimpleGrantedAuthority(UsuarioService.ROL_ACCESO));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor comoEmpleado() {
        return user("empleado").authorities(new SimpleGrantedAuthority(UsuarioService.ROL_ACCESO));
    }

    @Test
    @DisplayName("La pantalla y su API quedan fuera del alcance de un usuario corriente")
    void reservadaAlAdministrador() throws Exception {
        mockMvc.perform(get("/usuarios").with(comoEmpleado()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/usuarios/listar").with(comoEmpleado()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El listado no deja escapar el hash de la contraseña")
    void elListadoNoExponeElHash() throws Exception {
        String json = mockMvc.perform(get("/usuarios/listar").with(comoAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json).contains(ADMIN);
        // El prefijo de todo hash BCrypt. Serializar la entidad entera lo habria
        // enviado al navegador en cada carga de la pantalla.
        assertThat(json).doesNotContain("$2a$");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("userPassword");
    }

    @Test
    @DisplayName("Se crea un usuario y su contraseña queda cifrada")
    void altaDeUsuario() throws Exception {
        String cuerpo = """
                {"username":"mgarcia","password":"Inicial2026*","nombres":"María",
                 "apellidos":"García","correo":"mgarcia@fiscore.sv","rol":"CDSF-ACCESS"}
                """;

        mockMvc.perform(post("/usuarios/guardar").with(comoAdmin()).with(csrf())
                        .contentType("application/json").content(cuerpo))
                .andExpect(status().isOk());

        AdmUsuario creado = usuarioRepository.findByUserUsernameIgnoreCase("mgarcia").orElseThrow();
        assertThat(creado.getUserPassword()).isNotEqualTo("Inicial2026*");
        assertThat(creado.getUserPassword()).startsWith("$2");
        assertThat(creado.getUserRol()).isEqualTo(UsuarioService.ROL_ACCESO);
        // El autor queda registrado, no un "sistema" fijo.
        assertThat(creado.getUserUsuarioRegistra()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("Nadie puede desactivar su propia cuenta")
    void noSePuedeUnoDesactivarASiMismo() throws Exception {
        mockMvc.perform(patch("/usuarios/" + administrador.getId() + "/estado")
                        .with(comoAdmin()).with(csrf())
                        .contentType("application/json").content("{\"activo\":false}"))
                .andExpect(status().isConflict());

        AdmUsuario sigue = usuarioRepository.findById(administrador.getId()).orElseThrow();
        assertThat(sigue.getUserEstado()).isEqualByComparingTo(java.math.BigDecimal.ONE);
    }

    @Test
    @DisplayName("No se puede desactivar al último administrador activo")
    void elUltimoAdministradorNoSePuedeDesactivar() {
        assertThatThrownBy(() -> usuarioService.cambiarEstado(administrador.getId(), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("último administrador");
    }

    @Test
    @DisplayName("Tampoco se le puede quitar el rol al último administrador")
    void elUltimoAdministradorNoSeDegrada() {
        assertThatThrownBy(() -> usuarioService.actualizar(administrador.getId(),
                "Ada", "Ramírez", "ada@fiscore.sv", null, UsuarioService.ROL_ACCESO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("último administrador");
    }

    @Test
    @DisplayName("Con otro administrador de por medio, sí se puede desactivar")
    void conRelevoSePuedeDesactivar() {
        AdmUsuario relevo = usuarioService.crear("segundo.admin", CLAVE, "Beto", "Núñez",
                "beto@fiscore.sv", UsuarioService.ROL_ADMIN);

        usuarioService.cambiarEstado(relevo.getId(), false);

        AdmUsuario guardado = usuarioRepository.findById(relevo.getId()).orElseThrow();
        assertThat(guardado.getUserEstado()).isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }
}
