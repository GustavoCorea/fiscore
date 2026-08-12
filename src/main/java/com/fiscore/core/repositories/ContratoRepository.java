package com.fiscore.core.repositories;

import com.fiscore.core.models.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Long> {
    List<Contrato> findByEstado(String estado);
    List<Contrato> findByClienteId(Long clienteId);
    long countByEstado(String estado);

    /** Suma total de honorarios de contratos activos */
    @Query("SELECT COALESCE(SUM(c.honorariosPactados), 0) FROM Contrato c WHERE c.estado = 'ACTIVO'")
    BigDecimal sumHonorariosActivos();

    /** Ingresos recurrentes por categoría de servicio (vía tabla contrato_servicio) */
    @Query("SELECT cs.servicio.categoria, COALESCE(SUM(cs.precioAcordado), 0) " +
           "FROM ContratoServicio cs JOIN cs.contrato c " +
           "WHERE c.estado = 'ACTIVO' " +
           "GROUP BY cs.servicio.categoria ORDER BY SUM(cs.precioAcordado) DESC")
    List<Object[]> ingresosPorCategoria();

    /** Top clientes por honorarios totales */
    @Query("SELECT cl.nombre, COALESCE(SUM(c.honorariosPactados), 0) " +
           "FROM Contrato c JOIN c.cliente cl " +
           "WHERE c.estado = 'ACTIVO' " +
           "GROUP BY cl.id, cl.nombre ORDER BY SUM(c.honorariosPactados) DESC")
    List<Object[]> topClientesPorHonorarios();

    /** Distribución por tipo de facturación */
    @Query("SELECT c.tipoFacturacion, COUNT(c) FROM Contrato c WHERE c.estado = 'ACTIVO' GROUP BY c.tipoFacturacion")
    List<Object[]> distribucionPorTipo();

    /** Todos los contratos activos con relaciones cargadas */
    @Query("SELECT DISTINCT c FROM Contrato c JOIN FETCH c.cliente cl LEFT JOIN FETCH c.servicios WHERE c.estado = 'ACTIVO' ORDER BY cl.nombre")
    List<Contrato> findActivosConDetalle();

    /** Contratos activos cuya próxima facturación cae en o antes de la fecha indicada */
    @Query("SELECT DISTINCT c FROM Contrato c JOIN FETCH c.cliente cl LEFT JOIN FETCH c.servicios " +
           "WHERE c.estado = 'ACTIVO' AND c.fechaProximaFacturacion IS NOT NULL AND c.fechaProximaFacturacion <= :fecha " +
           "ORDER BY c.fechaProximaFacturacion ASC")
    List<Contrato> findPorFacturarHasta(@Param("fecha") LocalDate fecha);

    /** Agenda de facturación: próximos vencimientos ordenados */
    @Query("SELECT DISTINCT c FROM Contrato c JOIN FETCH c.cliente cl " +
           "WHERE c.estado = 'ACTIVO' AND c.fechaProximaFacturacion IS NOT NULL " +
           "  AND c.fechaProximaFacturacion BETWEEN :desde AND :hasta " +
           "ORDER BY c.fechaProximaFacturacion ASC")
    List<Contrato> findAgendaFacturacion(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}