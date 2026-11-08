INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('codigo_vigencia_minutos', '5', 'Minutos de vigencia del código de verificación'),
    ('codigo_reenvio_segundos', '30', 'Segundos mínimos entre envíos de código'),
    ('codigo_intentos_maximos', '5', 'Intentos fallidos permitidos por código'),
    ('edad_minima', '18', 'Edad mínima para registrarse'),
    ('alta_vigencia_horas', '24', 'Horas que se conserva un alta en curso'),
    ('domicilio_cambio_dias', '30', 'Días mínimos entre cambios de domicilio'),
    ('reportes_maximos_24h', '5', 'Reportes preventivos permitidos por vecino en 24 horas'),
    ('falsas_alarmas_umbral', '3', 'Falsas alarmas que suspenden el botón de pánico'),
    ('falsas_alarmas_ventana_dias', '30', 'Días considerados para contar falsas alarmas'),
    ('cuadrantes_cercanos_metros', '3000', 'Distancia para mostrar cuadrantes cercanos como referencia'),
    ('panico_radio_domicilio_metros', '150', 'Distancia al domicilio para usar su referencia de cuadra en un pánico'),
    ('codigos_maximos_hora', '5', 'Códigos de verificación permitidos por celular y propósito en una hora'),
    ('recuperacion_espera_horas', '24', 'Horas de espera antes de aplicar una recuperación de cuenta')
ON CONFLICT (clave) DO NOTHING;

INSERT INTO telefonos_nacionales (servicio, numero, orden) VALUES
    ('Policía Nacional', '105', 1),
    ('Bomberos', '116', 2),
    ('SAMU', '106', 3)
ON CONFLICT (servicio) DO NOTHING;
