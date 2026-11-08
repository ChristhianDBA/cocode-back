package com.ayudartrazable.api.comun.errores;

import java.sql.SQLException;
import java.util.List;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);
    private static final String PREFIJO_NEGOCIO = "AT";
    static final String MENSAJE_INTERNO = "Ocurrió un error inesperado. Intenta más tarde";

    @ExceptionHandler(ExcepcionNegocio.class)
    ResponseEntity<ApiError> negocio(ExcepcionNegocio ex) {
        return ResponseEntity.status(ex.estado()).body(ApiError.de(ex.codigo(), ex.getMessage()));
    }

    @ExceptionHandler(ExcepcionServicioNoDisponible.class)
    ResponseEntity<ApiError> servicioNoDisponible(ExcepcionServicioNoDisponible ex) {
        LOG.warn("Servicio externo no disponible: {}", ex.getCause() == null ? "-" : ex.getCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.de("SERVICIO_NO_DISPONIBLE", ex.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> baseDeDatos(DataAccessException ex) {
        ServerErrorMessage error = errorDelServidor(ex);
        if (error != null && esErrorDeNegocio(error.getSQLState())) {
            int estado = estadoHttp(error.getSQLState());
            String codigo = error.getDetail() == null ? "ERROR_NEGOCIO" : error.getDetail();
            return ResponseEntity.status(estado).body(ApiError.de(codigo, error.getMessage()));
        }
        LOG.error("Error de base de datos no controlado: sqlstate={}", error == null ? "-" : error.getSQLState(), ex);
        return interno();
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> sinPermiso(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.de("SIN_PERMISO", "No tienes permiso para realizar esta acción"));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> noAutenticado(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.de("NO_AUTENTICADO", "Tu sesión no es válida. Inicia sesión nuevamente"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> inesperado(Exception ex) {
        LOG.error("Error inesperado", ex);
        return interno();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.CampoError> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.CampoError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDACION", "Revisa los datos ingresados", campos));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.de("SOLICITUD_INVALIDA", "El formato de la solicitud no es válido"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ApiError error = switch (statusCode.value()) {
            case 404 -> ApiError.de("NO_ENCONTRADO", "El recurso solicitado no existe");
            case 405 -> ApiError.de("METODO_NO_PERMITIDO", "La operación no está permitida en este recurso");
            case 415 -> ApiError.de("SOLICITUD_INVALIDA", "El formato de la solicitud no es válido");
            default -> statusCode.is4xxClientError()
                    ? ApiError.de("SOLICITUD_INVALIDA", "La solicitud no es válida")
                    : ApiError.de("ERROR_INTERNO", MENSAJE_INTERNO);
        };
        return ResponseEntity.status(statusCode).headers(headers).body(error);
    }

    private static ResponseEntity<ApiError> interno() {
        return ResponseEntity.internalServerError().body(ApiError.de("ERROR_INTERNO", MENSAJE_INTERNO));
    }

    private static ServerErrorMessage errorDelServidor(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && psql.getServerErrorMessage() != null) {
                return psql.getServerErrorMessage();
            }
            if (t instanceof SQLException sql && sql.getNextException() instanceof PSQLException siguiente
                    && siguiente.getServerErrorMessage() != null) {
                return siguiente.getServerErrorMessage();
            }
        }
        return null;
    }

    private static boolean esErrorDeNegocio(String sqlState) {
        return sqlState != null && sqlState.length() == 5 && sqlState.startsWith(PREFIJO_NEGOCIO)
                && sqlState.substring(2).chars().allMatch(Character::isDigit);
    }

    private static int estadoHttp(String sqlState) {
        int estado = Integer.parseInt(sqlState.substring(2));
        return estado >= 400 && estado <= 499 ? estado : 400;
    }
}
