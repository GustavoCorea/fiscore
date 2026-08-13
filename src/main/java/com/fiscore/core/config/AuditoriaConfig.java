package com.fiscore.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Activa la auditoría de JPA: quién creó y quién modificó cada registro, y cuándo.
 *
 * Hasta ahora solo Cliente guardaba datos de registro, de modo que no había forma
 * de responder quién anuló una factura o quién cambió un contrato. Para documentos
 * fiscales eso no es un adorno: es lo que permite defender una cifra ante una
 * revisión, y en un despacho con varios empleados, deslindar responsabilidades.
 *
 * @see com.fiscore.core.models.EntidadAuditable
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class AuditoriaConfig {

    /**
     * Autor registrado cuando no hay una sesión detrás del cambio: la siembra de
     * arranque, las pruebas y cualquier proceso automático futuro.
     */
    public static final String SISTEMA = "sistema";

    @Bean
    public AuditorAware<String> auditorActual() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            boolean sinSesion = auth == null
                    || !auth.isAuthenticated()
                    || auth instanceof AnonymousAuthenticationToken;

            // Nunca se devuelve Optional.empty(): dejar la columna nula obligaria a
            // interpretar despues si el registro es anterior a la auditoria o si el
            // autor simplemente no se capturo.
            return Optional.of(sinSesion ? SISTEMA : auth.getName());
        };
    }
}
