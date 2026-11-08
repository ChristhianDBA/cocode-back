CREATE OR REPLACE FUNCTION fn_punto(p_latitud double precision, p_longitud double precision)
RETURNS geometry
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT ST_SetSRID(ST_MakePoint(p_longitud, p_latitud), 4326);
$$;

CREATE OR REPLACE FUNCTION fn_cuadrante_de_punto(p_punto geometry)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT c.id
    FROM cuadrantes c
    WHERE c.habilitado AND c.activo AND ST_Covers(c.poligono, p_punto)
    ORDER BY c.codigo
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION fn_cuadrante_o_cercano(p_punto geometry)
RETURNS uuid
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT coalesce(
        fn_cuadrante_de_punto(p_punto),
        (SELECT c.id
         FROM cuadrantes c
         WHERE c.habilitado AND c.activo
         ORDER BY c.poligono <-> p_punto
         LIMIT 1)
    );
$$;

CREATE OR REPLACE FUNCTION fn_resolver_punto(p_latitud double precision, p_longitud double precision)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT coalesce(
        (SELECT jsonb_build_object(
                    'dentroDeCobertura', true,
                    'cuadranteId', c.id,
                    'codigo', c.codigo,
                    'nombre', c.nombre,
                    'distrito', d.nombre)
         FROM cuadrantes c
         JOIN distritos d ON d.id = c.distrito_id
         WHERE c.id = fn_cuadrante_de_punto(fn_punto(p_latitud, p_longitud))),
        jsonb_build_object('dentroDeCobertura', false));
$$;

CREATE OR REPLACE FUNCTION fn_referencia_cuadra(p_direccion text, p_respaldo text)
RETURNS text
LANGUAGE plpgsql
IMMUTABLE
AS $$
DECLARE
    v_via    text;
    v_coinc  text[];
BEGIN
    IF p_direccion IS NULL OR btrim(p_direccion) = '' THEN
        RETURN p_respaldo;
    END IF;
    v_via := btrim(split_part(p_direccion, ',', 1));
    IF v_via !~ '[0-9]' THEN
        RETURN v_via;
    END IF;
    IF v_via ~* '^(mz|mza|manzana)\M' THEN
        RETURN p_respaldo;
    END IF;
    v_coinc := regexp_match(v_via, '^([^0-9]*[^0-9[:space:]])\s+([0-9]+)\s*$');
    IF v_coinc IS NULL THEN
        RETURN p_respaldo;
    END IF;
    RETURN btrim(v_coinc[1]) || ' cuadra ' || greatest(1, v_coinc[2]::integer / 100);
END
$$;

CREATE OR REPLACE FUNCTION fn_dni_enmascarado(p_dni text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT '******' || right(p_dni, 2);
$$;

CREATE OR REPLACE FUNCTION fn_nombre_abreviado(p_nombres text, p_apellido text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT p_nombres || ' ' || left(p_apellido, 1) || '.';
$$;
