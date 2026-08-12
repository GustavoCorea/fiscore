package com.fiscore.core.repositories;

import com.fiscore.core.models.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    List<Factura> findByEstadoOrderByFechaEmisionDesc(String estado);

    List<Factura> findAllByOrderByFechaEmisionDesc();

    Optional<Factura> findTopByTipoDteOrderByIdDesc(String tipoDte);

    List<Factura> findByContratoIdOrderByFechaEmisionDesc(Long contratoId);

    List<Factura> findByProyectoIdOrderByFechaEmisionDesc(Long proyectoId);

    long countByEstado(String estado);

    /** Facturas emitidas de un contrato para un periodo concreto (control de duplicados) */
    @Query("SELECT COUNT(f) FROM Factura f " +
           "WHERE f.contrato.id = :contratoId AND f.periodoFacturado = :periodo AND f.estado <> 'ANULADA'")
    long countByContratoYPeriodo(@Param("contratoId") Long contratoId, @Param("periodo") String periodo);

    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f WHERE f.estado = 'EMITIDA'")
    BigDecimal sumMontoPendiente();

    /** Total facturado (emitida + pagada) */
    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f WHERE f.estado IN ('EMITIDA','PAGADA')")
    BigDecimal sumTotalFacturado();

    /** Total cobrado */
    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f WHERE f.estado = 'PAGADA'")
    BigDecimal sumTotalCobrado();

    /** IVA débito fiscal generado en un rango */
    @Query("SELECT COALESCE(SUM(f.ivaPercibido), 0) FROM Factura f " +
           "WHERE f.estado IN ('EMITIDA','PAGADA') AND f.fechaEmision BETWEEN :desde AND :hasta")
    BigDecimal sumIvaEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT COALESCE(SUM(f.montoTotal), 0) FROM Factura f " +
           "WHERE f.estado IN ('EMITIDA','PAGADA') AND f.fechaEmision BETWEEN :desde AND :hasta")
    BigDecimal sumFacturadoEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Facturado y cobrado agrupado por mes dentro de un rango.
     * Devuelve: [mes 'YYYY-MM', totalFacturado, totalCobrado, ivaDebito, cantidad]
     */
    @Query(value = "SELECT TO_CHAR(f.fecha_emision, 'YYYY-MM') AS mes, " +
                   "       COALESCE(SUM(f.monto_total), 0) AS facturado, " +
                   "       COALESCE(SUM(CASE WHEN f.estado = 'PAGADA' THEN f.monto_total ELSE 0 END), 0) AS cobrado, " +
                   "       COALESCE(SUM(f.iva_percibido), 0) AS iva, " +
                   "       COUNT(*) AS cantidad " +
                   "FROM factura f " +
                   "WHERE f.estado IN ('EMITIDA','PAGADA') " +
                   "  AND f.fecha_emision >= :desde AND f.fecha_emision < :hasta " +
                   "GROUP BY 1 ORDER BY 1 ASC", nativeQuery = true)
    List<Object[]> resumenPorMes(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /**
     * Libro de ventas: totales fiscales por mes y tipo de DTE.
     * Devuelve: [mes 'YYYY-MM', tipoDte, gravado, exento, noSujeto, iva, total, cantidad]
     */
    @Query(value = "SELECT TO_CHAR(f.fecha_emision, 'YYYY-MM') AS mes, " +
                   "       f.tipo_dte, " +
                   "       COALESCE(SUM(f.subtotal_gravado), 0), " +
                   "       COALESCE(SUM(f.subtotal_exento), 0), " +
                   "       COALESCE(SUM(f.subtotal_no_sujeto), 0), " +
                   "       COALESCE(SUM(f.iva_percibido), 0), " +
                   "       COALESCE(SUM(f.monto_total), 0), " +
                   "       COUNT(*) " +
                   "FROM factura f " +
                   "WHERE f.estado IN ('EMITIDA','PAGADA') " +
                   "  AND f.fecha_emision >= :desde AND f.fecha_emision < :hasta " +
                   "GROUP BY 1, 2 ORDER BY 1 ASC, 2 ASC", nativeQuery = true)
    List<Object[]> libroVentas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    /** Conteo por estado */
    @Query("SELECT f.estado, COUNT(f) FROM Factura f GROUP BY f.estado")
    List<Object[]> distribucionPorEstado();

    /** Top clientes por monto facturado */
    @Query("SELECT cl.nombre, COALESCE(SUM(f.montoTotal), 0) " +
           "FROM Factura f JOIN f.cliente cl " +
           "WHERE f.estado IN ('EMITIDA','PAGADA') " +
           "GROUP BY cl.id, cl.nombre ORDER BY SUM(f.montoTotal) DESC")
    List<Object[]> topClientesPorFacturado();

    /** Facturas emitidas y aún no cobradas — base de la antigüedad de saldos */
    @Query("SELECT f FROM Factura f JOIN FETCH f.cliente " +
           "WHERE f.estado = 'EMITIDA' ORDER BY f.fechaVencimiento ASC NULLS LAST, f.fechaEmision ASC")
    List<Factura> findPendientesDeCobro();

    /** Facturas emitidas ya vencidas a la fecha indicada */
    @Query("SELECT f FROM Factura f JOIN FETCH f.cliente " +
           "WHERE f.estado = 'EMITIDA' AND f.fechaVencimiento IS NOT NULL AND f.fechaVencimiento < :fecha " +
           "ORDER BY f.fechaVencimiento ASC")
    List<Factura> findVencidas(@Param("fecha") LocalDate fecha);

    /** Últimas facturas registradas, para el panel de actividad reciente */
    List<Factura> findTop8ByOrderByFechaEmisionDesc();
}
