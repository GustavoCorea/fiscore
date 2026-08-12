package com.fiscore.core.security;

import com.fiscore.core.repositories.AdmUsuarioRepository;
import com.fiscore.core.services.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifica el inicio de sesión contra ADM_USUARIOS con la seguridad activada.
 * El resto de la suite corre con {@code authentication.enabled=false}, así que
 * esta clase levanta su propio contexto con la autenticación encendida.
 */
@SpringBootTest(properties = "authentication.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadTest {

    private static final String USUARIO = "contador.pruebas";
    private static final String CLAVE = "Prueba2026*";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioService usuarioService;
    @Autowired private AdmUsuarioRepository usuarioRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void crearUsuario() {
        usuarioRepository.deleteAll();
        usuarioService.crear(USUARIO, CLAVE, "Ana", "Contreras",
                "ana@fiscore.sv", UsuarioService.ROL_ADMIN);
    }

    @Test
    @DisplayName("La contraseña se guarda cifrada con BCrypt, nunca en claro")
    void passwordCifrada() {
        var usuario = usuarioRepository.findByUserUsernameIgnoreCase(USUARIO).orElseThrow();

        assertThat(usuario.getUserPassword()).isNotEqualTo(CLAVE);
        assertThat(usuario.getUserPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches(CLAVE, usuario.getUserPassword())).isTrue();
    }

    @Test
    @DisplayName("Sin sesión, cualquier pantalla redirige al login")
    void sinSesionRedirigeAlLogin() throws Exception {
        mockMvc.perform(get("/inicio"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(get("/reportes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("El login es público y se renderiza")
    void loginEsPublico() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Inicio de sesión")));
    }

    @Test
    @DisplayName("Las credenciales correctas autentican y llevan al panel")
    void loginCorrecto() throws Exception {
        mockMvc.perform(formLogin("/login").user(USUARIO).password(CLAVE))
                .andExpect(authenticated().withUsername(USUARIO))
                .andExpect(redirectedUrl("/inicio"));
    }

    @Test
    @DisplayName("El usuario se reconoce sin distinguir mayúsculas")
    void loginIgnoraMayusculas() throws Exception {
        mockMvc.perform(formLogin("/login").user(USUARIO.toUpperCase()).password(CLAVE))
                .andExpect(authenticated());
    }

    @Test
    @DisplayName("Una contraseña incorrecta no autentica")
    void loginIncorrecto() throws Exception {
        mockMvc.perform(formLogin("/login").user(USUARIO).password("equivocada"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("Un administrador recibe los roles CDSF-ADMIN y CDSF-ACCESS")
    void rolesDelAdministrador() {
        var detalles = usuarioService.loadUserByUsername(USUARIO);

        assertThat(detalles.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder(UsuarioService.ROL_ADMIN, UsuarioService.ROL_ACCESO);
    }

    @Test
    @DisplayName("Con sesión iniciada, el panel y los reportes responden")
    void navegacionAutenticada() throws Exception {
        mockMvc.perform(get("/inicio").with(user(usuarioService.loadUserByUsername(USUARIO))))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));

        mockMvc.perform(get("/reportes").with(user(usuarioService.loadUserByUsername(USUARIO))))
                .andExpect(status().isOk())
                .andExpect(view().name("reportes/reportes"));
    }

    @Test
    @DisplayName("Una escritura sin token CSRF se rechaza; con token, pasa")
    void csrfProtegeLasEscrituras() throws Exception {
        var autenticado = user(usuarioService.loadUserByUsername(USUARIO));
        String cuerpo = """
                {"nombre":"Cliente CSRF","nit":"0614-090909-009-0","tipoCliente":"JURIDICA","estado":"ACTIVO"}
                """;

        mockMvc.perform(post("/clientes/guardar")
                        .with(autenticado)
                        .contentType("application/json")
                        .content(cuerpo))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/clientes/guardar")
                        .with(autenticado).with(csrf())
                        .contentType("application/json")
                        .content(cuerpo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("No se admiten dos usuarios con el mismo nombre")
    void noSeDuplicanUsuarios() {
        assertThatThrownBy(() -> usuarioService.crear(USUARIO, "otra", "X", "Y", "x@y.sv", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ya existe un usuario");
    }
}
