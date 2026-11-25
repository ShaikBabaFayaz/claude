package com.library.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Pragmatic email rule from AC-3.BE.2: at most 254 characters, no whitespace, exactly one {@code @}, non-empty local
 * part, domain with at least one dot and no empty label. {@code null} is valid here: combine with {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = LibraryEmailValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LibraryEmail {

    String message() default "Email must be a valid email address, for example name@example.org.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
