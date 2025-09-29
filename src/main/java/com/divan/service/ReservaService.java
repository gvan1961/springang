package com.divan.service;

import com.divan.entity.*;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ReservaService {
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    @Autowired
    private DiariaRepository diariaRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoRepository;
    
    @Autowired
    private HistoricoHospedeRepository historicoRepository;
    
    @Autowired
    private ContaAReceberRepository contaReceberRepository;
    
    public Reserva criarReserva(Reserva reserva) {
        // Validar disponibilidade do apartamento
        List<Reserva> conflitos = reservaRepository.findReservasConflitantes(
            reserva.getApartamento().getId(),
            reserva.getDataCheckin(),
            reserva.getDataCheckout()
        );
        
        if (!conflitos.isEmpty()) {
            throw new RuntimeException("Apartamento não disponível para o período");
        }
        
        // Buscar diária adequada
        Optional<Diaria> diariaOpt = diariaRepository.findByTipoApartamentoAndQuantidade(
            reserva.getApartamento().getTipoApartamento(),
            reserva.getQuantidadeHospede()
        );
        
        if (diariaOpt.isEmpty()) {
            throw new RuntimeException("Diária não encontrada para este tipo e quantidade");
        }
        
        reserva.setDiaria(diariaOpt.get());
        
        // Calcular quantidade de diárias
        long dias = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()
        );
        
        if (dias <= 0) {
            dias = 1; // Mínimo 1 diária
        }
        
        reserva.setQuantidadeDiaria((int) dias);
        
        // Calcular totais
        BigDecimal totalDiaria = diariaOpt.get().getValor()
            .multiply(BigDecimal.valueOf(dias));
        
        reserva.setTotalDiaria(totalDiaria);
        reserva.setTotalHospedagem(totalDiaria);
        reserva.setTotalApagar(totalDiaria);
        
        // Salvar reserva
        Reserva reservaSalva = reservaRepository.save(reserva);
        
        // Atualizar status do apartamento
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.OCUPADO);
        apartamentoRepository.save(apartamento);
        
        // Criar extrato inicial
        criarExtratoInicial(reservaSalva);
        
        // Criar histórico inicial de hóspedes
        criarHistoricoInicial(reservaSalva);
        
        return reservaSalva;
    }
    
    private void criarExtratoInicial(Reserva reserva) {
        for (int i = 0; i < reserva.getQuantidadeDiaria(); i++) {
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            extrato.setDataHoraLancamento(reserva.getDataCheckin().plusDays(i));
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.DIARIA);
            extrato.setQuantidade(1);
            extrato.setValorUnitario(reserva.getDiaria().getValor());
            extrato.setTotalLancamento(reserva.getDiaria().getValor());
            extrato.setDescricao("Diária - " + reserva.getQuantidadeHospede() + " hóspede(s)");
            
            extratoRepository.save(extrato);
        }
    }
    
    private void criarHistoricoInicial(Reserva reserva) {
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reserva);
        historico.setDataHora(reserva.getDataCheckin());
        historico.setQuantidadeAnterior(0);
        historico.setQuantidadeNova(reserva.getQuantidadeHospede());
        historico.setMotivo("Check-in inicial");
        
        historicoRepository.save(historico);
    }
    
    public Reserva alterarQuantidadeHospedes(Long reservaId, Integer novaQuantidade, String motivo) {
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        Integer quantidadeAnterior = reserva.getQuantidadeHospede();
        
        // Buscar nova diária
        Optional<Diaria> novaDiariaOpt = diariaRepository.findByTipoApartamentoAndQuantidade(
            reserva.getApartamento().getTipoApartamento(),
            novaQuantidade
        );
        
        if (novaDiariaOpt.isEmpty()) {
            throw new RuntimeException("Diária não encontrada para a nova quantidade");
        }
        
        // Atualizar reserva
        reserva.setQuantidadeHospede(novaQuantidade);
        reserva.setDiaria(novaDiariaOpt.get());
        
        // Recalcular totais
        recalcularTotais(reserva);
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reserva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(quantidadeAnterior);
        historico.setQuantidadeNova(novaQuantidade);
        historico.setMotivo(motivo);
        
        historicoRepository.save(historico);
        
        return reservaRepository.save(reserva);
    }
    
    public Reserva finalizarReserva(Long reservaId) {
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        
        // Verificar se há saldo devedor
        if (reserva.getTotalApagar().compareTo(BigDecimal.ZERO) > 0) {
            // Criar conta a receber se forma de pagamento for faturado
            ContaAReceber conta = new ContaAReceber();
            conta.setReserva(reserva);
            conta.setCliente(reserva.getCliente());
            conta.setEmpresa(reserva.getCliente().getEmpresa());
            conta.setValor(reserva.getTotalApagar());
            conta.setSaldo(reserva.getTotalApagar());
            conta.setDataVencimento(LocalDateTime.now().toLocalDate().plusDays(30));
            conta.setDescricao("Fatura da reserva " + reserva.getId());
            
            contaReceberRepository.save(conta);
        }
        
        // Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);
        
        // Liberar apartamento
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);
        
        return reservaRepository.save(reserva);
    }
    
    public Reserva cancelarReserva(Long reservaId, String motivo) {
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        reserva.setStatus(Reserva.StatusReservaEnum.CANCELADA);
        
        // Liberar apartamento
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.DISPONIVEL);
        apartamentoRepository.save(apartamento);
        
        // Criar extrato de cancelamento
        ExtratoReserva extrato = new ExtratoReserva();
        extrato.setReserva(reserva);
        extrato.setDataHoraLancamento(LocalDateTime.now());
        extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.ESTORNO);
        extrato.setTotalLancamento(reserva.getTotalHospedagem().negate());
        extrato.setDescricao("Cancelamento: " + motivo);
        
        extratoRepository.save(extrato);
        
        return reservaRepository.save(reserva);
    }
    
    private void recalcularTotais(Reserva reserva) {
        BigDecimal totalDiaria = reserva.getDiaria().getValor()
            .multiply(BigDecimal.valueOf(reserva.getQuantidadeDiaria()));
        
        reserva.setTotalDiaria(totalDiaria);
        reserva.setTotalHospedagem(totalDiaria.add(reserva.getTotalProduto()));
        reserva.setTotalApagar(reserva.getTotalHospedagem()
            .subtract(reserva.getTotalRecebido())
            .subtract(reserva.getDesconto()));
    }
    
    @Transactional(readOnly = true)
    public Optional<Reserva> buscarPorId(Long id) {
        return reservaRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public List<Reserva> listarTodas() {
        return reservaRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<Reserva> buscarAtivas() {
        return reservaRepository.findReservasAtivas();
    }
    
    @Transactional(readOnly = true)
    public List<Reserva> buscarCheckinsDoDia(LocalDateTime data) {
        return reservaRepository.findReservasParaCheckinNaData(data);
    }
    
    @Transactional(readOnly = true)
    public List<Reserva> buscarCheckoutsDoDia(LocalDateTime data) {
        return reservaRepository.findReservasParaCheckoutNaData(data);
    }
    
    @Transactional(readOnly = true)
    public List<Reserva> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return reservaRepository.findReservasPorPeriodo(inicio, fim);
    }
}
