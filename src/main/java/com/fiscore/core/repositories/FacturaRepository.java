package com.fiscore.core.repositories;

import com.fiscore.core.models.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {
    List<Factura> findByEstadoOrderByFechaEmisionDesc(String estado);
    List<Factura> findAllByOrderByFechaEmisionDesc();
    Optional<Factura> findTopByTipoDteOrderByIdDesc(String tipoDte);

    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f WHERE f.estado = 'EMITIDA'")
    BigDecimal sumMontoPendiente();

    /** Total facturado (emitida + pagada) */
    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f WHERE f.estado IN ('EMITIDA','PAGADA')")
    BigDecimal sumTotalFacturado();

    /** Facturado por mes - últimos N meses */
    @Query(value = "SELECT TO_CHAR(f.fecha_emision, 'YYYY-MM') AS mes, COALESCE(SUM(f.monto_total), 0) " +
                   "FROM factura f WHERE f.estado IN ('EMITIDA','PAGADA') " +
                   "AND f.fecha_emision >= NOW() - INTERVAL ':meses months' " +
                   "GROUP BY mes ORDER BY mes ASC", nativeQuery = true)
    List<Object[]> facturadoPorMes(@Param("meses") int meses);

    /** Conteo por estado */
    @Query("SELECT f.estado, COUNT(f) FROM Factura f GROUP BY f.estado")
    List<Object[]> distribucionPorEstado();

    /** Top 5 clientes por monto facturado */
    @Query("SELECT cl.nombre, COALESCE(SUM(f.montoTotal), 0) " +
           "FROM Factura f JOIN f.cliente cl " +
           "WHERE f.estado IN ('EMITIDA','PAGADA') " +
           "GROUP BY cl.id, cl.nombre ORDER BY SUM(f.montoTotal) DESC")
    List<Object[]> topClientesPorFacturado();
}