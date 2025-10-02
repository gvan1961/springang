package com.divan.service;

import com.divan.dto.ResumoPagamentosDTO;
import com.divan.entity.ExtratoReserva;
import com.divan.entity.Pagamento;
import com.divan.entity.Reserva;
import com.divan.repository.ExtratoReservaRepository;
import com.divan.repository.PagamentoRepository;
import com.divan.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class PagamentoService {
    
    @Autowired
    private PagamentoRepository pagamentoRepository;
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoRepository;
    
    public Pagamento processarPagamento(Pagamento pagamento) {
        // Buscar reserva
        Optional<Reserva> reservaOpt = reservaRepository.findById(pagamento.getReserva().getId());
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        
        // Validar se reserva está ativa
        if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
            throw new RuntimeException("Não é possível adicionar pagamento a uma reserva não ativa");
        }
        
        // Validar se valor não excede saldo devedor
        if (pagamento.getValor().compareTo(reserva.getTotalApagar()) > 0) {
            throw new RuntimeException("Valor do pagamento excede o saldo devedor de R$ " + reserva.getTotalApagar());
        }
        
        // Salvar pagamento
        pagamento.setDataHoraPagamento(LocalDateTime.now());
        pagamento.setReserva(reserva);
        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);
        
        // Atualizar totais da reserva
        atualizarTotalRecebidoReserva(reserva.getId(), pagamento.getValor());
        
        // Criar extrato de pagamento
        criarExtratoPagamento(pagamento);
        
        return pagamentoSalvo;
    }
    
    private void atualizarTotalRecebidoReserva(Long reservaId, BigDecimal valorPago) {
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isPresent()) {
            Reserva reserva = reservaOpt.get();
            reserva.setTotalRecebido(reserva.getTotalRecebido().add(valorPago));
            reserva.setTotalApagar(reserva.getTotalHospedagem()
                .subtract(reserva.getTotalRecebido())
                .subtract(reserva.getDesconto()));
            
            reservaRepository.save(reserva);
        }
    }
    
    private void criarExtratoPagamento(Pagamento pagamento) {
        ExtratoReserva extrato = new ExtratoReserva();
        extrato.setReserva(pagamento.getReserva());
        extrato.setDataHoraLancamento(pagamento.getDataHoraPagamento());
        extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.PAGAMENTO);
        extrato.setTotalLancamento(pagamento.getValor().negate()); // Negativo pois diminui saldo
        extrato.setDescricao("Pagamento " + pagamento.getFormaPagamento().name().replace("_", " "));
        extrato.setValorUnitario(pagamento.getValor());
        
        extratoRepository.save(extrato);
    }
    
    @Transactional(readOnly = true)
    public List<Pagamento> buscarPorReserva(Long reservaId) {
        Optional<Reserva> reserva = reservaRepository.findById(reservaId);
        return reserva.map(pagamentoRepository::findByReserva).orElse(List.of());
    }
    
    @Transactional(readOnly = true)
    public List<Pagamento> buscarPagamentosDoDia(LocalDateTime data) {
        return pagamentoRepository.findPagamentosDoDia(data);
    }
    
    @Transactional(readOnly = true)
    public List<Pagamento> buscarPagamentosPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return pagamentoRepository.findPagamentosPorPeriodo(inicio, fim);
    }
    
    @Transactional(readOnly = true)
    public ResumoPagamentosDTO gerarResumoDoDia(LocalDateTime data) {
        List<Pagamento> pagamentos = pagamentoRepository.findPagamentosDoDia(data);
        
        Map<Pagamento.FormaPagamentoEnum, BigDecimal> totaisPorForma = new HashMap<>();
        for (Pagamento.FormaPagamentoEnum forma : Pagamento.FormaPagamentoEnum.values()) {
            totaisPorForma.put(forma, BigDecimal.ZERO);
        }
        
        BigDecimal totalGeral = BigDecimal.ZERO;
        
        for (Pagamento p : pagamentos) {
            BigDecimal atual = totaisPorForma.get(p.getFormaPagamento());
            totaisPorForma.put(p.getFormaPagamento(), atual.add(p.getValor()));
            totalGeral = totalGeral.add(p.getValor());
        }
        
        ResumoPagamentosDTO resumo = new ResumoPagamentosDTO();
        resumo.setTotalDinheiro(totaisPorForma.get(Pagamento.FormaPagamentoEnum.DINHEIRO));
        resumo.setTotalPix(totaisPorForma.get(Pagamento.FormaPagamentoEnum.PIX));
        resumo.setTotalCartaoDebito(totaisPorForma.get(Pagamento.FormaPagamentoEnum.CARTAO_DEBITO));
        resumo.setTotalCartaoCredito(totaisPorForma.get(Pagamento.FormaPagamentoEnum.CARTAO_CREDITO));
        resumo.setTotalTransferencia(totaisPorForma.get(Pagamento.FormaPagamentoEnum.TRANSFERENCIA_BANCARIA));
        resumo.setTotalFaturado(totaisPorForma.get(Pagamento.FormaPagamentoEnum.FATURADO));
        resumo.setTotalGeral(totalGeral);
        resumo.setQuantidadePagamentos(pagamentos.size());
        
        return resumo;
    }
}
