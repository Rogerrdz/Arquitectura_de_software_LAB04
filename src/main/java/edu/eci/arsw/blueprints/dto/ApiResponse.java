package edu.eci.arsw.blueprints.dto;

public record ApiResponse<T>(
        int code,
        String message,
        T data
) {}
