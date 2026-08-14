package com.fiscore.core.services;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Compra;
import com.fiscore.core.repositories.CompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Libro de compras y liquidación del IVA.
 *
 * El libro de ventas ya existía, pero sin el de compras no hay crédito fiscal
 * que oponer al débito, que es justo lo que se cierra cada mes.
 */
@Service
public class CompraService {

    private final CompraRepository compraRepository;
    private final ReportesService reportesService;
    private final ConfiguracionDteService configuracion;

    public CompraService(CompraRepository compraRepository,
                         ReportesService reportesService,
                         ConfiguracionDteService configuracion) {
        this.compraRepository = compraRepository;
        this.reportesService = reportesService;
        this.configuracion = configuracion;
    }

    // =================================================================
    // Registro
    // =================================================================

    @Transactional(readOnly = true)
    public List<Compra> findByPeriodo(int anio, Integer mes) {
        LocalDate desde = mes != null ? LocalDate.of(anio, mes, 1) : LocalDate.of(anio, 1, 1);
        LocalDate hasta = mes != null ? desde.plusMonths(1) : desde.plusYears(1);
        return compraRepository.findByFechaBetweenOrderByFechaDescIdDesc(desde, hasta.minusDays(0));
    }

    @Transactional(readOnly = true)
    public Optional<Compra> findById(Long id) {
        return compraRepository.findById(id);
    }

    @Transactional
    public Compra guardar(Compra compra) {
        normalizar(compra);
        validar(compra);
        return compraRepository.save(compra);
    }

    @Transactional
    public void eliminar(Long id) {
        compraRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> proveedoresConocidos() {
        List<Map<String, String>> proveedores = new ArrayList<>();
        for (Object[] fila : compraRepository.proveedoresConocidos()) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("nombre", (String) fila[0]);
            p.put("nit", (String) fila[1]);
            p.put("nrc", (String) fila[2]);
            proveedores.add(p);
        }
        return proveedores;
    }

