package com.fiscore.core.services;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.repositories.ContratoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ContratoService {

    @Autowired
    private ContratoRepository contratoRepository;

    public List<Contrato> findAll() {
        return contratoRepository.findAll();
    }

    public List<Contrato> findActivos() {
        return contratoRepository.findByEstado("ACTIVO");
    }

    public Optional<Contrato> findById(Long id) {
        return contratoRepository.findById(id);
    }

    public Contrato save(Contrato contrato) {
        if (contrato.getFechaCreacion() == null) {
            contrato.setFechaCreacion(LocalDate.now());
        }
        if (contrato.getEstado() == null) {
            contrato.setEstado("ACTIVO");
        }
        // Calcular próxima fecha de facturación según tipo
        if (contrato.getFechaProximaFacturacion() == null && contrato.getFechaInicio() != null) {
            String tipo = contrato.getTipoFacturacion();
            if ("RECURRENTE".equals(tipo)) {
                contrato.setFechaProximaFacturacion(contrato.getFechaInicio().plusMonths(1));
            } else {
                contrato.setFechaProximaFacturacion(contrato.getFechaInicio());
            }
        }
        return contratoRepository.save(contrato);
    }

    public void deleteById(Long id) {
        contratoRepository.deleteById(id);
    }

    public long countActivos() {
        return contratoRepository.countByEstado("ACTIVO");
    }

    public List<Contrato> findActivosConDetalle() {
        return contratoRepository.findActivosConDetalle();
    }

    public java.math.BigDecimal sumHonorariosActivos() {
        return contratoRepository.sumHonorariosActivos();
    }
}