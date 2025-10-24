package com.divan.dto;

import com.divan.entity.Reserva;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservaDetalhesDTO {
    
    private Long id;
    
    // CLIENTE
    private ClienteSimples cliente;
    
    // APARTAMENTO
    private ApartamentoSimples apartamento;
    
    // DATAS E DIÁRIAS
    private Integer quantidadeHospede;
    private LocalDateTime dataCheckin;
    private LocalDateTime dataCheckout;
    private Integer quantidadeDiaria;
    private BigDecimal valorDiaria;
    
    // TOTAIS FINANCEIROS
    private BigDecimal totalDiaria;
    private BigDecimal totalHospedagem;
    private BigDecimal totalRecebido;
    private BigDecimal totalApagar;
    private BigDecimal totalProduto;  // ✅ IMPORTANTE
    
    // STATUS
    private Reserva.StatusReservaEnum status;
    
    // HISTÓRICO E EXTRATOS
    private List<ExtratoDTO> extratos;
    private List<HistoricoDTO> historicos;
    
    // CLASSES INTERNAS PARA DADOS SIMPLIFICADOS
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClienteSimples {
        private Long id;
        private String nome;
        private String cpf;
        private String telefone;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApartamentoSimples {
        private Long id;
        private String numeroApartamento;
        private Integer capacidade;
        private String tipoApartamentoNome;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtratoDTO {
        private Long id;
        private LocalDateTime dataHoraLancamento;
        private String statusLancamento;
        private String descricao;
        private Integer quantidade;
        private BigDecimal valorUnitario;
        private BigDecimal totalLancamento;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricoDTO {
        private Long id;
        private LocalDateTime dataHora;
        private String motivo;
        private Integer quantidadeAnterior;
        private Integer quantidadeNova;
    }
}
