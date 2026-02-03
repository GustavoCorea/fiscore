package com.fiscore.core.services;

import com.fiscore.core.models.Servicio;
import com.fiscore.core.repositories.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioService {

    @Autowired
    private ServicioRepository servicioRepository;

    public List<Servicio> findAll() {
        return servicioRepository.findAll();
    }

    public Servicio save(Servicio servicio) {
        return servicioRepository.save(servicio);
    }
}