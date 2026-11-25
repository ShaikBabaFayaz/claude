package com.library.config;

import static com.library.support.ApiRequests.get;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** NFR-13: the OpenAPI document lists the loan endpoints and declares the required write header on write operations. */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("NFR-13 /v3/api-docs lists the loan endpoints and X-Requested-With (default library-ui) on writes only")
    void apiDocsDeclareRequiredHeaderOnWrites() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode paths = new ObjectMapper()
                .readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("paths");

        assertThat(paths.has("/api/loans")).isTrue();
        assertThat(paths.has("/api/loans/active")).isTrue();
        assertThat(paths.has("/api/loans/overdue")).isTrue();
        assertThat(paths.has("/api/loans/{id}")).isTrue();
        assertThat(paths.has("/api/loans/{id}/return")).isTrue();

        JsonNode checkout = paths.get("/api/loans").get("post");
        JsonNode header = null;
        for (JsonNode parameter : checkout.get("parameters")) {
            if ("X-Requested-With".equals(parameter.get("name").asText())) {
                header = parameter;
            }
        }
        assertThat(header).as("X-Requested-With parameter on POST /api/loans").isNotNull();
        assertThat(header.get("in").asText()).isEqualTo("header");
        assertThat(header.get("required").asBoolean()).isTrue();
        assertThat(header.get("schema").get("default").asText()).isEqualTo("library-ui");
        assertThat(paths.get("/api/books/{id}").get("delete").toString()).contains("X-Requested-With");
        assertThat(paths.get("/api/loans/{id}").get("get").toString()).doesNotContain("X-Requested-With");
    }
}
