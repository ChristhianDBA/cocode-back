package com.ayudartrazable.api.comun.documentacion;

import com.ayudartrazable.api.comun.errores.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionDocumentacion {

    @Bean
    OpenAPI documentoBase() {
        return new OpenAPI()
                .info(new Info().title("Ayudar Trazable API").version("v1")
                        .description("Red vecinal verificada: identidad, cuadrantes y emergencias"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer esquemaDeErrores() {
        return api -> ModelConverters.getInstance().readAll(ApiError.class)
                .forEach((nombre, esquema) -> api.getComponents().addSchemas(nombre, esquema));
    }
}
