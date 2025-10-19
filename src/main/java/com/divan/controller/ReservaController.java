package com.divan.controller;

import com.divan.dto.ItemVendaRequestDTO;
import com.divan.dto.ReservaRequestDTO;
import com.divan.dto.ReservaResponseDTO;
import com.divan.entity.Apartamento;
import com.divan.entity.Cliente;
import com.divan.entity.ItemVenda;
import com.divan.entity.NotaVenda;
import com.divan.entity.Reserva;
import com.divan.service.ApartamentoService;
import com.divan.service.ClienteService;
import com.divan.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    
    @PostMapping
    public ResponseEntity<?> criarReserva(@Valid @RequestBody ReservaRequestDTO dto) {
        try {
            // Validar datas
            if (dto.getDataCheckout().isBefore(dto.getDataCheckin()) || 
                dto.getDataCheckout().isEqual(dto.getDataCheckin())) {
                return ResponseEntity.badRequest()
                    .body("Data de check-out deve ser posterior ao check-in");
            }
            
            // Buscar apartamento
            Optional<Apartamento> apartamentoOpt = apartamentoService.buscarPorId(dto.getApartamentoId());
            if (apartamentoOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Apartamento não encontrado");
            }
            
            // Buscar cliente
            Optional<Cliente> clienteOpt = clienteService.buscarPorId(dto.getClienteId());
            if (clienteOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Cliente não encontrado");
            }
            
            // Validar capacidade do apartamento
            Apartamento apartamento = apartamentoOpt.get();
            if (dto.getQuantidadeHospede() > apartamento.getCapacidade()) {
                return ResponseEntity.badRequest()
                    .body("Quantidade de hóspedes (" + dto.getQuantidadeHospede() + 
                          ") excede a capacidade do apartamento (" + apartamento.getCapacidade() + ")");
            }
            
            // Criar reserva
            Reserva reserva = new Reserva();
            reserva.setApartamento(apartamento);
            reserva.setCliente(clienteOpt.get());
            reserva.setQuantidadeHospede(dto.getQuantidadeHospede());
            reserva.setDataCheckin(dto.getDataCheckin());
            reserva.setDataCheckout(dto.getDataCheckout());
            
            Reserva reservaCriada = reservaService.criarReserva(reserva);
            return ResponseEntity.status(HttpStatus.CREATED).body(reservaCriada);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
       
    @GetMapping
    public ResponseEntity<List<ReservaResponseDTO>> listarTodas() {
        List<ReservaResponseDTO> reservas = reservaService.listarTodasDTO();
        return ResponseEntity.ok(reservas);
    }
   
    
    
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable Long id) {
        try {
            ReservaResponseDTO reserva = reservaService.buscarPorIdDTO(id);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
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
            Reserva reserva = reservaService.finalizarReserva(id);
            return ResponseEntity.ok(reserva);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
    
        
}
