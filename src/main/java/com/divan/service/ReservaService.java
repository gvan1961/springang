package com.divan.service;

import com.divan.dto.ApartamentoResponseDTO;
import com.divan.dto.ClienteResponseDTO;
import com.divan.dto.ComandaRapidaDTO;
import com.divan.dto.LancamentoRapidoRequest;
import com.divan.dto.ReservaResponseDTO;
import com.divan.dto.TransferenciaApartamentoDTO;
import com.divan.entity.*;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.divan.dto.ReservaDetalhesDTO;

import java.time.LocalDate;


import com.divan.entity.ContaAReceber;
import com.divan.repository.ContaAReceberRepository;





@Service
@Transactional
public class ReservaService {
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private DiariaRepository diariaRepository; 
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    @Autowired
    private ProdutoRepository produtoRepository;
    
    @Autowired
    private NotaVendaRepository notaVendaRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoReservaRepository;
    
    @Autowired
    private HistoricoHospedeRepository historicoHospedeRepository;
    
    @Autowired
    private PagamentoRepository pagamentoRepository;   
    
    
    @Autowired
    private ClienteRepository clienteRepository;    
    
    @Autowired
    private ContaAReceberRepository contaReceberRepository;
    
    @Autowired
    private ContaAReceberRepository contaAReceberRepository;
     
    /**
     * Verifica se existe conflito de datas para o apartamento
     */
    private boolean existeConflitoDeDatas(Long apartamentoId, LocalDateTime checkin, LocalDateTime checkout, Long reservaIdExcluir) {
        List<Reserva> reservasDoApartamento = reservaRepository.findByApartamentoId(apartamentoId);
        
        for (Reserva r : reservasDoApartamento) {
            // Ignorar a própria reserva (para casos de alteração)
            if (reservaIdExcluir != null && r.getId().equals(reservaIdExcluir)) {
                continue;
            }
            
            // Ignorar reservas canceladas
            if (r.getStatus() == Reserva.StatusReservaEnum.CANCELADA) {
                continue;
            }
            
            // Verificar sobreposição de datas
            boolean temConflito = 
                (checkin.isBefore(r.getDataCheckout()) && checkout.isAfter(r.getDataCheckin())) ||
                (checkin.isEqual(r.getDataCheckin()) || checkout.isEqual(r.getDataCheckout()));
            
            if (temConflito) {
                System.out.println("❌ Conflito encontrado com reserva #" + r.getId());
                System.out.println("   Reserva existente: " + r.getDataCheckin() + " a " + r.getDataCheckout());
                System.out.println("   Tentativa: " + checkin + " a " + checkout);
                return true;
            }
        }
        
        return false;
    }
   
    // ============================================
    // ✅ MÉTODOS AUXILIARES PRIVADOS
    // ============================================
    
