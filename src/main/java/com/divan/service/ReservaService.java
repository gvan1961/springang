package com.divan.service;

import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import java.time.format.DateTimeFormatter;

import com.divan.dto.CheckoutParcialRequestDTO;
import com.divan.dto.HospedeReservaDTO;
import com.divan.entity.HospedagemHospede;
import com.divan.entity.Reserva.StatusReservaEnum;
import com.divan.repository.HospedagemHospedeRepository;
import java.time.LocalDate;
import com.divan.enums.FormaPagamento;
import com.divan.dto.ApartamentoResponseDTO;
import com.divan.dto.ClienteResponseDTO;
import com.divan.dto.ComandaRapidaDTO;
import com.divan.dto.LancamentoRapidoRequestDTO;
import com.divan.dto.ReservaResponseDTO;
import com.divan.dto.TransferenciaApartamentoDTO;
import com.divan.dto.TransferenciaHospedeDTO;
import com.divan.entity.*;
import com.divan.entity.Apartamento.StatusEnum;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.divan.dto.ReservaDetalhesDTO;

import com.divan.repository.PagamentoRepository;
import com.divan.repository.FechamentoCaixaRepository;

import java.time.LocalDate;

import com.divan.entity.ContaAReceber;
import com.divan.entity.ExtratoReserva.StatusLancamentoEnum;
import com.divan.repository.ContaAReceberRepository;

@Service
@Transactional
public class ReservaService {
    
	@Autowired
	private ControleDiariaService controleDiariaService;
	
    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private DescontoReservaRepository descontoReservaRepository;
    
    @Autowired
    private DiariaRepository diariaRepository; 
    
    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private FechamentoCaixaRepository fechamentoCaixaRepository;
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    @Autowired
    private ProdutoRepository produtoRepository;
    
    @Autowired
    private NotaVendaRepository notaVendaRepository;
    
    @Autowired
    private ClienteRepository clienteRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoReservaRepository;
    
    @Autowired
    private HistoricoHospedeRepository historicoHospedeRepository;
    
    @Autowired
    private ContaAReceberRepository contaAReceberRepository;
    
    @Autowired
    private HospedagemHospedeRepository hospedagemHospedeRepository;
     
    /**
     * Verifica se existe conflito de datas para o apartamento
     */
    private boolean existeConflitoDeDatas(Long apartamentoId, LocalDateTime checkin, LocalDateTime checkout, Long reservaIdExcluir) {
        List<Reserva> reservasExistentes = reservaRepository.findByApartamentoId(apartamentoId);
        
        System.out.println("🔍 Verificando conflito de datas:");
        System.out.println("   Apartamento ID: " + apartamentoId);
        System.out.println("   Check-in desejado: " + checkin.toLocalDate());
        System.out.println("   Check-out desejado: " + checkout.toLocalDate());
        System.out.println("   Total de reservas encontradas: " + reservasExistentes.size());
        
        for (Reserva r : reservasExistentes) {
            // ✅ IGNORAR reservas finalizadas, canceladas ou a própria reserva
            if (r.getStatus() == Reserva.StatusReservaEnum.FINALIZADA ||
                r.getStatus() == Reserva.StatusReservaEnum.CANCELADA ||
                (reservaIdExcluir != null && r.getId().equals(reservaIdExcluir))) {
                System.out.println("   ⏭️ Ignorando reserva #" + r.getId() + " (Status: " + r.getStatus() + ")");
                continue;
            }
            
            System.out.println("   ✅ Verificando reserva #" + r.getId() + " (Status: " + r.getStatus() + ")");
            System.out.println("      Check-in existente: " + r.getDataCheckin().toLocalDate());
            System.out.println("      Check-out existente: " + r.getDataCheckout().toLocalDate());
            
            boolean checkinConflita = !checkin.isBefore(r.getDataCheckin()) && checkin.isBefore(r.getDataCheckout());
            boolean checkoutConflita = checkout.isAfter(r.getDataCheckin()) && !checkout.isAfter(r.getDataCheckout());
            boolean envolveTudo = !checkin.isAfter(r.getDataCheckin()) && !checkout.isBefore(r.getDataCheckout());
            
            if (checkinConflita || checkoutConflita || envolveTudo) {
                System.out.println("      ❌ CONFLITO DETECTADO!");
                return true;
            }
        }
        
        System.out.println("   ✅ Nenhum conflito detectado");
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
        
        // ✅ CAPTURAR A HORA REAL AGORA (momento do check-in)
        LocalDateTime horaRealDoCheckin = LocalDateTime.now();
        
        System.out.println("📅 Criando " + dias + " diária(s) com hora REAL: " + horaRealDoCheckin);
        
        // Criar um extrato para cada dia
        for (int i = 0; i < dias; i++) {
            LocalDate dataDiaria = dataInicio.toLocalDate().plusDays(i);
            
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(reserva);
            
            // ✅ SEMPRE USAR A HORA REAL DO CHECK-IN (não a hora padronizada)
            extrato.setDataHoraLancamento(horaRealDoCheckin);
            
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
            
            System.out.println("✅ Diária " + (i+1) + "/" + dias + " criada: " + dataDiaria + " às " + horaRealDoCheckin.toLocalTime());
        }
        
        System.out.println("✅ Total de " + dias + " diária(s) criada(s) com sucesso!");
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
    
    /**
     * ✅ CRIAR RESERVA COM VALIDAÇÃO DE CONFLITOS E PROCESSAMENTO DE HÓSPEDES
     */
    /**
     * ✅ CRIAR RESERVA COM VALIDAÇÃO DE CONFLITOS E PROCESSAMENTO DE HÓSPEDES
     */
    @Transactional
    public Reserva criarReserva(Reserva reserva) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("📝 CRIANDO NOVA RESERVA");
        System.out.println("═══════════════════════════════════════════");
        
        // ✅ VERIFICAR CONFLITO DE DATAS
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

        // ═══════════════════════════════════════════════════════════
        // ✅ LÓGICA DE CHECK-IN: FUTURO vs PASSADO (DATA + HORA)
        // ═══════════════════════════════════════════════════════════

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime checkinRecebido = reserva.getDataCheckin();
        LocalDateTime checkoutRecebido = reserva.getDataCheckout();
        
        LocalDateTime checkinFinal;
        LocalDateTime checkoutFinal;

        System.out.println("═══════════════════════════════════════════");
        System.out.println("📅 PROCESSANDO DATAS DA RESERVA");
        System.out.println("═══════════════════════════════════════════");
        System.out.println("⏰ AGORA (servidor):        " + agora);
        System.out.println("⏰ Check-in recebido:       " + checkinRecebido);
        System.out.println("⏰ Check-out recebido:      " + checkoutRecebido);

        // ✅ COMPARAR DATA + HORA COMPLETA (não só a data)
        if (checkinRecebido.isAfter(agora)) {
            // ═══════════════════════════════════════════════════════════
            // ✅ CASO 1: CHECK-IN É NO FUTURO (depois de agora)
            // ═══════════════════════════════════════════════════════════
            
            System.out.println("\n📅 TIPO: PRÉ-RESERVA (check-in no futuro)");
            
            // ✅ USAR EXATAMENTE AS HORAS QUE O USUÁRIO DIGITOU
            checkinFinal = checkinRecebido;
            checkoutFinal = checkoutRecebido;
            
            System.out.println("   ✅ Usando HORAS DO FRONTEND (sem alterações)");
            System.out.println("   📍 Check-in final:  " + checkinFinal);
            System.out.println("   📍 Check-out final: " + checkoutFinal);
            
        } else {
            // ═══════════════════════════════════════════════════════════
            // ✅ CASO 2: CHECK-IN JÁ PASSOU (antes ou igual a agora)
            // ═══════════════════════════════════════════════════════════
            
            System.out.println("\n🏨 TIPO: CHECK-IN IMEDIATO (horário já passou)");
            
            // ✅ CHECK-IN = AGORA (hora atual do sistema)
            checkinFinal = agora;
            
            // ✅ CHECK-OUT = Mantém o que o usuário digitou
            checkoutFinal = checkoutRecebido;
            
            System.out.println("   ⏰ Check-in ajustado para HORA ATUAL");
            System.out.println("   📍 Check-in final:  " + checkinFinal);
            System.out.println("   📍 Check-out final: " + checkoutFinal + " (do frontend)");
        }

        System.out.println("═══════════════════════════════════════════");

        reserva.setDataCheckin(checkinFinal);
        reserva.setDataCheckout(checkoutFinal);
        
     // ═══════════════════════════════════════════════════════════════
     // ✅ VALIDAR SE JÁ EXISTE RESERVA PARA ESTE CLIENTE NESTA DATA
     // ═══════════════════════════════════════════════════════════════

     System.out.println("🔍 Validando duplicidade de reserva...");
     System.out.println("   Cliente ID: " + reserva.getCliente().getId());
     System.out.println("   Data check-in: " + checkinFinal);

     LocalDate dataCheckinDate = checkinFinal.toLocalDate();
     LocalDateTime inicioDia = dataCheckinDate.atStartOfDay();
     LocalDateTime fimDia = dataCheckinDate.atTime(23, 59, 59);

     System.out.println("   Buscando entre: " + inicioDia + " e " + fimDia);

     List<Reserva> reservasExistentes = reservaRepository
         .findByClienteIdAndDataCheckinBetween(
             reserva.getCliente().getId(),
             inicioDia,
             fimDia
         );

     System.out.println("   Reservas encontradas: " + reservasExistentes.size());

     // Filtrar apenas ATIVAS e PRE_RESERVA (ignorar CANCELADAS e FINALIZADAS)
     long reservasAtivas = reservasExistentes.stream()
         .filter(r -> {
             boolean isAtiva = r.getStatus() == StatusReservaEnum.ATIVA 
                            || r.getStatus() == StatusReservaEnum.PRE_RESERVA;
             if (isAtiva) {
                 System.out.println("   ⚠️ Encontrada reserva #" + r.getId() + " - Status: " + r.getStatus());
             }
             return isAtiva;
         })
         .count();

     System.out.println("   Reservas ativas/pré-reserva: " + reservasAtivas);

     if (reservasAtivas > 0) {
         String dataFormatada = dataCheckinDate.format(
             DateTimeFormatter.ofPattern("dd/MM/yyyy")
         );
         
         throw new RuntimeException(
             "❌ JÁ EXISTE UMA RESERVA PARA ESTE CLIENTE NESTA DATA!\n\n" +
             "Cliente: " + reserva.getCliente().getNome() + "\n" +
             "Data: " + dataFormatada + "\n\n" +
             "Cancele a reserva existente antes de criar uma nova ou escolha outra data."
         );
     }

     System.out.println("✅ Validação de duplicidade passou - nenhuma reserva ativa encontrada");

     // ═══════════════════════════════════════════════════════════════
        
        
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
        
        // ✅ DEFINIR STATUS: PRE_RESERVA se futuro, ATIVA se presente/passado
        LocalDate hoje = LocalDate.now();
        LocalDate diaCheckin = checkinFinal.toLocalDate();
        
        System.out.println("🔍 DEBUG - STATUS DA RESERVA:");
        System.out.println("   Data de HOJE: " + hoje);
        System.out.println("   Data Check-in: " + diaCheckin);
        
        if (diaCheckin.isAfter(hoje)) {
            // ✅ CHECK-IN É FUTURO → PRE_RESERVA
            reserva.setStatus(Reserva.StatusReservaEnum.PRE_RESERVA);
            
            System.out.println("📅 Reserva criada como PRÉ-RESERVA (check-in futuro)");
            System.out.println("   ⚠️ Apartamento NÃO será ocupado agora");
            
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
        
        // ✅ LANÇAR PRIMEIRA DIÁRIA (se for check-in imediato)
        if (salva.getStatus() == Reserva.StatusReservaEnum.ATIVA) {
            try {
                controleDiariaService.lancarDiaria(salva);
                System.out.println("💰 Primeira diária LANÇADA");
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao lançar primeira diária: " + e.getMessage());
            }
        }
        
        // ✅ Criar diárias no ExtratoReserva
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
                    ajuste.setTotalLancamento(diferenca);
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
            extrato.setDataHoraLancamento(LocalDateTime.now());
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.ESTORNO);
            extrato.setDescricao(String.format("Estorno - Diária dia %02d/%02d/%d removida", 
                dataDiaria.getDayOfMonth(),
                dataDiaria.getMonthValue(),
                dataDiaria.getYear()));
            extrato.setQuantidade(1);
            extrato.setValorUnitario(valorDiaria.negate());
            extrato.setTotalLancamento(valorDiaria.negate());
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
    public Reserva finalizarReserva(Long id) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ FINALIZANDO RESERVA FATURADA");
        System.out.println("═══════════════════════════════════════════");
        
        Optional<Reserva> reservaOpt = reservaRepository.findById(id);
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        
        // ✅ CAPTURAR O SALDO ANTES DE FINALIZAR
        BigDecimal saldoAntesDeFinalizar = reserva.getTotalApagar();
        
        System.out.println("📊 Reserva #" + reserva.getId());
        System.out.println("   Cliente: " + reserva.getCliente().getNome());
        System.out.println("   Total Hospedagem: R$ " + reserva.getTotalHospedagem());
        System.out.println("   Total Recebido: R$ " + reserva.getTotalRecebido());
        System.out.println("   Saldo a Pagar: R$ " + saldoAntesDeFinalizar);
        
        // ✅ VALIDAÇÃO 1: Reserva deve estar ATIVA
        if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
            throw new RuntimeException("Apenas reservas ATIVAS podem ser finalizadas");
        }
        
