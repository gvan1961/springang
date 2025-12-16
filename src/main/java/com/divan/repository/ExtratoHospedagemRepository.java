package com.divan.repository;

import com.divan.entity.ExtratoHospedagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtratoHospedagemRepository extends JpaRepository<ExtratoHospedagem, Long> {
    
    /**
     * Buscar todos os extratos de uma reserva
     */
    List<ExtratoHospedagem> findByReservaId(Long reservaId);
}
