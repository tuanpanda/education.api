package com.education.base.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void root_redirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", HomeController.SWAGGER_UI_PATH));
    }

    @Test
    void swaggerAliases_redirectToSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", HomeController.SWAGGER_UI_PATH));

        mockMvc.perform(get("/swagger/index.html"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", HomeController.SWAGGER_UI_PATH));
    }

    @Test
    void favicon_returnsNoContent() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNoContent());
    }
}
