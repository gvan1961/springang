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
    @Column(unique = true, nullable = false)
    private String numeroApartamento;
    
    @NotNull(message = "Tipo de apartamento é obrigatório")
    @ManyToOne
    @JoinColumn(name = "tipo_apartamento_id", nullable = false)
    @JsonIgnoreProperties({"apartamentos", "diarias"})
    private TipoApartamento tipoApartamento;
    
    @Min(value = 1, message = "Capacidade deve ser no mínimo 1")
    @Column(nullable = false)
    private Integer capacidade;
    
    @NotBlank(message = "Camas do apartamento é obrigatório")
    @Column(nullable = false, length = 100)
    private String camasDoApartamento;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusEnum status = StatusEnum.DISPONIVEL;
    
    @Column(length = 50)
    private String tv;
    
    @OneToMany(mappedBy = "apartamento", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"apartamento", "cliente", "diaria", "extratos", "historicos", "notasVenda"})
    private List<Reserva> reservas;
    
    public enum StatusEnum {
        DISPONIVEL, OCUPADO, LIMPEZA, PRE_RESERVA, MANUTENCAO
    }
}
