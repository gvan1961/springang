package com.divan.controller;

import com.divan.entity.ItemVenda;
import com.divan.entity.NotaVenda;
import com.divan.service.VendaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vendas")
@CrossOrigin(origins = "*")
public class VendaController {
    
    @Autowired
    private VendaService vendaService;
    
    @PostMapping
    public ResponseEntity<NotaVenda> processarVenda(@RequestBody Map<String, Object> vendaData) {
        try {
            NotaVenda notaVenda = new NotaVenda();
            // Mapear dados da venda
            // ... implementar mapeamento dos dados
            
            @SuppressWarnings("unchecked")
            List<ItemVenda> itens = (List<ItemVenda>) vendaData.get("itens");
            
            NotaVenda vendaProcessada = vendaService.processarVenda(notaVenda, itens);
            return ResponseEntity.status(HttpStatus.CREATED).body(vendaProcessada);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/do-dia")
    public ResponseEntity<List<NotaVenda>> buscarVendasDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<NotaVenda> vendas = vendaService.buscarVendasDoDia(data);
        return ResponseEntity.ok(vendas);
    }
    
    @GetMapping("/periodo")
    public ResponseEntity<List<NotaVenda>> buscarVendasPorPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fim) {
        List<NotaVenda> vendas = vendaService.buscarVendasPorPeriodo(inicio, fim);
        return ResponseEntity.ok(vendas);
    }
    
    @GetMapping("/vista-do-dia")
    public ResponseEntity<List<NotaVenda>> buscarVendasVistaDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime data) {
        List<NotaVenda> vendas = vendaService.buscarVendasVistaDoDia(data);
        return ResponseEntity.ok(vendas);
    }
}
