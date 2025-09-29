package com.divan.controller;

import com.divan.entity.ContaAReceber;
import com.divan.entity.ExtratoReserva;
import com.divan.service.RelatorioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/relatorios")
@CrossOrigin(origins = "*")
public class RelatorioController {
    
    @Autowired
    private RelatorioService relatorioService;
    
    @GetMapping("/fechamento-diario")
    public ResponseEntity<Map<String, Object>> gerarRelatorioFechamentoDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        Map<String, Object> relatorio = relatorioService.gerarRelatorioFechamentoDiario(data);
        return ResponseEntity.ok(relatorio);
    }
    
    @GetMapping("/extrato-reserva/{reservaId}")
    public ResponseEntity<List<ExtratoReserva>> gerarExtratoReserva(@PathVariable Long reservaId) {
        List<ExtratoReserva> extrato = relatorioService.gerarExtratoReserva(reservaId);
        return ResponseEntity.ok(extrato);
    }
    
    @GetMapping("/contas-receber")
    public ResponseEntity<List<ContaAReceber>> gerarRelatorioContasReceber() {
        List<ContaAReceber> contas = relatorioService.gerarRelatorioContasReceber();
        return ResponseEntity.ok(contas);
    }
    
    @GetMapping("/contas-vencidas")
    public ResponseEntity<List<ContaAReceber>> gerarRelatorioContasVencidas() {
        List<ContaAReceber> contas = relatorioService.gerarRelatorioContasVencidas();
        return ResponseEntity.ok(contas);
    }
    
    @GetMapping("/mapa-reservas")
    public ResponseEntity<Map<String, Object>> gerarMapaReservas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dataFim) {
        Map<String, Object> mapa = relatorioService.gerarMapaReservas(dataInicio, dataFim);
        return ResponseEntity.ok(mapa);
    }
}
