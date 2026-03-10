package com.fiscore.core.services;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.DetalleFactura;
import com.fiscore.core.models.Factura;
import com.fiscore.core.repositories.FacturaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FacturacionService {

    private static final BigDecimal IVA = new BigDecimal("0.13");

    @Autowired
    private FacturaRepository facturaRepository;

    public List<Factura> findAll() {
        return facturaRepository.findAllByOrderByFechaEmisionDesc();
    }

    public List<Factura> findByEstado(String estado) {
        return facturaRepository.findByEstadoOrderByFechaEmisionDesc(estado);
    }

    public Optional<Factura> findById(Long id) {
        return facturaRepository.findById(id);
    }

    /**
     * Genera una factura a partir de un contrato.
     * Si el receptor tiene NIT+NRC → CCF (03), si no → Factura CF (01)
     */
    public Factura generarDesdContrato(Contrato contrato, String periodoFacturado, String condicionPago, Integer plazoCredito) {
        Factura factura = new Factura();

        boolean esCCF = contrato.getCliente().getNrc() != null && !contrato.getCliente().getNrc().isBlank();
        factura.setTipoDte(esCCF ? "03" : "01");
        factura.setCodigoGeneracion(UUID.randomUUID().toString().toUpperCase());
        factura.setNumeroControl(generarNumeroControl(factura.getTipoDte()));
        factura.setNumeroFactura(generarCorrelativo(factura.getTipoDte()));

        factura.setCliente(contrato.getCliente());
        factura.setContrato(contrato);
        factura.setFechaEmision(LocalDateTime.now());
        factura.setPeriodoFacturado(periodoFacturado);
        factura.setCondicionPago(condicionPago != null ? condicionPago : "CONTADO");
        factura.setPlazoCredito(plazoCredito);
        factura.setEstado("BORRADOR");
        factura.setEstadoDte("PENDIENTE_ENVIO");

        // Calcular montos
        BigDecimal honorarios = contrato.getHonorariosPactados();
        // Los servicios contables generalmente son gravados con IVA
        BigDecimal gravado = honorarios.divide(BigDecimal.ONE.add(IVA), 2, RoundingMode.HALF_UP);
        BigDecimal iva = honorarios.subtract(gravado).setScale(2, RoundingMode.HALF_UP);

        factura.setSubtotalGravado(gravado);
        factura.setSubtotalExento(BigDecimal.ZERO);
        factura.setSubtotalNoSujeto(BigDecimal.ZERO);
        factura.setDescuento(BigDecimal.ZERO);
        factura.setIvaPercibido(iva);
        factura.setIvaRetenido(BigDecimal.ZERO);
        factura.setMontoTotal(honorarios);

        // Detalle
        DetalleFactura detalle = new DetalleFactura();
        detalle.setFactura(factura);
        detalle.setNumItem(1);
        detalle.setTipoItem("2");
        detalle.setDescripcion(contrato.getServicio().getNombre() + " - " + periodoFacturado);
        detalle.setCantidad(BigDecimal.ONE);
        detalle.setUnidadMedida("Servicio");
        detalle.setPrecioUnitario(gravado);
        detalle.setDescuento(BigDecimal.ZERO);
        detalle.setVentaGravada(gravado);
        detalle.setVentaExenta(BigDecimal.ZERO);
        detalle.setVentaNoSujeta(BigDecimal.ZERO);

        List<DetalleFactura> detalles = new ArrayList<>();
        detalles.add(detalle);
        factura.setDetalles(detalles);

        return facturaRepository.save(factura);
    }

    public Factura save(Factura factura) {
        if (factura.getFechaEmision() == null) {
            factura.setFechaEmision(LocalDateTime.now());
        }
        if (factura.getCodigoGeneracion() == null) {
            factura.setCodigoGeneracion(UUID.randomUUID().toString().toUpperCase());
        }
        if (factura.getNumeroControl() == null) {
            factura.setNumeroControl(generarNumeroControl(factura.getTipoDte()));
        }
        if (factura.getNumeroFactura() == null) {
            factura.setNumeroFactura(generarCorrelativo(factura.getTipoDte()));
        }
        if (factura.getEstado() == null) {
            factura.setEstado("BORRADOR");
        }
        if (factura.getEstadoDte() == null) {
            factura.setEstadoDte("PENDIENTE_ENVIO");
        }
        calcularMontos(factura);
        // Asegurar referencia bidireccional antes de persistir
        if (factura.getDetalles() != null) {
            for (DetalleFactura d : factura.getDetalles()) {
                d.setFactura(factura);
            }
        }
        return facturaRepository.save(factura);
    }

    public void deleteById(Long id) {
        facturaRepository.deleteById(id);
    }

    public BigDecimal getMontoPendiente() {
        return facturaRepository.sumMontoPendiente();
    }

    private void calcularMontos(Factura factura) {
        if (factura.getDetalles() == null || factura.getDetalles().isEmpty()) return;
        BigDecimal gravado = BigDecimal.ZERO;
        BigDecimal exento = BigDecimal.ZERO;
        BigDecimal noSujeto = BigDecimal.ZERO;
        for (DetalleFactura d : factura.getDetalles()) {
            if (d.getVentaGravada() != null) gravado = gravado.add(d.getVentaGravada());
            if (d.getVentaExenta() != null) exento = exento.add(d.getVentaExenta());
            if (d.getVentaNoSujeta() != null) noSujeto = noSujeto.add(d.getVentaNoSujeta());
        }
        BigDecimal descuento = factura.getDescuento() != null ? factura.getDescuento() : BigDecimal.ZERO;
        BigDecimal iva = gravado.multiply(IVA).setScale(2, RoundingMode.HALF_UP);
        factura.setSubtotalGravado(gravado);
        factura.setSubtotalExento(exento);
        factura.setSubtotalNoSujeto(noSujeto);
        factura.setIvaPercibido(iva);
        if (factura.getIvaRetenido() == null) factura.setIvaRetenido(BigDecimal.ZERO);
        factura.setMontoTotal(gravado.add(exento).add(noSujeto).add(iva).subtract(descuento).setScale(2, RoundingMode.HALF_UP));
    }

    private String generarNumeroControl(String tipoDte) {
        // Formato: DTE-[tipoDte]-[nrc_emisor]-[correlativo_14_digitos]
        // Ejemplo: DTE-03-M0114010TM-000000000000001
        String correlativo = String.format("%015d", System.currentTimeMillis() % 1000000000000000L);
        return "DTE-" + (tipoDte != null ? tipoDte : "01") + "-M0100000TM-" + correlativo;
    }

    private String generarCorrelativo(String tipoDte) {
        Optional<Factura> ultima = facturaRepository.findTopByTipoDteOrderByIdDesc(tipoDte);
        long siguiente = 1L;
        if (ultima.isPresent() && ultima.get().getNumeroFactura() != null) {
            String num = ultima.get().getNumeroFactura().replaceAll("[^0-9]", "");
            if (!num.isEmpty()) siguiente = Long.parseLong(num) + 1;
        }
        String prefijo = "03".equals(tipoDte) ? "CCF" : "FAC";
        return prefijo + "-" + String.format("%05d", siguiente);
    }
}