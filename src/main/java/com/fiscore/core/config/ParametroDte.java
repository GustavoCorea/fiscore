package com.fiscore.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de los parámetros que gobiernan la emisión de DTE ante el
 * Ministerio de Hacienda de El Salvador.
 *
 * Cada entrada describe cómo se pinta el campo en la pantalla de configuración
 * y qué valor se usa mientras el usuario no haya guardado nada. Los valores
 * vivos se guardan en la tabla DTE_PARAMETRO.
 */
public enum ParametroDte {

    // ---------------- Emisor ----------------
    EMISOR_NOMBRE(Grupo.EMISOR, "Razón social", "Nombre legal del emisor tal como aparece en el NIT",
            "Despacho Contable Fiscore, S.A. de C.V.", Tipo.TEXTO, true),
    EMISOR_NOMBRE_COMERCIAL(Grupo.EMISOR, "Nombre comercial", "Nombre con el que opera de cara al cliente",
            "Fiscore", Tipo.TEXTO, false),
    EMISOR_NIT(Grupo.EMISOR, "NIT", "Formato 0000-000000-000-0 o 14 dígitos",
            "0614-010101-001-0", Tipo.TEXTO, true),
    EMISOR_NRC(Grupo.EMISOR, "NRC", "Número de registro de contribuyente, sin ceros a la izquierda",
            "123456-7", Tipo.TEXTO, true),
    EMISOR_COD_ACTIVIDAD(Grupo.EMISOR, "Código de actividad", "Código CIIU de la actividad económica (catálogo CAT-019)",
            "69200", Tipo.TEXTO, true),
    EMISOR_GIRO(Grupo.EMISOR, "Giro / actividad", "Descripción de la actividad económica",
            "Servicios de contabilidad, auditoría y asesoría fiscal", Tipo.TEXTO, true),
    EMISOR_DIRECCION(Grupo.EMISOR, "Dirección", "Dirección fiscal completa del establecimiento",
            "Colonia Escalón, San Salvador", Tipo.TEXTO, true),
    EMISOR_DEPARTAMENTO(Grupo.EMISOR, "Código de departamento", "Catálogo CAT-012, p. ej. 06 = San Salvador",
            "06", Tipo.TEXTO, true),
    EMISOR_MUNICIPIO(Grupo.EMISOR, "Código de municipio", "Catálogo CAT-013 dentro del departamento",
            "14", Tipo.TEXTO, true),
    EMISOR_TELEFONO(Grupo.EMISOR, "Teléfono", "Teléfono de contacto del emisor",
            "+503 2222-0000", Tipo.TEXTO, false),
    EMISOR_CORREO(Grupo.EMISOR, "Correo electrónico", "Correo al que Hacienda notifica los DTE",
            "facturacion@fiscore.sv", Tipo.TEXTO, true),
    EMISOR_TIPO_ESTABLECIMIENTO(Grupo.EMISOR, "Tipo de establecimiento", "Catálogo CAT-009: 01 sucursal, 02 casa matriz, 04 bodega",
            "02", Tipo.TEXTO, true),

    // ---------------- Numeración ----------------
    ESTABLECIMIENTO_CODIGO(Grupo.NUMERACION, "Código de establecimiento", "4 caracteres del número de control, p. ej. M001",
            "M001", Tipo.TEXTO, true),
    PUNTO_VENTA_CODIGO(Grupo.NUMERACION, "Código de punto de venta", "4 caracteres del número de control, p. ej. P001",
            "P001", Tipo.TEXTO, true),
    PREFIJO_FACTURA(Grupo.NUMERACION, "Prefijo factura (01)", "Prefijo del correlativo interno de consumidor final",
            "FAC", Tipo.TEXTO, true),
    PREFIJO_CCF(Grupo.NUMERACION, "Prefijo CCF (03)", "Prefijo del correlativo interno de crédito fiscal",
            "CCF", Tipo.TEXTO, true),
    PREFIJO_NOTA_CREDITO(Grupo.NUMERACION, "Prefijo nota de crédito (05)", "Prefijo del correlativo interno",
            "NCR", Tipo.TEXTO, true),
    PREFIJO_NOTA_DEBITO(Grupo.NUMERACION, "Prefijo nota de débito (06)", "Prefijo del correlativo interno",
            "NDB", Tipo.TEXTO, true),
    PREFIJO_SUJETO_EXCLUIDO(Grupo.NUMERACION, "Prefijo sujeto excluido (14)", "Prefijo del correlativo interno",
            "FSE", Tipo.TEXTO, true),

