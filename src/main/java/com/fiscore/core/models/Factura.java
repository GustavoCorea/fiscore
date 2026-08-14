package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@Table(name = "factura", uniqueConstraints = {
        // Última defensa contra correlativos repetidos: si la aplicación fallara
        // en la reserva, la base de datos rechaza el documento duplicado.
        @UniqueConstraint(name = "uk_factura_numero_control", columnNames = "numero_control"),
        @UniqueConstraint(name = "uk_factura_codigo_generacion", columnNames = "codigo_generacion")
})
public class Factura extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Campos DTE El Salvador ----
    // 01=Factura ConsumidorFinal, 03=CCF, 05=NotaCredito, 06=NotaDebito
    private String tipoDte;
    private String codigoGeneracion;    // UUID v4
    private String numeroControl;       // DTE-03-MXXXXXXXXX-XXXXXXXXXXXXXXXXXXXX
    private String selloRecepcion;
    private String numeroFactura;       // Correlativo interno: CCF-00001 / FAC-00001

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    /** Proyecto de origen cuando la factura nace de un caso/proyecto finalizado */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    private LocalDateTime fechaEmision;
    private String periodoFacturado;    // Ej: "Marzo 2026"
    private String condicionPago;       // CONTADO, CREDITO
    private Integer plazoCredito;       // días si es crédito
    private LocalDate fechaVencimiento; // emisión + plazoCredito (contado = mismo día)
    private LocalDate fechaPago;        // se registra al marcar PAGADA

    // ---- Montos ----
    private BigDecimal subtotalGravado;
    private BigDecimal subtotalExento;
    private BigDecimal subtotalNoSujeto;
    private BigDecimal descuento;
    private BigDecimal ivaPercibido;    // 13%
    private BigDecimal ivaRetenido;
    /**
     * Retención de renta sobre honorarios profesionales. Como el IVA retenido,
     * resta del total: es dinero que el cliente entera a Hacienda en lugar de
     * pagarlo al despacho. Solo se calcula si RETENCION_RENTA_APLICA está
     * activo y la base alcanza RETENCION_MONTO_MINIMO.
     */
    private BigDecimal retencionRenta;
    private BigDecimal montoTotal;

    // ---- Estados ----
    private String estado;              // BORRADOR, EMITIDA, PAGADA, ANULADA
    /** Ciclo frente a Hacienda. Valores y transiciones válidas en {@link EstadoDte}. */
    private String estadoDte;

    /**
     * Documento que corrige esta nota. Hacienda lo exige en los tipos 05 y 06:
     * una nota de crédito que no dice qué documento corrige no es aceptada.
     *
     * Se guarda la referencia y no una copia de sus datos para que no puedan
     * divergir. Queda fuera del JSON de los listados porque arrastraría la
     * factura entera en cada fila; para mostrarla basta el número, que expone
     * getNumeroFacturaRelacionada().
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "factura_relacionada_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Factura facturaRelacionada;

    // ---- Respuesta MH ----
    private String respuestaMH;

    private String notas;

    /** Número del documento corregido, para las pantallas. */
    public String getNumeroFacturaRelacionada() {
        return facturaRelacionada != null ? facturaRelacionada.getNumeroFactura() : null;
    }

    @ToString.Exclude
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DetalleFactura> detalles;
}