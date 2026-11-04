package dev.worktime.common;

/** Некорректные параметры запроса, которые не покрываются Bean Validation → HTTP 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
