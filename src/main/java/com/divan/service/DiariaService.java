package com.divan.service;

import com.divan.entity.Diaria;
import com.divan.entity.TipoApartamento;
import com.divan.repository.DiariaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class DiariaService {
    
    @Autowired
    private DiariaRepository diariaRepository;
    
    public Diaria salvar(Diaria diaria) {
        if (diariaRepository.existsByTipoApartamentoAndQuantidade(
                diaria.getTipoApartamento(), diaria.getQuantidade()) 
            && diaria.getId() == null) {
            throw new RuntimeException("Já existe diária para este tipo e quantidade");
        }
        return diariaRepository.save(diaria);
    }
    
    public Diaria atualizar(Long id, Diaria diaria) {
        Optional<Diaria> diariaExistente = diariaRepository.findById(id);
        if (diariaExistente.isEmpty()) {
            throw new RuntimeException("Diária não encontrada");
        }
        
        diaria.setId(id);
        return diariaRepository.save(diaria);
    }
    
    @Transactional(readOnly = true)
    public Optional<Diaria> buscarPorId(Long id) {
        return diariaRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public Optional<Diaria> buscarPorTipoEQuantidade(TipoApartamento tipo, Integer quantidade) {
        return diariaRepository.findByTipoApartamentoAndQuantidade(tipo, quantidade);
    }
    
    @Transactional(readOnly = true)
    public List<Diaria> listarTodas() {
        return diariaRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<Diaria> buscarPorTipo(TipoApartamento tipo) {
        return diariaRepository.findByTipoApartamentoOrderByQuantidade(tipo);
    }
}
