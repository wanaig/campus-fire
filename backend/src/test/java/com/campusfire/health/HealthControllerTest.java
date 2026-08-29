package com.campusfire.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.campusfire.security.SecurityConfig;
import com.campusfire.security.JwtTokenService;
import com.campusfire.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Import;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HealthControllerTest {

    @MockBean
    private JwtTokenService jwtTokenService;


    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnHealthyApplicationStatus() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.application").value("campus-fire-backend"))
                .andExpect(jsonPath("$.serverTime").exists());
    }
}
