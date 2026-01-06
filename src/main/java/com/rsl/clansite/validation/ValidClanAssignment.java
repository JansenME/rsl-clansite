package com.rsl.clansite.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValidClanAssignmentValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidClanAssignment {
    String message() default "Invalid Clan Assignment";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
