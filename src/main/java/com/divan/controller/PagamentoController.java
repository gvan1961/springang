package com.divan.controller;

import com.divan.dto.PagamentoRequestDTO;
import com.divan.dto.ResumoPagamentosDTO;
import com.divan.entity.Pagamento;
import com.divan.entity.Reserva;
import com.divan.service.PagamentoService;
import com.divan.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/pagamentos")
@CrossOrigin(origins = "*")
public class PagamentoController {
    
    @Autowired
    private PagamentoService pagamentoService;
    
    @Autowired
    private ReservaService reservaService;
    
    @PostMapping("/pre-reserva")
    public ResponseEntity<?> processarPagamentoPreReserva(@Valid @RequestBody PagamentoRequestDTO dto) {
        try {
            System.out.println("═══════════════════════════════════════");
            System.out.println("💳 PROCESSAR PAGAMENTO DE PRÉ-RESERVA");
            System.out.println("═══════════════════════════════════════");
            System.out.println("Reserva ID: " + dto.getReservaId());
            System.out.println("Valor: " + dto.getValor());
            System.out.println("Forma: " + dto.getFormaPagamento());
            
            // Buscar reserva
            Optional<Reserva> reservaOpt = reservaService.buscarPorId(dto.getReservaId());
            if (reservaOpt.isEmpty()) {
                System.err.println("❌ Reserva não encontrada: " + dto.getReservaId());
                return ResponseEntity.badRequest().body("Reserva não encontrada");
            }

            Reserva reserva = reservaOpt.get();
            System.out.println("✅ Reserva encontrada: #" + reserva.getId());
            System.out.println("   Status atual: " + reserva.getStatus());
            
            // Verificar se é pré-reserva
            if (!"PRE_RESERVA".equals(reserva.getStatus().name())) {
                System.err.println("❌ Reserva não está em PRE_RESERVA");
                return ResponseEntity.badRequest().body("Reserva não está em status PRÉ-RESERVA");
            }

            // Criar pagamento
            Pagamento pagamento = new Pagamento();
            pagamento.setReserva(reserva);
            pagamento.setValor(dto.getValor());
            pagamento.setFormaPagamento(dto.getFormaPagamento());
            pagamento.setObservacao(dto.getObservacao());

            System.out.println("📤 Chamando service para processar pagamento e ativar...");
            
            // Processar pagamento E ativar reserva
            Pagamento pagamentoProcessado = pagamentoService.processarPagamentoPreReserva(pagamento);
            
            System.out.println("✅ Pagamento processado e reserva ativada com sucesso!");
            System.out.println("═══════════════════════════════════════");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoProcessado);

        } catch (Exception e) {
            System.err.println("❌ ERRO ao processar pagamento de pré-reserva: " + e.getMessage());
            e.printStackTrace();
            System.out.println("═══════════════════════════════════════");
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @PostMapping
    public ResponseEntity<?> processarPagamento(@Valid @RequestBody PagamentoRequestDTO dto) {
        try {
            // Buscar reserva
            Optional<Reserva> reservaOpt = reservaService.buscarPorId(dto.getReservaId());
            if (reservaOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Reserva não encontrada");
            }
            
            // Criar pagamento
            Pagamento pagamento = new Pagamento();
            pagamento.setReserva(reservaOpt.get());
            pagamento.setValor(dto.getValor());
            pagamento.setFormaPagamento(dto.getFormaPagamento());
            pagamento.setObservacao(dto.getObservacao());
            
            Pagamento pagamentoProcessado = pagamentoService.processarPagamento(pagamento);
            return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoProcessado);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/reserva/{reservaId}")
    public ResponseEntity<List<Pagamento>> buscarPorReserva(@PathVariable Long reservaId) {
        List<Pagamento> pagamentos = pagamentoService.buscarPorReserva(reservaId);
        return ResponseEntity.ok(pagamentos);
    }
    
    @GetMapping("/do-dia")
    public ResponseEntity<List<Pagamento>> buscarPagamentosDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<Pagamento> pagamentos = pagamentoService.buscarPagamentosDoDia(data);
        return ResponseEntity.ok(pagamentos);
    }
    
    @GetMapping("/periodo")
    public ResponseEntity<List<Pagamento>> buscarPagamentosPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        List<Pagamento> pagamentos = pagamentoService.buscarPagamentosPorPeriodo(inicio, fim);
        return ResponseEntity.ok(pagamentos);
    }
    
    @GetMapping("/resumo-do-dia")
    public ResponseEntity<ResumoPagamentosDTO> gerarResumoDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        ResumoPagamentosDTO resumo = pagamentoService.gerarResumoDoDia(data);
        return ResponseEntity.ok(resumo);
    }
}
