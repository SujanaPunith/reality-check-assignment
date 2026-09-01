package com.comeon.assignment.realitycheck.exception;

import lombok.Getter;

@Getter
public class RealityCheckException extends RuntimeException {

    private final String code;

    public RealityCheckException(String code) {
        super(code);
        this.code = code;
    }
}
