package com.ayudartrazable.api.integraciones.registrocivil;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public class RegistroCivilSimulado implements ConsultaRegistroCivil {

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

    @Override
    public Optional<DatosCiviles> consultar(String dni) {
        if (DNI_MENOR.equals(dni)) {
            return Optional.of(new DatosCiviles(DNI_MENOR, "Diego", "Flores", "Chávez",
                    LocalDate.now().minusYears(18).plusDays(1), "M"));
        }
        DatosCiviles encontrado = REGISTRO.get(dni);
        if (encontrado != null) {
            return Optional.of(encontrado);
        }
        // En modo simulado para desarrollo, permitir cualquier DNI peruano de 8 dígitos
        if (dni != null && dni.matches("\\d{8}")) {
            return Optional.of(new DatosCiviles(dni, "Vecino", "Registrado", "Prueba", LocalDate.of(2000, 1, 1), "M"));
        }
        return Optional.empty();
    }
}
