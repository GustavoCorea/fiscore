package com.fiscore.core.services;

import com.fiscore.core.models.Compra;
import com.fiscore.core.repositories.CompraRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Libro de compras y liquidación de IVA")
class LibroComprasTest {

    private static final int ANIO = 2026;

    @Autowired private CompraService compraService;
    @Autowired private CompraRepository compraRepository;

    @BeforeEach
    void limpiar() {
        compraRepository.deleteAll();
    }

    private Compra compra(String nit, String documento, String gravado, String exento,
                          String credito, int mes, String tipoOperacion) {
        Compra c = new Compra();
        c.setFecha(LocalDate.of(ANIO, mes, 15));
        c.setProveedorNombre("Proveedor " + nit);
        c.setProveedorNit(nit);
        c.setProveedorNrc("111111-1");
        c.setNumeroDocumento(documento);
        c.setTipoOperacion(tipoOperacion);
        c.setMontoGravado(new BigDecimal(gravado));
        c.setMontoExento(new BigDecimal(exento));
        c.setCreditoFiscal(new BigDecimal(credito));
        return c;
    }

    @Test
    @DisplayName("El total se calcula, no se teclea")
    void elTotalSeCalcula() {
        Compra guardada = compraService.guardar(
                compra("0614-111111-001-1", "A-001", "100.00", "20.00", "13.00", 3, "INTERNA"));

        assertThat(guardada.getTotal()).isEqualByComparingTo("133.00");
    }

    @Test
    @DisplayName("El crédito sugerido sale de la tasa configurada")
    void creditoSugerido() {
        assertThat(compraService.creditoFiscalSugerido(new BigDecimal("200.00")))
                .isEqualByComparingTo("26.00");
        assertThat(compraService.creditoFiscalSugerido(BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("No se admite el mismo documento dos veces del mismo proveedor")
    void sinDuplicados() {
        compraService.guardar(compra("0614-111111-001-1", "A-001", "100.00", "0", "13.00", 3, "INTERNA"));

        assertThatThrownBy(() -> compraService.guardar(
                compra("0614-111111-001-1", "A-001", "100.00", "0", "13.00", 4, "INTERNA")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya está registrado");
    }

    @Test
    @DisplayName("Una compra exenta no puede traer crédito fiscal")
    void exentaSinCredito() {
        assertThatThrownBy(() -> compraService.guardar(
                compra("0614-222222-002-2", "B-002", "0", "500.00", "65.00", 3, "INTERNA")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no genera crédito fiscal");
    }

    @Test
    @DisplayName("El libro separa las compras internas de las importaciones")
    void separaImportaciones() {
        compraService.guardar(compra("0614-111111-001-1", "A-001", "100.00", "0", "13.00", 3, "INTERNA"));
        compraService.guardar(compra("0614-333333-003-3", "IMP-1", "400.00", "0", "52.00", 3, "IMPORTACION"));

        Map<String, Object> marzo = compraService.getLibroCompras(ANIO).get(2);

        assertThat((BigDecimal) marzo.get("gravadoInterno")).isEqualByComparingTo("100.00");
        assertThat((BigDecimal) marzo.get("gravadoImportacion")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) marzo.get("creditoFiscal")).isEqualByComparingTo("65.00");
        assertThat(marzo.get("documentos")).isEqualTo(2L);
    }

    @Test
    @DisplayName("El libro tiene los doce meses aunque no haya movimiento")
    void doceMesesSiempre() {
        List<Map<String, Object>> libro = compraService.getLibroCompras(ANIO);

        assertThat(libro).hasSize(12);
        assertThat(libro.get(0).get("mes")).isEqualTo("2026-01");
        assertThat((BigDecimal) libro.get(0).get("total")).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Sin compras, el IVA a pagar es todo el débito de las ventas")
    void sinComprasSePagaTodoElDebito() {
        List<Map<String, Object>> liquidacion = compraService.getLiquidacionIva(ANIO);

        assertThat(liquidacion).hasSize(12);
        for (Map<String, Object> mes : liquidacion) {
            assertThat((BigDecimal) mes.get("creditoFiscal")).isEqualByComparingTo("0.00");
            // Sin ventas ni compras en la base de pruebas, todo queda en cero
            assertThat((BigDecimal) mes.get("aPagar"))
                    .isEqualByComparingTo((BigDecimal) mes.get("debitoFiscal"));
        }
    }

    @Test
    @DisplayName("Cuando el crédito supera al débito queda remanente, no un pago negativo")
    void elCreditoSobranteSeArrastra() {
        // Compra grande en enero, sin ventas que la compensen
        compraService.guardar(compra("0614-444444-004-4", "C-001", "1000.00", "0", "130.00", 1, "INTERNA"));

        List<Map<String, Object>> liquidacion = compraService.getLiquidacionIva(ANIO);
        Map<String, Object> enero = liquidacion.get(0);
        Map<String, Object> febrero = liquidacion.get(1);

        assertThat((BigDecimal) enero.get("creditoFiscal")).isEqualByComparingTo("130.00");
        assertThat((BigDecimal) enero.get("aPagar")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) enero.get("remanente")).isEqualByComparingTo("130.00");

        // Y el sobrante viaja al mes siguiente
        assertThat((BigDecimal) febrero.get("remanenteAnterior")).isEqualByComparingTo("130.00");
        assertThat((BigDecimal) febrero.get("remanente")).isEqualByComparingTo("130.00");
    }

    @Test
    @DisplayName("Los proveedores ya usados se ofrecen para no reescribirlos")
    void proveedoresConocidos() {
        compraService.guardar(compra("0614-555555-005-5", "D-001", "50.00", "0", "6.50", 2, "INTERNA"));

        assertThat(compraService.proveedoresConocidos())
                .anyMatch(p -> "0614-555555-005-5".equals(p.get("nit")));
    }

    @Test
    @DisplayName("No se registran compras con fecha futura ni sin proveedor")
    void validacionesBasicas() {
        Compra futura = compra("0614-666666-006-6", "E-001", "10.00", "0", "1.30", 3, "INTERNA");
        futura.setFecha(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> compraService.guardar(futura))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futura");

        Compra sinProveedor = compra("0614-777777-007-7", "F-001", "10.00", "0", "1.30", 3, "INTERNA");
        sinProveedor.setProveedorNombre("  ");
        assertThatThrownBy(() -> compraService.guardar(sinProveedor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proveedor");
    }
}
