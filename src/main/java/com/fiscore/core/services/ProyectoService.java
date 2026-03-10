package com.fiscore.core.services;

import com.fiscore.core.models.Proyecto;
import com.fiscore.core.repositories.ProyectoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class ProyectoService {

    @Autowired
    private ProyectoRepository proyectoRepository;

    public List<Proyecto> findAll() {
        return proyectoRepository.findAll();
    }

    public List<Proyecto> findByEstado(String estado) {
        return proyectoRepository.findByEstadoOrderByFechaCreacionDesc(estado);
    }

    public Optional<Proyecto> findById(Long id) {
        return proyectoRepository.findById(id);
    }

    public Proyecto save(Proyecto proyecto) {
        if (proyecto.getFechaCreacion() == null) {
            proyecto.setFechaCreacion(LocalDate.now());
        }
        if (proyecto.getEstado() == null) {
            proyecto.setEstado("COTIZADO");
        }
        if (proyecto.getPorcentajeAvance() == null) {
            proyecto.setPorcentajeAvance(0);
        }
        if (proyecto.getFacturado() == null) {
            proyecto.setFacturado(false);
        }
        return proyectoRepository.save(proyecto);
    }

    public void deleteById(Long id) {
        proyectoRepository.deleteById(id);
    }

    public long countEnEjecucion() {
        return proyectoRepository.countByEstado("EN_EJECUCION");
    }
}