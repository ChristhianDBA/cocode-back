package com.ayudartrazable.api.integraciones.mensajeria;

import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvioCodigoSimulado implements EnvioCodigo {

    private static final Logger log = LoggerFactory.getLogger(EnvioCodigoSimulado.class);

    public record Envio(String codigo, Canal canal) {
    }

    private final Map<String, Envio> enviados = new ConcurrentHashMap<>();
    private final AtomicBoolean fallar = new AtomicBoolean();

    @Override
    public void enviar(String celular, String codigo, Canal canal, int minutosDeVigencia) {
        if (fallar.getAndSet(false)) {
            throw new ExcepcionServicioNoDisponible("No pudimos enviar el código en este momento. Intenta más tarde");
        }
        String normalizado = ValidadorCelular.normalizarCelularPeru(celular);
        String e164 = ValidadorCelular.formatoE164(normalizado);
        enviados.put(normalizado, new Envio(codigo, canal));
        log.info("[OTP {}] Código enviado a {}: {} (vigencia {} min)", canal, e164, codigo, minutosDeVigencia);
    }

    private final Map<String, java.util.List<String>> avisos = new ConcurrentHashMap<>();

    @Override
    public void avisar(String celular, String mensaje) {
        if (fallar.getAndSet(false)) {
            throw new ExcepcionServicioNoDisponible("No pudimos enviar el aviso en este momento. Intenta más tarde");
        }
        String normalizado = ValidadorCelular.normalizarCelularPeru(celular);
        avisos.computeIfAbsent(normalizado, c -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(mensaje);
    }

    public java.util.List<String> avisosA(String celular) {
        return java.util.List.copyOf(avisos.getOrDefault(celular, java.util.List.of()));
    }

    public void fallarProximo() {
        fallar.set(true);
    }

    public Optional<Envio> ultimo(String celular) {
        return Optional.ofNullable(enviados.get(celular));
    }

    public void limpiar() {
        enviados.clear();
        avisos.clear();
        fallar.set(false);
    }
}
