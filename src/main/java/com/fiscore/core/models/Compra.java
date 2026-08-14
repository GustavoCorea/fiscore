package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Documento de compra recibido de un proveedor.
 *
 * Es la contrapartida del libro de ventas: sin registrar lo comprado no hay
 * crédito fiscal que oponer al débito, y la liquidación mensual del IVA —el
 * trabajo de cada mes— no se puede cerrar.
 *
 * Registra las compras <b>del propio despacho</b>. Llevar los libros de los
 * clientes es otra cosa: exige un emisor por empresa, que es una decisión de
 * producto todavía abierta (ver DEPLOY.md §12 y la evaluación).
 */
@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "compra", uniqueConstraints = {
        // El mismo proveedor no puede entregar dos veces el mismo documento:
        // duplicar una compra infla el crédito fiscal, que es un error caro.
        @UniqueConstraint(name = "uk_compra_proveedor_documento",
                columnNames = {"proveedor_nit", "numero_documento"})
})
public class Compra extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha del documento, que manda para el periodo, no la de captura. */
    private LocalDate fecha;

    /** Catálogo de Hacienda: 03 crédito fiscal, 01 factura, 11 importación… */
    private String tipoDocumento;

    @Column(name = "numero_documento")
    private String numeroDocumento;

    @Column(name = "proveedor_nombre")
    private String proveedorNombre;

    @Column(name = "proveedor_nit")
    private String proveedorNit;

    @Column(name = "proveedor_nrc")
    private String proveedorNrc;

    /** INTERNA o IMPORTACION: el libro las separa en columnas distintas. */
    private String tipoOperacion;

    private String descripcion;

    private BigDecimal montoExento;
    private BigDecimal montoGravado;

    /**
     * El IVA soportado, que es lo que se descuenta del débito del mes.
     *
     * Se guarda tal como viene en el documento y no se recalcula al leer:
     * el proveedor pudo redondear distinto, y el libro debe reflejar lo que
     * dice el papel, no lo que debería decir.
     */
    private BigDecimal creditoFiscal;

    private BigDecimal total;

    /** Suma de las bases, sin el IVA. */
    public BigDecimal getBaseTotal() {
        return valor(montoExento).add(valor(montoGravado));
    }

    public boolean esImportacion() {
        return "IMPORTACION".equals(tipoOperacion);
    }

    private static BigDecimal valor(BigDecimal b) {
        return b != null ? b : BigDecimal.ZERO;
    }
}