    /**
     * Cria lançamentos de diárias DIA A DIA no extrato
     */
    private void criarExtratosDiarias(Reserva reserva, LocalDateTime dataInicio, LocalDateTime dataFim) {
        long dias = ChronoUnit.DAYS.between(dataInicio.toLocalDate(), dataFim.toLocalDate());
        
        if (dias <= 0) {
            return;
        }
        
        BigDecimal valorDiaria = reserva.getDiaria().getValor();
        
        // Criar um extrato para cada dia
        for (int i = 0; i < dias; i++) {
            LocalDateTime dataDiaria = dataInicio.plusDays(i);
            
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            extrato.setDataHoraLancamento(dataDiaria);
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.DIARIA);
            extrato.setDescricao(String.format("Diária - Dia %02d/%02d/%d", 
                dataDiaria.getDayOfMonth(),
                dataDiaria.getMonthValue(),
                dataDiaria.getYear()));
            extrato.setQuantidade(1);
            extrato.setValorUnitario(valorDiaria);
            extrato.setTotalLancamento(valorDiaria);
            extrato.setNotaVendaId(null);
            
            extratoReservaRepository.save(extrato);
            
            System.out.println("📅 Diária criada para: " + dataDiaria.toLocalDate());
        }
    }
    
    /**
     * Remove lançamentos de diárias quando reduz período
     */
    private void removerExtratosDiarias(Reserva reserva, LocalDateTime dataInicio, LocalDateTime dataFim) {
        List<ExtratoReserva> todosExtratos = extratoReservaRepository.findByReservaId(reserva.getId());
        
        List<ExtratoReserva> extratosParaRemover = new ArrayList<>();
        
        for (ExtratoReserva extrato : todosExtratos) {
            if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA) {
                LocalDateTime dataLancamento = extrato.getDataHoraLancamento();
                
                boolean dentroDoRange = (dataLancamento.isEqual(dataInicio) || dataLancamento.isAfter(dataInicio)) 
                                      && dataLancamento.isBefore(dataFim);
                
                if (dentroDoRange) {
                    extratosParaRemover.add(extrato);
                }
            }
        }
        
        for (ExtratoReserva extrato : extratosParaRemover) {
            System.out.println("🗑️ Removendo diária: " + extrato.getDataHoraLancamento().toLocalDate());
            extratoReservaRepository.delete(extrato);
        }
    }
    
    /**
     * Recalcula valores da reserva após alterações
     */
    private void recalcularValores(Reserva reserva) {
        long dias = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()
        );
        
        reserva.setQuantidadeDiaria((int) dias);
        
        // Buscar diária correta baseada em TIPO + QUANTIDADE DE HÓSPEDES
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        Integer quantidadeHospedes = reserva.getQuantidadeHospede();
        
        Diaria diariaAtualizada = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, quantidadeHospedes)
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para o tipo '%s' com %d hóspede(s)", 
                    tipoApartamento.getTipo(), 
                    quantidadeHospedes)
            ));
        
        reserva.setDiaria(diariaAtualizada);
        
        // Calcular total (valor já inclui quantidade de hóspedes)
        BigDecimal valorDiaria = diariaAtualizada.getValor();
        BigDecimal totalDiaria = valorDiaria.multiply(new BigDecimal(dias));
        
        reserva.setTotalDiaria(totalDiaria);
        
        // Recalcular total da hospedagem (diárias + consumo)
        BigDecimal totalConsumo = reserva.getTotalProduto();
        
        BigDecimal totalHospedagem = totalDiaria.add(totalConsumo);
        reserva.setTotalHospedagem(totalHospedagem);
        reserva.setTotalApagar(totalHospedagem.subtract(reserva.getTotalRecebido()));
    }
    
    // ============================================
    // ✅ CRIAR RESERVA
    // ============================================
    
    public Reserva criarReserva(Reserva reserva) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("📝 CRIANDO NOVA RESERVA");
        System.out.println("═══════════════════════════════════════════");
        
        // ✅ VERIFICAR CONFLITO DE DATAS (em vez de status do apartamento)
        boolean temConflito = existeConflitoDeDatas(
            reserva.getApartamento().getId(),
            reserva.getDataCheckin(),
            reserva.getDataCheckout(),
            null
        );
        
        if (temConflito) {
            throw new RuntimeException("❌ JÁ EXISTE UMA RESERVA para este apartamento no período selecionado");
        }
        
        // Validar quantidade de hóspedes
        if (reserva.getQuantidadeHospede() > reserva.getApartamento().getCapacidade()) {
            throw new RuntimeException("Quantidade de hóspedes excede a capacidade do apartamento");
        }

        // Check-in: 14:00, Check-out: 13:00
        LocalDateTime checkinPadronizado = reserva.getDataCheckin().toLocalDate().atTime(14, 0);
        LocalDateTime checkoutPadronizado = reserva.getDataCheckout().toLocalDate().atTime(13, 0);
        
        reserva.setDataCheckin(checkinPadronizado);
        reserva.setDataCheckout(checkoutPadronizado);
        
        // Calcular quantidade de diárias (dias)
        long dias = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()
        );
        
        if (dias <= 0) {
            throw new RuntimeException("Data de checkout deve ser posterior ao checkin");
        }
        
        reserva.setQuantidadeDiaria((int) dias);
        
        // Buscar diária baseada no TIPO DE APARTAMENTO + QUANTIDADE DE HÓSPEDES
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        Integer quantidadeHospedes = reserva.getQuantidadeHospede();
        
        Diaria diariaEscolhida = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, quantidadeHospedes)
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para o tipo '%s' com %d hóspede(s)", 
                    tipoApartamento.getTipo(), 
                    quantidadeHospedes)
            ));
        
        reserva.setDiaria(diariaEscolhida);
        
        // Calcular total das diárias
        BigDecimal valorDiaria = diariaEscolhida.getValor();
        BigDecimal totalDiaria = valorDiaria.multiply(new BigDecimal(dias));
        
        reserva.setTotalDiaria(totalDiaria);
        reserva.setTotalHospedagem(totalDiaria);
        
        // Inicializar valores
        reserva.setTotalRecebido(BigDecimal.ZERO);
        reserva.setTotalProduto(BigDecimal.ZERO);
        reserva.setTotalApagar(totalDiaria);
        
        // ✅✅✅ DEFINIR STATUS BASEADO NA DATA DE CHECK-IN ✅✅✅
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime dataCheckin = reserva.getDataCheckin();
        
        if (dataCheckin.isAfter(agora)) {
            // ✅ CHECK-IN É FUTURO → PRE_RESERVA
            reserva.setStatus(Reserva.StatusReservaEnum.PRE_RESERVA);
            
            System.out.println("📅 Reserva criada como PRÉ-RESERVA (check-in futuro)");
            System.out.println("   Check-in: " + dataCheckin.toLocalDate());
            System.out.println("   ⚠️ Apartamento NÃO será ocupado agora");
            
            // ✅ NÃO MUDAR STATUS DO APARTAMENTO
            // Ele continua no status atual (pode estar ocupado por outra reserva ou disponível)
            
        } else {
            // ✅ CHECK-IN É HOJE OU PASSOU → ATIVA
            reserva.setStatus(Reserva.StatusReservaEnum.ATIVA);
            
            System.out.println("✅ Reserva criada como ATIVA (check-in hoje/passado)");
            
            // ✅ Atualizar status do apartamento para OCUPADO
            Apartamento apartamento = reserva.getApartamento();
            apartamento.setStatus(Apartamento.StatusEnum.OCUPADO);
            apartamentoRepository.save(apartamento);
            
            System.out.println("   Apartamento " + apartamento.getNumeroApartamento() + " → OCUPADO");
        }
        
        // Criar nota de venda para consumo
        NotaVenda notaVenda = new NotaVenda();
        notaVenda.setReserva(reserva);
        notaVenda.setDataHoraVenda(LocalDateTime.now());
        notaVenda.setTotal(BigDecimal.ZERO);
        notaVenda.setTipoVenda(NotaVenda.TipoVendaEnum.APARTAMENTO);
        notaVenda.setStatus(NotaVenda.Status.ABERTA);
        notaVenda.setItens(new ArrayList<>());
        
        if (reserva.getNotasVenda() == null) {
            reserva.setNotasVenda(new ArrayList<>());
        }
        reserva.getNotasVenda().add(notaVenda);
        
        // Salvar reserva
        Reserva salva = reservaRepository.save(reserva);
        
        // ✅ CRIAR LANÇAMENTOS DE DIÁRIAS DIA A DIA
        criarExtratosDiarias(salva, reserva.getDataCheckin(), reserva.getDataCheckout());
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(salva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historico.setQuantidadeNova(reserva.getQuantidadeHospede());
        historico.setMotivo(String.format("Reserva criada - %d hóspede(s) - Check-in: %s - Check-out: %s - Status: %s", 
            reserva.getQuantidadeHospede(),
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate(),
            salva.getStatus()));
        
        historicoHospedeRepository.save(historico);
        
        System.out.println("✅ Reserva criada: #" + salva.getId());
        System.out.println("   Status: " + salva.getStatus());
        System.out.println("💰 Diária para " + quantidadeHospedes + " hóspede(s): R$ " + valorDiaria);
        System.out.println("📅 Total " + dias + " dia(s): R$ " + totalDiaria);
        System.out.println("═══════════════════════════════════════════");
        
        return salva;
    }
    
    // ============================================
    // ✅ BUSCAR RESERVAS
    // ============================================
    
    public Optional<Reserva> buscarPorId(Long id) {
        return reservaRepository.findById(id);
    }
    
    public List<Reserva> listarTodas() {
        return reservaRepository.findAll();
    }
    
    public List<Reserva> buscarAtivas() {
        return reservaRepository.findByStatus(Reserva.StatusReservaEnum.ATIVA);
    }
    
    public List<Reserva> buscarCheckinsDoDia(LocalDateTime data) {
        LocalDateTime inicioDia = data.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1);
        return reservaRepository.findByDataCheckinBetween(inicioDia, fimDia);
    }
    
    public List<Reserva> buscarCheckoutsDoDia(LocalDateTime data) {
        LocalDateTime inicioDia = data.toLocalDate().atStartOfDay();
        LocalDateTime fimDia = inicioDia.plusDays(1);
        return reservaRepository.findByDataCheckoutBetween(inicioDia, fimDia);
    }
    
    public List<Reserva> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return reservaRepository.findByDataCheckinBetweenOrDataCheckoutBetween(inicio, fim, inicio, fim);
    }
    
    // ============================================
    // ✅ MÉTODOS COM DTO
    // ============================================
    
    @Transactional(readOnly = true)
    public ReservaResponseDTO buscarPorIdDTO(Long id) {
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        // Força o carregamento dos extratos e históricos (Lazy Loading)
        reserva.getExtratos().size();
        reserva.getHistoricos().size();
        
        return converterParaDTO(reserva);
    }
    
    public List<ReservaResponseDTO> listarTodasDTO() {
        return reservaRepository.findAll().stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }
    
    public List<ReservaResponseDTO> listarPorStatusDTO(Reserva.StatusReservaEnum status) {
        return reservaRepository.findByStatus(status).stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }
    
    private ReservaResponseDTO converterParaDTO(Reserva reserva) {
        ReservaResponseDTO dto = new ReservaResponseDTO();
        
        dto.setId(reserva.getId());
        
        // Converter cliente
        if (reserva.getCliente() != null) {
            ClienteResponseDTO clienteDTO = new ClienteResponseDTO();
            clienteDTO.setId(reserva.getCliente().getId());
            clienteDTO.setNome(reserva.getCliente().getNome());
            clienteDTO.setCpf(reserva.getCliente().getCpf());
            clienteDTO.setTelefone(reserva.getCliente().getCelular());
            clienteDTO.setEmail("");
            clienteDTO.setEndereco(reserva.getCliente().getEndereco());
            clienteDTO.setCidade(reserva.getCliente().getCidade());
            clienteDTO.setEstado(reserva.getCliente().getEstado());
            clienteDTO.setCep(reserva.getCliente().getCep());
            dto.setCliente(clienteDTO);
        }
        
        // Converter apartamento
        if (reserva.getApartamento() != null) {
            ApartamentoResponseDTO apartamentoDTO = new ApartamentoResponseDTO();
            apartamentoDTO.setId(reserva.getApartamento().getId());
            apartamentoDTO.setNumeroApartamento(reserva.getApartamento().getNumeroApartamento());
            apartamentoDTO.setCapacidade(reserva.getApartamento().getCapacidade());
            apartamentoDTO.setCamasDoApartamento(reserva.getApartamento().getCamasDoApartamento());
            apartamentoDTO.setTv(reserva.getApartamento().getTv());
            apartamentoDTO.setStatus(reserva.getApartamento().getStatus());
            
            if (reserva.getApartamento().getTipoApartamento() != null) {
                apartamentoDTO.setTipoApartamentoId(reserva.getApartamento().getTipoApartamento().getId());
                apartamentoDTO.setTipoApartamentoNome(reserva.getApartamento().getTipoApartamento().getTipo().toString());
                apartamentoDTO.setTipoApartamentoDescricao(reserva.getApartamento().getTipoApartamento().getDescricao());
            }
            
            dto.setApartamento(apartamentoDTO);
        }
        
        dto.setQuantidadeHospede(reserva.getQuantidadeHospede());
        dto.setDataCheckin(reserva.getDataCheckin());
        dto.setDataCheckout(reserva.getDataCheckout());
        dto.setQuantidadeDiaria(reserva.getQuantidadeDiaria());
        dto.setValorDiaria(reserva.getDiaria() != null ? reserva.getDiaria().getValor() : BigDecimal.ZERO);
        dto.setTotalDiaria(reserva.getTotalDiaria());
        dto.setTotalHospedagem(reserva.getTotalHospedagem());
        dto.setTotalRecebido(reserva.getTotalRecebido());
        dto.setTotalApagar(reserva.getTotalApagar());
        dto.setStatus(reserva.getStatus());
        dto.setObservacoes("");
        
        dto.setExtratos(reserva.getExtratos());
        dto.setHistoricos(reserva.getHistoricos());
        
        return dto;
    }
    
    // ============================================
    // ✅ ALTERAR DADOS DA RESERVA
    // ============================================
    
    public Reserva alterarQuantidadeHospedes(Long id, Integer novaQuantidade, String motivo) {
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        if (novaQuantidade > reserva.getApartamento().getCapacidade()) {
            throw new RuntimeException("Quantidade de hóspedes excede capacidade do apartamento");
        }
        
        Integer quantidadeAnterior = reserva.getQuantidadeHospede();
        BigDecimal totalAnterior = reserva.getTotalDiaria();
        
        // ✅ DATA ATUAL - A partir de hoje que muda
        LocalDateTime dataAtual = LocalDateTime.now();
        LocalDateTime proximoDia = dataAtual.toLocalDate().plusDays(1).atStartOfDay();
        
        System.out.println("📅 Alterando quantidade de hóspedes a partir de: " + proximoDia.toLocalDate());
        
        // Atualizar quantidade
        reserva.setQuantidadeHospede(novaQuantidade);
        
        // Buscar nova diária
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        Diaria diariaAtualizada = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, novaQuantidade)
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para o tipo '%s' com %d hóspede(s)", 
                    tipoApartamento.getTipo(), 
                    novaQuantidade)
            ));
        
        reserva.setDiaria(diariaAtualizada);
        
        // ✅ AJUSTAR APENAS AS DIÁRIAS FUTURAS
        ajustarDiariasFuturas(reserva, proximoDia, quantidadeAnterior, novaQuantidade);
        
        // Recalcular totais
        recalcularTotaisReserva(reserva);
        
        BigDecimal diferenca = reserva.getTotalDiaria().subtract(totalAnterior);
        
        // Salvar reserva
        Reserva reservaSalva = reservaRepository.save(reserva);
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reservaSalva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(quantidadeAnterior);
        historico.setQuantidadeNova(novaQuantidade);
        historico.setMotivo(String.format("Quantidade de hóspedes alterada de %d para %d a partir de %s - %s - Diferença: R$ %s", 
            quantidadeAnterior, 
            novaQuantidade,
            proximoDia.toLocalDate(),
            motivo != null && !motivo.isEmpty() ? motivo : "Sem motivo informado",
            diferenca.abs()));
        
        historicoHospedeRepository.save(historico);
        
        System.out.println("👥 Hóspedes alterados de " + quantidadeAnterior + " para " + novaQuantidade);
        System.out.println("💰 Nova diária (a partir de " + proximoDia.toLocalDate() + "): R$ " + diariaAtualizada.getValor());
        System.out.println("💰 Diferença no valor total: R$ " + diferenca);
        
        return reservaSalva;
    }
    
    private void ajustarDiariasFuturas(Reserva reserva, LocalDateTime dataInicio, Integer qtdAnterior, Integer qtdNova) {
        // Buscar diária antiga e nova
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        
        Diaria diariaAntiga = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, qtdAnterior)
            .orElseThrow(() -> new RuntimeException("Diária antiga não encontrada"));
        
        Diaria diariaNova = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, qtdNova)
            .orElseThrow(() -> new RuntimeException("Diária nova não encontrada"));
        
        BigDecimal valorAntigo = diariaAntiga.getValor();
        BigDecimal valorNovo = diariaNova.getValor();
        BigDecimal diferenca = valorNovo.subtract(valorAntigo);
        
        // Buscar todas as diárias futuras
        List<ExtratoReserva> todosExtratos = extratoReservaRepository.findByReservaOrderByDataHoraLancamento(reserva);
        
        int diasAjustados = 0;
        
        for (ExtratoReserva extrato : todosExtratos) {
            if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA) {
                LocalDateTime dataLancamento = extrato.getDataHoraLancamento();
                
                // ✅ AJUSTAR APENAS SE FOR DIA FUTURO (>= dataInicio)
                if (!dataLancamento.isBefore(dataInicio)) {
                    // Criar AJUSTE (positivo ou negativo)
                    ExtratoReserva ajuste = new ExtratoReserva();
                    ajuste.setReserva(reserva);
                    ajuste.setDataHoraLancamento(dataLancamento);
                    ajuste.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.ESTORNO);
                    ajuste.setDescricao(String.format("Ajuste - Alteração de %d para %d hóspede(s)", qtdAnterior, qtdNova));
                    ajuste.setQuantidade(1);
                    ajuste.setValorUnitario(diferenca);
                    ajuste.setTotalLancamento(diferenca); // Positivo se aumentou, negativo se diminuiu
                    ajuste.setNotaVendaId(null);
                    
                    extratoReservaRepository.save(ajuste);
                    diasAjustados++;
                    
                    System.out.println("📝 Ajuste criado para " + dataLancamento.toLocalDate() + ": R$ " + diferenca);
                } else {
                    System.out.println("⏭️ Mantendo diária de " + dataLancamento.toLocalDate() + " com valor original");
                }
            }
        }
        
        System.out.println("✅ Total de dias ajustados: " + diasAjustados);
    }

    /**
     * Recalcula os totais da reserva somando todos os extratos
     */
    private void recalcularTotaisReserva(Reserva reserva) {
        List<ExtratoReserva> todosExtratos = extratoReservaRepository.findByReservaOrderByDataHoraLancamento(reserva);
        
        // ✅ SOMAR TODAS AS DIÁRIAS + ESTORNOS
        BigDecimal totalDiarias = BigDecimal.ZERO;
        for (ExtratoReserva extrato : todosExtratos) {
            if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA ||
                extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.ESTORNO) {
                totalDiarias = totalDiarias.add(extrato.getTotalLancamento());
            }
        }
        
        // ✅ SOMAR TODOS OS PRODUTOS
        BigDecimal totalProdutos = BigDecimal.ZERO;
        for (ExtratoReserva extrato : todosExtratos) {
            if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.PRODUTO) {
                totalProdutos = totalProdutos.add(extrato.getTotalLancamento());
            }
        }
        
        // ✅ ATUALIZAR TOTAIS DA RESERVA
        reserva.setTotalDiaria(totalDiarias);
        reserva.setTotalProduto(totalProdutos);
        reserva.setTotalHospedagem(totalDiarias.add(totalProdutos));
        reserva.setTotalApagar(reserva.getTotalHospedagem().subtract(reserva.getTotalRecebido()));
           
        
        
        System.out.println("💰 Total de diárias recalculado: R$ " + totalDiarias);
        System.out.println("🛒 Total de produtos recalculado: R$ " + totalProdutos);
        System.out.println("💵 Total hospedagem: R$ " + reserva.getTotalHospedagem());
        System.out.println("💳 Total a pagar: R$ " + reserva.getTotalApagar());
    }
    
    public Reserva alterarDataCheckout(Long id, LocalDateTime novaDataCheckout, String motivo) {
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        if (novaDataCheckout.isBefore(reserva.getDataCheckin())) {
            throw new RuntimeException("Data de checkout não pode ser antes do checkin");
        }
        
        LocalDateTime checkoutAnterior = reserva.getDataCheckout();
        BigDecimal totalAnterior = reserva.getTotalDiaria();
        
        // Calcular nova quantidade de diárias
        long diasNovos = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            novaDataCheckout.toLocalDate()
        );
        
        long diasAntigos = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            checkoutAnterior.toLocalDate()
        );
        
        // Atualizar data de checkout
        reserva.setDataCheckout(novaDataCheckout);
        
        // Recalcular valores
        recalcularValores(reserva);
        
        BigDecimal diferenca = reserva.getTotalDiaria().subtract(totalAnterior);
        
        // Ajustar extratos de diárias
        if (diasNovos > diasAntigos) {
            // ✅ ADICIONAR DIAS
            System.out.println("➕ Adicionando " + (diasNovos - diasAntigos) + " dia(s)");
            criarExtratosDiarias(reserva, checkoutAnterior, novaDataCheckout);
            
        } else if (diasNovos < diasAntigos) {
            // ✅ REMOVER DIAS - CRIAR ESTORNOS
            System.out.println("➖ Removendo " + (diasAntigos - diasNovos) + " dia(s) - Criando estornos");
            criarEstornosDiarias(reserva, novaDataCheckout, checkoutAnterior);
        }
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reserva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historico.setQuantidadeNova(reserva.getQuantidadeHospede());
        historico.setMotivo(String.format("Checkout alterado de %s para %s - %s - Diferença: R$ %s", 
            checkoutAnterior.toLocalDate(),
            novaDataCheckout.toLocalDate(),
            motivo != null && !motivo.isEmpty() ? motivo : "Sem motivo informado",
            diferenca.abs()));
        
        historicoHospedeRepository.save(historico);
        
        Reserva salva = reservaRepository.save(reserva);
        
        System.out.println("📅 Checkout alterado de " + checkoutAnterior.toLocalDate() + " para " + novaDataCheckout.toLocalDate());
        System.out.println("💰 Diferença no valor: R$ " + diferenca);
        
        return salva;
    }
    
    private void criarEstornosDiarias(Reserva reserva, LocalDateTime dataInicio, LocalDateTime dataFim) {
        long dias = ChronoUnit.DAYS.between(dataInicio.toLocalDate(), dataFim.toLocalDate());
        
        if (dias <= 0) {
            return;
        }
        
        BigDecimal valorDiaria = reserva.getDiaria().getValor();
        
        // Criar um ESTORNO (valor negativo) para cada dia removido
        for (int i = 0; i < dias; i++) {
            LocalDateTime dataDiaria = dataInicio.plusDays(i);
            
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            extrato.setDataHoraLancamento(LocalDateTime.now()); // Data atual do estorno
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.ESTORNO);
            extrato.setDescricao(String.format("Estorno - Diária dia %02d/%02d/%d removida", 
                dataDiaria.getDayOfMonth(),
                dataDiaria.getMonthValue(),
                dataDiaria.getYear()));
            extrato.setQuantidade(1);
            extrato.setValorUnitario(valorDiaria.negate()); // ✅ VALOR NEGATIVO
            extrato.setTotalLancamento(valorDiaria.negate()); // ✅ VALOR NEGATIVO (crédito)
            extrato.setNotaVendaId(null);
            
            extratoReservaRepository.save(extrato);
            
            System.out.println("💳 Estorno criado para: " + dataDiaria.toLocalDate() + " - R$ " + valorDiaria.negate());
        }
    }
    
    // ============================================
    // ✅ CONSUMO (PRODUTOS)
    // ============================================
    
    public Reserva adicionarProdutoAoConsumo(Long reservaId, Long produtoId, Integer quantidade, String observacao) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        Produto produto = produtoRepository.findById(produtoId)
            .orElseThrow(() -> new RuntimeException("Produto não encontrado"));
        
        // Verificar estoque
        if (produto.getQuantidade() < quantidade) {
            throw new RuntimeException("Estoque insuficiente. Disponível: " + produto.getQuantidade());
        }
        
        // Buscar nota de venda APARTAMENTO da reserva
        NotaVenda notaVenda = reserva.getNotasVenda().stream()
            .filter(nv -> nv.getTipoVenda().equals(NotaVenda.TipoVendaEnum.APARTAMENTO))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Nota de venda não encontrada"));
        
        // Calcular valor total do item
        BigDecimal valorTotalItem = produto.getValorVenda().multiply(new BigDecimal(quantidade));
        
        // Criar item de venda
        ItemVenda item = new ItemVenda();
        item.setProduto(produto);
        item.setQuantidade(quantidade);
        item.setValorUnitario(produto.getValorVenda());
        item.setTotalItem(valorTotalItem);
        item.setNotaVenda(notaVenda);
        
        // Adicionar à nota
        if (notaVenda.getItens() == null) {
            notaVenda.setItens(new ArrayList<>());
        }
        notaVenda.getItens().add(item);
        
        // Recalcular total da nota
        BigDecimal novoTotal = notaVenda.getItens().stream()
            .map(ItemVenda::getTotalItem)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        notaVenda.setTotal(novoTotal);
        
        // Atualizar estoque
        produto.setQuantidade(produto.getQuantidade() - quantidade);
        produtoRepository.save(produto);
        
        // Atualizar totais da reserva
        reserva.setTotalProduto(novoTotal);
        reserva.setTotalHospedagem(reserva.getTotalDiaria().add(novoTotal));
        reserva.setTotalApagar(reserva.getTotalHospedagem().subtract(reserva.getTotalRecebido()));
        
        // Salvar reserva (isso salva a nota em cascata)
        Reserva reservaSalva = reservaRepository.save(reserva);
        
        // Buscar a nota novamente para ter certeza do ID
        NotaVenda notaSalva = reservaSalva.getNotasVenda().stream()
            .filter(nv -> nv.getTipoVenda().equals(NotaVenda.TipoVendaEnum.APARTAMENTO))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Nota de venda não encontrada após salvar"));
        
        // Criar lançamento no extrato
        ExtratoReserva extrato = new ExtratoReserva();
        extrato.setReserva(reservaSalva);
        extrato.setDataHoraLancamento(LocalDateTime.now());
        extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.PRODUTO);
        extrato.setDescricao(produto.getNomeProduto());
        extrato.setQuantidade(quantidade);
        extrato.setValorUnitario(produto.getValorVenda());
        extrato.setTotalLancamento(valorTotalItem);
        extrato.setNotaVendaId(notaSalva.getId());
        
        extratoReservaRepository.save(extrato);
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reservaSalva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reservaSalva.getQuantidadeHospede());
        historico.setQuantidadeNova(reservaSalva.getQuantidadeHospede());
        historico.setMotivo(String.format("Produto adicionado: %s - Qtd: %d - Total: R$ %s%s", 
            produto.getNomeProduto(),
            quantidade,
            valorTotalItem,
            observacao != null && !observacao.isEmpty() ? " - Obs: " + observacao : ""));
        
        historicoHospedeRepository.save(historico);
        
        System.out.println("🛒 Produto adicionado ao consumo: " + produto.getNomeProduto() + " x" + quantidade);
        System.out.println("📝 Lançamento criado no extrato: R$ " + valorTotalItem);
        
        return reservaSalva;
    }
    
    public List<ItemVenda> listarConsumoPorReserva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        if (reserva.getNotasVenda() == null || reserva.getNotasVenda().isEmpty()) {
            return new ArrayList<>();
        }
        
        return reserva.getNotasVenda().stream()
            .filter(nv -> nv.getItens() != null)
            .flatMap(nv -> nv.getItens().stream())
            .collect(Collectors.toList());
    }
    
    public List<NotaVenda> listarNotasVendaPorReserva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        return reserva.getNotasVenda() != null ? reserva.getNotasVenda() : new ArrayList<>();
    }
    
    // ============================================
    // ✅ FINALIZAR E CANCELAR
    // ============================================
    
    @Transactional
    public Reserva finalizarReserva(Long reservaId) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🚀 FINALIZANDO RESERVA: " + reservaId);
        System.out.println("═══════════════════════════════════════════");
        
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));

        System.out.println("📊 VALORES DA RESERVA:");
        System.out.println("   Total Hospedagem: R$ " + reserva.getTotalHospedagem());
        System.out.println("   Total Recebido: R$ " + reserva.getTotalRecebido());
        System.out.println("   Total A Pagar: R$ " + reserva.getTotalApagar());

        // ✅ VERIFICAR SALDO E CRÉDITO
        BigDecimal saldoDevedor = reserva.getTotalApagar();
        boolean temSaldoDevedor = saldoDevedor != null && saldoDevedor.compareTo(BigDecimal.ZERO) > 0;
        
        if (temSaldoDevedor) {
            // ✅✅✅ VERIFICAR SE O CLIENTE TEM CRÉDITO APROVADO ✅✅✅
            Cliente cliente = reserva.getCliente();
            boolean creditoAprovado = cliente.getCreditoAprovado() != null && cliente.getCreditoAprovado();
            boolean temEmpresa = cliente.getEmpresa() != null;
            boolean podeCredito = creditoAprovado || temEmpresa;
            
            System.out.println("👤 Cliente: " + cliente.getNome());
            System.out.println("   Crédito Aprovado: " + creditoAprovado);
            System.out.println("   Tem Empresa: " + temEmpresa);
            System.out.println("   Pode Faturar: " + podeCredito);
            
            if (!podeCredito) {
                System.err.println("❌ CLIENTE NÃO POSSUI CRÉDITO APROVADO!");
                throw new RuntimeException(
                    "❌ CLIENTE NÃO POSSUI CRÉDITO APROVADO.\n\n" +
                    "Cliente: " + cliente.getNome() + "\n" +
                    "Saldo devedor: R$ " + saldoDevedor + "\n\n" +
                    "Registre o pagamento antes de finalizar a reserva."
                );
            }
            
            System.out.println("⚠️ RESERVA FATURADA - Cliente possui crédito aprovado");
        } else {
            System.out.println("✅ RESERVA PAGA - Sem saldo devedor");
        }
        
        // ✅ CRIAR CONTA A RECEBER (PAGA OU FATURADA)
        try {
            criarContaAReceber(reserva);
            System.out.println("✅ Registro criado em Contas a Receber!");
        } catch (Exception e) {
            System.err.println("❌ ERRO ao criar registro: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro ao criar registro de finalização: " + e.getMessage());
        }

        // Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);

        // Liberar apartamento para limpeza
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);

        Reserva salva = reservaRepository.save(reserva);

        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ FINALIZAÇÃO CONCLUÍDA!");
        System.out.println("   Status: " + salva.getStatus());
        System.out.println("   Apartamento " + apartamento.getNumeroApartamento() + " → LIMPEZA");
        System.out.println("═══════════════════════════════════════════");

        return salva;
    }
    
    
    
    // ✅ MÉTODO PARA CRIAR CONTA A RECEBER
    private void criarContaAReceber(Reserva reserva) {
        try {
            ContaAReceber conta = new ContaAReceber();
            
            // ✅ CAMPOS OBRIGATÓRIOS
            conta.setReserva(reserva);
            conta.setCliente(reserva.getCliente());
            
            // ✅ VERIFICAR SE ESTÁ PAGO OU NÃO
            boolean estaPago = reserva.getTotalApagar().compareTo(BigDecimal.ZERO) == 0;
            
            if (estaPago) {
                // ✅ RESERVA PAGA (saldo = 0)
                conta.setValor(reserva.getTotalHospedagem()); // Valor total original
                conta.setValorPago(reserva.getTotalRecebido()); // Já recebeu tudo
                conta.setSaldo(BigDecimal.ZERO); // Sem saldo devedor
                conta.setStatus(ContaAReceber.StatusContaEnum.PAGA);
                conta.setDataPagamento(LocalDate.now()); // Data do pagamento
                conta.setDataVencimento(LocalDate.now()); // Vencimento = hoje (já pago)
                conta.setDescricao("Reserva PAGA #" + reserva.getId() + 
                                  " - Apt " + reserva.getApartamento().getNumeroApartamento());
                conta.setObservacao("Pagamento efetuado no check-out");
                
                System.out.println("💚 Registro PAGO criado:");
                System.out.println("   Valor Total: R$ " + conta.getValor());
                System.out.println("   Valor Pago: R$ " + conta.getValorPago());
                System.out.println("   Status: PAGA");
                
            } else {
                // ⚠️ RESERVA FATURADA (tem saldo devedor)
                conta.setValor(reserva.getTotalApagar()); // Valor a receber
                conta.setValorPago(BigDecimal.ZERO); // Ainda não pagou
                conta.setSaldo(reserva.getTotalApagar()); // Saldo = valor total
                conta.setStatus(ContaAReceber.StatusContaEnum.EM_ABERTO);
                conta.setDataPagamento(null); // Ainda não foi pago
                conta.setDataVencimento(LocalDate.now().plusDays(30)); // 30 dias para pagar
                conta.setDescricao("Reserva FATURADA #" + reserva.getId() + 
                                  " - Apt " + reserva.getApartamento().getNumeroApartamento());
                conta.setObservacao("Pagamento faturado - prazo 30 dias");
                
                System.out.println("💰 Conta a Receber criada:");
                System.out.println("   Valor: R$ " + conta.getValor());
                System.out.println("   Saldo: R$ " + conta.getSaldo());
                System.out.println("   Vencimento: " + conta.getDataVencimento());
                System.out.println("   Status: EM_ABERTO");
            }
            
            // ✅ CAMPOS COMUNS
            conta.setDataCriacao(LocalDateTime.now());
            
            // ✅ EMPRESA (se o cliente tiver)
            if (reserva.getCliente().getEmpresa() != null) {
                conta.setEmpresa(reserva.getCliente().getEmpresa());
            }
            
            contaAReceberRepository.save(conta);
            
            System.out.println("✅ Registro salvo com sucesso!");
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao criar registro: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro ao criar registro: " + e.getMessage());
        }
    }
    
    public Reserva cancelarReserva(Long reservaId, String motivo) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        reserva.setStatus(Reserva.StatusReservaEnum.CANCELADA);
        
        // Liberar apartamento
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.DISPONIVEL);
        apartamentoRepository.save(apartamento);
        
        // Devolver produtos ao estoque
        if (reserva.getNotasVenda() != null) {
            for (NotaVenda nota : reserva.getNotasVenda()) {
                if (nota.getItens() != null) {
                    for (ItemVenda item : nota.getItens()) {
                        Produto produto = item.getProduto();
                        produto.setQuantidade(produto.getQuantidade() + item.getQuantidade());
                        produtoRepository.save(produto);
                    }
                }
            }
        }
        
        Reserva salva = reservaRepository.save(reserva);
        
        System.out.println("❌ Reserva cancelada: " + reservaId);
        System.out.println("💬 Motivo: " + motivo);
        
        return salva;
    }
    
    // ============================================
    // ✅ GERAR EXTRATOS RETROATIVOS (DEBUG)
    // ============================================
    
    @Transactional
    public void gerarExtratosRetroativos(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        System.out.println("🔄 Gerando extratos retroativos para reserva " + reservaId);
        
        // Verificar se já existem diárias
        long existeDiaria = extratoReservaRepository.findByReservaId(reservaId).stream()
            .filter(e -> e.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA)
            .count();
        
        if (existeDiaria == 0) {
            criarExtratosDiarias(reserva, reserva.getDataCheckin(), reserva.getDataCheckout());
        }
        
        // Gerar extratos de produtos
        if (reserva.getNotasVenda() != null) {
            for (NotaVenda nota : reserva.getNotasVenda()) {
                if (nota.getItens() != null) {
                    for (ItemVenda item : nota.getItens()) {
                        ExtratoReserva extratoProduto = new ExtratoReserva();
                        extratoProduto.setReserva(reserva);
                        extratoProduto.setDataHoraLancamento(nota.getDataHoraVenda());
                        extratoProduto.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.PRODUTO);
                        extratoProduto.setDescricao(item.getProduto().getNomeProduto());
                        extratoProduto.setQuantidade(item.getQuantidade());
                        extratoProduto.setValorUnitario(item.getValorUnitario());
                        extratoProduto.setTotalLancamento(item.getTotalItem());
                        extratoProduto.setNotaVendaId(nota.getId());
                        
                        extratoReservaRepository.save(extratoProduto);
                        System.out.println("✅ Extrato de produto criado: " + item.getProduto().getNomeProduto());
                    }
                }
            }
        }
        
        System.out.println("✅ Extratos retroativos gerados com sucesso!");
    }
    
    @Transactional
    public Reserva transferirApartamento(TransferenciaApartamentoDTO dto) {
        // Buscar reserva
        Reserva reserva = reservaRepository.findById(dto.getReservaId())
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        // Buscar novo apartamento
        Apartamento novoApartamento = apartamentoRepository.findById(dto.getNovoApartamentoId())
            .orElseThrow(() -> new RuntimeException("Apartamento não encontrado"));
        
        Apartamento apartamentoAntigo = reserva.getApartamento();
        
        // ========== VALIDAÇÕES ==========
        
        if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
            throw new RuntimeException("Apenas reservas ATIVAS podem ser transferidas");
        }
        
        if (apartamentoAntigo.getId().equals(novoApartamento.getId())) {
            throw new RuntimeException("O apartamento de destino é o mesmo da reserva atual");
        }
        
        if (!novoApartamento.getStatus().equals(Apartamento.StatusEnum.DISPONIVEL)) {
            throw new RuntimeException("O apartamento de destino não está disponível");
        }
        
        if (reserva.getQuantidadeHospede() > novoApartamento.getCapacidade()) {
            throw new RuntimeException(
                String.format("Apartamento %s não suporta %d hóspede(s). Capacidade: %d", 
                    novoApartamento.getNumeroApartamento(),
                    reserva.getQuantidadeHospede(),
                    novoApartamento.getCapacidade())
            );
        }
        
        // ========== DEFINIR DATA DA TRANSFERÊNCIA ==========
        
        LocalDateTime dataTransferencia = dto.getDataTransferencia();
        boolean transferenciaImediata = false;
        
        if (dataTransferencia == null) {
            // Transferência IMEDIATA (hoje)
            dataTransferencia = LocalDateTime.now();
            transferenciaImediata = true;
            System.out.println("🔄 Transferência IMEDIATA");
        } else {
            // Transferência FUTURA
            LocalDateTime amanha = LocalDateTime.now().plusDays(1).toLocalDate().atStartOfDay();
            
            if (dataTransferencia.isBefore(amanha)) {
                throw new RuntimeException("Transferência futura deve ser a partir de amanhã");
            }
            
            if (dataTransferencia.isAfter(reserva.getDataCheckout())) {
                throw new RuntimeException("Data de transferência deve ser antes do checkout");
            }
            
            System.out.println("📅 Transferência FUTURA para: " + dataTransferencia.toLocalDate());
        }
        
        // ========== BUSCAR NOVA DIÁRIA ==========
        
        TipoApartamento novoTipo = novoApartamento.getTipoApartamento();
        TipoApartamento tipoAntigo = apartamentoAntigo.getTipoApartamento();
        
        Diaria novaDiaria = diariaRepository.findByTipoApartamentoAndQuantidade(
                novoTipo, 
                reserva.getQuantidadeHospede())
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para tipo '%s' com %d hóspede(s)", 
                    novoTipo.getTipo(), 
                    reserva.getQuantidadeHospede())
            ));
        
        Diaria diariaAntiga = reserva.getDiaria();
        
        BigDecimal valorAntigo = diariaAntiga.getValor();
        BigDecimal valorNovo = novaDiaria.getValor();
        BigDecimal diferenca = valorNovo.subtract(valorAntigo);
        
        boolean mudouTipo = !tipoAntigo.getId().equals(novoTipo.getId());
        
        System.out.println("🏨 Transferindo de: " + apartamentoAntigo.getNumeroApartamento() + 
                           " (" + tipoAntigo.getTipo() + ") → " + 
                           novoApartamento.getNumeroApartamento() + 
                           " (" + novoTipo.getTipo() + ")");
        
        if (mudouTipo) {
            System.out.println("💰 Valor antigo: R$ " + valorAntigo + " → Novo: R$ " + valorNovo + 
                              " (Diferença: R$ " + diferenca + ")");
        }
        
        // ========== ATUALIZAR STATUS DOS APARTAMENTOS ==========
        
        if (transferenciaImediata) {
            // Liberar apartamento antigo
            apartamentoAntigo.setStatus(Apartamento.StatusEnum.LIMPEZA);
            apartamentoRepository.save(apartamentoAntigo);
            
            // Ocupar novo apartamento
            novoApartamento.setStatus(Apartamento.StatusEnum.OCUPADO);
            apartamentoRepository.save(novoApartamento);
        }
        
        // ========== ATUALIZAR RESERVA ==========
        
        reserva.setApartamento(novoApartamento);
        reserva.setDiaria(novaDiaria);
        
        // ========== AJUSTAR DIÁRIAS NO EXTRATO ==========
        
       
        LocalDateTime dataInicioAjuste = transferenciaImediata ? 
        	    LocalDateTime.now().toLocalDate().atStartOfDay() : 
        	    dataTransferencia.toLocalDate().atStartOfDay();
        
        
        
        
        ajustarDiariasTransferencia(
            reserva, 
            dataInicioAjuste, 
            apartamentoAntigo, 
            novoApartamento,
            valorAntigo,
            valorNovo,
            diferenca
        );
        
        // ========== RECALCULAR TOTAIS ==========
        
        recalcularTotaisReserva(reserva);
        
        // ========== SALVAR RESERVA ==========
        
        Reserva reservaSalva = reservaRepository.save(reserva);
        
        // ========== CRIAR HISTÓRICO ==========
        
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reservaSalva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historico.setQuantidadeNova(reserva.getQuantidadeHospede());
        
        String descricao = String.format(
            "Transferência de apartamento: %s (%s) → %s (%s) %s%s - %s",
            apartamentoAntigo.getNumeroApartamento(),
            tipoAntigo.getTipo(),
            novoApartamento.getNumeroApartamento(),
            novoTipo.getTipo(),
            transferenciaImediata ? "IMEDIATA" : "a partir de " + dataTransferencia.toLocalDate(),
            mudouTipo ? String.format(" - Diferença: R$ %s", diferenca.abs()) : "",
            dto.getMotivo() != null && !dto.getMotivo().isEmpty() ? dto.getMotivo() : "Sem motivo informado"
        );
        
        historico.setMotivo(descricao);
        historicoHospedeRepository.save(historico);
        
        System.out.println("✅ Transferência concluída!");
        
        return reservaSalva;
    }
    
    private void ajustarDiariasTransferencia(
            Reserva reserva,
            LocalDateTime dataInicio,
            Apartamento aptoAntigo,
            Apartamento aptoNovo,
            BigDecimal valorAntigo,
            BigDecimal valorNovo,
            BigDecimal diferenca
        ) {
        
        List<ExtratoReserva> todosExtratos = extratoReservaRepository
            .findByReservaOrderByDataHoraLancamento(reserva);
        
        int diasAjustados = 0;
        
        // ✅ NORMALIZAR DATA PARA COMPARAÇÃO (SEM HORA)
        LocalDateTime dataInicioNormalizada = dataInicio.toLocalDate().atStartOfDay();
        
        for (ExtratoReserva extrato : todosExtratos) {
            if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA) {
                LocalDateTime dataLancamento = extrato.getDataHoraLancamento();
                LocalDateTime dataLancamentoNormalizada = dataLancamento.toLocalDate().atStartOfDay();
                
                // ✅ AJUSTAR DIÁRIAS A PARTIR DA DATA DE TRANSFERÊNCIA (INCLUSIVE)
                // Comparar apenas a data, ignorando hora
                if (!dataLancamentoNormalizada.isBefore(dataInicioNormalizada)) {
                    
                    // Se mudou o valor, criar ajuste
                    if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
                        ExtratoReserva ajuste = new ExtratoReserva();
                        ajuste.setReserva(reserva);
                        ajuste.setDataHoraLancamento(dataLancamento); // Manter a mesma data da diária
                        ajuste.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.ESTORNO);
                        ajuste.setDescricao(String.format(
                            "Ajuste - Transferência para Apto %s (%s)", 
                            aptoNovo.getNumeroApartamento(),
                            aptoNovo.getTipoApartamento().getTipo()
                        ));
                        ajuste.setQuantidade(1);
                        ajuste.setValorUnitario(diferenca);
                        ajuste.setTotalLancamento(diferenca);
                        ajuste.setNotaVendaId(null);
                        
                        extratoReservaRepository.save(ajuste);
                        diasAjustados++;
                        
                        System.out.println("📝 Ajuste criado para " + dataLancamento.toLocalDate() + 
                                         ": R$ " + diferenca);
                    } else {
                        System.out.println("💰 Mesmo valor - Sem ajuste para " + dataLancamento.toLocalDate());
                    }
                } else {
                    System.out.println("⏭️ Mantendo diária de " + dataLancamento.toLocalDate() + 
                                     " no apartamento antigo");
                }
            }
        }
        
        System.out.println("✅ Total de dias ajustados: " + diasAjustados);
    }
    

    @Transactional(readOnly = true)
    public ReservaDetalhesDTO buscarDetalhes(Long id) {
        System.out.println("🔍 Buscando detalhes da reserva: " + id);

        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));

        ReservaDetalhesDTO dto = new ReservaDetalhesDTO();

        // DADOS BÁSICOS
        dto.setId(reserva.getId());
        dto.setQuantidadeHospede(reserva.getQuantidadeHospede());
        dto.setDataCheckin(reserva.getDataCheckin());
        dto.setDataCheckout(reserva.getDataCheckout());
        dto.setQuantidadeDiaria(reserva.getQuantidadeDiaria());
        dto.setStatus(reserva.getStatus());
        dto.setObservacoes(reserva.getObservacoes());

        // ✅ VALOR DA DIÁRIA
        dto.setValorDiaria(reserva.getDiaria() != null ? reserva.getDiaria().getValor() : BigDecimal.ZERO);

        // ✅ TOTAIS FINANCEIROS
        dto.setTotalDiaria(reserva.getTotalDiaria());
        dto.setTotalProduto(reserva.getTotalProduto() != null ? reserva.getTotalProduto() : BigDecimal.ZERO);
        dto.setTotalHospedagem(reserva.getTotalHospedagem());
        dto.setTotalRecebido(reserva.getTotalRecebido());
        dto.setTotalApagar(reserva.getTotalApagar());
        dto.setDesconto(reserva.getDesconto() != null ? reserva.getDesconto() : BigDecimal.ZERO);

        System.out.println("💰 Totais da reserva:");
        System.out.println("  Total Diária: R$ " + dto.getTotalDiaria());
        System.out.println("  Total Produto: R$ " + dto.getTotalProduto());
        System.out.println("  Total Hospedagem: R$ " + dto.getTotalHospedagem());
        System.out.println("  Total Recebido: R$ " + dto.getTotalRecebido());
        System.out.println("  Total A Pagar: R$ " + dto.getTotalApagar());

        // CLIENTE
        if (reserva.getCliente() != null) {
            ReservaDetalhesDTO.ClienteSimples clienteDTO = new ReservaDetalhesDTO.ClienteSimples();
            clienteDTO.setId(reserva.getCliente().getId());
            clienteDTO.setNome(reserva.getCliente().getNome());
            clienteDTO.setCpf(reserva.getCliente().getCpf());
            clienteDTO.setTelefone(reserva.getCliente().getCelular()); // != null ? 
                               //    reserva.getCliente().getCelular() : 
                               //    reserva.getCliente().getTelefone());
            dto.setCliente(clienteDTO);
        }

        // APARTAMENTO
        if (reserva.getApartamento() != null) {
            ReservaDetalhesDTO.ApartamentoSimples aptDTO = new ReservaDetalhesDTO.ApartamentoSimples();
            aptDTO.setId(reserva.getApartamento().getId());
            aptDTO.setNumeroApartamento(reserva.getApartamento().getNumeroApartamento());
            aptDTO.setCapacidade(reserva.getApartamento().getCapacidade());

            if (reserva.getApartamento().getTipoApartamento() != null) {
                aptDTO.setTipoApartamentoNome(reserva.getApartamento().getTipoApartamento().getTipo().name());
            }

            dto.setApartamento(aptDTO);
        }

        // ✅ EXTRATOS
        List<ExtratoReserva> extratos = extratoReservaRepository.findByReservaOrderByDataHoraLancamento(reserva);
        List<ReservaDetalhesDTO.ExtratoSimples> extratosDTO = new ArrayList<>();
        
        for (ExtratoReserva extrato : extratos) {
            ReservaDetalhesDTO.ExtratoSimples extratoDTO = new ReservaDetalhesDTO.ExtratoSimples();
            extratoDTO.setId(extrato.getId());
            extratoDTO.setDataHoraLancamento(extrato.getDataHoraLancamento());
            extratoDTO.setDescricao(extrato.getDescricao());
            extratoDTO.setStatusLancamento(extrato.getStatusLancamento());
            extratoDTO.setQuantidade(extrato.getQuantidade());
            extratoDTO.setValorUnitario(extrato.getValorUnitario());
            extratoDTO.setTotalLancamento(extrato.getTotalLancamento());
            extratoDTO.setNotaVendaId(extrato.getNotaVendaId());
            extratosDTO.add(extratoDTO);
        }
        dto.setExtratos(extratosDTO);
        
        System.out.println("📊 Total de extratos: " + extratosDTO.size());

        // ✅ HISTÓRICO
        List<HistoricoHospede> historicos = historicoHospedeRepository.findByReserva(reserva);
        List<ReservaDetalhesDTO.HistoricoSimples> historicosDTO = new ArrayList<>();
        
        for (HistoricoHospede hist : historicos) {
            ReservaDetalhesDTO.HistoricoSimples histDTO = new ReservaDetalhesDTO.HistoricoSimples();
            histDTO.setId(hist.getId());
            histDTO.setDataHora(hist.getDataHora());
            histDTO.setMotivo(hist.getMotivo());
            histDTO.setQuantidadeAnterior(hist.getQuantidadeAnterior());
            histDTO.setQuantidadeNova(hist.getQuantidadeNova());
            historicosDTO.add(histDTO);
        }
        dto.setHistoricos(historicosDTO);

        System.out.println("✅ Detalhes da reserva carregados com sucesso");
        
        return dto;
    }
    
    @Transactional
    public Map<String, Object> processarComandasRapidas(LancamentoRapidoRequest request) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🍽️ PROCESSANDO COMANDAS RÁPIDAS");
        System.out.println("═══════════════════════════════════════════");
        
        int totalComandas = request.getComandas().size();
        int totalItens = request.getComandas().stream()
            .mapToInt(c -> c.getItens().size())
            .sum();
        
        System.out.println("📊 Total de comandas: " + totalComandas);
        System.out.println("📊 Total de itens: " + totalItens);
        
        List<String> erros = new ArrayList<>();
        List<String> sucessos = new ArrayList<>();
        int itensProcessados = 0;
        
        for (ComandaRapidaDTO comanda : request.getComandas()) {
            Long reservaId = comanda.getReservaId();
            
            try {
                // Buscar reserva
                Reserva reserva = reservaRepository.findById(reservaId)
                    .orElseThrow(() -> new RuntimeException("Reserva #" + reservaId + " não encontrada"));
                
                // Validar status
                if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
                    erros.add("Apt " + reserva.getApartamento().getNumeroApartamento() + 
                             ": Reserva não está ativa");
                    continue;
                }
                
                // Processar cada item
                for (ComandaRapidaDTO.ItemComanda item : comanda.getItens()) {
                    try {
                        adicionarProdutoAoConsumo(
                            reservaId, 
                            item.getProdutoId(), 
                            item.getQuantidade(), 
                            "Comanda Jantar"
                        );
                        itensProcessados++;
                        
                    } catch (Exception e) {
                        Produto produto = produtoRepository.findById(item.getProdutoId())
                            .orElse(null);
                        String nomeProduto = produto != null ? produto.getNomeProduto() : "Produto #" + item.getProdutoId();
                        
                        erros.add("Apt " + reserva.getApartamento().getNumeroApartamento() + 
                                 " - " + nomeProduto + ": " + e.getMessage());
                    }
                }
                
                sucessos.add("Apt " + reserva.getApartamento().getNumeroApartamento() + 
                            ": " + comanda.getItens().size() + " item(ns) adicionado(s)");
                
            } catch (Exception e) {
                erros.add("Reserva #" + reservaId + ": " + e.getMessage());
            }
        }
        
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ Processamento concluído!");
        System.out.println("   Itens processados: " + itensProcessados + "/" + totalItens);
        System.out.println("   Sucessos: " + sucessos.size());
        System.out.println("   Erros: " + erros.size());
        System.out.println("═══════════════════════════════════════════");
        
        // Montar resposta
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("totalComandas", totalComandas);
        resultado.put("totalItens", totalItens);
        resultado.put("itensProcessados", itensProcessados);
        resultado.put("sucessos", sucessos);
        resultado.put("erros", erros);
        resultado.put("sucesso", erros.isEmpty());
        
        return resultado;
    }

    
    
}