        // ✅ VALIDAÇÃO 2: Se tem saldo, cliente DEVE ter crédito aprovado
        if (saldoAntesDeFinalizar.compareTo(BigDecimal.ZERO) > 0) {
            Cliente cliente = reserva.getCliente();
            
            System.out.println("🔍 Verificando crédito do cliente:");
            System.out.println("   Cliente: " + cliente.getNome());
            System.out.println("   CPF: " + cliente.getCpf());
            System.out.println("   Crédito Aprovado: " + cliente.getCreditoAprovado());
            
            if (cliente.getCreditoAprovado() == null || !cliente.getCreditoAprovado()) {
                System.out.println("❌ CLIENTE SEM CRÉDITO APROVADO!");
                throw new RuntimeException(
                    "❌ CLIENTE SEM CRÉDITO APROVADO!\n\n" +
                    "O cliente '" + cliente.getNome() + "' não possui crédito aprovado para faturamento.\n\n" +
                    "Opções:\n" +
                    "1. Solicite o pagamento à vista\n" +
                    "2. Aprove o crédito do cliente antes de finalizar"
                );
            }
            
            System.out.println("✅ Cliente possui crédito aprovado - pode faturar");
        } else {
            System.out.println("✅ Reserva totalmente paga - não precisa validar crédito");
        }
        
        // 1️⃣ Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);
        
        // ✅ CRIAR HISTÓRICO DE FINALIZAÇÃO
        HistoricoHospede historicoFinalizacao = new HistoricoHospede();
        historicoFinalizacao.setReserva(reserva);
        historicoFinalizacao.setDataHora(LocalDateTime.now());
        historicoFinalizacao.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historicoFinalizacao.setQuantidadeNova(reserva.getQuantidadeHospede());
        historicoFinalizacao.setMotivo("Reserva FINALIZADA - Check-out realizado");
        historicoHospedeRepository.save(historicoFinalizacao);
        
        reservaRepository.save(reserva);
        System.out.println("✅ Reserva finalizada com sucesso");
        
