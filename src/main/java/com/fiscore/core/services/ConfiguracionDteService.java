package com.fiscore.core.services;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.entities.DteParametro;
import com.fiscore.core.repositories.DteParametroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punto único de acceso a la configuración de emisión de DTE.
 *
 * Los valores viven en la tabla DTE_PARAMETRO y se editan desde
 * Configuración → Parámetros DTE. Se mantienen en una caché en memoria
 * que se invalida al guardar, porque se consultan en cada emisión.
 */
@Service
public class ConfiguracionDteService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDteService.class);

    /** Marcador que el formulario devuelve cuando un secreto no se modificó. */
    public static final String SECRETO_SIN_CAMBIOS = "********";

    private static final Short ACTIVO = 1;

    private final DteParametroRepository parametroRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ConfiguracionDteService(DteParametroRepository parametroRepository) {
        this.parametroRepository = parametroRepository;
    }

    /**
     * Da de alta en la tabla los parámetros que aún no existan, con su valor
     * por defecto, para que la pantalla siempre tenga contenido que mostrar.
     */
    // Con el evento de arranque, no con @PostConstruct: ese se ejecuta sobre la
    // instancia sin envolver y @Transactional se queda sin efecto. Aquí funciona
    // de casualidad porque save() ya abre su propia transacción, pero en cuanto
    // se añadiera una consulta @Modifying fallaría igual que en CorrelativoService.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void inicializar() {
        try {
            int creados = 0;
            for (ParametroDte definicion : ParametroDte.values()) {
                if (!parametroRepository.existsByDtpaNombre(definicion.getClave())) {
                    parametroRepository.save(nuevoParametro(definicion));
                    creados++;
                }
            }
            if (creados > 0) {
                log.info("Configuración DTE: se registraron {} parámetro(s) con su valor por defecto.", creados);
            }
            recargarCache();
        } catch (RuntimeException e) {
            // No impedir el arranque: la pantalla de configuración mostrará los defectos.
            log.warn("No se pudo inicializar la configuración DTE ({}). Se usarán los valores por defecto.",
                    e.getMessage());
        }
    }

    private DteParametro nuevoParametro(ParametroDte definicion) {
        DteParametro parametro = new DteParametro();
        parametro.setDtpaNombre(definicion.getClave());
        parametro.setDtpaDescripcion(recortar(definicion.getEtiqueta(), 70));
        parametro.setDtpaVal(definicion.getValorPorDefecto());
        parametro.setDtpaEstado(ACTIVO);
        return parametro;
    }

    // =================================================================
    // Lectura
    // =================================================================

    /** Valor vigente del parámetro; si no hay nada guardado, su valor por defecto. */
    public String get(ParametroDte definicion) {
        String valor = cache.get(definicion.getClave());
        if (valor == null || valor.isBlank()) {
            return definicion.getValorPorDefecto();
        }
        return valor;
    }

    public int getEntero(ParametroDte definicion) {
        try {
            return Integer.parseInt(get(definicion).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(definicion.getValorPorDefecto());
        }
    }

    public BigDecimal getDecimal(ParametroDte definicion) {
        try {
            return new BigDecimal(get(definicion).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(definicion.getValorPorDefecto());
        }
    }

    public boolean getBooleano(ParametroDte definicion) {
        return Boolean.parseBoolean(get(definicion).trim());
    }

    /**
     * Parámetros agrupados por sección, listos para pintar el formulario.
     * Los secretos se devuelven vacíos con la marca {@code configurado}.
     */
    public Map<ParametroDte.Grupo, List<Map<String, Object>>> getFormulario() {
        Map<ParametroDte.Grupo, List<Map<String, Object>>> formulario = new LinkedHashMap<>();
        for (ParametroDte.Grupo grupo : ParametroDte.Grupo.values()) {
            formulario.put(grupo, new ArrayList<>());
        }

        for (ParametroDte definicion : ParametroDte.values()) {
            Map<String, Object> campo = new LinkedHashMap<>();
            campo.put("clave", definicion.getClave());
            campo.put("etiqueta", definicion.getEtiqueta());
            campo.put("descripcion", definicion.getDescripcion());
            campo.put("tipo", definicion.getTipo().name());
            campo.put("obligatorio", definicion.isObligatorio());
            campo.put("opciones", definicion.getOpciones());
            campo.put("secreto", definicion.isSecreto());

            if (definicion.isSecreto()) {
                // Nunca se envía el valor al navegador.
                campo.put("valor", "");
                campo.put("configurado", !get(definicion).isBlank());
            } else {
                campo.put("valor", get(definicion));
                campo.put("configurado", true);
            }
            formulario.get(definicion.getGrupo()).add(campo);
        }
        return formulario;
    }

    // =================================================================
    // Escritura
    // =================================================================

    /**
     * Guarda los valores enviados desde la pantalla de configuración.
     * Devuelve cuántos parámetros cambiaron realmente.
     */
    @Transactional
    public int guardar(Map<String, String> valores) {
        validar(valores);

        int cambios = 0;
        for (Map.Entry<String, String> entrada : valores.entrySet()) {
            ParametroDte definicion = ParametroDte.porClave(entrada.getKey());
            if (definicion == null) {
                continue; // clave desconocida: se ignora
            }

            String nuevo = entrada.getValue() != null ? entrada.getValue().trim() : "";

            // Un secreto que llega con el marcador significa "no lo toques".
            if (definicion.isSecreto() && (nuevo.isEmpty() || SECRETO_SIN_CAMBIOS.equals(nuevo))) {
                continue;
            }
            if (nuevo.length() > 500) {
                throw new IllegalArgumentException(
                        "El valor de \"" + definicion.getEtiqueta() + "\" excede los 500 caracteres.");
            }

            DteParametro parametro = parametroRepository.findByDtpaNombre(definicion.getClave())
                    .orElseGet(() -> nuevoParametro(definicion));

            if (!nuevo.equals(parametro.getDtpaVal())) {
                parametro.setDtpaVal(nuevo);
                parametro.setDtpaEstado(ACTIVO);
                parametroRepository.save(parametro);
                cambios++;
            }
        }

        recargarCache();
        log.info("Configuración DTE actualizada: {} parámetro(s) modificado(s).", cambios);
        return cambios;
    }

    /** Devuelve un parámetro a su valor de fábrica. */
    @Transactional
    public void restaurarPorDefecto(String clave) {
        ParametroDte definicion = ParametroDte.porClave(clave);
        if (definicion == null) {
            throw new IllegalArgumentException("Parámetro desconocido: " + clave);
        }
        DteParametro parametro = parametroRepository.findByDtpaNombre(definicion.getClave())
                .orElseGet(() -> nuevoParametro(definicion));
        parametro.setDtpaVal(definicion.getValorPorDefecto());
        parametroRepository.save(parametro);
        recargarCache();
    }

    private void validar(Map<String, String> valores) {
        for (Map.Entry<String, String> entrada : valores.entrySet()) {
            ParametroDte definicion = ParametroDte.porClave(entrada.getKey());
            if (definicion == null) continue;

            String valor = entrada.getValue() != null ? entrada.getValue().trim() : "";

            if (definicion.isSecreto() && (valor.isEmpty() || SECRETO_SIN_CAMBIOS.equals(valor))) {
                continue;
            }
            if (definicion.isObligatorio() && valor.isEmpty()) {
                throw new IllegalArgumentException(
                        "\"" + definicion.getEtiqueta() + "\" es obligatorio.");
            }
            if (valor.isEmpty()) continue;

            switch (definicion.getTipo()) {
                case ENTERO -> exigirEntero(definicion, valor);
                case DECIMAL -> exigirDecimal(definicion, valor);
                case SELECCION -> exigirOpcionValida(definicion, valor);
                default -> { /* TEXTO, BOOLEANO y SECRETO no requieren validación de formato */ }
            }
        }
    }

    private void exigirEntero(ParametroDte definicion, String valor) {
        try {
            if (Integer.parseInt(valor) < 0) {
                throw new IllegalArgumentException(
                        "\"" + definicion.getEtiqueta() + "\" no puede ser negativo.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "\"" + definicion.getEtiqueta() + "\" debe ser un número entero.");
        }
    }

    private void exigirDecimal(ParametroDte definicion, String valor) {
        BigDecimal numero;
        try {
            numero = new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "\"" + definicion.getEtiqueta() + "\" debe ser un número decimal (use punto).");
        }
        if (numero.signum() < 0) {
            throw new IllegalArgumentException(
                    "\"" + definicion.getEtiqueta() + "\" no puede ser negativo.");
        }
        // Las tasas se expresan como proporción: 0.13, no 13.
        if (definicion.getClave().endsWith("_TASA") && numero.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "\"" + definicion.getEtiqueta() + "\" se expresa como proporción: use 0.13 para el 13%.");
        }
    }

    private void exigirOpcionValida(ParametroDte definicion, String valor) {
        boolean valida = definicion.getOpciones().stream()
                .anyMatch(opcion -> opcion.get("valor").equals(valor));
        if (!valida) {
            throw new IllegalArgumentException(
                    "\"" + definicion.getEtiqueta() + "\" tiene un valor no permitido: " + valor);
        }
    }

    private void recargarCache() {
        cache.clear();
        for (DteParametro parametro : parametroRepository.findAll()) {
            if (parametro.getDtpaVal() != null) {
                cache.put(parametro.getDtpaNombre(), parametro.getDtpaVal());
            }
        }
    }

    /** Indica si la conexión con Hacienda tiene lo mínimo para transmitir. */
    public boolean isConexionConfigurada() {
        return !get(ParametroDte.MH_USUARIO).isBlank()
                && !get(ParametroDte.MH_CLAVE_API).isBlank()
                && !get(ParametroDte.MH_URL_RECEPCION).isBlank();
    }

    /** Etiqueta legible del ambiente configurado. */
    public String getAmbienteDescripcion() {
        return "01".equals(get(ParametroDte.MH_AMBIENTE)) ? "Producción" : "Pruebas";
    }

    private static String recortar(String texto, int maximo) {
        if (texto == null) return "";
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
