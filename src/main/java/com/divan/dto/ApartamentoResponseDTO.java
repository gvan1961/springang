package com.divan.dto;


import com.divan.entity.Apartamento;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApartamentoResponseDTO {
    
    private Long id;
    private String numeroApartamento;
    private Long tipoApartamentoId;
    private String tipoApartamentoNome; // Ex: "A", "B", "C"
    private String tipoApartamentoDescricao; // Ex: "Luxo"
    private Integer capacidade;
    private String camasDoApartamento;
    private String tv;
    private Apartamento.StatusEnum status;
}
