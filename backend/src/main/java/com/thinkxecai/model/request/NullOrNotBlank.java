package com.thinkxecai.model.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * For optional fields of update (PATCH) requests: null means "leave unchanged", but a given value
 * must not be blank. {@code @NotBlank} cannot be used because it also rejects null.
 */
@Documented
@Constraint(validatedBy = {})
@Pattern(regexp = "(?s).*\\S.*")
@ReportAsSingleViolation
@Target({ElementType.FIELD, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NullOrNotBlank {

    String message() default "must not be blank";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
