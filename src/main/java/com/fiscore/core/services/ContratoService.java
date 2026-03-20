package com.fiscore.core.services;

import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.ContratoServicio;
import com.fiscore.core.repositories.ContratoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    /**
     * Guarda o actualiza un contrato con su lista de servicios.
     * Si el contrato ya existe (tiene ID), carga la entidad gestionada
     * y actualiza sus campos y la colección de servicios en la misma transacción
     * para que orphanRemoval elimine los servicios anteriores correctamente.
     */
    @Transactional
    public Contrato save(Contrato contratoNuevo) {
        Contrato target;

        if (contratoNuevo.getId() != null) {
            // Actualización: cargar entidad gestionada y modificarla
            target = contratoRepository.findById(contratoNuevo.getId())
                    .orElseThrow(() -> new RuntimeException("Contrato no encontrado"));

            target.setCliente(contratoNuevo.getCliente());
            target.setTipoFacturacion(contratoNuevo.getTipoFacturacion());
            target.setHonorariosPactados(contratoNuevo.getHonorariosPactados());
            target.setFechaInicio(contratoNuevo.getFechaInicio());
            target.setNotas(contratoNuevo.getNotas());
            target.setEstado(contratoNuevo.getEstado() != null ? contratoNuevo.getEstado() : target.getEstado());

            // Reemplazar servicios: orphanRemoval elimina los anteriores
            target.getServicios().clear();
            for (ContratoServicio cs : contratoNuevo.getServicios()) {
                cs.setContrato(target);
                target.getServicios().add(cs);
            }
        } else {
            // Nuevo contrato
            target = contratoNuevo;
            if (target.getFechaCreacion() == null) {
                target.setFechaCreacion(LocalDate.now());
            }
            if (target.getEstado() == null) {
                target.setEstado("ACTIVO");
            }
            // Asignar próxima facturación
            if (target.getFechaProximaFacturacion() == null && target.getFechaInicio() != null) {
                if ("RECURRENTE".equals(target.getTipoFacturacion())) {
                    target.setFechaProximaFacturacion(target.getFechaInicio().plusMonths(1));
                } else {
                    target.setFechaProximaFacturacion(target.getFechaInicio());
                }
            }
            // Enlazar servicios al contrato padre antes de persistir
            for (ContratoServicio cs : target.getServicios()) {
                cs.setContrato(target);
            }
        }

        return contratoRepository.save(target);
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

    public BigDecimal sumHonorariosActivos() {
        return contratoRepository.sumHonorariosActivos();
    }
}