    // ---------------- Tributario ----------------
    IVA_TASA(Grupo.TRIBUTARIO, "Tasa de IVA", "Proporción, no porcentaje: 0.13 equivale al 13%",
            "0.13", Tipo.DECIMAL, true),
    RETENCION_IVA_TASA(Grupo.TRIBUTARIO, "Retención de IVA", "1% que retiene el gran contribuyente",
            "0.01", Tipo.DECIMAL, true),
    PERCEPCION_IVA_TASA(Grupo.TRIBUTARIO, "Percepción de IVA", "1% de percepción cuando aplica",
            "0.01", Tipo.DECIMAL, true),
    RETENCION_RENTA_TASA(Grupo.TRIBUTARIO, "Retención de renta", "10% sobre servicios profesionales",
            "0.10", Tipo.DECIMAL, true),
    RETENCION_MONTO_MINIMO(Grupo.TRIBUTARIO, "Monto mínimo de retención", "Importe a partir del cual se aplica retención",
            "100.00", Tipo.DECIMAL, true),
    MONEDA(Grupo.TRIBUTARIO, "Moneda", "Código ISO de la moneda de los documentos",
            "USD", Tipo.TEXTO, true),

    // ---------------- Conexión con Hacienda ----------------
    MH_AMBIENTE(Grupo.HACIENDA, "Ambiente", "00 = pruebas, 01 = producción",
            "00", Tipo.SELECCION, true, "00:00 - Pruebas", "01:01 - Producción"),
    MH_URL_AUTH(Grupo.HACIENDA, "URL de autenticación", "Endpoint que emite el token de sesión",
            "https://apitest.dtes.mh.gob.sv/seguridad/auth", Tipo.TEXTO, false),
    MH_URL_RECEPCION(Grupo.HACIENDA, "URL de recepción", "Endpoint de recepción de DTE",
            "https://apitest.dtes.mh.gob.sv/fesv/recepciondte", Tipo.TEXTO, false),
    MH_URL_ANULACION(Grupo.HACIENDA, "URL de anulación", "Endpoint de invalidación de documentos",
            "https://apitest.dtes.mh.gob.sv/fesv/anulardte", Tipo.TEXTO, false),
    MH_URL_CONTINGENCIA(Grupo.HACIENDA, "URL de contingencia", "Endpoint de eventos de contingencia",
            "https://apitest.dtes.mh.gob.sv/fesv/contingencia", Tipo.TEXTO, false),
    MH_USUARIO(Grupo.HACIENDA, "Usuario del API", "NIT del emisor usado para autenticarse",
            "", Tipo.TEXTO, false),
    MH_CLAVE_API(Grupo.HACIENDA, "Clave del API", "Contraseña del ambiente de Hacienda",
            "", Tipo.SECRETO, false),
    MH_CLAVE_PRIVADA(Grupo.HACIENDA, "Clave privada del certificado", "Contraseña del certificado de firma electrónica",
            "", Tipo.SECRETO, false),
    MH_URL_FIRMADOR(Grupo.HACIENDA, "URL del firmador", "Servicio local que firma el JSON del DTE",
            "http://localhost:8113/firmardocumento/", Tipo.TEXTO, false),
    MH_TIMEOUT_SEGUNDOS(Grupo.HACIENDA, "Tiempo de espera (s)", "Segundos antes de dar por fallida una llamada",
            "30", Tipo.ENTERO, false),