        // 2️⃣ Liberar apartamento para LIMPEZA
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);
        System.out.println("🧹 Apartamento " + apartamento.getNumeroApartamento() + " → LIMPEZA");
        
        // ✅ 3️⃣ REGISTRAR PAGAMENTO FATURADO NO CAIXA (NOVO!)
        if (saldoAntesDeFinalizar.compareTo(BigDecimal.ZERO) > 0) {
            registrarPagamentoFaturado(reserva, saldoAntesDeFinalizar);
        }
        
        // 4️⃣ Criar Conta a Receber
        criarContaAReceber(reserva, saldoAntesDeFinalizar);
        
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ FINALIZAÇÃO FATURADA CONCLUÍDA!");
        System.out.println("   Saldo enviado para Contas a Receber: R$ " + saldoAntesDeFinalizar);
        System.out.println("═══════════════════════════════════════════");
        
        return reserva;
    }
    
    
    /**
     * ✅ REGISTRAR PAGAMENTO FATURADO NO CAIXA
     */
    private void registrarPagamentoFaturado(Reserva reserva, BigDecimal valorFaturado) {
        try {
            System.out.println("💳 Registrando pagamento FATURADO no caixa...");
            
            // Buscar caixa aberto (qualquer um)
            Optional<FechamentoCaixa> caixaAbertoOpt = fechamentoCaixaRepository
                .findFirstByStatusOrderByDataHoraAberturaDesc(FechamentoCaixa.StatusCaixaEnum.ABERTO);
            
            Pagamento pagamento = new Pagamento();
            pagamento.setReserva(reserva);
            pagamento.setTipo("FATURADO");
            pagamento.setFormaPagamento(FormaPagamento.FATURADO);
            pagamento.setValor(valorFaturado);
            pagamento.setDataHora(LocalDateTime.now());
            pagamento.setDescricao("Checkout faturado - Apt " + reserva.getApartamento().getNumeroApartamento() + 
                                  " - " + reserva.getCliente().getNome());
            
            // Associar ao caixa aberto (se existir)
            if (caixaAbertoOpt.isPresent()) {
                pagamento.setCaixa(caixaAbertoOpt.get());
                System.out.println("   Caixa associado: #" + caixaAbertoOpt.get().getId());
            } else {
                System.out.println("   ⚠️ Nenhum caixa aberto - pagamento sem caixa associado");
            }
            
            pagamentoRepository.save(pagamento);
            
            System.out.println("✅ Pagamento FATURADO registrado: R$ " + valorFaturado);
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao registrar pagamento faturado: " + e.getMessage());
            // Não interrompe o fluxo - apenas log
        }
    }
    
    // ✅ MÉTODO PARA CRIAR CONTA A RECEBER
    private void criarContaAReceber(Reserva reserva, BigDecimal valorAPagar) {
        try {
            System.out.println("📋 Criando Conta a Receber:");
            System.out.println("   Valor a pagar: R$ " + valorAPagar);
            System.out.println("   Cliente: " + reserva.getCliente().getNome());
            
            ContaAReceber conta = new ContaAReceber();
            
            // Dados da reserva
            conta.setReserva(reserva);
            conta.setCliente(reserva.getCliente());
            
            // Valores
            conta.setValor(reserva.getTotalHospedagem());
            conta.setValorPago(reserva.getTotalRecebido());
            conta.setSaldo(valorAPagar);
            
            // ✅ DEFINIR STATUS baseado no saldo
            if (valorAPagar.compareTo(BigDecimal.ZERO) == 0) {
                conta.setStatus(ContaAReceber.StatusContaEnum.PAGA);
                conta.setDataPagamento(LocalDate.now());
            } else if (reserva.getTotalRecebido().compareTo(BigDecimal.ZERO) > 0) {
                conta.setStatus(ContaAReceber.StatusContaEnum.PARCIAL);
                conta.setDataPagamento(null);
            } else {
                conta.setStatus(ContaAReceber.StatusContaEnum.PENDENTE);
                conta.setDataPagamento(null);
            }
            
            // Data de vencimento
            conta.setDataVencimento(LocalDate.now());
            conta.setDataCriacao(LocalDateTime.now());
            
            // Descrição
            conta.setDescricao("Reserva #" + reserva.getId() + 
                              " - Apt " + reserva.getApartamento().getNumeroApartamento());
            
            // ✅ Observação
            if (valorAPagar.compareTo(BigDecimal.ZERO) > 0) {
                conta.setObservacao("Saldo devedor de R$ " + valorAPagar);
            } else {
                conta.setObservacao("Reserva paga integralmente");
            }
            
            // Empresa
            if (reserva.getCliente().getEmpresa() != null) {
                conta.setEmpresa(reserva.getCliente().getEmpresa());
            }
            
            contaAReceberRepository.save(conta);
            
            System.out.println("✅ Conta a Receber criada com sucesso!");
            System.out.println("   ID: " + conta.getId());
            System.out.println("   Status: " + conta.getStatus());
            System.out.println("   Valor: R$ " + conta.getValor());
            System.out.println("   Valor Pago: R$ " + conta.getValorPago());
            System.out.println("   Saldo: R$ " + conta.getSaldo());
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao criar Conta a Receber:");
            System.err.println("   Mensagem: " + e.getMessage());
            e.printStackTrace();
            
            System.err.println("⚠️ Continuando finalização mesmo com erro na Conta a Receber");
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
                if (!dataLancamentoNormalizada.isBefore(dataInicioNormalizada)) {
                    
                    // Se mudou o valor, criar ajuste
                    if (diferenca.compareTo(BigDecimal.ZERO) != 0) {
                        ExtratoReserva ajuste = new ExtratoReserva();
                        ajuste.setReserva(reserva);
                        ajuste.setDataHoraLancamento(dataLancamento);
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
        System.out.println("  Desconto: R$ " + dto.getDesconto());

        // CLIENTE
        if (reserva.getCliente() != null) {
            ReservaDetalhesDTO.ClienteSimples clienteDTO = new ReservaDetalhesDTO.ClienteSimples();
            clienteDTO.setId(reserva.getCliente().getId());
            clienteDTO.setNome(reserva.getCliente().getNome());
            clienteDTO.setCpf(reserva.getCliente().getCpf());
            clienteDTO.setTelefone(reserva.getCliente().getCelular());
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
        
     // ✅ DESCONTOS
        List<DescontoReserva> descontos = descontoReservaRepository.findByReserva(reserva);
        List<ReservaDetalhesDTO.DescontoSimples> descontosDTO = new ArrayList<>();

        for (DescontoReserva desc : descontos) {
            ReservaDetalhesDTO.DescontoSimples descDTO = new ReservaDetalhesDTO.DescontoSimples();
            descDTO.setId(desc.getId());
            descDTO.setValor(desc.getValor());
            descDTO.setMotivo(desc.getMotivo());
            descDTO.setDataHoraDesconto(desc.getDataHoraDesconto());
            descontosDTO.add(descDTO);
        }
        dto.setDescontos(descontosDTO);

        System.out.println("💰 Total de descontos: " + descontos.size());
        
        List<HospedagemHospede> hospedagens = hospedagemHospedeRepository.findByReservaId(reserva.getId());

        if (hospedagens != null && !hospedagens.isEmpty()) {
            List<ReservaDetalhesDTO.HospedeSimples> hospedesDTO = new ArrayList<>();
            
            for (HospedagemHospede h : hospedagens) {
                ReservaDetalhesDTO.HospedeSimples hs = new ReservaDetalhesDTO.HospedeSimples();
                hs.setId(h.getId());
                
                if (h.getCliente() != null) {
                    // ✅ TEM CLIENTE VINCULADO (99% dos casos)
                    hs.setClienteId(h.getCliente().getId());
                    hs.setNomeCompleto(h.getCliente().getNome());
                    hs.setCpf(h.getCliente().getCpf());
                    hs.setTelefone(h.getCliente().getCelular());
                } else {
                    // ✅ NÃO TEM CLIENTE (cadastro rápido sem vínculo - caso raro)
                    // Assumindo que HospedagemHospede pode ter esses campos opcionais
                    hs.setNomeCompleto("Hóspede sem cadastro");
                    hs.setCpf(null);
                    hs.setTelefone(null);
                }
                
                hs.setTitular(h.getTitular());
                hs.setStatus(h.getStatus() != null ? h.getStatus().name() : "HOSPEDADO");
                
                hospedesDTO.add(hs);
            }
            
            dto.setHospedes(hospedesDTO);
            
            System.out.println("✅ Hóspedes adicionados ao DTO: " + hospedesDTO.size());
        } else {
            System.out.println("⚠️ Nenhum hóspede encontrado para a reserva #" + reserva.getId());
        }

        return dto;
    }
    
    @Transactional
    public Map<String, Object> processarComandasRapidas(LancamentoRapidoRequestDTO request) {
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
    
    @Transactional
    public void excluirPreReserva(Long id) {
        System.out.println("🗑️ Excluindo pré-reserva #" + id);
        
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        // Validar se é realmente uma pré-reserva
        if (reserva.getStatus() != StatusReservaEnum.PRE_RESERVA) {
            throw new RuntimeException("Apenas pré-reservas podem ser excluídas. Status atual: " + reserva.getStatus());
        }
        
        Apartamento apartamento = reserva.getApartamento();
        
        // ✅ 1. PRIMEIRO - Excluir todos os hóspedes vinculados
        System.out.println("🗑️ Removendo hóspedes da reserva...");
        hospedagemHospedeRepository.deleteByReserva(reserva);
        
        // ✅ 2. DEPOIS - Excluir a reserva
        System.out.println("🗑️ Removendo reserva...");
        reservaRepository.delete(reserva);
        
        // ✅ 3. ATUALIZAR STATUS DO APARTAMENTO
        // Verificar se tem outras pré-reservas para este apartamento
        List<Reserva> outrasPreReservas = reservaRepository
            .findByApartamentoAndStatus(apartamento, StatusReservaEnum.PRE_RESERVA);
        
        if (outrasPreReservas.isEmpty()) {
            // Se não tem mais pré-reservas, voltar para DISPONIVEL
            apartamento.setStatus(Apartamento.StatusEnum.DISPONIVEL);
            apartamentoRepository.save(apartamento);
            System.out.println("✅ Apartamento " + apartamento.getNumeroApartamento() + " liberado");
        } else {
            System.out.println("ℹ️ Apartamento " + apartamento.getNumeroApartamento() + 
                             " ainda tem " + outrasPreReservas.size() + " pré-reserva(s)");
        }
        
        System.out.println("✅ Pré-reserva #" + id + " excluída com sucesso!");
    }
    
    @Transactional
    public Reserva editarPreReserva(Long id, Long novoApartamentoId, Integer novaQuantidade, 
                                     LocalDateTime novoCheckin, LocalDateTime novoCheckout) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✏️ EDITANDO PRÉ-RESERVA #" + id);
        System.out.println("═══════════════════════════════════════════");
        
        Reserva reserva = reservaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        // ✅ VALIDAR: Só pode editar PRÉ-RESERVA
        if (reserva.getStatus() != Reserva.StatusReservaEnum.PRE_RESERVA) {
            throw new RuntimeException("Apenas pré-reservas podem ser editadas desta forma");
        }
        
        // Buscar novo apartamento (se mudou)
        Apartamento novoApartamento = apartamentoRepository.findById(novoApartamentoId)
            .orElseThrow(() -> new RuntimeException("Apartamento não encontrado"));
        
        // ✅ VERIFICAR CONFLITO DE DATAS (excluindo esta reserva)
        boolean temConflito = existeConflitoDeDatas(
            novoApartamentoId,
            novoCheckin,
            novoCheckout,
            id
        );
        
        if (temConflito) {
            throw new RuntimeException("Já existe uma reserva para este apartamento no período selecionado");
        }
        
        // Validar capacidade
        if (novaQuantidade > novoApartamento.getCapacidade()) {
            throw new RuntimeException("Quantidade de hóspedes excede capacidade do apartamento");
        }
        
        // ✅ ATUALIZAR DADOS
        boolean mudouApartamento = !reserva.getApartamento().getId().equals(novoApartamentoId);
        
        reserva.setApartamento(novoApartamento);
        reserva.setQuantidadeHospede(novaQuantidade);
        reserva.setDataCheckin(novoCheckin);
        reserva.setDataCheckout(novoCheckout);
        
        // Recalcular valores
        recalcularValores(reserva);
        
        // ✅ RECRIAR EXTRATOS DE DIÁRIAS
        extratoReservaRepository.deleteAll(
            extratoReservaRepository.findByReservaId(id).stream()
                .filter(e -> e.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.DIARIA)
                .collect(Collectors.toList())
        );
        
        criarExtratosDiarias(reserva, reserva.getDataCheckin(), reserva.getDataCheckout());
        
        Reserva salva = reservaRepository.save(reserva);
        
        // Criar histórico
        HistoricoHospede historico = new HistoricoHospede();
        historico.setReserva(salva);
        historico.setDataHora(LocalDateTime.now());
        historico.setQuantidadeAnterior(novaQuantidade);
        historico.setQuantidadeNova(novaQuantidade);
        historico.setMotivo(String.format(
            "Pré-reserva editada%s - Check-in: %s - Check-out: %s - Hóspedes: %d",
            mudouApartamento ? " (apartamento alterado)" : "",
            novoCheckin.toLocalDate(),
            novoCheckout.toLocalDate(),
            novaQuantidade
        ));
        
        historicoHospedeRepository.save(historico);
        
        System.out.println("✅ Pré-reserva atualizada com sucesso!");
        System.out.println("═══════════════════════════════════════════");
        
        return salva;
    }
    
    /**
     * 💳 FINALIZAR RESERVA PAGA
     */
    @Transactional
    public void finalizarReservaPaga(Long reservaId) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("💳 FINALIZANDO RESERVA PAGA");
        System.out.println("═══════════════════════════════════════════");
        
        Reserva reserva = reservaRepository.findById(reservaId)
            .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
        
        System.out.println("📊 Reserva #" + reserva.getId());
        System.out.println("   Status: " + reserva.getStatus());
        System.out.println("   Total Hospedagem: R$ " + reserva.getTotalHospedagem());
        System.out.println("   Total Recebido: R$ " + reserva.getTotalRecebido());
        System.out.println("   Total A Pagar: R$ " + reserva.getTotalApagar());
        
        // ✅ VALIDAÇÃO: Só pode finalizar se estiver ATIVA
        if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
            throw new RuntimeException("Apenas reservas ATIVAS podem ser finalizadas");
        }
        
        // ✅ VALIDAÇÃO: Deve estar totalmente paga
        if (reserva.getTotalApagar().compareTo(BigDecimal.ZERO) != 0) {
            throw new RuntimeException(
                "Ainda há saldo devedor de R$ " + reserva.getTotalApagar() + "\n\n" +
                "Use 'Finalizar Faturada' para enviar para Contas a Receber."
            );
        }
        
        System.out.println("✅ Validação OK - Reserva totalmente paga");
        
        // 1️⃣ Finalizar reserva
        reserva.setStatus(Reserva.StatusReservaEnum.FINALIZADA);
        
        // ✅ CRIAR HISTÓRICO DE FINALIZAÇÃO
        HistoricoHospede historicoFinalizacao = new HistoricoHospede();
        historicoFinalizacao.setReserva(reserva);
        historicoFinalizacao.setDataHora(LocalDateTime.now());
        historicoFinalizacao.setQuantidadeAnterior(reserva.getQuantidadeHospede());
        historicoFinalizacao.setQuantidadeNova(reserva.getQuantidadeHospede());
        historicoFinalizacao.setMotivo("Reserva FINALIZADA (PAGA) - Check-out realizado");
        historicoHospedeRepository.save(historicoFinalizacao);
        
        reservaRepository.save(reserva);
        System.out.println("✅ Reserva finalizada");
        
        // 2️⃣ Liberar apartamento para LIMPEZA
        Apartamento apartamento = reserva.getApartamento();
        apartamento.setStatus(Apartamento.StatusEnum.LIMPEZA);
        apartamentoRepository.save(apartamento);
        System.out.println("🧹 Apartamento " + apartamento.getNumeroApartamento() + " → LIMPEZA");
        
        // 3️⃣ Criar registro em Contas a Receber (PAGA)
        criarContaAReceberPaga(reserva);
        
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ FINALIZAÇÃO PAGA CONCLUÍDA!");
        System.out.println("═══════════════════════════════════════════");
    }

    /**
     * ✅ CRIAR CONTA A RECEBER (PAGA)
     */
    private void criarContaAReceberPaga(Reserva reserva) {
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
            conta.setObservacao("Pagamento à vista no checkout");
            
            if (reserva.getCliente().getEmpresa() != null) {
                conta.setEmpresa(reserva.getCliente().getEmpresa());
            }
            
            contaAReceberRepository.save(conta);
            
            System.out.println("💚 Registro PAGO criado em Contas a Receber!");
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao criar Conta a Receber: " + e.getMessage());
            throw new RuntimeException("Erro ao criar registro financeiro: " + e.getMessage());
        }
    }
    
 // ============================================
 // ✅ GESTÃO DE HÓSPEDES INDIVIDUAIS
 // ============================================

 /**
  * Processa a lista de hóspedes e cria registros em HospedagemHospede
  */
 @Transactional
 public void processarHospedes(Reserva reserva, List<HospedeReservaDTO> hospedes) {
     System.out.println("═══════════════════════════════════════════");
     System.out.println("👥 PROCESSANDO HÓSPEDES INDIVIDUAIS");
     System.out.println("═══════════════════════════════════════════");
     System.out.println("📊 Total de hóspedes a processar: " + hospedes.size());
     
     // ✅ VALIDAÇÃO: Primeiro hóspede sempre é titular
     boolean temTitular = false;
     for (int i = 0; i < hospedes.size(); i++) {
         HospedeReservaDTO hospedeDTO = hospedes.get(i);
         
         if (i == 0) {
             // Forçar primeiro como titular
             hospedeDTO.setTitular(true);
             temTitular = true;
         } else {
             // Demais não podem ser titulares
             hospedeDTO.setTitular(false);
         }
         
         processarHospede(reserva, hospedeDTO, i + 1);
     }
     
     if (!temTitular) {
         throw new RuntimeException("É necessário ter um hóspede titular");
     }
     
     System.out.println("✅ " + hospedes.size() + " hóspede(s) processado(s) com sucesso!");
     System.out.println("═══════════════════════════════════════════");
 }

 /**
  * Processa um hóspede individual
  */
 private void processarHospede(Reserva reserva, HospedeReservaDTO hospedeDTO, int numero) {
     System.out.println("\n📝 Processando hóspede #" + numero + ":");
     
     Cliente clienteHospede;
     
     // ✅ CASO 1: Cadastrar novo cliente rapidamente
     if (hospedeDTO.getCadastrarNovo() != null && hospedeDTO.getCadastrarNovo()) {
         System.out.println("   ➕ Criando novo cliente...");
         
         // Validar dados mínimos
         if (hospedeDTO.getNomeCompleto() == null || hospedeDTO.getNomeCompleto().trim().isEmpty()) {
             throw new RuntimeException("Nome completo é obrigatório para cadastrar novo hóspede");
         }
         
         // Verificar se CPF já existe (se fornecido)
         if (hospedeDTO.getCpf() != null && !hospedeDTO.getCpf().trim().isEmpty()) {
             Optional<Cliente> clienteExistente = clienteRepository.findByCpf(hospedeDTO.getCpf());
             if (clienteExistente.isPresent()) {
                 throw new RuntimeException("CPF " + hospedeDTO.getCpf() + " já cadastrado para: " + 
                     clienteExistente.get().getNome());
             }
         }
         
         clienteHospede = criarClienteRapido(hospedeDTO);
         System.out.println("   ✅ Cliente criado: " + clienteHospede.getNome() + " (ID: " + clienteHospede.getId() + ")");
         
     }
     // ✅ CASO 2: Usar cliente existente
     else if (hospedeDTO.getClienteId() != null) {
         System.out.println("   🔍 Buscando cliente existente ID: " + hospedeDTO.getClienteId());
         
         clienteHospede = clienteRepository.findById(hospedeDTO.getClienteId())
             .orElseThrow(() -> new RuntimeException("Cliente ID " + hospedeDTO.getClienteId() + " não encontrado"));
         
         System.out.println("   ✅ Cliente encontrado: " + clienteHospede.getNome());
         
     }
     // ✅ CASO 3: Erro - precisa informar clienteId ou cadastrarNovo
     else {
         throw new RuntimeException("Hóspede #" + numero + ": É necessário informar clienteId ou marcar cadastrarNovo=true");
     }
     
     // ✅ CRIAR REGISTRO DE HOSPEDAGEM
     HospedagemHospede hospedagem = new HospedagemHospede();
     hospedagem.setReserva(reserva);
     hospedagem.setCliente(clienteHospede);
     hospedagem.setDataEntrada(reserva.getDataCheckin());
     hospedagem.setDataSaida(null); // Será preenchida no checkout individual
     hospedagem.setTitular(hospedeDTO.getTitular() != null && hospedeDTO.getTitular());
     
     // Status baseado no status da reserva
     if (reserva.getStatus() == Reserva.StatusReservaEnum.ATIVA) {
         hospedagem.setStatus(HospedagemHospede.StatusHospedeIndividual.HOSPEDADO);
     } else {
         hospedagem.setStatus(HospedagemHospede.StatusHospedeIndividual.HOSPEDADO); // Pode ajustar se quiser outro status para pré-reserva
     }
     
     hospedagemHospedeRepository.save(hospedagem);
     
     System.out.println("   ✅ Hospedagem registrada: " + 
         (hospedagem.getTitular() ? "TITULAR" : "Acompanhante"));
 }

 /**
  * Cria um cliente com dados mínimos para check-in rápido
  */
 private Cliente criarClienteRapido(HospedeReservaDTO hospedeDTO) {
     Cliente cliente = new Cliente();
     
     // Dados obrigatórios
     cliente.setNome(hospedeDTO.getNomeCompleto());
     cliente.setDataNascimento(LocalDate.now()); // Placeholder - pode ajustar
     
     // Dados opcionais
     if (hospedeDTO.getCpf() != null && !hospedeDTO.getCpf().trim().isEmpty()) {
         cliente.setCpf(hospedeDTO.getCpf());
     }
     
     if (hospedeDTO.getTelefone() != null && !hospedeDTO.getTelefone().trim().isEmpty()) {
         cliente.setCelular(hospedeDTO.getTelefone());
     }
     
     // Campos com valores padrão
     cliente.setEndereco("");
     cliente.setCidade("");
     cliente.setEstado("");
     cliente.setCep("");
          cliente.setCreditoAprovado(false);
     
     return clienteRepository.save(cliente);
 }

 /**
  * Lista hóspedes de uma reserva
  */
 public List<HospedagemHospede> listarHospedesPorReserva(Long reservaId) {
     return hospedagemHospedeRepository.findByReservaId(reservaId);
 }
 
//============================================
//✅ CHECKOUT PARCIAL
//============================================

/**
* Realiza checkout parcial de um hóspede específico
*/
@Transactional
public Reserva checkoutParcial(Long reservaId, CheckoutParcialRequestDTO dto) {
  System.out.println("═══════════════════════════════════════════");
  System.out.println("👋 CHECKOUT PARCIAL");
  System.out.println("═══════════════════════════════════════════");
  
  // 1️⃣ BUSCAR RESERVA
  Reserva reserva = reservaRepository.findById(reservaId)
      .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
  
  // ✅ VALIDAÇÃO: Reserva deve estar ATIVA
  if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
      throw new RuntimeException("Apenas reservas ATIVAS permitem checkout parcial");
  }
  
  // 2️⃣ BUSCAR REGISTRO DE HOSPEDAGEM
  HospedagemHospede hospedagem = hospedagemHospedeRepository.findById(dto.getHospedagemHospedeId())
      .orElseThrow(() -> new RuntimeException("Hospedagem não encontrada"));
  
  // ✅ VALIDAÇÃO: Deve ser da mesma reserva
  if (!hospedagem.getReserva().getId().equals(reservaId)) {
      throw new RuntimeException("Hóspede não pertence a esta reserva");
  }
  
  // ✅ VALIDAÇÃO: Não pode fazer checkout duas vezes
  if (hospedagem.getStatus() == HospedagemHospede.StatusHospedeIndividual.CHECKOUT_REALIZADO) {
      throw new RuntimeException("Este hóspede já realizou checkout");
  }
  
  // ✅ VALIDAÇÃO: Não pode ser o ÚLTIMO hóspede
  long hospedesAtivos = hospedagemHospedeRepository.findByReservaId(reservaId).stream()
      .filter(h -> h.getStatus() == HospedagemHospede.StatusHospedeIndividual.HOSPEDADO)
      .count();
  
  if (hospedesAtivos <= 1) {
      throw new RuntimeException("Não é possível fazer checkout parcial do último hóspede. Use o checkout completo da reserva.");
  }
  
  System.out.println("📋 Hóspede: " + hospedagem.getCliente().getNome());
  System.out.println("   Titular: " + (hospedagem.getTitular() ? "SIM" : "NÃO"));
  System.out.println("   Hóspedes ativos antes: " + hospedesAtivos);
  
  // 3️⃣ MARCAR CHECKOUT DO HÓSPEDE
  LocalDateTime dataHoraSaida = dto.getDataHoraSaida() != null ? 
      dto.getDataHoraSaida() : LocalDateTime.now();
  
  hospedagem.setDataSaida(dataHoraSaida);
  hospedagem.setStatus(HospedagemHospede.StatusHospedeIndividual.CHECKOUT_REALIZADO);
  hospedagemHospedeRepository.save(hospedagem);
  
  System.out.println("✅ Checkout marcado para: " + dataHoraSaida);
  
  // 4️⃣ SE ERA TITULAR, PROMOVER PRÓXIMO HÓSPEDE
  if (hospedagem.getTitular()) {
      List<HospedagemHospede> hospedesRestantes = hospedagemHospedeRepository.findByReservaId(reservaId).stream()
          .filter(h -> h.getStatus() == HospedagemHospede.StatusHospedeIndividual.HOSPEDADO)
          .collect(Collectors.toList());
      
      if (!hospedesRestantes.isEmpty()) {
          HospedagemHospede novoTitular = hospedesRestantes.get(0);
          novoTitular.setTitular(true);
          hospedagemHospedeRepository.save(novoTitular);
          
          System.out.println("👑 Novo titular: " + novoTitular.getCliente().getNome());
      }
  }
  
  // 5️⃣ ATUALIZAR QUANTIDADE DE HÓSPEDES DA RESERVA
  Integer quantidadeAnterior = reserva.getQuantidadeHospede();
  
  Integer quantidadeNova = (int) hospedagemHospedeRepository.findByReservaId(reservaId).stream()
		    .filter(h -> h.getStatus() == HospedagemHospede.StatusHospedeIndividual.HOSPEDADO)
		    .count();
  
  reserva.setQuantidadeHospede(quantidadeNova);
  
  System.out.println("👥 Quantidade de hóspedes: " + quantidadeAnterior + " → " + quantidadeNova);
  
  // 6️⃣ BUSCAR NOVA DIÁRIA (para quantidade menor)
  TipoApartamento tipoApartamento = reserva.getApartamento().getTipoApartamento();
  
  Diaria novaDiaria = diariaRepository.findByTipoApartamentoAndQuantidade(tipoApartamento, quantidadeNova)
      .orElseThrow(() -> new RuntimeException(
          String.format("Nenhuma diária cadastrada para tipo '%s' com %d hóspede(s)", 
              tipoApartamento.getTipo(), 
              quantidadeNova)
      ));
  
  Diaria diariaAntiga = reserva.getDiaria();
  BigDecimal valorAntigo = diariaAntiga.getValor();
  BigDecimal valorNovo = novaDiaria.getValor();
  BigDecimal diferenca = valorNovo.subtract(valorAntigo);
  
  reserva.setDiaria(novaDiaria);
  
  System.out.println("💰 Valor diária: R$ " + valorAntigo + " → R$ " + valorNovo);
  System.out.println("   Diferença: R$ " + diferenca);
  
  // 7️⃣ CANCELAR DIÁRIAS LANCADAS NÃO FECHADAS
  if (controleDiariaService != null) {
      cancelarDiariasNaoFechadasCheckoutParcial(reserva, hospedagem.getCliente().getNome());
  }
  
  // 8️⃣ AJUSTAR DIÁRIAS FUTURAS NO EXTRATO
  LocalDateTime dataInicioAjuste = dataHoraSaida.toLocalDate().plusDays(1).atStartOfDay();
  ajustarDiariasFuturas(reserva, dataInicioAjuste, quantidadeAnterior, quantidadeNova);
  
  // 9️⃣ RECALCULAR TOTAIS
  recalcularTotaisReserva(reserva);
  
  // 🔟 SALVAR RESERVA
  Reserva reservaSalva = reservaRepository.save(reserva);
  
  // 1️⃣1️⃣ CRIAR HISTÓRICO
  HistoricoHospede historico = new HistoricoHospede();
  historico.setReserva(reservaSalva);
  historico.setDataHora(LocalDateTime.now());
  historico.setQuantidadeAnterior(quantidadeAnterior);
  historico.setQuantidadeNova(quantidadeNova);
  historico.setMotivo(String.format(
      "Checkout parcial - Hóspede: %s - Data: %s - Hóspedes restantes: %d - %s",
      hospedagem.getCliente().getNome(),
      dataHoraSaida.toLocalDate(),
      quantidadeNova,
      dto.getMotivo() != null && !dto.getMotivo().isEmpty() ? dto.getMotivo() : "Sem motivo informado"
  ));
  
  historicoHospedeRepository.save(historico);
  
  System.out.println("═══════════════════════════════════════════");
  System.out.println("✅ CHECKOUT PARCIAL CONCLUÍDO!");
  System.out.println("═══════════════════════════════════════════");
  
  return reservaSalva;
}

