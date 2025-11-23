package com.divan.repository;

import com.divan.entity.FechamentoCaixa;
import com.divan.entity.FechamentoCaixaDetalhe;
import com.divan.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FechamentoCaixaRepository extends JpaRepository<FechamentoCaixa, Long> {
    
    /**
     * ✅ BUSCAR CAIXA ABERTO DO USUÁRIO
     */
    Optional<FechamentoCaixa> findByUsuarioAndStatus(
        Usuario usuario, 
        FechamentoCaixa.StatusCaixaEnum status
    );
    
    /**
     * ✅ BUSCAR CAIXAS POR PERÍODO
     */
    List<FechamentoCaixa> findByDataHoraAberturaBetween(
        LocalDateTime dataInicio, 
        LocalDateTime dataFim
    );
    
   
     /**
     * ✅ BUSCAR CAIXAS POR USUÁRIO
     */
    List<FechamentoCaixa> findByUsuarioOrderByDataHoraAberturaDesc(Usuario usuario);
 
    /**
     * ✅ BUSCAR CAIXAS POR STATUS
     */
    List<FechamentoCaixa> findByStatusOrderByDataHoraAberturaDesc(
        FechamentoCaixa.StatusCaixaEnum status
    );
    
    Optional<FechamentoCaixa> findFirstByStatusOrderByDataHoraAberturaDesc(FechamentoCaixa.StatusCaixaEnum status);
}