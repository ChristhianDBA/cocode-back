package com.ayudartrazable.api.integraciones.mensajeria;

import com.ayudartrazable.api.comun.errores.ExcepcionNegocio;
import com.ayudartrazable.api.comun.errores.ExcepcionServicioNoDisponible;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class EnvioCodigoTwilio implements EnvioCodigo {

    private final RestClient cliente;
    private final String cuenta;
    private final String remitenteSms;
    private final String remitenteWhatsapp;

    public EnvioCodigoTwilio(RestClient.Builder builder, String cuenta, String clave, String remitenteSms,
            String remitenteWhatsapp) {
        String basica = Base64.getEncoder().encodeToString((cuenta + ":" + clave).getBytes(StandardCharsets.UTF_8));
        this.cliente = builder.defaultHeader("Authorization", "Basic " + basica).build();
        this.cuenta = cuenta;
        this.remitenteSms = remitenteSms;
        this.remitenteWhatsapp = remitenteWhatsapp;
    }

    @Override
    public void enviar(String celular, String codigo, Canal canal, int minutosDeVigencia) {
        String prefijo = canal == Canal.WHATSAPP ? "whatsapp:" : "";
        publicar(prefijo + "+51" + celular, prefijo + (canal == Canal.WHATSAPP ? remitenteWhatsapp : remitenteSms),
                "Tu código de Ayudar Trazable es " + codigo + ". Vence en " + minutosDeVigencia + " minutos.");
    }

    @Override
    public void avisar(String celular, String mensaje) {
        publicar("+51" + celular, remitenteSms, mensaje);
    }

    private void publicar(String destino, String remitente, String cuerpo) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("To", destino);
        formulario.add("From", remitente);
        formulario.add("Body", cuerpo);
        try {
            cliente.post().uri("/2010-04-01/Accounts/{cuenta}/Messages.json", cuenta)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ExcepcionNegocio(400, "CELULAR_RECHAZADO",
                    "No pudimos enviar el código a ese número. Revisa el celular ingresado");
        } catch (RestClientException e) {
            throw new ExcepcionServicioNoDisponible("No pudimos enviar el código en este momento. Intenta más tarde", e);
        }
    }
}
