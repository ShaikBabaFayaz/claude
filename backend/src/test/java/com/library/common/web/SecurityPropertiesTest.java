package com.library.common.web;

import static com.library.support.ErrorBodyAssert.assertError;
import static org.assertj.core.api.Assertions.assertThat;

import com.library.support.ApiRequests;
import com.library.support.TestClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** The allowlists are configuration (architecture 10.4, R-12), with the documented defaults. */
class SecurityPropertiesTest {

    @Nested
    @SpringBootTest
    class Defaults {

        @Autowired
        private SecurityProperties properties;

        @Test
        @DisplayName("10.4 defaults list localhost and 127.0.0.1 on ports 8080 and 5173")
        void defaults() {
            assertThat(properties.allowedHosts())
                    .containsExactly("localhost:8080", "127.0.0.1:8080", "localhost:5173", "127.0.0.1:5173");
            assertThat(properties.allowedOrigins()).containsExactly("http://localhost:8080",
                    "http://127.0.0.1:8080", "http://localhost:5173", "http://127.0.0.1:5173");
        }
    }

    @Nested
    @SpringBootTest(properties = {
            "library.security.allowed-hosts=library.test:9000, localhost:7000",
            "library.security.allowed-origins=http://library.test:9000"})
    @AutoConfigureMockMvc
    @Import(TestClockConfig.class)
    class Overridden {

        @Autowired
        private SecurityProperties properties;

        @Autowired
        private MockMvc mvc;

        @Test
        @DisplayName("R-12 both allowlists can be overridden by property (whitespace around entries is ignored)")
        void overridden() {
            assertThat(properties.allowedHosts()).containsExactly("library.test:9000", "localhost:7000");
            assertThat(properties.allowedOrigins()).containsExactly("http://library.test:9000");
        }

        @Test
        @DisplayName("R-12 with overridden lists the old defaults are rejected and the new values pass")
        void overriddenListsAreEnforced() throws Exception {
            assertError(mvc.perform(MockMvcRequestBuilders.get("/actuator/health").header("Host", "localhost:8080"))
                    .andReturn(), 403, "FORBIDDEN_ORIGIN");

            assertThat(mvc.perform(MockMvcRequestBuilders.get("/actuator/health").header("Host", "library.test:9000"))
                    .andReturn().getResponse().getStatus()).isEqualTo(200);

            assertError(mvc.perform(ApiRequests.post("/anything").header("Host", "library.test:9000")
                    .header("Origin", "http://localhost:5173")).andReturn(), 403, "FORBIDDEN_ORIGIN");
        }
    }

    @Test
    @DisplayName("blank and null entries are dropped, null lists become empty (nothing is allowed)")
    void normalisation() {
        SecurityProperties properties = new SecurityProperties(java.util.Arrays.asList(" a:1 ", "", "  "), null);

        assertThat(properties.allowedHosts()).containsExactly("a:1");
        assertThat(properties.allowedOrigins()).isEmpty();
    }
}
