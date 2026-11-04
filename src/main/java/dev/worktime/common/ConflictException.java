package dev.worktime.common;

/** Операция противоречит текущему состоянию (например, таймер уже запущен) → HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
