package com.divan.controller;

import com.divan.dto.ApartamentoResponseDTO;
import com.divan.dto.ClienteResponseDTO;
import com.divan.dto.ItemVendaRequestDTO;
import com.divan.dto.LancamentoRapidoRequest;
import com.divan.dto.ReservaDetalhesDTO;
import com.divan.dto.ReservaRequestDTO;
import com.divan.dto.ReservaResponseDTO;
import com.divan.dto.TransferenciaApartamentoDTO;
import com.divan.entity.Apartamento;
import com.divan.entity.Cliente;
import com.divan.entity.HistoricoReserva;
import com.divan.entity.ItemVenda;
import com.divan.entity.NotaVenda;
import com.divan.entity.Reserva;
import com.divan.entity.Reserva.StatusReservaEnum;
import com.divan.repository.ApartamentoRepository;
import com.divan.repository.ClienteRepository;
import com.divan.repository.HistoricoReservaRepository;
import com.divan.repository.ReservaRepository;
import com.divan.service.ApartamentoService;
import com.divan.service.ClienteService;
import com.divan.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/reservas")
@CrossOrigin(origins = "*")
public class ReservaController {
    
    @Autowired
    private ReservaService reservaService;
    
    @Autowired
    private ApartamentoService apartamentoService;
    
    @Autowired
    private ClienteService clienteService;
    
    @Autowired
    private ClienteRepository clienteRepository;
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private HistoricoReservaRepository historicoReservaRepository;
    
    @PostMapping
    public ResponseEntity<?> criarReserva(@Valid @RequestBody ReservaRequestDTO dto) {
        try {
            // Log de debug
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            System.out.println("========== CRIAR RESERVA ==========");
            System.out.println("👤 Usuário: " + (auth != null ? auth.getName() : "NÃO AUTENTICADO"));
            System.out.println("🔑 Authorities: " + (auth != null ? auth.getAuthorities() : "NENHUMA"));
            System.out.println("📝 DTO recebido: " + dto);
            
            // Validar datas
            if (dto.getDataCheckout().isBefore(dto.getDataCheckin()) || 
                dto.getDataCheckout().isEqual(dto.getDataCheckin())) {
                return ResponseEntity.badRequest()
                    .body("Data de checkout deve ser posterior ao checkin");
            }
            
            // Buscar cliente
            Cliente cliente = clienteRepository.findById(dto.getClienteId())
                .orElseThrow(() -> new RuntimeException("Cliente não encontrado"));
            
            // Buscar apartamento
            Apartamento apartamento = apartamentoRepository.findById(dto.getApartamentoId())
                .orElseThrow(() -> new RuntimeException("Apartamento não encontrado"));
            
            // Validar capacidade do apartamento
            if (dto.getQuantidadeHospede() > apartamento.getCapacidade()) {
                return ResponseEntity.badRequest()
                    .body("Quantidade de hóspedes (" + dto.getQuantidadeHospede() + 
                          ") excede a capacidade do apartamento (" + apartamento.getCapacidade() + ")");
            }
            
            // Criar reserva
            Reserva reserva = new Reserva();
            reserva.setCliente(cliente);
            reserva.setApartamento(apartamento);
            reserva.setQuantidadeHospede(dto.getQuantidadeHospede());
            reserva.setDataCheckin(dto.getDataCheckin());
            reserva.setDataCheckout(dto.getDataCheckout());
            // Adicionar observações se necessário
            // reserva.setObservacoes("");
            
            // Chamar o service
            Reserva reservaCriada = reservaService.criarReserva(reserva);
            
            // Converter para DTO de resposta
            ReservaResponseDTO response = converterParaDTO(reservaCriada);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            System.out.println("❌ Erro ao criar reserva: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        List<ReservaResponseDTO> reservas = reservaService.listarTodasDTO();
        return ResponseEntity.ok(reservas);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ReservaDetalhesDTO> buscarPorId(@PathVariable Long id) {
        System.out.println("📋 Requisição para buscar reserva: " + id);
        
        ReservaDetalhesDTO reserva = reservaService.buscarDetalhes(id);
        
        System.out.println("📤 Retornando reserva com:");
        System.out.println("  Total Diária: R$ " + reserva.getTotalDiaria());
        System.out.println("  Total Produto: R$ " + reserva.getTotalProduto());
        System.out.println("  Total Hospedagem: R$ " + reserva.getTotalHospedagem());
        
        return ResponseEntity.ok(reserva);
    }
    
    @GetMapping("/ativas")
    public ResponseEntity<List<Reserva>> buscarAtivas() {
        List<Reserva> reservas = reservaService.buscarAtivas();
        return ResponseEntity.ok(reservas);
    }
    
    @GetMapping("/checkins-do-dia")
    public ResponseEntity<List<Reserva>> buscarCheckinsDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<Reserva> reservas = reservaService.buscarCheckinsDoDia(data);
        return ResponseEntity.ok(reservas);
    }
    
    @GetMapping("/checkouts-do-dia")
    public ResponseEntity<List<Reserva>> buscarCheckoutsDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<Reserva> reservas = reservaService.buscarCheckoutsDoDia(data);
        return ResponseEntity.ok(reservas);
    }
    
    @GetMapping("/periodo")
    public ResponseEntity<List<Reserva>> buscarPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        List<Reserva> reservas = reservaService.buscarPorPeriodo(inicio, fim);
        return ResponseEntity.ok(reservas);
    }
    
