package com.library.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the root-level {@code shared-test-vectors/*.json} files (copied onto the test classpath by pom.xml).
 * The same files are read by the frontend tests, see shared-test-vectors/README.md for the format.
 */
public final class SharedVectors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SharedVectors() {
    }

    public record Vector(String input, String normalized, String note) {
        @Override
        public String toString() {
            return "'" + (input.length() > 40 ? input.substring(0, 37) + "..." : input) + "' - " + note;
        }
    }

    public static List<Vector> isbnValid() {
        return vectors("isbn.json", "valid");
    }

    public static List<Vector> isbnInvalid() {
        return vectors("isbn.json", "invalid");
    }

    public static List<Vector> emailValid() {
        return vectors("email.json", "valid");
    }

    public static List<Vector> emailInvalid() {
        return vectors("email.json", "invalid");
    }

    /** Pairs of ISBNs that are both valid but must stay different after normalisation (Q-A1). */
    public static List<String[]> isbnNotEquivalent() {
        List<String[]> pairs = new ArrayList<>();
        for (JsonNode node : read("isbn.json").get("notEquivalent")) {
            pairs.add(new String[] {node.get("a").asText(), node.get("b").asText()});
        }
        return pairs;
    }

    private static List<Vector> vectors(String file, String section) {
        List<Vector> result = new ArrayList<>();
        for (JsonNode node : read(file).get(section)) {
            result.add(new Vector(
                    node.get("input").asText(),
                    node.has("normalized") ? node.get("normalized").asText() : null,
                    node.path("note").asText("")));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException(file + " section '" + section + "' is empty");
        }
        return result;
    }

    private static JsonNode read(String file) {
        try (InputStream in = SharedVectors.class.getResourceAsStream("/shared-test-vectors/" + file)) {
            if (in == null) {
                throw new IllegalStateException("shared-test-vectors/" + file
                        + " is not on the test classpath; check the testResources entry in backend/pom.xml");
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
