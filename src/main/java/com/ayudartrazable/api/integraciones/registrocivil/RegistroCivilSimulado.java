package com.ayudartrazable.api.integraciones.registrocivil;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

public class RegistroCivilSimulado implements ConsultaRegistroCivil {

    private static final Logger LOG = LoggerFactory.getLogger(RegistroCivilSimulado.class);

    public static final String DNI_ANA = "45678912";
    public static final String DNI_JUAN = "71234567";
    public static final String DNI_ROSA = "40112233";
    public static final String DNI_LUIS = "42223344";
    public static final String DNI_CARMEN = "43334455";
    public static final String DNI_MENOR = "76543210";
    public static final String DNI_ALEX = "72407570";

    private static final Map<String, DatosCiviles> REGISTRO = Map.of(
            DNI_ANA, new DatosCiviles(DNI_ANA, "Ana María", "Quispe", "Ramos", LocalDate.of(1992, 3, 15), "F"),
            DNI_JUAN, new DatosCiviles(DNI_JUAN, "Juan Carlos", "Pérez", "Soto", LocalDate.of(1985, 7, 2), "M"),
            DNI_ROSA, new DatosCiviles(DNI_ROSA, "Rosa", "Huamán", "Torres", LocalDate.of(1978, 11, 20), "F"),
            DNI_LUIS, new DatosCiviles(DNI_LUIS, "Luis", "Mendoza", "Rojas", LocalDate.of(1990, 1, 9), "M"),
            DNI_CARMEN, new DatosCiviles(DNI_CARMEN, "Carmen", "Vargas", "Lima", LocalDate.of(1969, 5, 30), "F"),
            DNI_ALEX, new DatosCiviles(DNI_ALEX, "Juan Alexander", "Miranda", "Quispe", LocalDate.of(2001, 8, 22), "M"));

    private final Map<String, DatosCiviles> cacheReniec = new ConcurrentHashMap<>(REGISTRO);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ReniecPublico(
            @JsonProperty("nombres") String nombres,
            @JsonProperty("apellidoPaterno") String apellidoPaterno,
            @JsonProperty("apellidoMaterno") String apellidoMaterno) {
    }

    private final RestClient clienteHttp = RestClient.create();

    @Override
    public Optional<DatosCiviles> consultar(String dni) {
        if (DNI_MENOR.equals(dni)) {
            return Optional.of(new DatosCiviles(DNI_MENOR, "Diego", "Flores", "Chávez",
                    LocalDate.now().minusYears(18).plusDays(1), "M"));
        }
        DatosCiviles encontrado = cacheReniec.get(dni);
        if (encontrado != null) {
            return Optional.of(encontrado);
        }

        // Si el DNI tiene 8 dígitos, consultar API pública de RENIEC en tiempo real
        if (dni != null && dni.matches("\\d{8}")) {
            try {
                ReniecPublico r = clienteHttp.get()
                        .uri("https://api.apis.net.pe/v1/dni?numero={dni}", dni)
                        .retrieve()
                        .body(ReniecPublico.class);

                if (r != null && r.nombres() != null && !r.nombres().isBlank()) {
                    LOG.info("DNI {} consultado exitosamente con RENIEC online: {} {}", dni, r.nombres(), r.apellidoPaterno());
                    DatosCiviles datos = new DatosCiviles(
                            dni,
                            capitalizar(r.nombres()),
                            capitalizar(r.apellidoPaterno()),
                            capitalizar(r.apellidoMaterno()),
                            LocalDate.of(1995, 1, 1),
                            "M");
                    cacheReniec.put(dni, datos);
                    return Optional.of(datos);
                }
            } catch (Exception e) {
                LOG.warn("Consulta RENIEC online para DNI {} no disponible ({}), usando datos base.", dni, e.getMessage());
            }

            // Fallback seguro para pruebas
            return Optional.of(new DatosCiviles(dni, "Vecino", "Registrado", "Prueba", LocalDate.of(2000, 1, 1), "M"));
        }
        return Optional.empty();
    }

    private static String capitalizar(String str) {
        if (str == null || str.isBlank()) return "";
        String[] palabras = str.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palabras) {
            if (!p.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) sb.append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }
}
