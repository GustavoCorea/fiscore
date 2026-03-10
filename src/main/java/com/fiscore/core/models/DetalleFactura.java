package com.fiscore.core.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Data
@Table(name = "detalle_factura")
public class DetalleFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id")
    private Factura factura;

    private Integer numItem;
    private String tipoItem;        // 1=Bien, 2=Servicio, 3=Ambos
    private String descripcion;
    private BigDecimal cantidad;
    private String unidadMedida;    // Unidad, Hora, Mes, etc.
    private BigDecimal precioUnitario;
    private BigDecimal descuento;
    private BigDecimal ventaGravada;
    private BigDecimal ventaExenta;
    private BigDecimal ventaNoSujeta;
}