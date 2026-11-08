package com.ayudartrazable.api.comun.errores;

import java.util.List;

public record ApiError(String codigo, String mensaje, List<CampoError> campos) {

    public record CampoError(String campo, String mensaje) {
    }

    public static ApiError de(String codigo, String mensaje) {
        return new ApiError(codigo, mensaje, List.of());
    }
}
