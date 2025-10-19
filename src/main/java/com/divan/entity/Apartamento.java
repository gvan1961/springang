package com.divan.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "apartamentos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Apartamento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Número do apartamento é obrigatório")
    @Column(unique = true, nullable = false, length = 10)
    private String numeroApartamento;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tipo_apartamento_id", nullable = false)
    @NotNull(message = "Tipo de apartamento é obrigatório")
    @JsonIgnoreProperties("apartamentos")
    private TipoApartamento tipoApartamento;
    
    @NotNull(message = "Capacidade é obrigatória")
    @Min(value = 1, message = "Capacidade deve ser no mínimo 1 pessoa")
    @Column(nullable = false)
    private Integer capacidade;
    
    // ✅✅✅ CORRIGIR AQUI - camelCase ✅✅✅
    @NotBlank(message = "Descrição das camas é obrigatória")
    @Column(name = "camas_do_apartamento", nullable = false, length = 200)
    private String camasDoApartamento; // ✅ CORRETO - camelCase
    
   
    
    @Column(length =200)
    private String tv;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEnum status = StatusEnum.DISPONIVEL;
    
    @OneToMany(mappedBy = "apartamento")
    @JsonIgnoreProperties("apartamento")
    private List<Reserva> reservas;
    
    public enum StatusEnum {
        DISPONIVEL,
        OCUPADO,
        LIMPEZA,
        PRE_RESERVA,
        MANUTENCAO,
        INDISPONIVEL
    }
}
