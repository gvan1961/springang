package com.divan.controller;

import com.divan.dto.ItemVendaRequestDTO;
import com.divan.dto.VendaReservaRequestDTO;
import com.divan.entity.ItemVenda;
import com.divan.entity.NotaVenda;
import com.divan.entity.Produto;
import com.divan.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/vendas")
@CrossOrigin(origins = "*")
public class VendaController {
    
    @Autowired
    private VendaService vendaService;
    
    @GetMapping("/teste")
    public ResponseEntity<String> teste() {
        return ResponseEntity.ok("VendaController funcionando!");
    }
    
    @PostMapping("/reserva")
    public ResponseEntity<?> adicionarVendaParaReserva(@Valid @RequestBody VendaReservaRequestDTO dto) {
        try {
            List<ItemVenda> itens = new ArrayList<>();
            
            for (ItemVendaRequestDTO itemDto : dto.getItens()) {
                ItemVenda item = new ItemVenda();
                Produto produto = new Produto();
                produto.setId(itemDto.getProdutoId());
                item.setProduto(produto);
                item.setQuantidade(itemDto.getQuantidade());
                itens.add(item);
            }
            
            NotaVenda vendaProcessada = vendaService.adicionarVendaParaReserva(dto.getReservaId(), itens);
            return ResponseEntity.status(HttpStatus.CREATED).body(vendaProcessada);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
