package com.divan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaMapaDTO {
    
    private Long id;
    private String clienteNome;
    private LocalDateTime dataCheckin;
    private LocalDateTime dataCheckout;
    private Integer quantidadeHospede;
    private String status;
}
