package com.divan.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "reservas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reserva {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Apartamento é obrigatório")
    @ManyToOne
    @JoinColumn(name = "apartamento_id", nullable = false)
    @JsonIgnoreProperties({"reservas", "tipoApartamento"})
    private Apartamento apartamento;
    
    @NotNull(message = "Cliente é obrigatório")
    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnoreProperties({"reservas", "empresa"})
    private Cliente cliente;
    
    @Min(value = 1, message = "Quantidade de hóspedes deve ser no mínimo 1")
    @Column(nullable = false)
    private Integer quantidadeHospede;
    
    @NotNull(message = "Diária é obrigatória")
    @ManyToOne
    @JoinColumn(name = "diaria_id", nullable = false)
    @JsonIgnoreProperties({"reservas", "tipoApartamento"})
    private Diaria diaria;
    
    @NotNull(message = "Data de check-in é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataCheckin;
    
    @NotNull(message = "Data de check-out é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataCheckout;
    
    @Min(value = 1, message = "Quantidade de diárias deve ser no mínimo 1")
    @Column(nullable = false)
    private Integer quantidadeDiaria;
    
    @DecimalMin(value = "0.0", message = "Total de diária não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalDiaria = BigDecimal.ZERO;
    
    @DecimalMin(value = "0.0", message = "Total de produto não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalProduto = BigDecimal.ZERO;
    
    @Column(length = 500)
    private String observacoes;
    
    @DecimalMin(value = "0.0", message = "Total de hospedagem não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalHospedagem = BigDecimal.ZERO;
    
    @DecimalMin(value = "0.0", message = "Total recebido não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalRecebido = BigDecimal.ZERO;
    
    @DecimalMin(value = "0.0", message = "Desconto não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal desconto = BigDecimal.ZERO;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalApagar = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusReservaEnum status = StatusReservaEnum.ATIVA;
    
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("reserva")
    private List<ExtratoReserva> extratos;
    
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("reserva")
    private List<HistoricoHospede> historicos;
    
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL)
    @JsonIgnoreProperties("reserva")
    private List<NotaVenda> notasVenda;
    
    public enum StatusReservaEnum {
        ATIVA, CANCELADA, FINALIZADA
    }
}


//@OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
//private List<NotaVenda> notasVenda;
