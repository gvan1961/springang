package com.divan.service;

import com.divan.entity.Apartamento;
import com.divan.repository.ApartamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ApartamentoService {
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;
    
    public Apartamento salvar(Apartamento apartamento) {
        if (apartamentoRepository.existsByNumeroApartamento(apartamento.getNumeroApartamento()) 
            && apartamento.getId() == null) {
            throw new RuntimeException("Número do apartamento já cadastrado");
        }
        return apartamentoRepository.save(apartamento);
    }
    
    public Apartamento atualizar(Long id, Apartamento apartamento) {
        Optional<Apartamento> apartamentoExistente = apartamentoRepository.findById(id);
        if (apartamentoExistente.isEmpty()) {
            throw new RuntimeException("Apartamento não encontrado");
        }
        
        apartamento.setId(id);
        return apartamentoRepository.save(apartamento);
    }
    
    public Apartamento atualizarStatus(Long id, Apartamento.StatusEnum novoStatus) {
        Optional<Apartamento> apartamentoOpt = apartamentoRepository.findById(id);
        if (apartamentoOpt.isEmpty()) {
            throw new RuntimeException("Apartamento não encontrado");
        }
        
        Apartamento apartamento = apartamentoOpt.get();
        apartamento.setStatus(novoStatus);
        return apartamentoRepository.save(apartamento);
    }
    
    @Transactional(readOnly = true)
    public Optional<Apartamento> buscarPorId(Long id) {
        return apartamentoRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public Optional<Apartamento> buscarPorNumero(String numero) {
        return apartamentoRepository.findByNumeroApartamento(numero);
    }
    
    @Transactional(readOnly = true)
    public List<Apartamento> listarTodos() {
        return apartamentoRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<Apartamento> buscarDisponiveis() {
        return apartamentoRepository.findDisponiveis();
    }
    
    @Transactional(readOnly = true)
    public List<Apartamento> buscarOcupados() {
        return apartamentoRepository.findOcupados();
    }
    
    @Transactional(readOnly = true)
    public List<Apartamento> buscarDisponiveisParaPeriodo(LocalDateTime checkin, LocalDateTime checkout) {
        return apartamentoRepository.findDisponiveisParaPeriodo(checkin, checkout);
    }
    
    @Transactional(readOnly = true)
    public List<Apartamento> buscarPorStatus(Apartamento.StatusEnum status) {
        return apartamentoRepository.findByStatus(status);
    }
}
