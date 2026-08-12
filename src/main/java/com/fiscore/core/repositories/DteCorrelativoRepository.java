package com.fiscore.core.repositories;

import com.fiscore.core.entities.DteCorrelativo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DteCorrelativoRepository extends JpaRepository<DteCorrelativo, Long> {

    /**
     * Lee el correlativo bloqueando la fila hasta el final de la transacción.
     * Cualquier otra emisión del mismo tipo de DTE espera aquí, que es lo que
     * garantiza que no se repitan los números.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DteCorrelativo c WHERE c.tipoDte = :tipoDte")
    Optional<DteCorrelativo> bloquearPorTipo(@Param("tipoDte") String tipoDte);

    Optional<DteCorrelativo> findByTipoDte(String tipoDte);

    /**
     * Comprueba la existencia sin traer la entidad al contexto de persistencia.
     *
     * Es deliberado: si la entidad ya está cargada, la consulta con bloqueo
     * devuelve la copia en memoria —con el contador obsoleto— en lugar del
     * valor recién leído de la base, y dos emisiones simultáneas terminaban
     * calculando el mismo correlativo.
     */
    boolean existsByTipoDte(String tipoDte);

    /**
     * Crea la fila del tipo solo si aún no existe.
     *
     * Se hace con SQL en lugar de save() porque, si dos transacciones intentan
     * insertarla a la vez, la que pierde recibe una violación de unicidad que
     * deja inutilizable la sesión de Hibernate y hace perder la emisión.
     * La forma {@code INSERT ... WHERE NOT EXISTS} es portable entre H2 y
     * PostgreSQL; el caso de carrera real lo evita la siembra al arrancar.
     */
    @Modifying
    @Query(value = "INSERT INTO dte_correlativo (dtco_tipo_dte, dtco_ultimo) " +
                   "SELECT :tipoDte, :ultimo " +
                   "WHERE NOT EXISTS (SELECT 1 FROM dte_correlativo WHERE dtco_tipo_dte = :tipoDte)",
           nativeQuery = true)
    void crearSiNoExiste(@Param("tipoDte") String tipoDte, @Param("ultimo") long ultimo);
}
