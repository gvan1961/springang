package com.divan.repository;

import com.divan.entity.Apartamento;
import com.divan.entity.Cliente;
import com.divan.entity.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    
    List<Reserva> findByCliente(Cliente cliente);
    
    List<Reserva> findByApartamento(Apartamento apartamento);
    
    List<Reserva> findByStatus(Reserva.StatusReservaEnum status);
    
    @Query("SELECT r FROM Reserva r WHERE r.status = 'ATIVA'")
    List<Reserva> findReservasAtivas();
    
    @Query("SELECT r FROM Reserva r WHERE DATE(r.dataCheckin) = DATE(:data)")
    List<Reserva> findReservasParaCheckinNaData(@Param("data") LocalDateTime data);
    
    @Query("SELECT r FROM Reserva r WHERE DATE(r.dataCheckout) = DATE(:data)")
    List<Reserva> findReservasParaCheckoutNaData(@Param("data") LocalDateTime data);
    
    @Query("SELECT r FROM Reserva r WHERE r.dataCheckin BETWEEN :inicio AND :fim")
    List<Reserva> findReservasPorPeriodo(@Param("inicio") LocalDateTime inicio, 
                                        @Param("fim") LocalDateTime fim);
    
    @Query("SELECT r FROM Reserva r WHERE r.apartamento.id = :apartamentoId AND " +
           "r.status = 'ATIVA' AND " +
           "((r.dataCheckin <= :checkout AND r.dataCheckout >= :checkin))")
    List<Reserva> findReservasConflitantes(@Param("apartamentoId") Long apartamentoId,
                                          @Param("checkin") LocalDateTime checkin,
                                          @Param("checkout") LocalDateTime checkout);
    
    @Query("SELECT r FROM Reserva r WHERE r.apartamento = :apartamento AND r.status = 'ATIVA'")
    Optional<Reserva> findReservaAtivaDoApartamento(@Param("apartamento") Apartamento apartamento);
    
    @Query("SELECT r FROM Reserva r WHERE r.totalApagar > 0")
    List<Reserva> findReservasComSaldoDevedor();
}
