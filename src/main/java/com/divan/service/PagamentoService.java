package com.divan.service;

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
import java.util.List;
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
        // Salvar pagamento
        pagamento.setDataHoraPagamento(LocalDateTime.now());
        Pagamento pagamentoSalvo = pagamentoRepository.save(pagamento);
        
        // Atualizar totais da reserva
        atualizarTotalRecebidoReserva(pagamento.getReserva().getId(), pagamento.getValor());
        
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
        extrato.setTotalLancamento(pagamento.getValor().negate()); // Negativo pois é pagamento
        extrato.setDescricao("Pagamento " + pagamento.getFormaPagamento().name());
        
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
    public List<Object[]> buscarTotalPorFormaPagamento(LocalDateTime data) {
        return pagamentoRepository.findTotalPorFormaPagamentoNoDia(data);
    }
}
