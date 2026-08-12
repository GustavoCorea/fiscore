package com.fiscore.core.services;

import com.fiscore.core.entities.AdmUsuario;
import com.fiscore.core.repositories.AdmUsuarioRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Autenticación contra la tabla ADM_USUARIOS.
 *
 * El rol se guarda en la columna USER_ROL y debe ser uno de los que
 * {@code SecurityConfig} acepta (CDSF-ADMIN o CDSF-ACCESS); un CDSF-ADMIN
 * recibe además la autorización de acceso general.
 */
@Service
public class UsuarioService implements UserDetailsService {

    public static final String ROL_ADMIN = "CDSF-ADMIN";
    public static final String ROL_ACCESO = "CDSF-ACCESS";

    /** USER_ESTADO = 1 significa cuenta habilitada. */
    private static final BigDecimal ACTIVO = BigDecimal.ONE;

    private final AdmUsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UsuarioService(AdmUsuarioRepository usuarioRepository, BCryptPasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdmUsuario usuario = usuarioRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        boolean habilitado = usuario.getUserEstado() == null
                || usuario.getUserEstado().compareTo(ACTIVO) == 0;

        return User.withUsername(usuario.getUserUsername())
                .password(usuario.getUserPassword())
                .authorities(autoridadesDe(usuario))
                .disabled(!habilitado)
                .build();
    }

    private List<GrantedAuthority> autoridadesDe(AdmUsuario usuario) {
        String rol = (usuario.getUserRol() != null && !usuario.getUserRol().isBlank())
                ? usuario.getUserRol().trim().toUpperCase()
                : ROL_ACCESO;

        // Un administrador conserva también el permiso de acceso general.
        if (ROL_ADMIN.equals(rol)) {
            return List.of(new SimpleGrantedAuthority(ROL_ADMIN), new SimpleGrantedAuthority(ROL_ACCESO));
        }
        return List.of(new SimpleGrantedAuthority(ROL_ACCESO));
    }

    // =================================================================
    // Alta y mantenimiento
    // =================================================================

    @Transactional(readOnly = true)
    public Optional<AdmUsuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUserUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public long contar() {
        return usuarioRepository.count();
    }

    /**
     * Crea un usuario con la contraseña cifrada con BCrypt.
     * ADM_USUARIOS no usa secuencia, por lo que el identificador se calcula
     * a partir del máximo existente.
     */
    @Transactional
    public AdmUsuario crear(String username, String passwordEnClaro, String nombres,
                            String apellidos, String correo, String rol) {

        if (usuarioRepository.existsByUserUsernameIgnoreCase(username)) {
            throw new IllegalStateException("Ya existe un usuario con el nombre \"" + username + "\".");
        }
        if (passwordEnClaro == null || passwordEnClaro.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía.");
        }

        AdmUsuario usuario = new AdmUsuario();
        usuario.setId(siguienteId());
        usuario.setUserUsername(username);
        usuario.setUserPassword(passwordEncoder.encode(passwordEnClaro));
        usuario.setUserNombres(nombres);
        usuario.setUserApellidos(apellidos);
        usuario.setUserCorreo(correo);
        usuario.setUserRol(rol != null && !rol.isBlank() ? rol : ROL_ACCESO);
        usuario.setUserEstado(ACTIVO);
        usuario.setUserUsuarioRegistra("sistema");
        usuario.setUserFchRegistro(LocalDate.now());
        return usuarioRepository.save(usuario);
    }

    /** Restablece la contraseña de un usuario existente. */
    @Transactional
    public AdmUsuario cambiarPassword(String username, String passwordEnClaro) {
        AdmUsuario usuario = usuarioRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + username));
        usuario.setUserPassword(passwordEncoder.encode(passwordEnClaro));
        usuario.setUserUsuarioModifica("sistema");
        usuario.setUserFchModifica(LocalDate.now());
        return usuarioRepository.save(usuario);
    }

    private Long siguienteId() {
        return usuarioRepository.findAll().stream()
                .map(AdmUsuario::getId)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(0L) + 1L;
    }
}
