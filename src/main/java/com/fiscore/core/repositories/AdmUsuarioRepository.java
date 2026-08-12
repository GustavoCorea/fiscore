package com.fiscore.core.repositories;

import com.fiscore.core.entities.AdmUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdmUsuarioRepository extends JpaRepository<AdmUsuario, Long> {

    /** Búsqueda para el login. El usuario se compara sin distinguir mayúsculas. */
    Optional<AdmUsuario> findByUserUsernameIgnoreCase(String userUsername);

    boolean existsByUserUsernameIgnoreCase(String userUsername);
}
