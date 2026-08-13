package com.fiscore.core.services;

import com.fiscore.core.entities.AdmUsuario;
import com.fiscore.core.repositories.AdmUsuarioRepository;
import org.springframework.data.domain.AuditorAware;
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
    private final AuditorAware<String> auditor;

    public UsuarioService(AdmUsuarioRepository usuarioRepository,
                          BCryptPasswordEncoder passwordEncoder,
                          AuditorAware<String> auditor) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditor = auditor;
    }

    /**
     * Quién está actuando. Se reutiliza el AuditorAware de la auditoría en vez
     * de repetir aquí la lectura del contexto de seguridad: así las columnas
     * USER_USUARIO_REGISTRA y USER_USUARIO_MODIFICA dicen lo mismo que
     * CREADO_POR y MODIFICADO_POR en el resto de tablas.
     */
    private String actorActual() {
        return auditor.getCurrentAuditor().orElse("sistema");
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
        usuario.setUserUsuarioRegistra(actorActual());
        usuario.setUserFchRegistro(LocalDate.now());
        return usuarioRepository.save(usuario);
    }

    /** Restablece la contraseña de un usuario existente. */
    @Transactional
    public AdmUsuario cambiarPassword(String username, String passwordEnClaro) {
        AdmUsuario usuario = usuarioRepository.findByUserUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + username));
        usuario.setUserPassword(passwordEncoder.encode(passwordEnClaro));
        usuario.setUserUsuarioModifica(actorActual());
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

    // =================================================================
    // Pantalla de gestión
    // =================================================================

    @Transactional(readOnly = true)
    public List<AdmUsuario> listar() {
        return usuarioRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(
                        AdmUsuario::getUserUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AdmUsuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    /** Datos de contacto y rol. La contraseña se cambia por separado. */
    @Transactional
    public AdmUsuario actualizar(Long id, String nombres, String apellidos, String correo,
                                 String telefono, String rol) {
        AdmUsuario usuario = exigir(id);
        String rolNuevo = normalizarRol(rol);

        if (!ROL_ADMIN.equals(rolNuevo)) {
            noContraUnoMismo(usuario, "quitarte a ti mismo el rol de administrador");
            exigirQueQuedeUnAdministrador(usuario);
        }

        usuario.setUserNombres(nombres);
        usuario.setUserApellidos(apellidos);
        usuario.setUserCorreo(correo);
        usuario.setUserTelefono(telefono);
        usuario.setUserRol(rolNuevo);
        marcarModificado(usuario);
        return usuarioRepository.save(usuario);
    }

    /**
     * Habilita o deshabilita la cuenta. No se borran usuarios: su nombre queda
     * escrito en las columnas de auditoría de facturas y contratos, y un
     * registro que apunta a alguien que ya no existe no se puede interpretar.
     */
    @Transactional
    public AdmUsuario cambiarEstado(Long id, boolean activo) {
        AdmUsuario usuario = exigir(id);

        if (!activo) {
            noContraUnoMismo(usuario, "desactivar tu propia cuenta");
            exigirQueQuedeUnAdministrador(usuario);
        }

        usuario.setUserEstado(activo ? ACTIVO : BigDecimal.ZERO);
        marcarModificado(usuario);
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public AdmUsuario restablecerPassword(Long id, String passwordEnClaro) {
        if (passwordEnClaro == null || passwordEnClaro.isBlank()) {
            throw new IllegalArgumentException("La contraseña no puede estar vacía.");
        }
        AdmUsuario usuario = exigir(id);
        usuario.setUserPassword(passwordEncoder.encode(passwordEnClaro));
        marcarModificado(usuario);
        return usuarioRepository.save(usuario);
    }

    // ---- Salvaguardas ----

    /**
     * Impide quedarse sin nadie que pueda administrar. Sin esto, degradar o
     * desactivar al último administrador deja la configuración fiscal y la
     * propia gestión de usuarios fuera del alcance de todos, y solo se sale
     * de ahí tocando la base a mano.
     */
    private void exigirQueQuedeUnAdministrador(AdmUsuario objetivo) {
        boolean quedaOtro = usuarioRepository.findAll().stream()
                .filter(u -> !java.util.Objects.equals(u.getId(), objetivo.getId()))
                .anyMatch(this::esAdministradorActivo);

        if (!quedaOtro) {
            throw new IllegalStateException(
                    "Es el último administrador activo. Nombra a otro antes de cambiar este.");
        }
    }

    /** Las dos operaciones que podrían dejar fuera a quien las ejecuta. */
    private void noContraUnoMismo(AdmUsuario objetivo, String accion) {
        if (objetivo.getUserUsername() != null
                && objetivo.getUserUsername().equalsIgnoreCase(actorActual())) {
            throw new IllegalStateException("No puedes " + accion + ".");
        }
    }

    private boolean esAdministradorActivo(AdmUsuario u) {
        boolean activo = u.getUserEstado() == null || u.getUserEstado().compareTo(ACTIVO) == 0;
        return activo && ROL_ADMIN.equals(normalizarRol(u.getUserRol()));
    }

    private String normalizarRol(String rol) {
        String limpio = rol != null ? rol.trim().toUpperCase() : "";
        return ROL_ADMIN.equals(limpio) ? ROL_ADMIN : ROL_ACCESO;
    }

    private AdmUsuario exigir(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
    }

    private void marcarModificado(AdmUsuario usuario) {
        usuario.setUserUsuarioModifica(actorActual());
        usuario.setUserFchModifica(LocalDate.now());
    }
}
