package dev.worktime.common;

/** Запрошенный объект не существует → HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
