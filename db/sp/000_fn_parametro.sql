CREATE OR REPLACE FUNCTION fn_parametro(p_clave text, p_defecto text)
RETURNS text
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
    SELECT coalesce((SELECT valor FROM parametros WHERE clave = p_clave AND activo), p_defecto);
$$;

CREATE OR REPLACE FUNCTION fn_parametro_entero(p_clave text, p_defecto integer)
RETURNS integer
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
DECLARE
    v_valor text := fn_parametro(p_clave, NULL);
BEGIN
    IF v_valor IS NULL OR v_valor !~ '^\d+$' THEN
        RETURN p_defecto;
    END IF;
    RETURN v_valor::integer;
END
$$;

CREATE OR REPLACE FUNCTION fn_error(p_estado integer, p_codigo text, p_mensaje text)
RETURNS void
LANGUAGE plpgsql
VOLATILE
AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE = 'AT' || p_estado::text, MESSAGE = p_mensaje, DETAIL = p_codigo;
END
$$;
