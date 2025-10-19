package com.divan.service;

import com.divan.dto.ApartamentoResponseDTO;
import com.divan.dto.ClienteResponseDTO;
import com.divan.dto.ReservaResponseDTO;
import com.divan.entity.*;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    
    // ✅ REPOSITÓRIOS CORRETOS
    @Autowired
    private ExtratoReservaRepository extratoReservaRepository;
    
    @Autowired
    private HistoricoHospedeRepository historicoHospedeRepository;
    
    // ============================================
    // ✅ CRIAR RESERVA
    // ============================================
    
    public Reserva criarReserva(Reserva reserva) {
        // Validar disponibilidade do apartamento
        if (!reserva.getApartamento().getStatus().equals(Apartamento.StatusEnum.DISPONIVEL)) {
            throw new RuntimeException("Apartamento não está disponível");
        }
        
        // Validar quantidade de hóspedes
        if (reserva.getQuantidadeHospede() > reserva.getApartamento().getCapacidade()) {
            throw new RuntimeException("Quantidade de hóspedes excede a capacidade do apartamento");
        }
        
        // Calcular quantidade de diárias (dias)
        long dias = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()
        );
        
        if (dias <= 0) {
            throw new RuntimeException("Data de checkout deve ser posterior ao checkin");
        }
        
        reserva.setQuantidadeDiaria((int) dias);
        
        // ✅ BUSCAR DIÁRIA baseada no TIPO DE APARTAMENTO + QUANTIDADE DE HÓSPEDES
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        Integer quantidadeHospedes = reserva.getQuantidadeHospede();
        
        Diaria diariaEscolhida = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, quantidadeHospedes)
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para o tipo '%s' com %d hóspede(s)", 
                    tipoApartamento.getTipo(), 
                    quantidadeHospedes)
            ));
        
        reserva.setDiaria(diariaEscolhida);
        
        // ✅ CALCULAR TOTAL DAS DIÁRIAS (VALOR JÁ INCLUI QUANTIDADE DE HÓSPEDES)
        // Apenas multiplicar pelo número de dias
        BigDecimal valorDiaria = diariaEscolhida.getValor();
        BigDecimal totalDiaria = valorDiaria.multiply(new BigDecimal(dias));
        
        reserva.setTotalDiaria(totalDiaria);
        reserva.setTotalHospedagem(totalDiaria);
        
        // Inicializar valores
        reserva.setTotalRecebido(BigDecimal.ZERO);
        reserva.setTotalProduto(BigDecimal.ZERO);
        reserva.setTotalApagar(totalDiaria);
        reserva.setStatus(Reserva.StatusReservaEnum.ATIVA);
        
        // Criar nota de venda para consumo
        NotaVenda notaVenda = new NotaVenda();
        notaVenda.setReserva(reserva);
        notaVenda.setDataHoraVenda(LocalDateTime.now());
        notaVenda.setTotal(BigDecimal.ZERO);
        notaVenda.setTipoVenda(NotaVenda.TipoVendaEnum.APARTAMENTO);
        notaVenda.setStatus(NotaVenda.Status.ABERTA);
        notaVenda.setItens(new ArrayList<>());
        
        // Inicializar lista de notas de venda
        if (reserva.getNotasVenda() == null) {
            reserva.setNotasVenda(new ArrayList<>());
        }
        reserva.getNotasVenda().add(notaVenda);
        
        // Atualizar status do apartamento para OCUPADO
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.OCUPADO);
        apartamentoRepository.save(apartamento);
        
        // Salvar reserva
        Reserva salva = reservaRepository.save(reserva);
        
        // ✅ CRIAR LANÇAMENTO NO EXTRATO
        ExtratoReserva extrato = new ExtratoReserva();
        extrato.setReserva(salva);
        extrato.setDataHoraLancamento(LocalDateTime.now());
        extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.DIARIA);
        extrato.setDescricao(String.format("Diária - %d hóspede(s) × %d dia(s)", 
            reserva.getQuantidadeHospede(), 
            reserva.getQuantidadeDiaria()));
        extrato.setQuantidade(reserva.getQuantidadeDiaria());
        extrato.setValorUnitario(valorDiaria);
        extrato.setTotalLancamento(totalDiaria);
        
        extratoReservaRepository.save(extrato);
        
        // ✅ CRIAR HISTÓRICO
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(salva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historico.setQuantidadeNova(reserva.getQuantidadeHospede());
        historico.setMotivo(String.format("Reserva criada - %d hóspede(s) - Check-in: %s - Check-out: %s", 
            reserva.getQuantidadeHospede(),
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()));
        
        historicoHospedeRepository.save(historico);
        
        System.out.println("✅ Reserva criada: " + salva.getId());
        System.out.println("💰 Diária para " + quantidadeHospedes + " hóspede(s): R$ " + valorDiaria);
        System.out.println("💰 Total " + dias + " dia(s): R$ " + totalDiaria);
        
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
        
        // ✅ FORÇA O CARREGAMENTO DOS EXTRATOS E HISTÓRICOS (Lazy Loading)
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
        
        // ✅ CONVERTER CLIENTE
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
        
        // ✅ CONVERTER APARTAMENTO
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
        
        // ✅ ATUALIZAR QUANTIDADE
        reserva.setQuantidadeHospede(novaQuantidade);
        
        // ✅ RECALCULAR VALORES (vai buscar nova diária automaticamente)
        recalcularValores(reserva);
        
        BigDecimal diferenca = reserva.getTotalDiaria().subtract(totalAnterior);
        
        // ✅ CRIAR LANÇAMENTO NO EXTRATO SE HOUVER DIFERENÇA
        if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            extrato.setDataHoraLancamento(LocalDateTime.now());
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.DIARIA);
            extrato.setDescricao(String.format("Ajuste - Hóspedes alterados de %d para %d", 
                quantidadeAnterior, 
                novaQuantidade));
            extrato.setQuantidade(reserva.getQuantidadeDiaria());
            extrato.setValorUnitario(reserva.getDiaria().getValor());
            extrato.setTotalLancamento(diferenca);
            
            extratoReservaRepository.save(extrato);
        }
        
        // ✅ CRIAR HISTÓRICO
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reserva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(quantidadeAnterior);
        historico.setQuantidadeNova(novaQuantidade);
        historico.setMotivo(String.format("Quantidade de hóspedes alterada de %d para %d - %s - Diferença: R$ %s", 
            quantidadeAnterior, 
            novaQuantidade,
            motivo != null && !motivo.isEmpty() ? motivo : "Sem motivo informado",
            diferenca.abs()));
        
        historicoHospedeRepository.save(historico);
        
        Reserva salva = reservaRepository.save(reserva);
        
        System.out.println("👥 Hóspedes alterados de " + quantidadeAnterior + " para " + novaQuantidade);
        System.out.println("💰 Nova diária: R$ " + reserva.getDiaria().getValor());
        System.out.println("💰 Diferença no valor: R$ " + diferenca);
        
        return salva;
    }
    
    public Reserva alterarDataCheckout(Long id, LocalDateTime novaDataCheckout, String motivo) {
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        if (novaDataCheckout.isBefore(reserva.getDataCheckin())) {
            throw new RuntimeException("Data de checkout não pode ser antes do checkin");
        }
        
        LocalDateTime checkoutAnterior = reserva.getDataCheckout();
        BigDecimal totalAnterior = reserva.getTotalDiaria();
        
        // ✅ ATUALIZAR DATA
        reserva.setDataCheckout(novaDataCheckout);
        
        // ✅ RECALCULAR VALORES
        recalcularValores(reserva);
        
        BigDecimal diferenca = reserva.getTotalDiaria().subtract(totalAnterior);
        
        // ✅ CRIAR LANÇAMENTO NO EXTRATO SE HOUVER DIFERENÇA
        if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            extrato.setDataHoraLancamento(LocalDateTime.now());
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.DIARIA);
            extrato.setDescricao(String.format("Ajuste - Checkout alterado para %s", 
                novaDataCheckout.toLocalDate()));
            extrato.setQuantidade(reserva.getQuantidadeHospede() * reserva.getQuantidadeDiaria());
            extrato.setValorUnitario(reserva.getDiaria().getValor());
            extrato.setTotalLancamento(diferenca);
            
            extratoReservaRepository.save(extrato); // ✅ CORRETO
        }
        
        // ✅ CRIAR HISTÓRICO
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(reserva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(reserva.getQuantidadeHospede()); // ✅ Mantém a mesma
        historico.setQuantidadeNova(reserva.getQuantidadeHospede()); // ✅ Mantém a mesma
        historico.setMotivo(String.format("Checkout alterado para %s - %s - Diferença: R$ %s", 
            novaDataCheckout.toLocalDate(),
            motivo != null && !motivo.isEmpty() ? motivo : "Sem motivo informado",
            diferenca.abs()));

        historicoHospedeRepository.save(historico);
        
        Reserva salva = reservaRepository.save(reserva);
        
        System.out.println("📅 Checkout alterado de " + checkoutAnterior + " para " + novaDataCheckout);
        System.out.println("💰 Diferença no valor: R$ " + diferenca);
        
        return salva;
    }
    
    private void recalcularValores(Reserva reserva) {
        long dias = ChronoUnit.DAYS.between(
            reserva.getDataCheckin().toLocalDate(),
            reserva.getDataCheckout().toLocalDate()
        );
        
        reserva.setQuantidadeDiaria((int) dias);
        
        // ✅ BUSCAR DIÁRIA CORRETA baseada em TIPO + QUANTIDADE DE HÓSPEDES
        TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
        Integer quantidadeHospedes = reserva.getQuantidadeHospede();
        
        Diaria diariaAtualizada = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, quantidadeHospedes)
            .orElseThrow(() -> new RuntimeException(
                String.format("Nenhuma diária cadastrada para o tipo '%s' com %d hóspede(s)", 
                    tipoApartamento.getTipo(), 
                    quantidadeHospedes)
            ));
        
        reserva.setDiaria(diariaAtualizada);
        
        // ✅ CALCULAR TOTAL (valor já inclui quantidade de hóspedes)
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
        
        // ✅ SALVAR RESERVA PRIMEIRO
        Reserva reservaSalva = reservaRepository.save(reserva);
        
        // ✅ CRIAR LANÇAMENTO NO EXTRATO
        ExtratoReserva extrato = new ExtratoReserva();
        extrato.setReserva(reservaSalva);
        extrato.setDataHoraLancamento(LocalDateTime.now());
        extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.PRODUTO);
        extrato.setDescricao(produto.getNomeProduto());
        extrato.setQuantidade(quantidade);
        extrato.setValorUnitario(produto.getValorVenda());
        extrato.setTotalLancamento(valorTotalItem);
        extrato.setNotaVendaId(notaVenda.getId());
        
        extratoReservaRepository.save(extrato);
        
        // ✅ CRIAR HISTÓRICO
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
        
        return reservaSalva; // ✅ RETORNA A RESERVA SALVA
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
    
    public Reserva finalizarReserva(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        // Validar se há saldo devedor
        if (reserva.getTotalApagar().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException(
                "Não é possível finalizar reserva com saldo devedor de R$ " + 
                reserva.getTotalApagar() + 
                ". Quite o valor antes de finalizar."
            );
        }
        
        // Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);
        
        // Liberar apartamento para limpeza
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);
        
        Reserva salva = reservaRepository.save(reserva);
        
        System.out.println("✅ Reserva finalizada: " + reservaId);
        System.out.println("🧹 Apartamento " + apartamento.getNumeroApartamento() + " em LIMPEZA");
        
        return salva;
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
}
