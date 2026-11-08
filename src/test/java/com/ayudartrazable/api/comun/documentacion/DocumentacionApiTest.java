package com.ayudartrazable.api.comun.documentacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DocumentacionApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void catalogoOpenApiPublicoConEsquemaApiError() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Ayudar Trazable API"))
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.codigo").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.mensaje").exists())
                .andExpect(jsonPath("$.components.schemas.ApiError.properties.campos").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }
}
