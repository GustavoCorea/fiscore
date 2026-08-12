package com.fiscore.core.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La identidad visual debe ser la de Fiscore, no la de la plantilla Hyper.
 * Estas pruebas evitan que los logotipos de ejemplo vuelvan a colarse al
 * copiar bloques de la plantilla original.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarcaTest {

    /** Recursos de la plantilla que ya no deben existir ni referenciarse. */
    private static final List<String> LOGOS_DE_PLANTILLA = List.of(
            "logo.png", "logo-dark.png", "logo-sm.png", "logo-dark-sm.png", "favicon.ico");

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Los recursos de logotipo de Hyper ya no están en el proyecto")
    void logosDePlantillaEliminados() {
        for (String recurso : LOGOS_DE_PLANTILLA) {
            assertThat(new ClassPathResource("static/assets/images/" + recurso).exists())
                    .as("sigue presente el recurso de la plantilla: %s", recurso)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("El símbolo y el favicon propios se sirven correctamente")
    void recursosPropiosDisponibles() throws Exception {
        for (String recurso : List.of("fiscore-mark.svg", "favicon.svg")) {
            mockMvc.perform(get("/assets/images/" + recurso))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("<svg")));
        }
    }

    @Test
    @DisplayName("Ninguna plantilla referencia los logotipos de Hyper")
    void plantillasSinReferenciasAHyper() throws IOException {
        Path templates = Path.of("src/main/resources/templates");
        try (Stream<Path> archivos = Files.walk(templates)) {
            archivos.filter(p -> p.toString().endsWith(".html")).forEach(archivo -> {
                String contenido = leer(archivo);
                for (String recurso : LOGOS_DE_PLANTILLA) {
                    assertThat(contenido)
                            .as("%s todavía referencia %s", archivo.getFileName(), recurso)
                            .doesNotContain(recurso);
                }
            });
        }
    }

    @Test
    @DisplayName("El menú, la barra superior y el acceso muestran la marca Fiscore")
    void marcaPresenteEnLasPantallas() throws Exception {
        String panel = mockMvc.perform(get("/inicio"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(panel).contains("fiscore-mark.svg");
        assertThat(panel).contains("fs-logo-text");
        assertThat(panel).contains("favicon.svg");

        String acceso = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(acceso).contains("fiscore-mark.svg");
        assertThat(acceso).contains("Fiscore");
    }

    @Test
    @DisplayName("El menú lateral trae la marca en su versión clara y en la oscura")
    void marcaEnAmbasVariantesDelMenu() throws Exception {
        String panel = mockMvc.perform(get("/inicio"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Hyper muestra .logo-light u .logo-dark según el color del menú:
        // si una de las dos faltara, el logotipo desaparecería en ese tema.
        assertThat(panel).contains("logo logo-light");
        assertThat(panel).contains("logo logo-dark");
    }

    private String leer(Path archivo) {
        try {
            return Files.readString(archivo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
    }
}
