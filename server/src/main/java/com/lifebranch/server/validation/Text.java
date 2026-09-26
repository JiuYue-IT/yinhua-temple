package com.lifebranch.server.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段必须去首尾空白后非空，且码点数不超过 max。
 * message 用于直接展示给用户，例如「请填写未选择的道路。」。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Text.Validator.class)
public @interface Text {

    int max();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Text, String> {
        private int max;

        @Override
        public void initialize(Text a) {
            this.max = a.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext ctx) {
            return TextRules.fits(value, max);
        }
    }
}
