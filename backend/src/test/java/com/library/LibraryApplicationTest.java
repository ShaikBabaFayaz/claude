package com.library;

import static com.library.support.ApiRequests.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class LibraryApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("T-01: application context loads")
    void contextLoads() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("library-backend");
    }

    @Test
    @DisplayName("T-01: /actuator/health reports UP (request carries an allow-listed Host, T-06)")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("T-01: test configuration uses a per-context database and has demo data off (DR-9)")
    void testConfigurationOverrides() {
        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:mem:")
                .doesNotContain("mem:library;");
        assertThat(environment.getProperty("library.demo-data.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("T-01: hardening defaults from architecture 10.4 are in effect")
    void hardeningDefaults() {
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(environment.getProperty("server.error.whitelabel.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }
}
