package com.fiscore.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${authentication.enabled:true}") // Por defecto, la autenticación está habilitada
    private boolean authenticationEnabled;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Spring Security 6 carga el token CSRF de forma diferida: si nadie lo consulta
     * durante la petición, la cookie XSRF-TOKEN nunca se emite y el primer POST
     * se rechaza con 403. Poner a null el nombre del atributo fuerza la carga
     * inmediata, de modo que la cookie viaja siempre.
     *
     * Se usa el handler simple (no el XOR de protección BREACH) porque el valor
     * de la cookie debe coincidir literalmente con el que el JavaScript reenvía
     * en la cabecera X-XSRF-TOKEN.
     */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (authenticationEnabled) {
            http
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                // withHttpOnlyFalse permite que el JS lea la cookie XSRF-TOKEN
                // y la reenvíe como cabecera en las llamadas fetch().
                .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login**", "/recover-password", "/autenticar", "/resources/**", "/error", "/assets/**").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(login -> login
                    .loginPage("/login")
                    .defaultSuccessUrl("/inicio", true)
                    .failureUrl("/login?error")
                    .permitAll()
                )
                .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout")
                    // El enlace del menú es un GET, no un POST
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                    .permitAll()
                )
                .sessionManagement(session -> session
                    .maximumSessions(1)
                    .expiredUrl("/login?expired=true")
                    // false: al iniciar sesión de nuevo se cierra la anterior en lugar
                    // de bloquear el acceso, que dejaba al usuario fuera tras un cierre sucio.
                    .maxSessionsPreventsLogin(false)
                );
        } else {
            // Configuración sin autenticación
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                )
                    .addFilterBefore((request, response, chain) -> {
                        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("DEV-ADMIN"));
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken("user.local", null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        chain.doFilter(request, response);
                    }, UsernamePasswordAuthenticationFilter.class);
        }
        return http.build();
    }
}