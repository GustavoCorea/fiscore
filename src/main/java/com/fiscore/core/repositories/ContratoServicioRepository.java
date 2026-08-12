package com.fiscore.core.repositories;

import com.fiscore.core.models.ContratoServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContratoServicioRepository extends JpaRepository<ContratoServicio, Long> {
}