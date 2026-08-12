package com.fiscore.core.config;

import com.fiscore.core.services.UsuarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Crea la cuenta administradora inicial cuando ADM_USUARIOS está vacía,
 * de forma que exista una credencial con la que entrar en un entorno nuevo.
 *
 * Solo actúa si no hay ningún usuario: en cuanto exista uno, este componente
 * no vuelve a tocar la tabla. Se desactiva con
 * {@code fiscore.seguridad.usuario-inicial.habilitado=false}.
 */
@Component
@Profile("!test")
public class UsuarioInicialBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuarioInicialBootstrap.class);

    private final UsuarioService usuarioService;

    @Value("${fiscore.seguridad.usuario-inicial.habilitado:true}")
    private boolean habilitado;

    @Value("${fiscore.seguridad.usuario-inicial.username:admin}")
    private String username;

    @Value("${fiscore.seguridad.usuario-inicial.password:Fiscore2026*}")
    private String password;

    @Value("${fiscore.seguridad.usuario-inicial.correo:admin@fiscore.sv}")
    private String correo;

    public UsuarioInicialBootstrap(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            return;
        }
        if (usuarioService.contar() > 0) {
            log.debug("ADM_USUARIOS ya tiene usuarios: no se crea la cuenta inicial.");
            return;
        }

        usuarioService.crear(username, password, "Administrador", "Fiscore",
                correo, UsuarioService.ROL_ADMIN);

        log.warn("""

                ==========================================================
                  Se creó la cuenta administradora inicial de Fiscore
                    Usuario:    {}
                    Contraseña: {}
                  Cambie esta contraseña antes de exponer el sistema.
                  Para no volver a crearla:
                    fiscore.seguridad.usuario-inicial.habilitado=false
                ==========================================================
                """, username, password);
    }
}
