package com.example.SistemaBarbearia.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDTO(
        @NotBlank(message = "O idToken do Google é obrigatório")
        String idToken
) {}
