package com.divan.controller;

import com.divan.entity.Apartamento;
import com.divan.service.ApartamentoService;
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
@RequestMapping("/api/apartamentos")
@CrossOrigin(origins = "*")
public class ApartamentoController {
    
    @Autowired
    private ApartamentoService apartamentoService;
    
    @PostMapping
    public ResponseEntity<Apartamento> criar(@Valid @RequestBody Apartamento apartamento) {
        try {
            Apartamento apartamentoSalvo = apartamentoService.salvar(apartamento);
            return ResponseEntity.status(HttpStatus.CREATED).body(apartamentoSalvo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Apartamento>> listarTodos() {
        List<Apartamento> apartamentos = apartamentoService.listarTodos();
        return ResponseEntity.ok(apartamentos);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Apartamento> buscarPorId(@PathVariable Long id) {
        Optional<Apartamento> apartamento = apartamentoService.buscarPorId(id);
        return apartamento.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/numero/{numero}")
    public ResponseEntity<Apartamento> buscarPorNumero(@PathVariable String numero) {
        Optional<Apartamento> apartamento = apartamentoService.buscarPorNumero(numero);
        return apartamento.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/disponiveis")
    public ResponseEntity<List<Apartamento>> buscarDisponiveis() {
        List<Apartamento> apartamentos = apartamentoService.buscarDisponiveis();
        return ResponseEntity.ok(apartamentos);
    }
    
    @GetMapping("/ocupados")
    public ResponseEntity<List<Apartamento>> buscarOcupados() {
        List<Apartamento> apartamentos = apartamentoService.buscarOcupados();
        return ResponseEntity.ok(apartamentos);
    }
    
    @GetMapping("/disponiveis-periodo")
    public ResponseEntity<List<Apartamento>> buscarDisponiveisParaPeriodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkin,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime checkout) {
        List<Apartamento> apartamentos = apartamentoService.buscarDisponiveisParaPeriodo(checkin, checkout);
        return ResponseEntity.ok(apartamentos);
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Apartamento>> buscarPorStatus(@PathVariable Apartamento.StatusEnum status) {
        List<Apartamento> apartamentos = apartamentoService.buscarPorStatus(status);
        return ResponseEntity.ok(apartamentos);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Apartamento> atualizar(@PathVariable Long id, @Valid @RequestBody Apartamento apartamento) {
        try {
            Apartamento apartamentoAtualizado = apartamentoService.atualizar(id, apartamento);
            return ResponseEntity.ok(apartamentoAtualizado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    @PatchMapping("/{id}/status")
    public ResponseEntity<Apartamento> atualizarStatus(@PathVariable Long id, @RequestParam Apartamento.StatusEnum status) {
        try {
            Apartamento apartamento = apartamentoService.atualizarStatus(id, status);
            return ResponseEntity.ok(apartamento);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
