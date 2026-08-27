package com.comeon.assignment.realitycheck.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class RestResponse implements Serializable {

    private Object data;
    private String error;

    public RestResponse(Object data) {
        this.data = data;
    }

    public RestResponse(String error, Object data) {
        this.error = error;
        this.data = data;
    }
}
