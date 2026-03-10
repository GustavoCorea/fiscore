package com.fiscore.core.repositories;

import com.fiscore.core.models.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findByEstadoOrderByFechaCreacionDesc(String estado);
    List<Proyecto> findByClienteId(Long clienteId);
    long countByEstado(String estado);
}