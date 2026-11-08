package com.ayudartrazable.api.vecinos;

import com.ayudartrazable.api.vecinos.dto.ResenasDto.PerfilVecinoPublico;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.ResenaItem;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.RespuestaVistoBueno;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudOcupacion;
import com.ayudartrazable.api.vecinos.dto.ResenasDto.SolicitudResena;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class ResenasServicio {

    private final Map<String, PerfilVecinoPublico> perfiles = new ConcurrentHashMap<>();
    private final Map<String, List<ResenaItem>> resenasPorVecino = new ConcurrentHashMap<>();
    private final Map<String, Boolean> misVistosBuenos = new ConcurrentHashMap<>();

    public ResenasServicio() {
        // Rosa Quispe (Figma Screen 1)
        perfiles.put("1", new PerfilVecinoPublico(
                "1", "RQ", "Rosa Quispe", "Gasfitería", 12, "A 180 m", "Mz. D, Los Pinos",
                42, 4.9, 18, "vecino desde marzo 2026", false
        ));

        // Semilla de reseñas para Rosa Quispe
        List<ResenaItem> resenasRosa = new CopyOnWriteArrayList<>();
        resenasRosa.add(new ResenaItem(
                "r1", "Carmen M.", "CM", "Mz. B · a 2 cuadras", 5, true,
                List.of("Puntual", "Precio justo", "Trabajo limpio"),
                "Vino en menos de media hora por una fuga en la cocina. Cobró lo que dijo y dejó todo limpio.",
                "Hace 3 días", true
        ));
        resenasRosa.add(new ResenaItem(
                "r2", "Julio R.", "JR", "Mz. A · a 1 cuadra", 5, true,
                List.of("Honesta", "Explica bien"),
                "Cambió una llave de paso rota el domingo por la tarde. Muy atenta y honesta con los materiales.",
                "Hace 2 semanas", true
        ));
        resenasPorVecino.put("1", resenasRosa);

        // Miguel Huamán
        perfiles.put("2", new PerfilVecinoPublico(
                "2", "MH", "Miguel Huamán", "Electricista", 8, "A 350 m", "Mz. B, Los Pinos",
                31, 4.8, 12, "vecino desde enero 2026", false
        ));
    }

    public PerfilVecinoPublico obtenerPerfil(String vecinoId) {
        PerfilVecinoPublico base = perfiles.get(vecinoId);
        if (base == null) {
            return new PerfilVecinoPublico(
                    vecinoId, "V", "Vecino Verificado", "Servicio general", 5, "Cerca de ti",
                    "Los Pinos", 10, 5.0, 3, "vecino verificado", false
            );
        }
        boolean miVisto = misVistosBuenos.getOrDefault(vecinoId, false);
        return new PerfilVecinoPublico(
                base.id(), base.iniciales(), base.nombre(), base.oficio(), base.aniosOficio(),
                base.distancia(), base.ubicacion(),
                miVisto ? base.vistosBuenos() + 1 : base.vistosBuenos(),
                base.calificacion(), base.resenasCount(), base.fechaRegistro(), miVisto
        );
    }

    public RespuestaVistoBueno alternarVistoBueno(String vecinoId) {
        boolean actual = misVistosBuenos.getOrDefault(vecinoId, false);
        boolean nuevo = !actual;
        misVistosBuenos.put(vecinoId, nuevo);

        PerfilVecinoPublico base = perfiles.get(vecinoId);
        int total = (base != null ? base.vistosBuenos() : 10) + (nuevo ? 1 : 0);
        return new RespuestaVistoBueno(nuevo, total);
    }

    public List<ResenaItem> listarResenas(String vecinoId) {
        return resenasPorVecino.computeIfAbsent(vecinoId, k -> new CopyOnWriteArrayList<>());
    }

    public ResenaItem agregarResena(String vecinoId, SolicitudResena solicitud, String autorNombre) {
        List<ResenaItem> list = resenasPorVecino.computeIfAbsent(vecinoId, k -> new CopyOnWriteArrayList<>());
        String autor = autorNombre != null && !autorNombre.isBlank() ? autorNombre : "Vecino Verificado";
        String iniciales = autor.substring(0, Math.min(2, autor.length())).toUpperCase();

        ResenaItem nueva = new ResenaItem(
                UUID.randomUUID().toString(),
                autor,
                iniciales,
                "Mz. D · a tu manzana",
                solicitud.calificacion(),
                solicitud.recomendo(),
                solicitud.destacados() != null ? solicitud.destacados() : List.of(),
                solicitud.comentario(),
                "Hoy",
                true
        );
        list.add(0, nueva);

        if (solicitud.recomendo()) {
            misVistosBuenos.put(vecinoId, true);
        }

        return nueva;
    }

    public boolean actualizarOcupacion(UUID usuarioId, SolicitudOcupacion ocupacion) {
        return true;
    }
}
