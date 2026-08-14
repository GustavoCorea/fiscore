package com.fiscore.core.repositories;

import com.fiscore.core.models.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    List<Compra> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);

    boolean existsByProveedorNitAndNumeroDocumento(String proveedorNit, String numeroDocumento);

    /**
     * Resumen por mes y tipo de operación, que es como se presenta el libro.
     * Se agrupa en la base y no en memoria: el libro de un año con movimiento
     * diario son cientos de documentos que no hace falta traer enteros.
     */
    @Query(value = "SELECT TO_CHAR(c.fecha, 'YYYY-MM') AS mes, "
                 + "       COALESCE(c.tipo_operacion, 'INTERNA'), "
                 + "       COALESCE(SUM(c.monto_gravado), 0), "
                 + "       COALESCE(SUM(c.monto_exento), 0), "
                 + "       COALESCE(SUM(c.credito_fiscal), 0), "
                 + "       COALESCE(SUM(c.total), 0), "
                 + "       COUNT(*) "
                 + "FROM compra c "
                 + "WHERE c.fecha >= :desde AND c.fecha < :hasta "
                 + "GROUP BY 1, 2 ORDER BY 1 ASC, 2 ASC", nativeQuery = true)
    List<Object[]> libroCompras(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Proveedores ya usados, para no reescribir el NIT cada mes. */
    @Query(value = "SELECT DISTINCT c.proveedor_nombre, c.proveedor_nit, c.proveedor_nrc "
                 + "FROM compra c WHERE c.proveedor_nombre IS NOT NULL "
                 + "ORDER BY 1 ASC", nativeQuery = true)
    List<Object[]> proveedoresConocidos();
}
