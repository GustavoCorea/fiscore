package com.fiscore.core.repositories;

import com.fiscore.core.models.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    List<Proyecto> findByEstadoOrderByFechaCreacionDesc(String estado);

    List<Proyecto> findByClienteId(Long clienteId);

    long countByEstado(String estado);

    /** Proyectos terminados que aún no se han facturado */
    @Query("SELECT p FROM Proyecto p LEFT JOIN FETCH p.cliente " +
           "WHERE p.estado = 'FINALIZADO' AND (p.facturado IS NULL OR p.facturado = false) " +
           "ORDER BY p.fechaFin ASC NULLS LAST, p.fechaCreacion ASC")
    List<Proyecto> findFinalizadosSinFacturar();

    /** Proyectos en ejecución cuya fecha estimada de fin ya pasó */
    @Query("SELECT p FROM Proyecto p LEFT JOIN FETCH p.cliente " +
           "WHERE p.estado = 'EN_EJECUCION' AND p.fechaEstimadaFin IS NOT NULL AND p.fechaEstimadaFin < :fecha " +
           "ORDER BY p.fechaEstimadaFin ASC")
    List<Proyecto> findAtrasados(LocalDate fecha);

    /** Presupuesto y conteo agrupados por estado */
    @Query("SELECT p.estado, COUNT(p), COALESCE(SUM(p.presupuesto), 0) FROM Proyecto p GROUP BY p.estado")
    List<Object[]> resumenPorEstado();

    /** Presupuesto y conteo agrupados por categoría */
    @Query("SELECT p.categoria, COUNT(p), COALESCE(SUM(p.presupuesto), 0) FROM Proyecto p " +
           "WHERE p.estado <> 'CANCELADO' GROUP BY p.categoria ORDER BY SUM(p.presupuesto) DESC")
    List<Object[]> resumenPorCategoria();

    /** Presupuesto comprometido en proyectos aún no facturados */
    @Query("SELECT COALESCE(SUM(p.presupuesto), 0) FROM Proyecto p " +
           "WHERE p.estado IN ('COTIZADO','EN_EJECUCION','FINALIZADO') AND (p.facturado IS NULL OR p.facturado = false)")
    BigDecimal sumPresupuestoPendiente();
}
