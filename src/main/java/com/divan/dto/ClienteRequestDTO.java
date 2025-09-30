package com.divan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClienteRequestDTO {
    
    @NotBlank(message = "Nome é obrigatório")
    private String nome;
    
    @CPF(message = "CPF inválido")
    private String cpf;
    
    @Pattern(regexp = "\\(\\d{2}\\)\\s\\d{4,5}-\\d{4}", message = "Celular deve estar no formato (XX) XXXXX-XXXX")
    private String celular;
    
    private String endereco;
    
    @Pattern(regexp = "\\d{5}-\\d{3}", message = "CEP deve estar no formato XXXXX-XXX")
    private String cep;
    
    private String cidade;
    private String estado;
    
    @NotNull(message = "Data de nascimento é obrigatória")
    private LocalDate dataNascimento;
    
    // APENAS O ID DA EMPRESA, NÃO O OBJETO COMPLETO
    private Long empresaId;
}
