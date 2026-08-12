package com.fiscore.core.services;

import com.fiscore.core.entities.DteCorrelativo;
import com.fiscore.core.models.Factura;
import com.fiscore.core.repositories.DteCorrelativoRepository;
import com.fiscore.core.repositories.FacturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Entrega los correlativos de los documentos tributarios.
 *
 * La reserva ocurre con bloqueo de fila, de modo que varias emisiones
 * simultáneas obtienen números distintos. Antes se calculaba con un MAX()
 * sobre las facturas y bajo concurrencia se emitían documentos repetidos.
 */
@Service
public class CorrelativoService {

    private static final Logger log = LoggerFactory.getLogger(CorrelativoService.class);

    /** Tipos de DTE que el sistema puede emitir. */
    public static final List<String> TIPOS_DTE = List.of("01", "03", "05", "06", "14");

    private final DteCorrelativoRepository correlativoRepository;
    private final FacturaRepository facturaRepository;

    public CorrelativoService(DteCorrelativoRepository correlativoRepository,
                              FacturaRepository facturaRepository) {
        this.correlativoRepository = correlativoRepository;
        this.facturaRepository = facturaRepository;
    }

    /**
     * Crea la fila de cada tipo de documento al arrancar.
     *
     * Es lo que evita que varias emisiones simultáneas compitan por crearla:
     * si dos hilos intentaban insertarla a la vez, uno violaba la restricción
     * única y su sesión de Hibernate quedaba inutilizable, perdiendo la emisión.
     */
    // Se dispara con el evento de arranque, NO con @PostConstruct: los métodos
    // @PostConstruct se invocan sobre la instancia sin envolver, así que
    // @Transactional no tiene efecto y la consulta de inserción fallaba con
    // "Executing an update/delete query". El fallo quedaba oculto tras el
    // catch y la siembra nunca llegó a ejecutarse.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializar() {
        try {
            for (String tipo : TIPOS_DTE) {
                asegurarFila(tipo);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron preparar los correlativos DTE ({}). " +
                     "Se crearán en la primera emisión.", e.getMessage());
        }
    }

    /**
     * Garantiza que exista la fila del tipo indicado. Es idempotente y seguro
     * ante concurrencia: si otra transacción la creó primero, no hace nada.
     */
    private void asegurarFila(String tipoDte) {
        // existsByTipoDte, no findByTipoDte: cargar la entidad aquí haría que la
        // consulta con bloqueo devolviera la copia en memoria con el contador viejo.
        if (correlativoRepository.existsByTipoDte(tipoDte)) {
            return;
        }
        correlativoRepository.crearSiNoExiste(tipoDte, ultimoSegunHistorico(tipoDte));
    }

    /**
     * Reserva y devuelve el siguiente número para el tipo de DTE indicado.
     *
     * Participa en la transacción de quien llama (REQUIRED) a propósito: con
     * REQUIRES_NEW cada emisión necesitaría dos conexiones a la vez y el pool
     * —de cinco conexiones en producción— se agotaba en cuanto tres usuarios
     * facturaban al mismo tiempo, bloqueando la aplicación.
     *
     * El bloqueo dura hasta el commit de la factura, lo que serializa las
     * emisiones del mismo tipo de documento. Es un coste asumible: emitir un DTE
     * no es una operación de alta frecuencia y la alternativa era arriesgarse a
     * correlativos duplicados.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public long siguiente(String tipoDte) {
        String tipo = (tipoDte != null && !tipoDte.isBlank()) ? tipoDte : "01";

        // La fila debe existir antes de intentar bloquearla.
        asegurarFila(tipo);

        DteCorrelativo correlativo = correlativoRepository.bloquearPorTipo(tipo)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo reservar el correlativo del tipo de DTE " + tipo + "."));

        long siguiente = correlativo.getUltimo() + 1;
        correlativo.setUltimo(siguiente);
        correlativoRepository.saveAndFlush(correlativo);
        return siguiente;
    }

    /** Último correlativo entregado para un tipo, sin consumirlo. */
    @Transactional(readOnly = true)
    public long ultimoEmitido(String tipoDte) {
        return correlativoRepository.findByTipoDte(tipoDte)
                .map(DteCorrelativo::getUltimo)
                .orElse(0L);
    }

    /**
     * Arranca desde el mayor correlativo ya existente, para no chocar con
     * documentos emitidos antes de que existiera esta tabla.
     */
    private long ultimoSegunHistorico(String tipoDte) {
        return facturaRepository.findTopByTipoDteOrderByIdDesc(tipoDte)
                .map(Factura::getNumeroFactura)
                .map(numero -> numero.replaceAll("\\D", ""))
                .filter(numero -> !numero.isEmpty())
                .map(Long::parseLong)
                .orElse(0L);
    }
}
