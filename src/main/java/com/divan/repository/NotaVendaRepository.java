package com.divan.repository;

import com.divan.entity.NotaVenda;
import com.divan.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotaVendaRepository extends JpaRepository<NotaVenda, Long> {
    
    List<NotaVenda> findByReserva(Reserva reserva);
    
    List<NotaVenda> findByTipoVenda(NotaVenda.TipoVendaEnum tipoVenda);
    
    @Query("SELECT n FROM NotaVenda n WHERE DATE(n.dataHoraVenda) = DATE(:data)")
    List<NotaVenda> findVendasDoDia(LocalDateTime data);
    
    @Query("SELECT n FROM NotaVenda n WHERE n.dataHoraVenda BETWEEN :inicio AND :fim")
    List<NotaVenda> findVendasPorPeriodo(LocalDateTime inicio, LocalDateTime fim);
    
    @Query("SELECT n FROM NotaVenda n WHERE n.tipoVenda = 'VISTA' AND DATE(n.dataHoraVenda) = DATE(:data)")
    List<NotaVenda> findVendasVistaDelDia(LocalDateTime data);
}
