package com.fiscore.core.services;

import com.fiscore.core.config.ParametroDte;
import com.fiscore.core.models.Cliente;
import com.fiscore.core.models.Contrato;
import com.fiscore.core.models.ContratoServicio;
import com.fiscore.core.models.Factura;
import com.fiscore.core.models.Servicio;
import com.fiscore.core.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La configuración de emisión DTE debe ser editable y tener efecto real
 * sobre los documentos que se generan a partir de ese momento.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfiguracionDteTest {

    @Autowired private ConfiguracionDteService configuracion;
    @Autowired private FacturacionService facturacionService;
    @Autowired private ContratoService contratoService;

    @Autowired private DteParametroRepository parametroRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private ServicioRepository servicioRepository;
    @Autowired private ContratoRepository contratoRepository;
    @Autowired private FacturaRepository facturaRepository;

    private Cliente contribuyente;
    private Servicio servicio;

    @BeforeEach
    void preparar() {
        facturaRepository.deleteAll();
        contratoRepository.deleteAll();
        clienteRepository.deleteAll();
        servicioRepository.deleteAll();

        // Devolver todos los parámetros a fábrica entre pruebas
        for (ParametroDte definicion : ParametroDte.values()) {
            configuracion.restaurarPorDefecto(definicion.getClave());
        }

        contribuyente = new Cliente();
        contribuyente.setNombre("Inversiones ACME, S.A. de C.V.");
        contribuyente.setNit("0614-010101-001-0");
        contribuyente.setNrc("123456-7");
        contribuyente.setEstado("ACTIVO");
        contribuyente = clienteRepository.save(contribuyente);

        servicio = new Servicio();
        servicio.setNombre("Contabilidad mensual");
        servicio.setCategoria("CONTABILIDAD");
        servicio.setPeriodicidad("MENSUAL");
        servicio.setPrecio(new BigDecimal("250.00"));
        servicio = servicioRepository.save(servicio);
    }

    private Contrato contrato(String monto) {
        Contrato c = new Contrato();
        c.setCliente(contribuyente);
        c.setTipoFacturacion("RECURRENTE");
        c.setHonorariosPactados(new BigDecimal(monto));
        c.setFechaInicio(LocalDate.now().minusMonths(1));
        c.setEstado("ACTIVO");
        ContratoServicio cs = new ContratoServicio();
        cs.setServicio(servicio);
        cs.setPrecioAcordado(new BigDecimal(monto));
        c.getServicios().add(cs);
        return contratoService.save(c);
    }

    // =================================================================

    @Test
    @DisplayName("Todos los parámetros del catálogo quedan registrados en DTE_PARAMETRO")
    void catalogoCompletoEnBaseDeDatos() {
        for (ParametroDte definicion : ParametroDte.values()) {
            assertThat(parametroRepository.existsByDtpaNombre(definicion.getClave()))
                    .as("falta el parámetro %s", definicion.getClave())
                    .isTrue();
        }
        assertThat(parametroRepository.count()).isGreaterThanOrEqualTo(ParametroDte.values().length);
    }

    @Test
    @DisplayName("El formulario agrupa los parámetros y nunca expone los secretos")
    void formularioOcultaSecretos() {
        configuracion.guardar(Map.of(ParametroDte.MH_CLAVE_API.getClave(), "clave-super-secreta"));

        var formulario = configuracion.getFormulario();
        assertThat(formulario.keySet()).containsExactly(ParametroDte.Grupo.values());

        var campoSecreto = formulario.get(ParametroDte.Grupo.HACIENDA).stream()
                .filter(c -> ParametroDte.MH_CLAVE_API.getClave().equals(c.get("clave")))
                .findFirst().orElseThrow();

        assertThat(campoSecreto.get("valor")).isEqualTo("");
        assertThat(campoSecreto.get("configurado")).isEqualTo(true);
        // El valor sí quedó guardado, solo que no se devuelve a la pantalla
        assertThat(configuracion.get(ParametroDte.MH_CLAVE_API)).isEqualTo("clave-super-secreta");
    }

    @Test
    @DisplayName("Un secreto enviado vacío o con el marcador conserva el valor anterior")
    void secretoNoSePisaSinQuerer() {
        configuracion.guardar(Map.of(ParametroDte.MH_CLAVE_API.getClave(), "original"));

        configuracion.guardar(Map.of(ParametroDte.MH_CLAVE_API.getClave(), ""));
        assertThat(configuracion.get(ParametroDte.MH_CLAVE_API)).isEqualTo("original");

        configuracion.guardar(Map.of(
                ParametroDte.MH_CLAVE_API.getClave(), ConfiguracionDteService.SECRETO_SIN_CAMBIOS));
        assertThat(configuracion.get(ParametroDte.MH_CLAVE_API)).isEqualTo("original");

        configuracion.guardar(Map.of(ParametroDte.MH_CLAVE_API.getClave(), "nueva"));
        assertThat(configuracion.get(ParametroDte.MH_CLAVE_API)).isEqualTo("nueva");
    }

    @Test
    @DisplayName("Cambiar la tasa de IVA cambia el cálculo de las facturas nuevas")
    void tasaDeIvaEditable() {
        // Con 13%: 226.00 => base 200.00 + IVA 26.00
        Factura conTasaOriginal = facturacionService.generarDesdeContrato(
                contrato("226.00"), "Enero 2026", "CONTADO", null);
        assertThat(conTasaOriginal.getSubtotalGravado()).isEqualByComparingTo("200.00");
        assertThat(conTasaOriginal.getIvaPercibido()).isEqualByComparingTo("26.00");

        // Con 10%: 220.00 => base 200.00 + IVA 20.00
        configuracion.guardar(Map.of(ParametroDte.IVA_TASA.getClave(), "0.10"));
        Factura conTasaNueva = facturacionService.generarDesdeContrato(
                contrato("220.00"), "Febrero 2026", "CONTADO", null);

        assertThat(conTasaNueva.getSubtotalGravado()).isEqualByComparingTo("200.00");
        assertThat(conTasaNueva.getIvaPercibido()).isEqualByComparingTo("20.00");
        assertThat(conTasaNueva.getMontoTotal()).isEqualByComparingTo("220.00");
    }

    @Test
    @DisplayName("El número de control usa el establecimiento y punto de venta configurados")
    void numeroDeControlConfigurable() {
        configuracion.guardar(Map.of(
                ParametroDte.ESTABLECIMIENTO_CODIGO.getClave(), "S042",
                ParametroDte.PUNTO_VENTA_CODIGO.getClave(), "V007"));

        Factura factura = facturacionService.generarDesdeContrato(
                contrato("226.00"), "Enero 2026", "CONTADO", null);

        assertThat(factura.getNumeroControl()).startsWith("DTE-03-S042V007-");
        assertThat(factura.getNumeroControl()).matches("DTE-03-S042V007-\\d{15}");
    }

    @Test
    @DisplayName("Los prefijos de los correlativos internos son configurables")
    void prefijosConfigurables() {
        configuracion.guardar(Map.of(ParametroDte.PREFIJO_CCF.getClave(), "CRF"));

        Factura factura = facturacionService.generarDesdeContrato(
                contrato("226.00"), "Enero 2026", "CONTADO", null);

        assertThat(factura.getNumeroFactura()).startsWith("CRF-");
    }

    @Test
    @DisplayName("El plazo de crédito por defecto sale de la configuración")
    void plazoCreditoPorDefectoConfigurable() {
        configuracion.guardar(Map.of(ParametroDte.PLAZO_CREDITO_DEFECTO.getClave(), "45"));

        // Sin plazo explícito, debe tomar el configurado
        Factura factura = facturacionService.generarDesdeContrato(
                contrato("226.00"), "Enero 2026", "CREDITO", null);

        assertThat(factura.getPlazoCredito()).isEqualTo(45);
        assertThat(factura.getFechaVencimiento()).isEqualTo(LocalDate.now().plusDays(45));
    }

    // ---------------- Validación ----------------

    @Test
    @DisplayName("Un obligatorio vacío se rechaza con el nombre del campo")
    void obligatorioNoPuedeQuedarVacio() {
        assertThatThrownBy(() -> configuracion.guardar(Map.of(ParametroDte.EMISOR_NIT.getClave(), "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NIT")
                .hasMessageContaining("obligatorio");
    }

    @Test
    @DisplayName("Una tasa expresada como porcentaje se rechaza: debe ser proporción")
    void tasaComoPorcentajeSeRechaza() {
        assertThatThrownBy(() -> configuracion.guardar(Map.of(ParametroDte.IVA_TASA.getClave(), "13")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proporción");

        // Y el valor anterior no se tocó
        assertThat(configuracion.getDecimal(ParametroDte.IVA_TASA)).isEqualByComparingTo("0.13");
    }

    @Test
    @DisplayName("Los tipos numéricos y las opciones se validan")
    void validacionDeTipos() {
        assertThatThrownBy(() -> configuracion.guardar(
                Map.of(ParametroDte.PLAZO_CREDITO_DEFECTO.getClave(), "treinta")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entero");

        assertThatThrownBy(() -> configuracion.guardar(
                Map.of(ParametroDte.RETENCION_MONTO_MINIMO.getClave(), "-5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");

        assertThatThrownBy(() -> configuracion.guardar(
                Map.of(ParametroDte.MH_AMBIENTE.getClave(), "99")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no permitido");
    }

    @Test
    @DisplayName("Un valor demasiado largo para la columna se rechaza antes de guardar")
    void valorDemasiadoLargo() {
        String largo = "x".repeat(501);
        assertThatThrownBy(() -> configuracion.guardar(Map.of(ParametroDte.EMISOR_DIRECCION.getClave(), largo)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Las claves desconocidas se ignoran sin romper el guardado")
    void clavesDesconocidasSeIgnoran() {
        int cambios = configuracion.guardar(Map.of(
                "PARAMETRO_QUE_NO_EXISTE", "valor",
                ParametroDte.EMISOR_TELEFONO.getClave(), "+503 7777-8888"));

        assertThat(cambios).isEqualTo(1);
        assertThat(configuracion.get(ParametroDte.EMISOR_TELEFONO)).isEqualTo("+503 7777-8888");
    }

    @Test
    @DisplayName("Restaurar devuelve el parámetro a su valor de fábrica")
    void restaurarPorDefecto() {
        configuracion.guardar(Map.of(ParametroDte.EMISOR_NOMBRE.getClave(), "Otro nombre"));
        assertThat(configuracion.get(ParametroDte.EMISOR_NOMBRE)).isEqualTo("Otro nombre");

        configuracion.restaurarPorDefecto(ParametroDte.EMISOR_NOMBRE.getClave());

        assertThat(configuracion.get(ParametroDte.EMISOR_NOMBRE))
                .isEqualTo(ParametroDte.EMISOR_NOMBRE.getValorPorDefecto());
    }

    @Test
    @DisplayName("El ambiente y el estado de la conexión se reportan correctamente")
    void estadoDeLaConexion() {
        assertThat(configuracion.getAmbienteDescripcion()).isEqualTo("Pruebas");
        assertThat(configuracion.isConexionConfigurada()).isFalse();

        configuracion.guardar(Map.of(
                ParametroDte.MH_AMBIENTE.getClave(), "01",
                ParametroDte.MH_USUARIO.getClave(), "06140101010010",
                ParametroDte.MH_CLAVE_API.getClave(), "secreto"));

        assertThat(configuracion.getAmbienteDescripcion()).isEqualTo("Producción");
        assertThat(configuracion.isConexionConfigurada()).isTrue();
    }

    @Test
    @DisplayName("Ninguna descripción supera el largo de la columna DTPA_DESCRIPCION")
    void descripcionesCabenEnLaColumna() {
        for (ParametroDte definicion : ParametroDte.values()) {
            assertThat(definicion.getEtiqueta().length())
                    .as("etiqueta demasiado larga en %s", definicion.getClave())
                    .isLessThanOrEqualTo(70);
            assertThat(definicion.getClave().length())
                    .as("clave demasiado larga en %s", definicion.getClave())
                    .isLessThanOrEqualTo(60);
            assertThat(definicion.getValorPorDefecto().length())
                    .as("valor por defecto demasiado largo en %s", definicion.getClave())
                    .isLessThanOrEqualTo(500);
        }
    }

    @Test
    @DisplayName("Cada parámetro de selección declara sus opciones y su defecto es válido")
    void seleccionesCoherentes() {
        for (ParametroDte definicion : ParametroDte.values()) {
            if (definicion.getTipo() != ParametroDte.Tipo.SELECCION) continue;

            List<Map<String, String>> opciones = definicion.getOpciones();
            assertThat(opciones).as("%s sin opciones", definicion.getClave()).isNotEmpty();
            assertThat(opciones).as("%s: el defecto no está entre las opciones", definicion.getClave())
                    .anyMatch(o -> o.get("valor").equals(definicion.getValorPorDefecto()));
        }
    }
}
