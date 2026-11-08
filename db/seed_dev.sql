INSERT INTO parametros (clave, valor, descripcion) VALUES
    ('codigo_fijo', '1234', 'Solo desarrollo: código de verificación fijo')
ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor;

INSERT INTO distritos (nombre, telefono_serenazgo, telefono_comisaria, telefono_bomberos) VALUES
    ('San Borja', '(01) 000-0001', '(01) 000-0002', '(01) 000-0003'),
    ('Miraflores', NULL, NULL, NULL)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO cuadrantes (codigo, nombre, distrito_id, poligono) VALUES
    ('CUAD-SB-04', 'San Borja · Sector 4', (SELECT id FROM distritos WHERE nombre = 'San Borja'),
     ST_GeomFromText('POLYGON((-76.9960 -12.0960, -76.9860 -12.0960, -76.9860 -12.0860, -76.9960 -12.0860, -76.9960 -12.0960))', 4326)),
    ('CUAD-SB-05', 'San Borja · Sector 5', (SELECT id FROM distritos WHERE nombre = 'San Borja'),
     ST_GeomFromText('POLYGON((-76.9860 -12.0960, -76.9760 -12.0960, -76.9760 -12.0860, -76.9860 -12.0860, -76.9860 -12.0960))', 4326)),
    ('CUAD-MF-01', 'Miraflores · Sector 1', (SELECT id FROM distritos WHERE nombre = 'Miraflores'),
     ST_GeomFromText('POLYGON((-77.0350 -12.1250, -77.0250 -12.1250, -77.0250 -12.1150, -77.0350 -12.1150, -77.0350 -12.1250))', 4326))
ON CONFLICT (codigo) DO NOTHING;

SELECT sp_admin_inicial('43334455', 'Carmen', 'Vargas', 'Lima', '1969-05-30', 'F', '900000001')
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE tipo = 'ADMIN');
