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
        // Un proyecto finalizado cierra su avance y registra la fecha de cierre.
        if ("FINALIZADO".equals(proyecto.getEstado()) || "FACTURADO".equals(proyecto.getEstado())) {
            proyecto.setPorcentajeAvance(100);
            if (proyecto.getFechaFin() == null) {
                proyecto.setFechaFin(LocalDate.now());
            }
        }
        return proyectoRepository.save(proyecto);
    }

    public void deleteById(Long id) {
        proyectoRepository.deleteById(id);
    }

    public long countEnEjecucion() {
        return proyectoRepository.countByEstado("EN_EJECUCION");
    }

    /** Proyectos terminados pendientes de emitir su factura. */
    public List<Proyecto> findFinalizadosSinFacturar() {
        return proyectoRepository.findFinalizadosSinFacturar();
    }

    /** Proyectos en ejecución que ya pasaron su fecha estimada de cierre. */
    public List<Proyecto> findAtrasados() {
        return proyectoRepository.findAtrasados(LocalDate.now());
    }
}
