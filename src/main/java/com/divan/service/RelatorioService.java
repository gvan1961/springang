package com.divan.service;

import com.divan.entity.*;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class RelatorioService {
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private NotaVendaRepository notaVendaRepository;
    
    @Autowired
    private PagamentoRepository pagamentoRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoRepository;
    
    @Autowired
    private ContaAReceberRepository contaReceberRepository;
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    public Map<String, Object> gerarRelatorioFechamentoDiario(LocalDateTime data) {
        Map<String, Object> relatorio = new HashMap<>();
        
        // Reservas do dia
        List<Reserva> checkins = reservaRepository.findReservasParaCheckinNaData(data);
        List<Reserva> checkouts = reservaRepository.findReservasParaCheckoutNaData(data);
        
        // Vendas do dia
        List<NotaVenda> vendas = notaVendaRepository.findVendasDoDia(data);
        BigDecimal totalVendas = vendas.stream()
            .map(NotaVenda::getTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Pagamentos do dia
        List<Pagamento> pagamentos = pagamentoRepository.findPagamentosDoDia(data);
        BigDecimal totalPagamentos = pagamentos.stream()
            .map(Pagamento::getValor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Ocupação
        List<Apartamento> ocupados = apartamentoRepository.findOcupados();
        List<Apartamento> disponiveis = apartamentoRepository.findDisponiveis();
        
        relatorio.put("data", data.toLocalDate());
        relatorio.put("checkins", checkins);
        relatorio.put("checkouts", checkouts);
        relatorio.put("vendas", vendas);
        relatorio.put("totalVendas", totalVendas);
        relatorio.put("pagamentos", pagamentos);
        relatorio.put("totalPagamentos", totalPagamentos);
        relatorio.put("apartamentosOcupados", ocupados.size());
        relatorio.put("apartamentosDisponiveis", disponiveis.size());
        relatorio.put("taxaOcupacao", calcularTaxaOcupacao(ocupados.size(), disponiveis.size()));
        
        return relatorio;
    }
    
    public List<ExtratoReserva> gerarExtratoReserva(Long reservaId) {
        return extratoRepository.findByReservaIdOrderByDataHoraLancamento(reservaId);
    }
    
    public List<ContaAReceber> gerarRelatorioContasReceber() {
        return contaReceberRepository.findContasEmAberto();
    }
    
    public List<ContaAReceber> gerarRelatorioContasVencidas() {
        return contaReceberRepository.findContasVencidas(LocalDateTime.now().toLocalDate());
    }
    
    public Map<String, Object> gerarMapaReservas(LocalDateTime dataInicio, LocalDateTime dataFim) {
        Map<String, Object> mapa = new HashMap<>();
        
        List<Reserva> reservas = reservaRepository.findReservasPorPeriodo(dataInicio, dataFim);
        List<Apartamento> apartamentos = apartamentoRepository.findAll();
        
        mapa.put("reservas", reservas);
        mapa.put("apartamentos", apartamentos);
        mapa.put("periodo", Map.of("inicio", dataInicio, "fim", dataFim));
        
        return mapa;
    }
    
    private double calcularTaxaOcupacao(int ocupados, int disponiveis) {
        int total = ocupados + disponiveis;
        return total > 0 ? (double) ocupados / total * 100 : 0.0;
    }
}
