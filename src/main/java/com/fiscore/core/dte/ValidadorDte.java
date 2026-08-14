package com.fiscore.core.dte;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Factura;
import com.fiscore.core.services.ConfiguracionDteService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprueba que un documento tenga lo necesario antes de transmitirlo.
 *
 * La alternativa es enterarse por el rechazo de Hacienda, que llega tarde, sin
 * el usuario delante y con un mensaje pensado para máquinas. Aquí los problemas
 * se enumeran todos de una vez y en español.
 *
 * Solo comprueba lo que se puede afirmar sin el esquema oficial delante: datos
 * que la propia aplicación ya declara obligatorios, y reglas que ya están
 * codificadas en ella (un crédito fiscal exige NRC del receptor; una nota debe
 * decir qué corrige). No inventa requisitos.
 */
@Service
public class ValidadorDte {

    /**
     * Parámetros del emisor cuyo valor de ejemplo no puede ser el correcto por
     * casualidad: una razón social, un NIT o un correo que sigan siendo los de
     * la plantilla delatan que nadie entró a configurarlos.
     *
     * Quedan fuera a propósito los códigos de catálogo —departamento, municipio,
     * tipo de establecimiento—: son de dos dígitos y su valor real coincide con
     * el de ejemplo a menudo. Avisar ahí sería un falso positivo, y unos cuantos
     * enseñan a ignorar todos los avisos.
     */
    private static final List<ParametroDte> DELATAN_FALTA_DE_CONFIGURACION = List.of(
            ParametroDte.EMISOR_NOMBRE,
            ParametroDte.EMISOR_NIT,
            ParametroDte.EMISOR_NRC,
            ParametroDte.EMISOR_COD_ACTIVIDAD,
            ParametroDte.EMISOR_GIRO,
            ParametroDte.EMISOR_DIRECCION,
            ParametroDte.EMISOR_CORREO);

    private final ConfiguracionDteService configuracion;

    public ValidadorDte(ConfiguracionDteService configuracion) {
        this.configuracion = configuracion;
    }

    /** Lista vacía significa que el documento está listo para transmitirse. */
    public List<String> problemas(Factura factura) {
        List<String> problemas = new ArrayList<>();
        revisarEmisor(problemas);
        revisarReceptor(problemas, factura);
        revisarDocumento(problemas, factura);
        return problemas;
    }

    public boolean puedeTransmitirse(Factura factura) {
        return problemas(factura).isEmpty();
    }

    // -----------------------------------------------------------------

    /**
     * El emisor no se comprueba solo por "está vacío": los parámetros nacen con
     * valores de ejemplo que parecen reales —una razón social, un NIT bien
     * formado—, así que un despacho que nunca entró a Configuración emitiría
     * documentos a nombre de una empresa inventada sin que nada chistara.
     * Por eso se compara también contra el valor de ejemplo.
     */
    private void revisarEmisor(List<String> problemas) {
        for (ParametroDte definicion : ParametroDte.values()) {
            if (definicion.getGrupo() != ParametroDte.Grupo.EMISOR || !definicion.isObligatorio()) {
                continue;
            }
            String valor = configuracion.get(definicion);

            if (valor == null || valor.isBlank()) {
                problemas.add("Falta el dato del emisor: " + definicion.getEtiqueta() + ".");
            } else if (DELATAN_FALTA_DE_CONFIGURACION.contains(definicion)
                    && valor.equals(definicion.getValorPorDefecto())) {
                problemas.add("El emisor sigue con el valor de ejemplo en \""
                        + definicion.getEtiqueta() + "\": revísalo en Configuración → Parámetros DTE.");
            }
        }
    }

    private void revisarReceptor(List<String> problemas, Factura factura) {
        Cliente cliente = factura.getCliente();
        if (cliente == null) {
            problemas.add("El documento no tiene cliente.");
            return;
        }
        if (esVacio(cliente.getNombre())) {
            problemas.add("El cliente no tiene nombre o razón social.");
        }
        if (esVacio(cliente.getNit())) {
            problemas.add("El cliente no tiene NIT ni DUI.");
        }
        // Regla que la propia aplicación ya aplica al elegir el tipo de
        // documento: sin NRC el cliente no es contribuyente y no cabe un CCF.
        if ("03".equals(factura.getTipoDte()) && esVacio(cliente.getNrc())) {
            problemas.add("Un comprobante de crédito fiscal exige el NRC del cliente, y \""
                    + cliente.getNombre() + "\" no lo tiene.");
        }
    }

    private void revisarDocumento(List<String> problemas, Factura factura) {
        if (esVacio(factura.getNumeroControl())) {
            problemas.add("El documento no tiene número de control.");
        }
        if (esVacio(factura.getCodigoGeneracion())) {
            problemas.add("El documento no tiene código de generación.");
        }
        if (factura.getDetalles() == null || factura.getDetalles().isEmpty()) {
            problemas.add("El documento no tiene líneas de detalle.");
        }
        if (factura.getMontoTotal() == null || factura.getMontoTotal().compareTo(BigDecimal.ZERO) <= 0) {
            problemas.add("El total del documento debe ser mayor que cero.");
        }
        boolean esNota = "05".equals(factura.getTipoDte()) || "06".equals(factura.getTipoDte());
        if (esNota && factura.getFacturaRelacionada() == null) {
            problemas.add("Una nota debe indicar el documento que corrige.");
        }
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