    /**
     * Crédito fiscal que corresponde a una base gravada, para proponerlo en el
     * formulario. Es una ayuda de captura, no una imposición: el importe que
     * manda es el que diga el documento del proveedor.
     */
    public BigDecimal creditoFiscalSugerido(BigDecimal montoGravado) {
        if (montoGravado == null || montoGravado.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return montoGravado.multiply(configuracion.getDecimal(ParametroDte.IVA_TASA))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // =================================================================
    // Libro
    // =================================================================

    /** Resumen mensual del año, separando compras internas de importaciones. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLibroCompras(int anio) {
        Map<String, Map<String, Object>> porMes = new LinkedHashMap<>();

        for (int m = 1; m <= 12; m++) {
            String clave = String.format("%04d-%02d", anio, m);
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("mes", clave);
            fila.put("label", etiquetaMes(YearMonth.of(anio, m)));
            fila.put("gravadoInterno", BigDecimal.ZERO);
            fila.put("gravadoImportacion", BigDecimal.ZERO);
            fila.put("exento", BigDecimal.ZERO);
            fila.put("creditoFiscal", BigDecimal.ZERO);
            fila.put("total", BigDecimal.ZERO);
            fila.put("documentos", 0L);
            porMes.put(clave, fila);
        }

        for (Object[] row : compraRepository.libroCompras(
                LocalDate.of(anio, 1, 1), LocalDate.of(anio + 1, 1, 1))) {

            Map<String, Object> fila = porMes.get(String.valueOf(row[0]));
            if (fila == null) continue;

            String campoGravado = "IMPORTACION".equals(String.valueOf(row[1]))
                    ? "gravadoImportacion" : "gravadoInterno";

            fila.put(campoGravado, ((BigDecimal) fila.get(campoGravado)).add(decimal(row[2])));
            fila.put("exento", ((BigDecimal) fila.get("exento")).add(decimal(row[3])));
            fila.put("creditoFiscal", ((BigDecimal) fila.get("creditoFiscal")).add(decimal(row[4])));
            fila.put("total", ((BigDecimal) fila.get("total")).add(decimal(row[5])));
            fila.put("documentos", (Long) fila.get("documentos") + ((Number) row[6]).longValue());
        }

        return new ArrayList<>(porMes.values());
    }

    /**
     * Liquidación mensual del IVA: débito de las ventas contra crédito de las
     * compras.
     *
     * Cuando el crédito supera al débito no se cobra nada: la diferencia queda
     * como <b>remanente</b> y se arrastra al mes siguiente, donde se suma al
     * crédito. Sin ese arrastre la liquidación de un mes con más compras que
     * ventas daría un pago negativo, que no existe.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLiquidacionIva(int anio) {
        List<Map<String, Object>> ventas = reportesService.getLibroVentas(anio);
        List<Map<String, Object>> compras = getLibroCompras(anio);

        Map<String, BigDecimal> creditoPorMes = new HashMap<>();
        for (Map<String, Object> fila : compras) {
            creditoPorMes.put((String) fila.get("mes"), (BigDecimal) fila.get("creditoFiscal"));
        }

        List<Map<String, Object>> liquidacion = new ArrayList<>();
        BigDecimal remanenteAnterior = BigDecimal.ZERO;

        for (Map<String, Object> filaVentas : ventas) {
            String mes = (String) filaVentas.get("mes");

            BigDecimal debito = decimal(filaVentas.get("iva"));
            BigDecimal credito = creditoPorMes.getOrDefault(mes, BigDecimal.ZERO);
            BigDecimal creditoDisponible = credito.add(remanenteAnterior);
            BigDecimal diferencia = debito.subtract(creditoDisponible);

            BigDecimal aPagar = diferencia.signum() > 0 ? diferencia : BigDecimal.ZERO;
            BigDecimal remanente = diferencia.signum() < 0 ? diferencia.negate() : BigDecimal.ZERO;

            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("mes", mes);
            fila.put("label", filaVentas.get("label"));
            fila.put("debitoFiscal", escala(debito));
            fila.put("creditoFiscal", escala(credito));
            fila.put("remanenteAnterior", escala(remanenteAnterior));
            fila.put("aPagar", escala(aPagar));
            fila.put("remanente", escala(remanente));
            liquidacion.add(fila);

            remanenteAnterior = remanente;
        }
        return liquidacion;
    }

    // =================================================================

    private void normalizar(Compra compra) {
        if (compra.getFecha() == null) compra.setFecha(LocalDate.now());
        if (compra.getTipoOperacion() == null || compra.getTipoOperacion().isBlank()) {
            compra.setTipoOperacion("INTERNA");
        }
        if (compra.getTipoDocumento() == null || compra.getTipoDocumento().isBlank()) {
            compra.setTipoDocumento("03");
        }
        if (compra.getMontoExento() == null) compra.setMontoExento(BigDecimal.ZERO);
        if (compra.getMontoGravado() == null) compra.setMontoGravado(BigDecimal.ZERO);
        if (compra.getCreditoFiscal() == null) compra.setCreditoFiscal(BigDecimal.ZERO);

        // El total se calcula siempre: es una suma, y dejar que se teclee solo
        // abre la puerta a que el libro no cuadre con sus propias columnas.
        compra.setTotal(compra.getBaseTotal().add(compra.getCreditoFiscal())
                .setScale(2, RoundingMode.HALF_UP));
    }

    private void validar(Compra compra) {
        if (compra.getProveedorNombre() == null || compra.getProveedorNombre().isBlank()) {
            throw new IllegalArgumentException("Indica el proveedor.");
        }
        if (compra.getNumeroDocumento() == null || compra.getNumeroDocumento().isBlank()) {
            throw new IllegalArgumentException("Indica el número del documento.");
        }
        if (compra.getFecha().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("No se puede registrar una compra con fecha futura.");
        }
        if (compra.getBaseTotal().signum() <= 0) {
            throw new IllegalArgumentException("La compra debe tener algún importe.");
        }
        if (compra.getMontoExento().signum() < 0 || compra.getMontoGravado().signum() < 0
                || compra.getCreditoFiscal().signum() < 0) {
            throw new IllegalArgumentException("Los importes no pueden ser negativos.");
        }
        // Un documento sin base gravada no genera crédito fiscal. Colarlo
        // significaria descontar un IVA que nadie pagó.
        if (compra.getMontoGravado().signum() == 0 && compra.getCreditoFiscal().signum() > 0) {
            throw new IllegalArgumentException(
                    "Una compra exenta no genera crédito fiscal. Revisa los importes.");
        }
        if (compra.getId() == null
                && compraRepository.existsByProveedorNitAndNumeroDocumento(
                        compra.getProveedorNit(), compra.getNumeroDocumento())) {
            throw new IllegalStateException("Ese documento ya está registrado para el mismo proveedor.");
        }
    }

    private static BigDecimal decimal(Object valor) {
        if (valor == null) return BigDecimal.ZERO;
        if (valor instanceof BigDecimal b) return b;
        return new BigDecimal(valor.toString());
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String etiquetaMes(YearMonth ym) {
        String nombre = ym.getMonth().getDisplayName(
                java.time.format.TextStyle.SHORT, Locale.forLanguageTag("es-SV"));
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1).replace(".", "");
    }
}
