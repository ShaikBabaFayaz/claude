package com.library.support;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * MockMvc request builders that look like real browser/API traffic: an allow-listed {@code Host} on every request and
 * {@code X-Requested-With: library-ui} on every method except GET/HEAD/OPTIONS (architecture 10.6, DR-7, DR-17).
 */
public final class ApiRequests {

    public static final String HOST = "localhost:8080";
    public static final String REQUIRED_HEADER = "X-Requested-With";
    public static final String REQUIRED_HEADER_VALUE = "library-ui";

    private ApiRequests() {
    }

    public static MockHttpServletRequestBuilder get(String url, Object... vars) {
        return base(MockMvcRequestBuilders.get(url, vars));
    }

    public static MockHttpServletRequestBuilder head(String url, Object... vars) {
        return base(MockMvcRequestBuilders.head(url, vars));
    }

    public static MockHttpServletRequestBuilder options(String url, Object... vars) {
        return base(MockMvcRequestBuilders.options(url, vars));
    }

    public static MockHttpServletRequestBuilder post(String url, Object... vars) {
        return mutating(MockMvcRequestBuilders.post(url, vars));
    }

    public static MockHttpServletRequestBuilder put(String url, Object... vars) {
        return mutating(MockMvcRequestBuilders.put(url, vars));
    }

    public static MockHttpServletRequestBuilder patch(String url, Object... vars) {
        return mutating(MockMvcRequestBuilders.patch(url, vars));
    }

    public static MockHttpServletRequestBuilder delete(String url, Object... vars) {
        return mutating(MockMvcRequestBuilders.delete(url, vars));
    }

    /** JSON write with the standard headers. */
    public static MockHttpServletRequestBuilder json(HttpMethod method, String url, String body, Object... vars) {
        MockHttpServletRequestBuilder builder = switch (method.name()) {
            case "POST" -> post(url, vars);
            case "PUT" -> put(url, vars);
            case "PATCH" -> patch(url, vars);
            case "DELETE" -> delete(url, vars);
            default -> throw new IllegalArgumentException("Not a body method: " + method);
        };
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public static MockHttpServletRequestBuilder postJson(String url, String body, Object... vars) {
        return json(HttpMethod.POST, url, body, vars);
    }

    public static MockHttpServletRequestBuilder putJson(String url, String body, Object... vars) {
        return json(HttpMethod.PUT, url, body, vars);
    }

    private static MockHttpServletRequestBuilder mutating(MockHttpServletRequestBuilder builder) {
        return base(builder).header(REQUIRED_HEADER, REQUIRED_HEADER_VALUE);
    }

    private static MockHttpServletRequestBuilder base(MockHttpServletRequestBuilder builder) {
        return builder.header("Host", HOST).accept(MediaType.APPLICATION_JSON);
    }
}
