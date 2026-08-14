package com.fiscore.core.repositories;

import com.fiscore.core.models.RegistroHoras;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegistroHorasRepository extends JpaRepository<RegistroHoras, Long> {

    List<RegistroHoras> findByProyectoIdOrderByFechaDescIdDesc(Long proyectoId);

    /** Pendientes de cobrar: facturables y todavía sin factura asignada. */
    @Query("""
           select r from RegistroHoras r
            where r.proyecto.id = :proyectoId
              and r.factura is null
              and r.facturable = true
            order by r.fecha, r.id
           """)
    List<RegistroHoras> findPendientesDeFacturar(@Param("proyectoId") Long proyectoId);

    long countByFacturaId(Long facturaId);
}
