package com.fiscore.core.services;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.ContratoServicio;
import com.fiscore.core.models.DetalleFactura;
import com.fiscore.core.models.EstadoDte;
import com.fiscore.core.models.Factura;
import com.fiscore.core.models.Proyecto;
import com.fiscore.core.repositories.ContratoRepository;
import com.fiscore.core.repositories.FacturaRepository;
import com.fiscore.core.repositories.ProyectoRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FacturacionService {

    private static final Locale ES_SV = Locale.forLanguageTag("es-SV");

    /** Largo de la columna periodo_facturado. */
    private static final int MAX_PERIODO = 255;

    /** Parámetro que define el prefijo del correlativo interno de cada tipo de DTE. */
    private static final Map<String, ParametroDte> PREFIJO_POR_TIPO = Map.of(
            "01", ParametroDte.PREFIJO_FACTURA,
            "03", ParametroDte.PREFIJO_CCF,
            "05", ParametroDte.PREFIJO_NOTA_CREDITO,
            "06", ParametroDte.PREFIJO_NOTA_DEBITO,
            "14", ParametroDte.PREFIJO_SUJETO_EXCLUIDO
    );

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ContratoRepository contratoRepository;

    @Autowired
    private ProyectoRepository proyectoRepository;

    @Autowired
    private ConfiguracionDteService configuracion;

    @Autowired
    private CorrelativoService correlativoService;

    /** Tasa de IVA vigente, tomada de la configuración editable. */
    private BigDecimal iva() {
        return configuracion.getDecimal(ParametroDte.IVA_TASA);
    }

    // =================================================================
    // Consultas
    // =================================================================

    public List<Factura> findAll() {
        return facturaRepository.findAllByOrderByFechaEmisionDesc();
    }

    public List<Factura> findByEstado(String estado) {
        return facturaRepository.findByEstadoOrderByFechaEmisionDesc(estado);
    }

    public Optional<Factura> findById(Long id) {
        return facturaRepository.findById(id);
    }

    public List<Factura> findRecientes() {
        return facturaRepository.findTop8ByOrderByFechaEmisionDesc();
    }

    public List<Factura> findVencidas() {
        return facturaRepository.findVencidas(LocalDate.now());
    }

    public BigDecimal getMontoPendiente() {
        return facturaRepository.sumMontoPendiente();
    }

    /** Etiqueta del periodo actual, p.ej. "Marzo 2026". */
    public static String periodoDe(LocalDate fecha) {
        String mes = fecha.getMonth().getDisplayName(TextStyle.FULL, ES_SV);
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + fecha.getYear();
    }

    // =================================================================
    // Emisión
    // =================================================================

    /**
     * Genera una factura a partir de un contrato.
     * El tipo de DTE se deduce del receptor: con NRC → CCF (03); sin NRC → Factura CF (01).
     * Al emitirla se adelanta la próxima fecha de facturación del contrato, de modo que
     * el contrato deja de aparecer en la bandeja "Por emitir" hasta el siguiente ciclo.
     */
    @Transactional
    public Factura generarDesdeContrato(Contrato contrato, String periodoFacturado,
                                        String condicionPago, Integer plazoCredito) {

        if (contrato.getCliente() == null) {
            throw new IllegalArgumentException("El contrato no tiene un cliente asignado.");
        }
        BigDecimal honorarios = contrato.getHonorariosPactados();
        if (honorarios == null || honorarios.signum() <= 0) {
            throw new IllegalArgumentException("El contrato no tiene honorarios pactados válidos.");
        }

        String periodo = normalizarPeriodo(periodoFacturado);

        if (facturaRepository.countByContratoYPeriodo(contrato.getId(), periodo) > 0) {
            throw new IllegalStateException(
                    "Ya existe una factura vigente para este contrato en el periodo \"" + periodo + "\".");
        }

        Factura factura = nuevaFactura(tipoDtePara(contrato.getCliente().getNrc()));
        factura.setCliente(contrato.getCliente());
        factura.setContrato(contrato);
        factura.setPeriodoFacturado(periodo);
        aplicarCondicionPago(factura, condicionPago, plazoCredito);

        // Una línea por servicio contratado; si no hay servicios, una línea global.
        List<DetalleFactura> detalles = new ArrayList<>();
        List<ContratoServicio> servicios = contrato.getServicios() == null
                ? List.of() : contrato.getServicios();

        BigDecimal sumaServicios = servicios.stream()
                .map(cs -> cs.getPrecioAcordado() != null ? cs.getPrecioAcordado() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!servicios.isEmpty() && sumaServicios.signum() > 0) {
            int item = 1;
            for (ContratoServicio cs : servicios) {
                BigDecimal precio = cs.getPrecioAcordado() != null ? cs.getPrecioAcordado() : BigDecimal.ZERO;
                if (precio.signum() <= 0) continue;
                // Los honorarios pactados se registran IVA incluido: se desagrega la base gravada.
                BigDecimal proporcion = precio.divide(sumaServicios, 8, RoundingMode.HALF_UP);
                BigDecimal brutoLinea = honorarios.multiply(proporcion).setScale(2, RoundingMode.HALF_UP);
                BigDecimal baseLinea = desagregarBase(brutoLinea);
                String nombre = cs.getServicio() != null ? cs.getServicio().getNombre() : "Servicio contratado";
                detalles.add(nuevoDetalle(item++, nombre + " — " + periodo, baseLinea));
            }
        }

        if (detalles.isEmpty()) {
            String descripcion = servicios.stream()
                    .map(cs -> cs.getServicio() != null ? cs.getServicio().getNombre() : "")
                    .filter(n -> !n.isEmpty())
                    .collect(Collectors.joining(", "));
            if (descripcion.isEmpty()) descripcion = "Servicios contratados";
            detalles.add(nuevoDetalle(1, descripcion + " — " + periodo, desagregarBase(honorarios)));
        }

        asignarDetalles(factura, detalles);
        cuadrarConTotalPactado(factura, honorarios);

        Factura guardada = facturaRepository.save(factura);
        avanzarProximaFacturacion(contrato);
        return guardada;
    }

    /**
     * Genera la factura de un proyecto/caso finalizado y lo marca como facturado.
     */
    @Transactional
    public Factura generarDesdeProyecto(Proyecto proyecto, String condicionPago, Integer plazoCredito) {
        if (proyecto.getCliente() == null) {
            throw new IllegalArgumentException("El proyecto no tiene un cliente asignado.");
        }
        if (Boolean.TRUE.equals(proyecto.getFacturado())) {
            throw new IllegalStateException("El proyecto \"" + proyecto.getNombre() + "\" ya fue facturado.");
        }
        BigDecimal presupuesto = proyecto.getPresupuesto();
        if (presupuesto == null || presupuesto.signum() <= 0) {
            throw new IllegalArgumentException("El proyecto no tiene presupuesto definido.");
        }

        Factura factura = nuevaFactura(tipoDtePara(proyecto.getCliente().getNrc()));
        factura.setCliente(proyecto.getCliente());
        factura.setProyecto(proyecto);
        factura.setPeriodoFacturado(periodoDe(LocalDate.now()));
        factura.setNotas("Proyecto: " + proyecto.getNombre());
        aplicarCondicionPago(factura, condicionPago, plazoCredito);

        String descripcion = proyecto.getNombre()
                + (proyecto.getCategoria() != null ? " (" + proyecto.getCategoria() + ")" : "");
        asignarDetalles(factura, List.of(nuevoDetalle(1, descripcion, desagregarBase(presupuesto))));
        cuadrarConTotalPactado(factura, presupuesto);

        Factura guardada = facturaRepository.save(factura);

        proyecto.setFacturado(true);
        proyecto.setEstado("FACTURADO");
        if (proyecto.getFechaFin() == null) proyecto.setFechaFin(LocalDate.now());
        proyectoRepository.save(proyecto);

        return guardada;
    }

    /**
     * Facturación masiva de todos los contratos activos con fecha de próxima
     * facturación vencida. Devuelve el resumen de lo emitido y lo omitido.
     */
    @Transactional
    public Map<String, Object> generarLoteRecurrente(String periodoFacturado, String condicionPago, Integer plazoCredito) {
        String periodo = (periodoFacturado == null || periodoFacturado.isBlank())
                ? periodoDe(LocalDate.now())
                : periodoFacturado.trim();

        List<String> emitidas = new ArrayList<>();
        List<String> omitidas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Contrato contrato : contratoRepository.findPorFacturarHasta(LocalDate.now())) {
            try {
                Factura f = generarDesdeContrato(contrato, periodo, condicionPago, plazoCredito);
                emitidas.add(f.getNumeroFactura() + " — " + f.getCliente().getNombre());
                total = total.add(f.getMontoTotal());
            } catch (RuntimeException e) {
                String cliente = contrato.getCliente() != null ? contrato.getCliente().getNombre() : "Contrato #" + contrato.getId();
                omitidas.add(cliente + ": " + e.getMessage());
            }
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("periodo", periodo);
        resumen.put("cantidadEmitidas", emitidas.size());
        resumen.put("cantidadOmitidas", omitidas.size());
        resumen.put("montoTotal", total.setScale(2, RoundingMode.HALF_UP));
        resumen.put("emitidas", emitidas);
        resumen.put("omitidas", omitidas);
        return resumen;
    }

    /** Alta o actualización de una factura capturada manualmente. */
    @Transactional
    public Factura save(Factura factura) {
        if (factura.getTipoDte() == null || factura.getTipoDte().isBlank()) {
            factura.setTipoDte("01");
        }
        if (factura.getFechaEmision() == null) {
            factura.setFechaEmision(LocalDateTime.now());
        }
        if (factura.getCodigoGeneracion() == null) {
            factura.setCodigoGeneracion(UUID.randomUUID().toString().toUpperCase());
        }
        if (factura.getNumeroFactura() == null) {
            long correlativo = siguienteCorrelativo(factura.getTipoDte());
            factura.setNumeroFactura(formatearCorrelativo(factura.getTipoDte(), correlativo));
            factura.setNumeroControl(generarNumeroControl(factura.getTipoDte(), correlativo));
        }
        if (factura.getEstado() == null) {
            factura.setEstado("BORRADOR");
        }
        if (factura.getEstadoDte() == null) {
            factura.setEstadoDte("PENDIENTE_ENVIO");
        }
        if (factura.getDetalles() == null || factura.getDetalles().isEmpty()) {
            throw new IllegalArgumentException("La factura debe tener al menos una línea de detalle.");
        }
        exigirDocumentoRelacionado(factura);
        if (factura.getCliente() == null) {
            throw new IllegalArgumentException("Debe seleccionar el cliente receptor del documento.");
        }
        if ("03".equals(factura.getTipoDte())
                && (factura.getCliente().getNrc() == null || factura.getCliente().getNrc().isBlank())) {
            throw new IllegalArgumentException(
                    "El Comprobante de Crédito Fiscal exige que el receptor tenga NRC registrado.");
        }

        aplicarCondicionPago(factura, factura.getCondicionPago(), factura.getPlazoCredito());
        calcularMontos(factura);

        // Asegurar referencia bidireccional y numeración de ítems antes de persistir
        int item = 1;
        for (DetalleFactura d : factura.getDetalles()) {
            d.setFactura(factura);
            if (d.getNumItem() == null) d.setNumItem(item);
            item++;
        }
        return facturaRepository.save(factura);
    }

    // =================================================================
    // Cambios de estado
    // =================================================================

    /** Registra el cobro de una factura emitida. */
    @Transactional
    public Factura registrarPago(Factura factura, LocalDate fechaPago) {
        if ("ANULADA".equals(factura.getEstado())) {
            throw new IllegalStateException("No se puede registrar el pago de una factura anulada.");
        }
        factura.setEstado("PAGADA");
        factura.setFechaPago(fechaPago != null ? fechaPago : LocalDate.now());
        return facturaRepository.save(factura);
    }

    /**
     * Anula una factura conservando el documento (requisito fiscal: los DTE no se borran).
     * Si venía de un proyecto, el proyecto vuelve a quedar pendiente de facturar.
     */
    @Transactional
    public Factura anular(Factura factura, String motivo) {
        if ("ANULADA".equals(factura.getEstado())) {
            throw new IllegalStateException("La factura ya se encuentra anulada.");
        }
        // Un documento con sello de recepción existe para Hacienda, y anularlo
        // solo en el sistema dejaría las dos versiones en desacuerdo. Requiere
        // el evento de invalidación ante el Ministerio, que aún no está hecho.
        if (EstadoDte.desde(factura.getEstadoDte()).tieneValidezFiscal()) {
            throw new IllegalStateException(
                    "La factura tiene sello de Hacienda: hay que invalidarla ante el Ministerio, "
                            + "no basta con anularla en el sistema.");
        }
        factura.setEstado("ANULADA");
        factura.setFechaPago(null);
        String nota = "Anulada el " + LocalDate.now()
                + (motivo != null && !motivo.isBlank() ? " — " + motivo.trim() : "");
        factura.setNotas(factura.getNotas() == null || factura.getNotas().isBlank()
                ? nota : factura.getNotas() + " | " + nota);

        if (factura.getProyecto() != null) {
            Proyecto p = factura.getProyecto();
            p.setFacturado(false);
            p.setEstado("FINALIZADO");
            proyectoRepository.save(p);
        }

        Factura anulada = facturaRepository.save(factura);
        devolverContratoALaAgenda(anulada);
        return anulada;
    }

    /**
     * Al anular, el periodo vuelve a quedar sin facturar. Si esa era la última
     * factura vigente del contrato, se retrocede su próxima facturación para
     * que reaparezca en la bandeja "Por emitir"; de lo contrario el contrato
     * quedaba fuera de la agenda y el periodo no se podía reemitir.
     */
    private void devolverContratoALaAgenda(Factura anulada) {
        Contrato contrato = anulada.getContrato();
        if (contrato == null || !"ACTIVO".equals(contrato.getEstado())) {
            return;
        }

        // Si ya existe otra factura vigente posterior, el ciclo actual es correcto.
        boolean hayFacturaVigentePosterior = facturaRepository
                .findByContratoIdOrderByFechaEmisionDesc(contrato.getId()).stream()
                .anyMatch(f -> !f.getId().equals(anulada.getId()) && !"ANULADA".equals(f.getEstado()));

        if (hayFacturaVigentePosterior) {
            return;
        }

        LocalDate fechaEmision = anulada.getFechaEmision() != null
                ? anulada.getFechaEmision().toLocalDate()
                : LocalDate.now();
        contrato.setFechaProximaFacturacion(fechaEmision);
        contratoRepository.save(contrato);
    }

    /** Cambio de estado genérico usado por la API. */
    @Transactional
    public Factura cambiarEstado(Factura factura, String nuevoEstado, String motivo) {
        if (nuevoEstado == null || nuevoEstado.isBlank()) {
            throw new IllegalArgumentException("Debe indicar el nuevo estado.");
        }
        switch (nuevoEstado.toUpperCase()) {
            case "PAGADA":
                return registrarPago(factura, null);
            case "ANULADA":
                return anular(factura, motivo);
            case "EMITIDA":
                factura.setEstado("EMITIDA");
                factura.setFechaPago(null);
                return facturaRepository.save(factura);
            case "BORRADOR":
                factura.setEstado("BORRADOR");
                return facturaRepository.save(factura);
            default:
                throw new IllegalArgumentException("Estado no soportado: " + nuevoEstado);
        }
    }

    /**
     * Avanza el estado del documento frente a Hacienda validando la transición.
     *
     * Es el único punto por el que debería cambiar {@code estadoDte}: escribirlo
     * a mano desde cada paso de la integración es como se acaba con documentos
     * ACEPTADOS que nunca se enviaron.
     */
    @Transactional
    public Factura cambiarEstadoDte(Factura factura, EstadoDte destino) {
        EstadoDte actual = EstadoDte.desde(factura.getEstadoDte());
        if (actual == destino) {
            return factura;
        }
        if (!actual.puedePasarA(destino)) {
            throw new IllegalStateException("Un documento en " + actual + " no puede pasar a "
                    + destino + ". Desde " + actual + " solo cabe " + actual.siguientesPosibles() + ".");
        }
        factura.setEstadoDte(destino.name());
        return facturaRepository.save(factura);
    }

    /**
     * Las notas de crédito (05) y débito (06) deben decir qué documento
     * corrigen: el esquema de Hacienda lo exige y sin ese dato el documento se
     * rechaza. Se comprueba al guardar y no al transmitir para que el fallo
     * aparezca mientras el usuario tiene el formulario delante.
     */
    private void exigirDocumentoRelacionado(Factura factura) {
        boolean esNota = "05".equals(factura.getTipoDte()) || "06".equals(factura.getTipoDte());

        if (esNota && factura.getFacturaRelacionada() == null) {
            throw new IllegalArgumentException(
                    "Una nota de crédito o débito debe indicar el documento que corrige.");
        }
        if (!esNota && factura.getFacturaRelacionada() != null) {
            throw new IllegalArgumentException(
                    "Solo las notas de crédito y débito pueden referirse a otro documento.");
        }
    }

    /**
     * Solo se permite borrar borradores; el resto se anula para conservar la
     * correlatividad de los documentos tributarios.
     */
    @Transactional
    public void deleteById(Long id) {
        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factura no encontrada."));
        if (!"BORRADOR".equals(factura.getEstado())) {
            throw new IllegalStateException(
                    "Solo se pueden eliminar borradores. Una factura emitida debe anularse.");
        }
        facturaRepository.delete(factura);
    }

    // =================================================================
    // Helpers internos
    // =================================================================

    private Factura nuevaFactura(String tipoDte) {
        Factura factura = new Factura();
        long correlativo = siguienteCorrelativo(tipoDte);
        factura.setTipoDte(tipoDte);
        factura.setCodigoGeneracion(UUID.randomUUID().toString().toUpperCase());
        factura.setNumeroFactura(formatearCorrelativo(tipoDte, correlativo));
        factura.setNumeroControl(generarNumeroControl(tipoDte, correlativo));
        factura.setFechaEmision(LocalDateTime.now());
        factura.setEstado("EMITIDA");
        factura.setEstadoDte("PENDIENTE_ENVIO");
        return factura;
    }

    /**
     * Normaliza la etiqueta del periodo. Se valida aquí para dar un mensaje
     * entendible: sin este control, un texto largo llegaba hasta la base de
     * datos y el usuario solo veía un error de columna desbordada.
     */
    private String normalizarPeriodo(String periodoFacturado) {
        if (periodoFacturado == null || periodoFacturado.isBlank()) {
            return periodoDe(LocalDate.now());
        }
        String periodo = periodoFacturado.trim();
        if (periodo.length() > MAX_PERIODO) {
            throw new IllegalArgumentException(
                    "El periodo facturado no puede superar los " + MAX_PERIODO + " caracteres.");
        }
        return periodo;
    }

    private String tipoDtePara(String nrcReceptor) {
        return (nrcReceptor != null && !nrcReceptor.isBlank()) ? "03" : "01";
    }

    private void aplicarCondicionPago(Factura factura, String condicionPago, Integer plazoCredito) {
        String condicion = (condicionPago != null && !condicionPago.isBlank()) ? condicionPago : "CONTADO";
        factura.setCondicionPago(condicion);
        LocalDate emision = factura.getFechaEmision() != null
                ? factura.getFechaEmision().toLocalDate() : LocalDate.now();
        if ("CREDITO".equalsIgnoreCase(condicion)) {
            int dias = (plazoCredito != null && plazoCredito > 0)
                    ? plazoCredito
                    : configuracion.getEntero(ParametroDte.PLAZO_CREDITO_DEFECTO);
            factura.setPlazoCredito(dias);
            factura.setFechaVencimiento(emision.plusDays(dias));
        } else {
            factura.setPlazoCredito(null);
            factura.setFechaVencimiento(emision);
        }
    }

    private DetalleFactura nuevoDetalle(int numItem, String descripcion, BigDecimal baseGravada) {
        DetalleFactura d = new DetalleFactura();
        d.setNumItem(numItem);
        d.setTipoItem("2"); // 2 = servicio
        d.setDescripcion(descripcion);
        d.setCantidad(BigDecimal.ONE);
        d.setUnidadMedida("Servicio");
        d.setPrecioUnitario(baseGravada);
        d.setDescuento(BigDecimal.ZERO);
        d.setVentaGravada(baseGravada);
        d.setVentaExenta(BigDecimal.ZERO);
        d.setVentaNoSujeta(BigDecimal.ZERO);
        return d;
    }

    private void asignarDetalles(Factura factura, List<DetalleFactura> detalles) {
        List<DetalleFactura> lista = new ArrayList<>(detalles);
        for (DetalleFactura d : lista) {
            d.setFactura(factura);
        }
        factura.setDetalles(lista);
        calcularMontos(factura);
    }

    /** Extrae la base imponible de un importe que ya incluye IVA. */
    private BigDecimal desagregarBase(BigDecimal montoConIva) {
        return montoConIva.divide(BigDecimal.ONE.add(iva()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Corrige el redondeo para que el total de la factura coincida exactamente
     * con el importe pactado (los céntimos perdidos al desagregar el IVA se
     * ajustan contra el IVA).
     */
    private void cuadrarConTotalPactado(Factura factura, BigDecimal totalPactado) {
        BigDecimal objetivo = totalPactado.setScale(2, RoundingMode.HALF_UP);
        BigDecimal diferencia = objetivo.subtract(factura.getMontoTotal());
        if (diferencia.signum() != 0) {
            factura.setIvaPercibido(factura.getIvaPercibido().add(diferencia));
            factura.setMontoTotal(objetivo);
        }
    }

    private void calcularMontos(Factura factura) {
        BigDecimal gravado = BigDecimal.ZERO;
        BigDecimal exento = BigDecimal.ZERO;
        BigDecimal noSujeto = BigDecimal.ZERO;

        if (factura.getDetalles() != null) {
            for (DetalleFactura d : factura.getDetalles()) {
                if (d.getVentaGravada() != null) gravado = gravado.add(d.getVentaGravada());
                if (d.getVentaExenta() != null) exento = exento.add(d.getVentaExenta());
                if (d.getVentaNoSujeta() != null) noSujeto = noSujeto.add(d.getVentaNoSujeta());
            }
        }

        BigDecimal descuento = factura.getDescuento() != null ? factura.getDescuento() : BigDecimal.ZERO;
        BigDecimal iva = gravado.subtract(descuento).max(BigDecimal.ZERO)
                .multiply(iva()).setScale(2, RoundingMode.HALF_UP);

        factura.setSubtotalGravado(gravado.setScale(2, RoundingMode.HALF_UP));
        factura.setSubtotalExento(exento.setScale(2, RoundingMode.HALF_UP));
        factura.setSubtotalNoSujeto(noSujeto.setScale(2, RoundingMode.HALF_UP));
        factura.setDescuento(descuento.setScale(2, RoundingMode.HALF_UP));
        factura.setIvaPercibido(iva);
        if (factura.getIvaRetenido() == null) factura.setIvaRetenido(BigDecimal.ZERO);

        BigDecimal base = gravado.add(exento).add(noSujeto).subtract(descuento).max(BigDecimal.ZERO);
        factura.setRetencionRenta(retencionRentaSobre(base));

        factura.setMontoTotal(base.add(iva)
                .subtract(factura.getIvaRetenido())
                .subtract(factura.getRetencionRenta())
                .setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Retención de renta sobre la base del documento.
     *
     * Devuelve cero salvo que el parámetro esté activo, porque no todo emisor la
     * sufre: depende de su naturaleza, no del documento. Y por debajo del monto
     * mínimo no hay obligación de retener, así que aplicarla igual descuadraría
     * el cobro contra lo que el cliente realmente entera.
     *
     * Las tasas llevaban desde el principio en DTE_PARAMETRO sin que nada las
     * leyera: este es su primer uso.
     */
    private BigDecimal retencionRentaSobre(BigDecimal base) {
        if (!configuracion.getBooleano(ParametroDte.RETENCION_RENTA_APLICA)) {
            return BigDecimal.ZERO;
        }
        if (base.compareTo(configuracion.getDecimal(ParametroDte.RETENCION_MONTO_MINIMO)) < 0) {
            return BigDecimal.ZERO;
        }
        return base.multiply(configuracion.getDecimal(ParametroDte.RETENCION_RENTA_TASA))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Adelanta la próxima facturación del contrato según su periodicidad. */
    private void avanzarProximaFacturacion(Contrato contrato) {
        LocalDate base = contrato.getFechaProximaFacturacion() != null
                ? contrato.getFechaProximaFacturacion()
                : LocalDate.now();

        if ("RECURRENTE".equals(contrato.getTipoFacturacion())) {
            // Si el contrato venía atrasado, se recupera hasta el próximo ciclo futuro.
            LocalDate proxima = base.plusMonths(1);
            while (!proxima.isAfter(LocalDate.now())) {
                proxima = proxima.plusMonths(1);
            }
            contrato.setFechaProximaFacturacion(proxima);
        } else {
            // Pago único / bolsa de horas: no hay siguiente ciclo automático.
            contrato.setFechaProximaFacturacion(null);
        }
        contratoRepository.save(contrato);
    }

    /**
     * Reserva el siguiente correlativo de forma atómica. Calcularlo con un
     * MAX() sobre las facturas producía documentos con el mismo número cuando
     * se emitía en paralelo.
     */
    private long siguienteCorrelativo(String tipoDte) {
        return correlativoService.siguiente(tipoDte);
    }

    private String formatearCorrelativo(String tipoDte, long correlativo) {
        ParametroDte definicion = PREFIJO_POR_TIPO.get(tipoDte);
        String prefijo = definicion != null ? configuracion.get(definicion) : "DTE";
        return prefijo + "-" + String.format("%05d", correlativo);
    }

    /**
     * Número de control DTE: {@code DTE-[tipo]-[estable][puntoVenta]-[correlativo 15]}
     * según la especificación del Ministerio de Hacienda de El Salvador.
     */
    private String generarNumeroControl(String tipoDte, long correlativo) {
        String tipo = (tipoDte != null && !tipoDte.isBlank()) ? tipoDte : "01";
        String establecimiento = configuracion.get(ParametroDte.ESTABLECIMIENTO_CODIGO)
                + configuracion.get(ParametroDte.PUNTO_VENTA_CODIGO);
        return "DTE-" + tipo + "-" + establecimiento + "-" + String.format("%015d", correlativo);
    }
}
