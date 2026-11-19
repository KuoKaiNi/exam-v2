package com.example.holdings.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 錯誤回應一律是 {"error":{"code":"...","message":"..."}}。 */
public record ApiError(@JsonProperty("error") Body error) {

    public record Body(@JsonProperty("code") String code, @JsonProperty("message") String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(new Body(code, message));
    }
}
