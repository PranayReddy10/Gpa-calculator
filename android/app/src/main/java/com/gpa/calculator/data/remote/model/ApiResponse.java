package com.gpa.calculator.data.remote.model;

import com.google.gson.JsonElement;

/** Standard envelope returned by every backend endpoint. */
public class ApiResponse {
    public boolean ok;
    public String error;
    public JsonElement data;
}
