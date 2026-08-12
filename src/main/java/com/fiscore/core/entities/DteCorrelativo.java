package com.fiscore.core.entities;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Último correlativo consumido por cada tipo de DTE.
 *
 * Existe para que la numeración se reserve de forma atómica: el registro se lee
 * con bloqueo de escritura, de modo que dos emisiones simultáneas no puedan
 * obtener el mismo número. Antes se calculaba con un MAX() sobre las facturas,
 * lo que bajo concurrencia producía documentos con el mismo correlativo.
 */
@Entity
@Data
@Table(name = "DTE_CORRELATIVO",
       uniqueConstraints = @UniqueConstraint(name = "uk_dte_correlativo_tipo", columnNames = "DTCO_TIPO_DTE"))
public class DteCorrelativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DTCO_ID", nullable = false)
    private Long id;

    /** Tipo de documento: 01, 03, 05, 06, 14. */
    @Column(name = "DTCO_TIPO_DTE", nullable = false, length = 2)
    private String tipoDte;

    /** Último número entregado. El siguiente documento usa este valor + 1. */
    @Column(name = "DTCO_ULTIMO", nullable = false)
    private Long ultimo;
}