/**
* Cancela diárias LANCADAS (ainda não fechadas) quando há checkout parcial
*/
private void cancelarDiariasNaoFechadasCheckoutParcial(Reserva reserva, String nomeHospede) {
  try {
      List<ControleDiaria> diariasLancadas = controleDiariaService.buscarDiariasLancadasPorReserva(reserva.getId());
      
      for (ControleDiaria controle : diariasLancadas) {
          String motivo = String.format("Checkout parcial de %s", nomeHospede);
          controleDiariaService.cancelarDiaria(controle, motivo);
          
          System.out.println("❌ Diária LANCADA cancelada: " + controle.getDataLancamento().toLocalDate());
      }
      
      System.out.println("✅ Total de " + diariasLancadas.size() + " diária(s) LANCADA(s) cancelada(s)");
      
  } catch (Exception e) {
      System.err.println("⚠️ Erro ao cancelar diárias lançadas: " + e.getMessage());
      // Não bloqueia o checkout parcial
  }
}

@Transactional
public Reserva transferirHospede(TransferenciaHospedeDTO dto) {
    
    System.out.println("\n🔍 ETAPA 1: VALIDAÇÕES INICIAIS");
    System.out.println("════════════════════════════════════════");
    
    // ====================================
    // VALIDAÇÕES INICIAIS
    // ====================================
    
    // 1. Buscar o hóspede
    HospedagemHospede hospede = hospedagemHospedeRepository.findById(dto.getHospedeId())
        .orElseThrow(() -> new IllegalArgumentException("Hóspede não encontrado"));
    
    System.out.println("✅ Hóspede encontrado: " + hospede.getCliente().getNome());
    System.out.println("   Status atual: " + hospede.getStatus());
    System.out.println("   É titular: " + (hospede.getTitular() ? "SIM" : "NÃO")); // ⭐ LOG ADICIONADO
    
    // 2. Validar se está hospedado
    if (hospede.getStatus() != HospedagemHospede.StatusHospedeIndividual.HOSPEDADO) {
        throw new IllegalArgumentException("Hóspede não está com status HOSPEDADO");
    }
    
    // 3. Buscar reserva origem
    Reserva reservaOrigem = hospede.getReserva();
    if (reservaOrigem == null) {
        throw new IllegalArgumentException("Reserva de origem não encontrada");
    }
    
    System.out.println("✅ Reserva origem: #" + reservaOrigem.getId());
    System.out.println("   Apartamento: " + reservaOrigem.getApartamento().getNumeroApartamento());
    System.out.println("   Hóspedes na reserva: " + reservaOrigem.getQuantidadeHospede());
    
    // 4. Buscar apartamento destino
    Apartamento apartamentoDestino = apartamentoRepository.findById(dto.getApartamentoDestinoId())
        .orElseThrow(() -> new IllegalArgumentException("Apartamento destino não encontrado"));
    
    System.out.println("✅ Apartamento destino: " + apartamentoDestino.getNumeroApartamento());
    System.out.println("   Capacidade: " + apartamentoDestino.getCapacidade());
    System.out.println("   Status atual: " + apartamentoDestino.getStatus());
    
    // 5. Validar datas
    if (dto.getDataCheckoutNovo().isBefore(dto.getDataCheckinNovo()) || 
        dto.getDataCheckoutNovo().isEqual(dto.getDataCheckinNovo())) {
        throw new IllegalArgumentException("Data de checkout deve ser posterior ao check-in");
    }
    
    // 6. Verificar se destino é diferente da origem
    if (reservaOrigem.getApartamento().getId().equals(apartamentoDestino.getId())) {
        throw new IllegalArgumentException("Apartamento destino não pode ser o mesmo da origem");
    }
    
    // ====================================
    // VERIFICAR STATUS DO DESTINO
    // ====================================
    
    System.out.println("\n🔍 ETAPA 2: VERIFICANDO APARTAMENTO DESTINO");
    System.out.println("════════════════════════════════════════");
    
    // Buscar reservas ATIVAS no apartamento destino
    List<Reserva> reservasDestino = reservaRepository.findByApartamento(apartamentoDestino)
        .stream()
        .filter(r -> r.getStatus() == StatusReservaEnum.ATIVA)
        .collect(Collectors.toList());
    
    boolean destinoVazio = reservasDestino.isEmpty();
    Reserva reservaDestino = null;
    
    if (destinoVazio) {
        System.out.println("✅ Apartamento destino está VAZIO");
    } else {
        reservaDestino = reservasDestino.get(0);
        System.out.println("⚠️ Apartamento destino está OCUPADO");
        System.out.println("   Reserva #" + reservaDestino.getId());
        System.out.println("   Hóspedes atuais: " + reservaDestino.getQuantidadeHospede());
        System.out.println("   Capacidade disponível: " + 
            (apartamentoDestino.getCapacidade() - reservaDestino.getQuantidadeHospede()));
        
        // Validar se tem vaga
        if (reservaDestino.getQuantidadeHospede() >= apartamentoDestino.getCapacidade()) {
            throw new IllegalArgumentException("Apartamento destino está na capacidade máxima");
        }
    }
    
    // ====================================
    // VERIFICAR SE HÓSPEDE ESTÁ SOZINHO
    // ====================================
    
    System.out.println("\n🔍 ETAPA 3: ANALISANDO RESERVA ORIGEM");
    System.out.println("════════════════════════════════════════");
    
    // Contar hóspedes HOSPEDADOS na reserva origem
    long hospedesHospedadosOrigem = hospedagemHospedeRepository
        .findByReserva(reservaOrigem).stream()
        .filter(h -> h.getStatus() == HospedagemHospede.StatusHospedeIndividual.HOSPEDADO)
        .count();
    
    boolean hospedeSozinho = (hospedesHospedadosOrigem == 1);
    boolean transferirDespesas = hospedeSozinho && destinoVazio;
    
    System.out.println("👥 Hóspedes HOSPEDADOS na origem: " + hospedesHospedadosOrigem);
    System.out.println("🏠 Hóspede está sozinho? " + (hospedeSozinho ? "SIM" : "NÃO"));
    System.out.println("💰 Vai transferir despesas? " + (transferirDespesas ? "SIM" : "NÃO"));
    
    // ====================================
    // PROCESSAR APARTAMENTO ORIGEM
    // ====================================
    
    System.out.println("\n🔄 ETAPA 4: PROCESSANDO APARTAMENTO ORIGEM");
    System.out.println("════════════════════════════════════════");
    
    // Fazer checkout do hóspede
    hospede.setStatus(HospedagemHospede.StatusHospedeIndividual.CHECKOUT_REALIZADO);
    hospede.setDataSaida(LocalDateTime.now());
    hospedagemHospedeRepository.save(hospede);
    
    System.out.println("✅ Hóspede com status CHECKOUT");
    
    if (hospedeSozinho) {
        // CASO 1: Hóspede estava sozinho
        System.out.println("📦 CASO 1: Hóspede estava SOZINHO");
        
        // Finalizar reserva origem
        reservaOrigem.setStatus(StatusReservaEnum.FINALIZADA);
        reservaOrigem.setDataCheckoutReal(LocalDateTime.now());
        reservaRepository.save(reservaOrigem);
        
        System.out.println("✅ Reserva origem #" + reservaOrigem.getId() + " → FINALIZADA");
        
        // Apartamento para limpeza
        reservaOrigem.getApartamento().setStatus(StatusEnum.LIMPEZA);
        apartamentoRepository.save(reservaOrigem.getApartamento());
        
        System.out.println("✅ Apartamento " + reservaOrigem.getApartamento().getNumeroApartamento() + 
                         " → LIMPEZA");
        
    } else {
        // CASO 2: Hóspede estava com outros
        System.out.println("📦 CASO 2: Hóspede estava COMPARTILHADO");
        
        // Diminuir quantidade
        int novaQuantidade = reservaOrigem.getQuantidadeHospede() - 1;
        reservaOrigem.setQuantidadeHospede(novaQuantidade);
        
        System.out.println("👥 Quantidade na origem: " + 
            (reservaOrigem.getQuantidadeHospede() + 1) + " → " + novaQuantidade);
        
        // ══════════════════════════════════════════════════════════════
        // ⭐⭐⭐ CORREÇÃO DO BUG DO TITULAR ⭐⭐⭐
        // ══════════════════════════════════════════════════════════════
        if (hospede.getTitular()) {
            System.out.println("\n👑 ATENÇÃO: Hóspede transferido ERA o TITULAR!");
            System.out.println("   Promovendo próximo hóspede...");
            
            // Buscar hóspedes restantes HOSPEDADOS
            List<HospedagemHospede> hospedesRestantes = hospedagemHospedeRepository
                .findByReserva(reservaOrigem)
                .stream()
                .filter(h -> h.getStatus() == HospedagemHospede.StatusHospedeIndividual.HOSPEDADO)
                .collect(Collectors.toList());
            
            if (!hospedesRestantes.isEmpty()) {
                // Promover o primeiro hóspede restante
                HospedagemHospede novoTitular = hospedesRestantes.get(0);
                
                // Definir como titular
                novoTitular.setTitular(true);
                hospedagemHospedeRepository.save(novoTitular);
                
                // ✅ ATUALIZAR O CLIENTE DA RESERVA
                reservaOrigem.setCliente(novoTitular.getCliente());
                
                System.out.println("   ✅ Novo titular: " + novoTitular.getCliente().getNome());
                System.out.println("   ✅ Cliente da reserva atualizado!");
            } else {
                System.out.println("   ⚠️ AVISO: Não há outros hóspedes para promover");
            }
        }
        // ══════════════════════════════════════════════════════════════
        
        // Buscar nova diária para a quantidade reduzida
        Optional<Diaria> novaDiariaOrigem = diariaRepository
            .findByTipoApartamentoAndQuantidade(
                reservaOrigem.getApartamento().getTipoApartamento(),
                novaQuantidade
            );
        
        if (novaDiariaOrigem.isPresent()) {
            reservaOrigem.setDiaria(novaDiariaOrigem.get());
            System.out.println("💰 Nova diária origem: R$ " + novaDiariaOrigem.get().getValor());
            
            // Recalcular diárias futuras (apenas as PENDENTES)
            List<ExtratoReserva> extratosOrigem = extratoReservaRepository.findByReserva(reservaOrigem);
            for (ExtratoReserva extrato : extratosOrigem) {
                if (extrato.getStatusLancamento() == StatusLancamentoEnum.PENDENTE) {
                    extrato.setValorUnitario(novaDiariaOrigem.get().getValor());
                    extrato.setTotalLancamento(novaDiariaOrigem.get().getValor());
                    extratoReservaRepository.save(extrato);
                }
            }
            System.out.println("✅ Diárias futuras recalculadas");
        }
        
        // Registrar histórico
        HistoricoHospede hist = new HistoricoHospede();
        hist.setReserva(reservaOrigem);
        hist.setQuantidadeAnterior(reservaOrigem.getQuantidadeHospede() + 1);
        hist.setQuantidadeNova(novaQuantidade);
        hist.setMotivo("Transferência de hóspede " + hospede.getCliente().getNome());
        hist.setDataHora(LocalDateTime.now());
        historicoHospedeRepository.save(hist);
        
        reservaRepository.save(reservaOrigem);
        
        System.out.println("✅ Reserva origem atualizada");
    }
    
    // ====================================
    // PROCESSAR APARTAMENTO DESTINO
    // ====================================
    
    System.out.println("\n🔄 ETAPA 5: PROCESSANDO APARTAMENTO DESTINO");
    System.out.println("════════════════════════════════════════");
    
    Reserva reservaFinal;
    
    if (destinoVazio) {
        // CASO A: Destino está vazio - CRIAR NOVA RESERVA
        System.out.println("📦 CASO A: Destino VAZIO - Criando nova reserva");
        
        Reserva novaReserva = new Reserva();
        novaReserva.setCliente(hospede.getCliente());
        novaReserva.setApartamento(apartamentoDestino);
        novaReserva.setQuantidadeHospede(1);
        novaReserva.setDataCheckin(dto.getDataCheckinNovo().atTime(LocalTime.now()));
        novaReserva.setDataCheckout(dto.getDataCheckoutNovo().atTime(13, 0));
        novaReserva.setStatus(StatusReservaEnum.ATIVA);
        
        // Buscar diária para 1 pessoa
        Optional<Diaria> diariaDestino = diariaRepository
            .findByTipoApartamentoAndQuantidade(apartamentoDestino.getTipoApartamento(), 1);
        
        if (!diariaDestino.isPresent()) {
            throw new IllegalArgumentException("Diária não encontrada para o tipo de apartamento destino");
        }
        
        novaReserva.setDiaria(diariaDestino.get());
        
        // Calcular totais
        long dias = ChronoUnit.DAYS.between(
            dto.getDataCheckinNovo(),
            dto.getDataCheckoutNovo()
        );
        
        BigDecimal valorDiaria = diariaDestino.get().getValor();
        BigDecimal totalDiarias = valorDiaria.multiply(BigDecimal.valueOf(dias));
        
        novaReserva.setQuantidadeDiaria((int) dias);
        novaReserva.setTotalDiaria(totalDiarias);
        novaReserva.setTotalHospedagem(totalDiarias);
        novaReserva.setTotalApagar(totalDiarias);
        novaReserva.setTotalRecebido(BigDecimal.ZERO);
        novaReserva.setTotalProduto(BigDecimal.ZERO);
        
        // Salvar nova reserva
        reservaFinal = reservaRepository.save(novaReserva);
        
        System.out.println("✅ Nova reserva criada: #" + reservaFinal.getId());
        System.out.println("   Dias: " + dias);
        System.out.println("   Valor diária: R$ " + valorDiaria);
        System.out.println("   Total: R$ " + totalDiarias);
        
        // Criar nota de venda
        NotaVenda nota = new NotaVenda();
        nota.setReserva(reservaFinal);
        nota.setDataHoraVenda(LocalDateTime.now());
        nota.setStatus(NotaVenda.Status.ABERTA);
        nota.setTipoVenda(NotaVenda.TipoVendaEnum.APARTAMENTO);
        nota.setTotal(BigDecimal.ZERO);
        notaVendaRepository.save(nota);
        
        // Atualizar apartamento
        apartamentoDestino.setStatus(StatusEnum.OCUPADO);
        apartamentoRepository.save(apartamentoDestino);
        
        System.out.println("✅ Apartamento " + apartamentoDestino.getNumeroApartamento() + 
                         " → OCUPADO");
        
        // Adicionar hóspede na nova reserva
        HospedagemHospede novoHospede = new HospedagemHospede();
        novoHospede.setReserva(reservaFinal);
        novoHospede.setCliente(hospede.getCliente());
        novoHospede.setDataEntrada(LocalDateTime.now());
        novoHospede.setStatus(HospedagemHospede.StatusHospedeIndividual.HOSPEDADO);
        novoHospede.setTitular(true);
        hospedagemHospedeRepository.save(novoHospede);
        
        System.out.println("✅ Hóspede adicionado na nova reserva como TITULAR");
        
        // TRANSFERIR DESPESAS (se aplicável)
        if (transferirDespesas) {
            System.out.println("\n💰 TRANSFERINDO DESPESAS");
            System.out.println("════════════════════════════════════════");
            
            int despesasTransferidas = 0;
            
            List<ExtratoReserva> extratosOrigemTransferir = extratoReservaRepository.findByReserva(reservaOrigem);
            for (ExtratoReserva extrato : extratosOrigemTransferir)  {
                // Criar novo extrato na reserva destino
                ExtratoReserva novoExtrato = new ExtratoReserva();
                novoExtrato.setReserva(reservaFinal);
                novoExtrato.setDescricao(extrato.getDescricao() + " (Transferido)");
                novoExtrato.setQuantidade(extrato.getQuantidade());
                novoExtrato.setValorUnitario(extrato.getValorUnitario());
                novoExtrato.setTotalLancamento(extrato.getTotalLancamento());
                novoExtrato.setDataHoraLancamento(LocalDateTime.now());
                novoExtrato.setStatusLancamento(extrato.getStatusLancamento());
                novoExtrato.setNotaVendaId(extrato.getNotaVendaId());
                
                extratoReservaRepository.save(novoExtrato);
                despesasTransferidas++;
            }
            
            System.out.println("✅ " + despesasTransferidas + " despesa(s) transferida(s)");
            
            // Atualizar totais da nova reserva
            BigDecimal totalProdutos = reservaOrigem.getTotalProduto();
            reservaFinal.setTotalProduto(totalProdutos);
            reservaFinal.setTotalApagar(reservaFinal.getTotalDiaria().add(totalProdutos));
            reservaRepository.save(reservaFinal);
        }
        
    } else {
        // CASO B: Destino está ocupado - ADICIONAR À RESERVA EXISTENTE
        System.out.println("📦 CASO B: Destino OCUPADO - Adicionando à reserva existente");
        
        // Aumentar quantidade
        int novaQuantidadeDestino = reservaDestino.getQuantidadeHospede() + 1;
        reservaDestino.setQuantidadeHospede(novaQuantidadeDestino);
        
        System.out.println("👥 Quantidade no destino: " + 
            (reservaDestino.getQuantidadeHospede() - 1) + " → " + novaQuantidadeDestino);
        
        // Buscar nova diária
        Optional<Diaria> novaDiariaDestino = diariaRepository
            .findByTipoApartamentoAndQuantidade(
                apartamentoDestino.getTipoApartamento(),
                novaQuantidadeDestino
            );
        
        if (novaDiariaDestino.isPresent()) {
            reservaDestino.setDiaria(novaDiariaDestino.get());
            System.out.println("💰 Nova diária destino: R$ " + novaDiariaDestino.get().getValor());
            
            // Recalcular diárias futuras (apenas PENDENTES)
            List<ExtratoReserva> extratosDestino = extratoReservaRepository.findByReserva(reservaDestino);
            for (ExtratoReserva extrato : extratosDestino) {
                if (extrato.getStatusLancamento() == ExtratoReserva.StatusLancamentoEnum.PENDENTE) {
                    extrato.setValorUnitario(novaDiariaDestino.get().getValor());
                    extrato.setTotalLancamento(novaDiariaDestino.get().getValor());
                    extratoReservaRepository.save(extrato);
                }
            }
            System.out.println("✅ Diárias futuras recalculadas");
        }
        
        // Registrar histórico
        HistoricoHospede hist = new HistoricoHospede();
        hist.setReserva(reservaDestino);
        hist.setQuantidadeAnterior(reservaDestino.getQuantidadeHospede() - 1);
        hist.setQuantidadeNova(novaQuantidadeDestino);
        hist.setMotivo("Recebeu transferência de hóspede " + hospede.getCliente().getNome());
        hist.setDataHora(LocalDateTime.now());
        historicoHospedeRepository.save(hist);
        
        reservaRepository.save(reservaDestino);
        
        // Adicionar hóspede
        HospedagemHospede novoHospede = new HospedagemHospede();
        novoHospede.setReserva(reservaDestino);
        novoHospede.setCliente(hospede.getCliente());
        novoHospede.setDataEntrada(LocalDateTime.now());
        novoHospede.setStatus(HospedagemHospede.StatusHospedeIndividual.HOSPEDADO);
        novoHospede.setTitular(false); // Não é titular
        hospedagemHospedeRepository.save(novoHospede);
        
        System.out.println("✅ Hóspede adicionado na reserva existente");
        
        reservaFinal = reservaDestino;
    }
    
    System.out.println("\n✅ TRANSFERÊNCIA CONCLUÍDA COM SUCESSO!");
    System.out.println("════════════════════════════════════════");
    
    return reservaFinal;
   }    
}
