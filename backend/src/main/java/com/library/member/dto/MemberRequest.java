package com.library.member.dto;

import com.library.common.validation.LibraryEmail;
import com.library.common.validation.TextNormalizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/members} and {@code PUT /api/members/{id}} (architecture 6.3). Name and email are trimmed in
 * the compact constructor, before Bean Validation runs (null-safe, DR-15). The email is kept as entered (display value);
 * the case-insensitive uniqueness key is derived by the entity.
 */
public record MemberRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 100, message = "Name must be at most 100 characters.")
        String name,

        @NotBlank(message = "Email is required.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        @LibraryEmail
        String email) {

    public MemberRequest {
        name = TextNormalizer.trim(name);
        email = TextNormalizer.trim(email);
    }
}
