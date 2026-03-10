package com.fiscore.core.repositories;

import com.fiscore.core.models.Contrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ContratoRepository extends JpaRepository<Contrato, Long> {
    List<Contrato> findByEstado(String estado);
    List<Contrato> findByClienteId(Long clienteId);
    long countByEstado(String estado);

    /** Suma total de honorarios de contratos activos */
    @Query("SELECT COALESCE(SUM(c.honorariosPactados), 0) FROM Contrato c WHERE c.estado = 'ACTIVO'")
    BigDecimal sumHonorariosActivos();

    /** Ingresos recurrentes por categoría de servicio */
    @Query("SELECT s.categoria, COALESCE(SUM(c.honorariosPactados), 0) " +
           "FROM Contrato c JOIN c.servicio s " +
           "WHERE c.estado = 'ACTIVO' " +
           "GROUP BY s.categoria ORDER BY SUM(c.honorariosPactados) DESC")
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

    /** Todos los contratos agrupados por cliente (activos) */
    @Query("SELECT c FROM Contrato c JOIN FETCH c.cliente cl JOIN FETCH c.servicio WHERE c.estado = 'ACTIVO' ORDER BY cl.nombre")
    List<Contrato> findActivosConDetalle();
}