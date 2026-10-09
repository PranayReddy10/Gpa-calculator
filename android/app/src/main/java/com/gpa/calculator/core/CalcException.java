package com.gpa.calculator.core;

/** Thrown for invalid input; message is shown to the user. */
public class CalcException extends RuntimeException {
    public final String inputKey; // may be null

    public CalcException(String message) {
        this(null, message);
    }

    public CalcException(String inputKey, String message) {
        super(message);
        this.inputKey = inputKey;
    }
}
