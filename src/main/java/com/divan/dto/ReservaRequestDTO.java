package com.divan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaRequestDTO {
    
    @NotNull(message = "Apartamento é obrigatório")
    private Long apartamentoId;
    
    @NotNull(message = "Cliente é obrigatório")
    private Long clienteId;
    
    @Min(value = 1, message = "Quantidade de hóspedes deve ser no mínimo 1")
    @NotNull(message = "Quantidade de hóspedes é obrigatória")
    private Integer quantidadeHospede;
    
    @NotNull(message = "Data de check-in é obrigatória")
    private LocalDateTime dataCheckin;
    
    @NotNull(message = "Data de check-out é obrigatória")
    private LocalDateTime dataCheckout;
}
