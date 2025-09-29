package com.divan.controller;

import com.divan.entity.Pagamento;
import com.divan.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pagamentos")
@CrossOrigin(origins = "*")
public class PagamentoController {
    
    @Autowired
    private PagamentoService pagamentoService;
    
    @PostMapping
    public ResponseEntity<Pagamento> processarPagamento(@Valid @RequestBody Pagamento pagamento) {
        try {
            Pagamento pagamentoProcessado = pagamentoService.processarPagamento(pagamento);
            return ResponseEntity.status(HttpStatus.CREATED).body(pagamentoProcessado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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
    
    @GetMapping("/total-por-forma-pagamento")
    public ResponseEntity<List<Object[]>> buscarTotalPorFormaPagamento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<Object[]> totais = pagamentoService.buscarTotalPorFormaPagamento(data);
        return ResponseEntity.ok(totais);
    }
}
