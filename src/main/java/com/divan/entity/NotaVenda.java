package com.divan.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "notas_venda")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotaVenda {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotNull(message = "Data/hora da venda é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataHoraVenda;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVendaEnum tipoVenda;
    
    @ManyToOne
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;
    
    @DecimalMin(value = "0.01", message = "Total deve ser maior que zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    
    @OneToMany(mappedBy = "notaVenda", cascade = CascadeType.ALL)
    private List<ItemVenda> itens;
    
    public enum TipoVendaEnum {
        VISTA, APARTAMENTO
    }
}