    @PatchMapping("/{id}/alterar-hospedes")
    public ResponseEntity<?> alterarQuantidadeHospedes(
            @PathVariable Long id, 
            @RequestParam Integer quantidade,
            @RequestParam(required = false) String motivo) {
        try {
            Reserva reserva = reservaService.alterarQuantidadeHospedes(id, quantidade, motivo);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<?> finalizarReserva(@PathVariable Long id) {
        try {
            System.out.println("═══════════════════════════════════════");
            System.out.println("📝 ENDPOINT: FINALIZAR RESERVA");
            System.out.println("   Reserva ID: " + id);
            System.out.println("═══════════════════════════════════════");
            
            Reserva reserva = reservaService.finalizarReserva(id);
            
            System.out.println("✅ Reserva finalizada com sucesso!");
            
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Reserva finalizada com sucesso!",
                "reserva", reserva
            ));
            
        } catch (RuntimeException e) {
            System.err.println("❌ ERRO ao finalizar reserva:");
            System.err.println("   Mensagem: " + e.getMessage());
            e.printStackTrace();
            
            // ✅ Retornar erro estruturado para o frontend
            return ResponseEntity.badRequest().body(Map.of(
                "sucesso", false,
                "erro", e.getMessage()
            ));
        } catch (Exception e) {
            System.err.println("❌ ERRO INESPERADO:");
            System.err.println("   Mensagem: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "sucesso", false,
                "erro", "Erro inesperado ao finalizar reserva: " + e.getMessage()
            ));
        }
    }
    
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarReserva(@PathVariable Long id, @RequestParam String motivo) {
        try {
            Reserva reserva = reservaService.cancelarReserva(id, motivo);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PatchMapping("/{id}/alterar-checkout")
    public ResponseEntity<?> alterarDataCheckout(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime novaDataCheckout,
            @RequestParam(required = false) String motivo) {
        try {
            Reserva reserva = reservaService.alterarDataCheckout(id, novaDataCheckout, motivo);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping("/{id}/consumo")
    public ResponseEntity<?> adicionarProdutoAoConsumo(
            @PathVariable Long id,
            @RequestBody ItemVendaRequestDTO request) {
        try {
            Reserva reserva = reservaService.adicionarProdutoAoConsumo(
                id, 
                request.getProdutoId(), 
                request.getQuantidade(), 
                request.getObservacao()
            );
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    @GetMapping("/{id}/consumo")
    public ResponseEntity<?> listarConsumo(@PathVariable Long id) {
        try {
            List<ItemVenda> itens = reservaService.listarConsumoPorReserva(id);
            return ResponseEntity.ok(itens);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
    
    @GetMapping("/{id}/notas-venda")
    public ResponseEntity<?> listarNotasVenda(@PathVariable Long id) {
        try {
            List<NotaVenda> notas = reservaService.listarNotasVendaPorReserva(id);
            return ResponseEntity.ok(notas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<List<ReservaResponseDTO>> listarPorStatus(@PathVariable Reserva.StatusReservaEnum status) {
        List<ReservaResponseDTO> reservas = reservaService.listarPorStatusDTO(status);
        return ResponseEntity.ok(reservas);
    }
    
    @PostMapping("/transferir-apartamento")
    public ResponseEntity<?> transferirApartamento(@RequestBody TransferenciaApartamentoDTO dto) {
        try {
            Reserva reserva = reservaService.transferirApartamento(dto);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
    
    @PostMapping("/comandas-rapidas")
    public ResponseEntity<Map<String, Object>> processarComandasRapidas(@RequestBody LancamentoRapidoRequest request) {
        System.out.println("🍽️ Recebendo comandas rápidas");
        Map<String, Object> resultado = reservaService.processarComandasRapidas(request);
        return ResponseEntity.ok(resultado);
    }
    
    @PatchMapping("/{id}/editar-pre-reserva")
    public ResponseEntity<?> editarPreReserva(
            @PathVariable Long id,
            @RequestBody Map<String, Object> updates) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            
            String dataCheckinStr = (String) updates.get("dataCheckin");
            String dataCheckoutStr = (String) updates.get("dataCheckout");
            
            dataCheckinStr = dataCheckinStr.replaceAll("\\.\\d{3}Z?$", "");
            dataCheckoutStr = dataCheckoutStr.replaceAll("\\.\\d{3}Z?$", "");
            
            LocalDateTime dataCheckin = LocalDateTime.parse(dataCheckinStr, formatter);
            LocalDateTime dataCheckout = LocalDateTime.parse(dataCheckoutStr, formatter);
            
            Reserva reserva = reservaService.editarPreReserva(
                id,
                ((Number) updates.get("apartamentoId")).longValue(),
                (Integer) updates.get("quantidadeHospede"),
                dataCheckin,
                dataCheckout
            );
            
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}/pre-reserva")
    public ResponseEntity<?> excluirPreReserva(@PathVariable Long id) {
        try {
            reservaService.excluirPreReserva(id);
            return ResponseEntity.ok(Map.of("message", "Pré-reserva excluída com sucesso"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    
 // Método auxiliar para converter Reserva para ReservaResponseDTO
    private ReservaResponseDTO converterParaDTO(Reserva reserva) {
        ReservaResponseDTO dto = new ReservaResponseDTO();
        
        // Dados básicos
        dto.setId(reserva.getId());
        dto.setQuantidadeHospede(reserva.getQuantidadeHospede());
        dto.setDataCheckin(reserva.getDataCheckin());
        dto.setDataCheckout(reserva.getDataCheckout());
        dto.setQuantidadeDiaria(reserva.getQuantidadeDiaria());
        dto.setStatus(reserva.getStatus());
        dto.setObservacoes(reserva.getObservacoes() != null ? reserva.getObservacoes() : "");
        
        // Dados financeiros
        dto.setValorDiaria(reserva.getDiaria() != null ? reserva.getDiaria().getValor() : BigDecimal.ZERO);
        dto.setTotalDiaria(reserva.getTotalDiaria());
        dto.setTotalHospedagem(reserva.getTotalHospedagem());
        dto.setTotalRecebido(reserva.getTotalRecebido());
        dto.setTotalApagar(reserva.getTotalApagar());
        
        // Cliente
        if (reserva.getCliente() != null) {
            ClienteResponseDTO clienteDTO = new ClienteResponseDTO();
            clienteDTO.setId(reserva.getCliente().getId());
            clienteDTO.setNome(reserva.getCliente().getNome());
            clienteDTO.setCpf(reserva.getCliente().getCpf());
            clienteDTO.setTelefone(reserva.getCliente().getCelular());
            clienteDTO.setEndereco(reserva.getCliente().getEndereco());
            clienteDTO.setCidade(reserva.getCliente().getCidade());
            clienteDTO.setEstado(reserva.getCliente().getEstado());
            clienteDTO.setCep(reserva.getCliente().getCep());
            dto.setCliente(clienteDTO);
        }
        
        // Apartamento
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
        
        return dto;
    }
    
    
    @PatchMapping("/{id}/aplicar-desconto")
    public ResponseEntity<?> aplicarDesconto(
            @PathVariable Long id,
            @RequestParam BigDecimal valorDesconto,
            @RequestParam(required = false) String motivo) {
        
        try {
            Reserva reserva = reservaRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
            
            if (!reserva.getStatus().equals(StatusReservaEnum.ATIVA)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "Só é possível aplicar desconto em reservas ativas"));
            }
            
            if (valorDesconto.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "Valor do desconto deve ser maior que zero"));
            }
            
            if (valorDesconto.compareTo(reserva.getTotalHospedagem()) > 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "Desconto não pode ser maior que o total da hospedagem"));
            }
            
            // Aplicar o desconto
            reserva.setDesconto(valorDesconto);
            
            // Recalcular totais
            BigDecimal novoTotal = reserva.getTotalHospedagem().subtract(valorDesconto);
            BigDecimal novoSaldo = novoTotal.subtract(reserva.getTotalRecebido());
            reserva.setTotalApagar(novoSaldo);
            
            // Registrar no histórico
            HistoricoReserva historico = new HistoricoReserva();
            historico.setReserva(reserva);
            historico.setDataHora(LocalDateTime.now());
            historico.setTipo("DESCONTO_APLICADO");
            historico.setDescricao("Desconto aplicado no valor de R$ " + valorDesconto);
            historico.setDetalhes(
                "Valor do desconto: R$ " + valorDesconto + "\n" +
                "Total anterior: R$ " + reserva.getTotalHospedagem().add(valorDesconto) + "\n" +
                "Total com desconto: R$ " + novoTotal + "\n" +
                "Novo saldo: R$ " + novoSaldo +
                (motivo != null && !motivo.trim().isEmpty() ? "\nMotivo: " + motivo : "")
            );
            historicoReservaRepository.save(historico);
            
            reservaRepository.save(reserva);
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Desconto aplicado com sucesso",
                "desconto", valorDesconto,
                "novoTotal", novoTotal,
                "novoSaldo", novoSaldo
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}/remover-desconto")
    public ResponseEntity<?> removerDesconto(@PathVariable Long id) {
        try {
            Reserva reserva = reservaRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Reserva não encontrada"));
            
            if (!reserva.getStatus().equals(StatusReservaEnum.ATIVA)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "Só é possível remover desconto em reservas ativas"));
            }
            
            if (reserva.getDesconto() == null || reserva.getDesconto().compareTo(BigDecimal.ZERO) == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("erro", "Não há desconto aplicado nesta reserva"));
            }
            
            BigDecimal descontoRemovido = reserva.getDesconto();
            BigDecimal totalAnterior = reserva.getTotalHospedagem().subtract(descontoRemovido);
            
            // Remover o desconto
            reserva.setDesconto(BigDecimal.ZERO);
            
            // Recalcular totais
            BigDecimal novoSaldo = reserva.getTotalHospedagem().subtract(reserva.getTotalRecebido());
            reserva.setTotalApagar(novoSaldo);
            
            // Registrar no histórico
            HistoricoReserva historico = new HistoricoReserva();
            historico.setReserva(reserva);
            historico.setDataHora(LocalDateTime.now());
            historico.setTipo("DESCONTO_REMOVIDO");
            historico.setDescricao("Desconto removido no valor de R$ " + descontoRemovido);
            historico.setDetalhes(
                "Valor do desconto removido: R$ " + descontoRemovido + "\n" +
                "Total anterior (com desconto): R$ " + totalAnterior + "\n" +
                "Total atual (sem desconto): R$ " + reserva.getTotalHospedagem() + "\n" +
                "Novo saldo: R$ " + novoSaldo
            );
            historicoReservaRepository.save(historico);
            
            reservaRepository.save(reserva);
            
            return ResponseEntity.ok(Map.of(
                "mensagem", "Desconto removido com sucesso",
                "descontoRemovido", descontoRemovido,
                "novoSaldo", novoSaldo
            ));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("erro", e.getMessage()));
        }
    }
    
    
    
    /**
     * 💳 FINALIZAR RESERVA PAGA (checkout com pagamento à vista)
     */
    @PatchMapping("/{id}/finalizar-paga")
    public ResponseEntity<?> finalizarReservaPaga(@PathVariable Long id) {
        try {
            System.out.println("═══════════════════════════════════════");
            System.out.println("💳 ENDPOINT: FINALIZAR RESERVA PAGA");
            System.out.println("   Reserva ID: " + id);
            System.out.println("═══════════════════════════════════════");
            
            // Buscar reserva
            Optional<Reserva> reservaOpt = reservaService.buscarPorId(id);
            if (reservaOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "erro", "Reserva não encontrada"
                ));
            }
            
            Reserva reserva = reservaOpt.get();
            
            // Validar se está totalmente paga
            if (reserva.getTotalApagar().compareTo(BigDecimal.ZERO) != 0) {
                return ResponseEntity.badRequest().body(Map.of(
                    "sucesso", false,
                    "erro", "Ainda há saldo devedor de R$ " + reserva.getTotalApagar() + 
                           ". Use 'Finalizar Faturada' para enviar para Contas a Receber."
                ));
            }
            
            // Finalizar como PAGA
            reservaService.finalizarReservaPaga(id);
            
            System.out.println("✅ Reserva finalizada como PAGA com sucesso!");
            System.out.println("═══════════════════════════════════════");
            
            return ResponseEntity.ok(Map.of(
                "sucesso", true,
                "mensagem", "Reserva finalizada! Recibo disponível para impressão."
            ));
            
        } catch (Exception e) {
            System.err.println("❌ Erro: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "sucesso", false,
                "erro", e.getMessage()
            ));
        }
    }
}
