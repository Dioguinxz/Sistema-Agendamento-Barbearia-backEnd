package com.example.SistemaBarbearia.dto;

import com.example.SistemaBarbearia.entity.TipoServico;

import java.time.LocalDateTime;

public record HorarioOcupadoDTO(LocalDateTime horario, TipoServico tipoServico) {}

