package com.fiscore.core.dte;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convierte un importe a su expresión en letras, como exige el campo
 * {@code totalLetras} del DTE.
 *
 * El formato es el habitual en los documentos tributarios salvadoreños: la
 * parte entera en palabras y los centavos como fracción sobre cien, todo en
 * mayúsculas. Por ejemplo, 515.00 → "QUINIENTOS QUINCE 00/100".
 *
 * El español tiene suficientes irregularidades para que esto no sea un bucle
 * trivial: "veintiuno" se escribe junto pero "treinta y uno" separado, 100 es
 * "cien" y 101 "ciento uno", y el millón lleva "un" y no "uno".
 */
public final class MontoEnLetras {

    private MontoEnLetras() {
    }

    private static final String[] UNIDADES = {
            "", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
            "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE",
            "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS",
            "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    };

    private static final String[] DECENAS = {
            "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA"
    };

    private static final String[] CENTENAS = {
            "", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS",
            "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS"
    };

    /** Tope de la parte entera; por encima el DTE no tiene sentido práctico. */
    private static final long MAXIMO = 999_999_999L;

    /**
     * @param monto importe a expresar; se redondea a dos decimales
     * @return por ejemplo "DOSCIENTOS VEINTISÉIS 00/100"
     */
    public static String de(BigDecimal monto) {
        if (monto == null) {
            monto = BigDecimal.ZERO;
        }
        BigDecimal redondeado = monto.setScale(2, RoundingMode.HALF_UP).abs();

        long entero = redondeado.longValue();
        int centavos = redondeado.subtract(new BigDecimal(entero))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        if (entero > MAXIMO) {
            throw new IllegalArgumentException("Importe fuera de rango para expresar en letras: " + monto);
        }

        String palabras = entero == 0 ? "CERO" : enLetras(entero);
        String signo = monto.signum() < 0 ? "MENOS " : "";

        return signo + palabras + " " + String.format("%02d", centavos) + "/100";
    }

    private static String enLetras(long n) {
        if (n < 1_000_000) {
            return hastaMillon(n);
        }
        long millones = n / 1_000_000;
        long resto = n % 1_000_000;

        // "UN MILLÓN", no "UNO MILLÓN"
        String cabeza = millones == 1 ? "UN MILLÓN" : hastaMillon(millones) + " MILLONES";
        return resto == 0 ? cabeza : cabeza + " " + hastaMillon(resto);
    }

    private static String hastaMillon(long n) {
        if (n < 1000) {
            return hastaMil(n);
        }
        long miles = n / 1000;
        long resto = n % 1000;

        // "MIL" a secas, no "UNO MIL"
        String cabeza = miles == 1 ? "MIL" : hastaMil(miles) + " MIL";
        return resto == 0 ? cabeza : cabeza + " " + hastaMil(resto);
    }

    private static String hastaMil(long n) {
        if (n == 0) {
            return "";
        }
        if (n == 100) {
            return "CIEN";
        }
        StringBuilder sb = new StringBuilder();

        int centenas = (int) (n / 100);
        int resto = (int) (n % 100);

        if (centenas > 0) {
            sb.append(CENTENAS[centenas]);
        }
        if (resto > 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(hastaCien(resto));
        }
        return sb.toString();
    }

    private static String hastaCien(int n) {
        if (n < 30) {
            return UNIDADES[n];
        }
        int decena = n / 10;
        int unidad = n % 10;

        // A partir de treinta la conjunción se escribe: "TREINTA Y UNO"
        return unidad == 0 ? DECENAS[decena] : DECENAS[decena] + " Y " + UNIDADES[unidad];
    }
}
