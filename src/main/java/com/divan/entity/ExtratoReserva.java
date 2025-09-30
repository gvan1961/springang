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

@Entity
@Table(name = "extratos_reserva")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtratoReserva {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Reserva é obrigatória")
    @ManyToOne
    @JoinColumn(name = "reserva_id", nullable = false)
    @JsonIgnoreProperties({"extratos", "historicos", "notasVenda", "apartamento", "cliente", "diaria"})
    private Reserva reserva;
    
    @NotNull(message = "Data/hora do lançamento é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataHoraLancamento;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusLancamentoEnum statusLancamento;
    
    @Min(value = 0, message = "Quantidade não pode ser negativa")
    private Integer quantidade;
    
    @DecimalMin(value = "0.0", message = "Valor unitário não pode ser negativo")
    @Column(precision = 10, scale = 2)
    private BigDecimal valorUnitario;
    
    @DecimalMin(value = "0.0", message = "Total do lançamento não pode ser negativo")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalLancamento;
    
    @Column(length = 200)
    private String descricao;
    
    @Column
    private Long notaVendaId;
    
    public enum StatusLancamentoEnum {
        PRODUTO, DIARIA, PAGAMENTO, ESTORNO
    }
}
