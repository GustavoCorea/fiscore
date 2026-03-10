package com.fiscore.core.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "factura")
public class Factura {

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

    private LocalDateTime fechaEmision;
    private String periodoFacturado;    // Ej: "Marzo 2026"
    private String condicionPago;       // CONTADO, CREDITO
    private Integer plazoCredito;       // días si es crédito

    // ---- Montos ----
    private BigDecimal subtotalGravado;
    private BigDecimal subtotalExento;
    private BigDecimal subtotalNoSujeto;
    private BigDecimal descuento;
    private BigDecimal ivaPercibido;    // 13%
    private BigDecimal ivaRetenido;
    private BigDecimal montoTotal;

    // ---- Estados ----
    private String estado;              // BORRADOR, EMITIDA, PAGADA, ANULADA
    private String estadoDte;           // PENDIENTE_ENVIO, ACEPTADO, RECHAZADO, CONTINGENCIA

    // ---- Respuesta MH ----
    private String respuestaMH;

    private String notas;

    @ToString.Exclude
    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DetalleFactura> detalles;
}