    // ---------------- Operación ----------------
    VERSION_JSON(Grupo.OPERACION, "Versión del esquema JSON", "Versión del esquema DTE que exige Hacienda",
            "1", Tipo.ENTERO, true),
    MODELO_FACTURACION(Grupo.OPERACION, "Modelo de facturación", "1 = previo, 2 = diferido",
            "1", Tipo.SELECCION, true, "1:1 - Previo", "2:2 - Diferido"),
    TIPO_TRANSMISION(Grupo.OPERACION, "Tipo de transmisión", "1 = normal, 2 = contingencia",
            "1", Tipo.SELECCION, true, "1:1 - Normal", "2:2 - Contingencia"),
    PLAZO_CREDITO_DEFECTO(Grupo.OPERACION, "Plazo de crédito por defecto", "Días de crédito cuando no se indica otro",
            "30", Tipo.ENTERO, true),
    ENVIO_AUTOMATICO(Grupo.OPERACION, "Transmitir al emitir", "Enviar el DTE a Hacienda en cuanto se emite",
            "false", Tipo.BOOLEANO, false);

    // =================================================================

    /** Secciones en las que se agrupan los parámetros en la pantalla. */
    public enum Grupo {
        EMISOR("Datos del emisor", "mdi-domain", "Identificación fiscal que viaja en cada documento"),
        NUMERACION("Numeración y correlativos", "mdi-format-list-numbered", "Componentes del número de control y prefijos internos"),
        TRIBUTARIO("Parámetros tributarios", "mdi-percent", "Tasas e importes que intervienen en el cálculo"),
        HACIENDA("Conexión con Hacienda", "mdi-cloud-sync", "Ambiente, endpoints y credenciales del Ministerio de Hacienda"),
        OPERACION("Operación", "mdi-cog", "Comportamiento del sistema al emitir documentos");

        private final String titulo;
        private final String icono;
        private final String descripcion;

        Grupo(String titulo, String icono, String descripcion) {
            this.titulo = titulo;
            this.icono = icono;
            this.descripcion = descripcion;
        }

        public String getTitulo() { return titulo; }
        public String getIcono() { return icono; }
        public String getDescripcion() { return descripcion; }
        public String getNombre() { return name(); }
    }

    /** Cómo se captura el valor en el formulario. */
    public enum Tipo {
        TEXTO, ENTERO, DECIMAL, BOOLEANO, SELECCION,
        /** No se devuelve nunca al navegador; solo se sobrescribe si se escribe algo. */
        SECRETO
    }

    private final Grupo grupo;
    private final String etiqueta;
    private final String descripcion;
    private final String valorPorDefecto;
    private final Tipo tipo;
    private final boolean obligatorio;
    private final List<String> opciones;

    ParametroDte(Grupo grupo, String etiqueta, String descripcion, String valorPorDefecto,
                 Tipo tipo, boolean obligatorio, String... opciones) {
        this.grupo = grupo;
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.valorPorDefecto = valorPorDefecto;
        this.tipo = tipo;
        this.obligatorio = obligatorio;
        this.opciones = List.of(opciones);
    }

    public Grupo getGrupo() { return grupo; }
    public String getClave() { return name(); }
    public String getEtiqueta() { return etiqueta; }
    public String getDescripcion() { return descripcion; }
    public String getValorPorDefecto() { return valorPorDefecto; }
    public Tipo getTipo() { return tipo; }
    public boolean isObligatorio() { return obligatorio; }
    public boolean isSecreto() { return tipo == Tipo.SECRETO; }

    /** Opciones de un desplegable como pares valor/etiqueta. */
    public List<Map<String, String>> getOpciones() {
        List<Map<String, String>> lista = new ArrayList<>();
        for (String opcion : opciones) {
            int separador = opcion.indexOf(':');
            Map<String, String> item = new LinkedHashMap<>();
            item.put("valor", opcion.substring(0, separador));
            item.put("etiqueta", opcion.substring(separador + 1));
            lista.add(item);
        }
        return lista;
    }

    public static ParametroDte porClave(String clave) {
        for (ParametroDte p : values()) {
            if (p.name().equalsIgnoreCase(clave)) return p;
        }
        return null;
    }
}
