package com.divan.service;

import com.divan.dto.ResumoPagamentosDTO;
import com.divan.entity.Apartamento;
import com.divan.entity.ContaAReceber;
import com.divan.entity.ExtratoReserva;
import com.divan.entity.Pagamento;
import com.divan.entity.Reserva;
import com.divan.repository.ExtratoReservaRepository;
import com.divan.repository.PagamentoRepository;
import com.divan.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.divan.repository.ContaAReceberRepository;
import java.time.LocalDate;



import com.divan.repository.ApartamentoRepository;

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
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;

    @Autowired
    private ContaAReceberRepository contaAReceberRepository;
    
    
    public Pagamento processarPagamento(Pagamento pagamento) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("💰 PROCESSANDO PAGAMENTO");
        System.out.println("═══════════════════════════════════════════");
        
        // Buscar reserva
        Optional<Reserva> reservaOpt = reservaRepository.findById(pagamento.getReserva().getId());
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        
        System.out.println("📊 VALORES ANTES DO PAGAMENTO:");
        System.out.println("   Total Hospedagem: R$ " + reserva.getTotalHospedagem());
        System.out.println("   Total Recebido: R$ " + reserva.getTotalRecebido());
        System.out.println("   Total A Pagar: R$ " + reserva.getTotalApagar());
        System.out.println("   Valor do Pagamento: R$ " + pagamento.getValor());
        
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
        
        System.out.println("✅ Pagamento registrado: R$ " + pagamento.getValor());
        
        // Atualizar totais da reserva
        atualizarTotalRecebidoReserva(reserva.getId(), pagamento.getValor());
        
        // Criar extrato de pagamento
        criarExtratoPagamento(pagamento);
        
        // ✅ BUSCAR RESERVA ATUALIZADA
        reserva = reservaRepository.findById(reserva.getId()).get();
        
        System.out.println("📊 VALORES APÓS O PAGAMENTO:");
        System.out.println("   Total Recebido: R$ " + reserva.getTotalRecebido());
        System.out.println("   Total A Pagar: R$ " + reserva.getTotalApagar());
        
        // ✅✅✅ FINALIZAR AUTOMATICAMENTE SE ZEROU O SALDO ✅✅✅
        if (reserva.getTotalApagar().compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("═══════════════════════════════════════════");
            System.out.println("✅ PAGAMENTO TOTAL EFETUADO!");
            System.out.println("🔄 FINALIZANDO RESERVA AUTOMATICAMENTE...");
            System.out.println("═══════════════════════════════════════════");
            
            finalizarReservaAutomaticamente(reserva);
            
            System.out.println("✅ Reserva finalizada automaticamente!");
            System.out.println("🧹 Apartamento liberado para LIMPEZA");
            System.out.println("═══════════════════════════════════════════");
        } else {
            System.out.println("⚠️ Pagamento PARCIAL - Saldo restante: R$ " + reserva.getTotalApagar());
            System.out.println("   Reserva continua ATIVA");
            System.out.println("═══════════════════════════════════════════");
        }
        
        return pagamentoSalvo;
    }
    
 // ✅ NOVO MÉTODO - FINALIZAR AUTOMATICAMENTE
    private void finalizarReservaAutomaticamente(Reserva reserva) {
        // Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);
        
        // Liberar apartamento para limpeza
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);
        
        // Salvar reserva
        reservaRepository.save(reserva);
        
        // ✅ CRIAR REGISTRO EM CONTAS A RECEBER (PAGA)
        criarRegistroReservaPaga(reserva);
        
        System.out.println("   Status da Reserva: " + reserva.getStatus());
        System.out.println("   Status do Apartamento " + apartamento.getNumeroApartamento() + ": " + apartamento.getStatus());
    }

    // ✅ NOVO MÉTODO - CRIAR REGISTRO DE RESERVA PAGA
    private void criarRegistroReservaPaga(Reserva reserva) {
        try {
            ContaAReceber conta = new ContaAReceber();
            
            conta.setReserva(reserva);
            conta.setCliente(reserva.getCliente());
            conta.setValor(reserva.getTotalHospedagem());
            conta.setValorPago(reserva.getTotalRecebido());
            conta.setSaldo(BigDecimal.ZERO);
            conta.setStatus(ContaAReceber.StatusContaEnum.PAGA);
            conta.setDataPagamento(LocalDate.now());
            conta.setDataVencimento(LocalDate.now());
            conta.setDataCriacao(LocalDateTime.now());
            conta.setDescricao("Reserva PAGA #" + reserva.getId() + 
                              " - Apt " + reserva.getApartamento().getNumeroApartamento());
            conta.setObservacao("Pagamento efetuado durante a hospedagem");
            
            if (reserva.getCliente().getEmpresa() != null) {
                conta.setEmpresa(reserva.getCliente().getEmpresa());
            }
            
            contaAReceberRepository.save(conta);
            
            System.out.println("💚 Registro de reserva PAGA criado!");
            
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao criar registro de reserva paga: " + e.getMessage());
            // Não lança exceção para não bloquear a finalização
        }
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
     //   resumo.setTotalFaturado(totaisPorForma.get(Pagamento.FormaPagamentoEnum.FATURADO));
        resumo.setTotalGeral(totalGeral);
        resumo.setQuantidadePagamentos(pagamentos.size());
        
        return resumo;
    }
}
