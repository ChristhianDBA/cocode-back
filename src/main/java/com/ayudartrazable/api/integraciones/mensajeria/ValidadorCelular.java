package com.ayudartrazable.api.integraciones.mensajeria;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Utilidad basada en Google libphonenumber para validar, normalizar y formatear
 * números de celular peruanos (+51) para el envío de códigos OTP.
 */
public final class ValidadorCelular {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    private static final String REGION_PERU = "PE";

    private ValidadorCelular() {
    }

    /**
     * Normaliza y valida un número de celular peruano devolviendo los 9 dígitos nacionales (ej. "987654321").
     * Lanza ExcepcionNegocio (400) si el número no es un celular válido en Perú.
     */
    public static String normalizarCelularPeru(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new ExcepcionNegocio(400, "CELULAR_INVALIDO", "Ingresa tu número de celular");
        }
        try {
            PhoneNumber parsed = UTIL.parse(entrada.trim(), REGION_PERU);
            String nacional = String.valueOf(parsed.getNationalNumber());
            boolean validoLib = UTIL.isValidNumberForRegion(parsed, REGION_PERU)
                    && (UTIL.getNumberType(parsed) == PhoneNumberType.MOBILE
                            || UTIL.getNumberType(parsed) == PhoneNumberType.FIXED_LINE_OR_MOBILE);
            boolean patronPeru = nacional.matches("^9\\d{8}$");
            if (!validoLib && !patronPeru) {
                throw new ExcepcionNegocio(400, "CELULAR_INVALIDO",
                        "El celular debe tener 9 dígitos y empezar con 9");
            }
            return nacional;
        } catch (NumberParseException e) {
            String soloDigitos = entrada.replaceAll("\\D", "");
            if (soloDigitos.startsWith("51") && soloDigitos.length() == 11) {
                soloDigitos = soloDigitos.substring(2);
            }
            if (soloDigitos.matches("^9\\d{8}$")) {
                return soloDigitos;
            }
            throw new ExcepcionNegocio(400, "CELULAR_INVALIDO",
                    "El celular debe tener 9 dígitos y empezar con 9");
        }
    }

    /**
     * Formatea el celular en formato E.164 (ej. "+51987654321") usando libphonenumber.
     */
    public static String formatoE164(String celular) {
        String nacional = normalizarCelularPeru(celular);
        try {
            PhoneNumber parsed = UTIL.parse(nacional, REGION_PERU);
            return UTIL.format(parsed, PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return "+51" + nacional;
        }
    }

    /**
     * Formatea el celular en formato internacional legible (ej. "+51 987 654 321").
     */
    public static String formatoInternacional(String celular) {
        String nacional = normalizarCelularPeru(celular);
        try {
            PhoneNumber parsed = UTIL.parse(nacional, REGION_PERU);
            return UTIL.format(parsed, PhoneNumberFormat.INTERNATIONAL);
        } catch (NumberParseException e) {
            return "+51 " + nacional;
        }
    }

    /**
     * Enmascara el celular dejando visibles los últimos 3 dígitos (ej. "******321").
     */
    public static String enmascarar(String celular) {
        String nacional = normalizarCelularPeru(celular);
        return "******" + nacional.substring(nacional.length() - 3);
    }
}
