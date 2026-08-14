package com.fiscore.core.dte;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El campo totalLetras del DTE. Las irregularidades del español son la razón de
 * que esto tenga pruebas propias: cada caso de abajo es una regla distinta, y
 * equivocarse en una sola deja el importe mal escrito en todos los documentos
 * que caigan en ese tramo.
 */
@DisplayName("Importe en letras")
class MontoEnLetrasTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            // Base
            "0.00,        CERO 00/100",
            "1.00,        UNO 00/100",
            "15.00,       QUINCE 00/100",

            // Los adolescentes y los veinte van juntos
            "16.00,       DIECISÉIS 00/100",
            "21.00,       VEINTIUNO 00/100",
            "26.00,       VEINTISÉIS 00/100",

            // A partir de treinta aparece la conjunción
            "30.00,       TREINTA 00/100",
            "31.00,       TREINTA Y UNO 00/100",
            "99.00,       NOVENTA Y NUEVE 00/100",

            // Cien es 'cien'; con resto pasa a 'ciento'
            "100.00,      CIEN 00/100",
            "101.00,      CIENTO UNO 00/100",
            "115.00,      CIENTO QUINCE 00/100",
            "200.00,      DOSCIENTOS 00/100",
            "500.00,      QUINIENTOS 00/100",
            "900.00,      NOVECIENTOS 00/100",

            // Mil va sin 'uno' delante
            "1000.00,     MIL 00/100",
            "1001.00,     MIL UNO 00/100",
            "2000.00,     DOS MIL 00/100",
            "15000.00,    QUINCE MIL 00/100",
            "100000.00,   CIEN MIL 00/100",

            // El millón lleva 'un', no 'uno'
            "1000000.00,  UN MILLÓN 00/100",
            "2000000.00,  DOS MILLONES 00/100",
            "1000500.00,  UN MILLÓN QUINIENTOS 00/100",

            // Los importes que emite hoy la aplicación
            "226.00,      DOSCIENTOS VEINTISÉIS 00/100",
            "515.00,      QUINIENTOS QUINCE 00/100",
            "90.40,       NOVENTA 40/100"
    })
    void convierte(String importe, String esperado) {
        assertThat(MontoEnLetras.de(new BigDecimal(importe))).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Los centavos siempre van con dos dígitos")
    void centavosConDosDigitos() {
        assertThat(MontoEnLetras.de(new BigDecimal("10.05"))).isEqualTo("DIEZ 05/100");
        assertThat(MontoEnLetras.de(new BigDecimal("10.50"))).isEqualTo("DIEZ 50/100");
        assertThat(MontoEnLetras.de(new BigDecimal("0.99"))).isEqualTo("CERO 99/100");
    }

    @Test
    @DisplayName("Un tercer decimal se redondea antes de escribirlo")
    void redondeaAntesDeEscribir() {
        assertThat(MontoEnLetras.de(new BigDecimal("10.005"))).isEqualTo("DIEZ 01/100");
        assertThat(MontoEnLetras.de(new BigDecimal("10.994"))).isEqualTo("DIEZ 99/100");
        // El redondeo arrastra al entero
        assertThat(MontoEnLetras.de(new BigDecimal("10.999"))).isEqualTo("ONCE 00/100");
    }

    @Test
    @DisplayName("Un importe nulo se trata como cero, no revienta")
    void nuloEsCero() {
        assertThat(MontoEnLetras.de(null)).isEqualTo("CERO 00/100");
    }

    @Test
    @DisplayName("Por encima del rango se avisa en lugar de escribir algo absurdo")
    void fueraDeRango() {
        assertThatThrownBy(() -> MontoEnLetras.de(new BigDecimal("1000000000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fuera de rango");
    }
}
