package com.library.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The annotated string must be a valid ISBN-10 or ISBN-13 including its check digit; hyphens and spaces are ignored
 * (FR-5, AC-1.BE.3). {@code null} is valid here: combine with {@code @NotBlank} to require a value.
 */
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Isbn {

    String message() default "ISBN must be a valid ISBN-10 or ISBN-13.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
