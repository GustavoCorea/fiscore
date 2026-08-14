package com.fiscore.core.services;

import com.fiscore.core.models.Proyecto;
import com.fiscore.core.models.RegistroHoras;
import com.fiscore.core.repositories.ProyectoRepository;
import com.fiscore.core.repositories.RegistroHorasRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro del tiempo dedicado a cada caso.
 *
 * Es la pieza que faltaba para facturar por horas, que es como cobra un bufete:
 * hasta ahora un proyecto solo admitía precio cerrado contra su presupuesto.
 */
@Service
public class RegistroHorasService {

    /**
     * Tope por registro. No es una regla de negocio sino un cazador de erratas:
     * quien escribe 80 en lugar de 8 lo descubre al guardar y no tres meses
     * después, cuando la cifra ya viajó en una factura.
     */
    private static final BigDecimal MAXIMO_HORAS_POR_REGISTRO = new BigDecimal("24");

    private final RegistroHorasRepository registroRepository;
    private final ProyectoRepository proyectoRepository;
    private final AuditorAware<String> auditor;

    public RegistroHorasService(RegistroHorasRepository registroRepository,
                                ProyectoRepository proyectoRepository,
                                AuditorAware<String> auditor) {
        this.registroRepository = registroRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    public List<RegistroHoras> listarPorProyecto(Long proyectoId) {
        return registroRepository.findByProyectoIdOrderByFechaDescIdDesc(proyectoId);
    }

    @Transactional
    public RegistroHoras registrar(Long proyectoId, LocalDate fecha, BigDecimal horas,
                                   String descripcion, String usuario,
                                   BigDecimal tarifaHora, Boolean facturable) {

        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El caso indicado no existe."));

        RegistroHoras registro = new RegistroHoras();
        registro.setProyecto(proyecto);
        registro.setFecha(fecha != null ? fecha : LocalDate.now());
        registro.setHoras(horas);
        registro.setDescripcion(descripcion);
        registro.setFacturable(facturable == null || facturable);

        // Sin persona indicada, el trabajo es de quien lo registra.
        registro.setUsuario(usuario != null && !usuario.isBlank() ? usuario.trim() : actorActual());

        // La tarifa del caso es solo el punto de partida: se copia al registro
        // para que subirla mañana no reescriba el precio del trabajo de hoy.
        BigDecimal tarifa = tarifaHora != null ? tarifaHora : proyecto.getTarifaHora();
        registro.setTarifaHora(tarifa != null ? tarifa.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        validar(registro);
        return registroRepository.save(registro);
    }

    @Transactional
    public RegistroHoras actualizar(Long id, LocalDate fecha, BigDecimal horas, String descripcion,
                                    String usuario, BigDecimal tarifaHora, Boolean facturable) {
        RegistroHoras registro = exigir(id);
        exigirQueNoEsteFacturado(registro, "modificar");

        registro.setFecha(fecha != null ? fecha : registro.getFecha());
        registro.setHoras(horas);
        registro.setDescripcion(descripcion);
        if (usuario != null && !usuario.isBlank()) {
            registro.setUsuario(usuario.trim());
        }
        if (tarifaHora != null) {
            registro.setTarifaHora(tarifaHora.setScale(2, RoundingMode.HALF_UP));
        }
        if (facturable != null) {
            registro.setFacturable(facturable);
        }

        validar(registro);
        return registroRepository.save(registro);
    }

    @Transactional
    public void eliminar(Long id) {
        RegistroHoras registro = exigir(id);
        exigirQueNoEsteFacturado(registro, "eliminar");
        registroRepository.delete(registro);
    }

    /** Horas e importes del caso, separando lo pendiente de lo ya cobrado. */
    @Transactional(readOnly = true)
    public Map<String, Object> resumen(Long proyectoId) {
        List<RegistroHoras> registros = listarPorProyecto(proyectoId);

        BigDecimal horasTotales = BigDecimal.ZERO;
        BigDecimal horasNoFacturables = BigDecimal.ZERO;
        BigDecimal importePendiente = BigDecimal.ZERO;
        BigDecimal importeFacturado = BigDecimal.ZERO;

        for (RegistroHoras r : registros) {
            BigDecimal horas = r.getHoras() != null ? r.getHoras() : BigDecimal.ZERO;
            horasTotales = horasTotales.add(horas);

            if (Boolean.FALSE.equals(r.getFacturable())) {
                horasNoFacturables = horasNoFacturables.add(horas);
            } else if (r.estaFacturado()) {
                importeFacturado = importeFacturado.add(r.getImporte());
            } else {
                importePendiente = importePendiente.add(r.getImporte());
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("registros", registros.size());
        resumen.put("horasTotales", horasTotales);
        resumen.put("horasNoFacturables", horasNoFacturables);
        resumen.put("importePendiente", importePendiente);
        resumen.put("importeFacturado", importeFacturado);
        return resumen;
    }

    // -----------------------------------------------------------------

    private void validar(RegistroHoras registro) {
        BigDecimal horas = registro.getHoras();

        if (horas == null || horas.signum() <= 0) {
            throw new IllegalArgumentException("Las horas deben ser mayores que cero.");
        }
        if (horas.compareTo(MAXIMO_HORAS_POR_REGISTRO) > 0) {
            throw new IllegalArgumentException(
                    "Un registro no puede superar las 24 horas. Divídelo por días si el trabajo se alargó.");
        }
        if (registro.getFecha() != null && registro.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede registrar trabajo con fecha futura.");
        }
        if (registro.getDescripcion() == null || registro.getDescripcion().isBlank()) {
            // Sin descripción, el registro no sirve para justificar la minuta
            // ante el cliente, que es la mitad de su utilidad.
            throw new IllegalArgumentException("Describe el trabajo realizado.");
        }
        if (registro.getTarifaHora() != null && registro.getTarifaHora().signum() < 0) {
            throw new IllegalArgumentException("La tarifa no puede ser negativa.");
        }
    }

    private void exigirQueNoEsteFacturado(RegistroHoras registro, String accion) {
        if (registro.estaFacturado()) {
            throw new IllegalStateException("No se puede " + accion
                    + " un registro ya cobrado en la factura " + registro.getNumeroFactura()
                    + ". Emite una nota de crédito si hay que corregirlo.");
        }
    }

    private RegistroHoras exigir(Long id) {
        return registroRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Registro de horas no encontrado: " + id));
    }

    private String actorActual() {
        return auditor.getCurrentAuditor().orElse("sistema");
    }
}
