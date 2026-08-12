package com.fiscore.core.repositories;

import com.fiscore.core.entities.DteParametro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DteParametroRepository extends JpaRepository<DteParametro, Long> {

    Optional<DteParametro> findByDtpaNombre(String dtpaNombre);

    List<DteParametro> findAllByOrderByDtpaNombreAsc();

    boolean existsByDtpaNombre(String dtpaNombre);
}
