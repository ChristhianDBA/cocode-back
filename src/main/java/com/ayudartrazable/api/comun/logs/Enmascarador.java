package com.ayudartrazable.api.comun.logs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Enmascarador {

    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern TOKEN_OPACO = Pattern.compile("(?<![\\w-])[A-Za-z0-9_-]{32,}(?![\\w(-])");
    private static final Pattern CODIGO = Pattern.compile("(?i)(\"?(?:codigo|code|otp)\"?\\s*[:=]\\s*\"?)\\d{4,6}");
    private static final Pattern CELULAR = Pattern.compile("(?<!\\d)(?:\\+?51)?9\\d{6}(\\d{2})(?!\\d)");
    private static final Pattern DNI = Pattern.compile("(?<!\\d)\\d{6}(\\d{2})(?!\\d)");
    private static final Pattern COORDENADA = Pattern.compile("(-?\\d{1,3}\\.\\d{2})\\d{2,}");

    private Enmascarador() {
    }

    public static String enmascarar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        String resultado = JWT.matcher(texto).replaceAll("[TOKEN]");
        resultado = tokensOpacos(resultado);
        resultado = CODIGO.matcher(resultado).replaceAll("$1****");
        resultado = CELULAR.matcher(resultado).replaceAll("*******$1");
        resultado = DNI.matcher(resultado).replaceAll("******$1");
        resultado = COORDENADA.matcher(resultado).replaceAll("$1***");
        return resultado;
    }

    private static String tokensOpacos(String texto) {
        Matcher m = TOKEN_OPACO.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String candidato = m.group();
            String reemplazo = UUID.matcher(candidato).matches() ? candidato : "[TOKEN]";
            m.appendReplacement(sb, Matcher.quoteReplacement(reemplazo));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
