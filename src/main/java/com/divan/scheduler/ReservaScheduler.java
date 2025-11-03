package com.divan.scheduler;

import com.divan.entity.Apartamento;
import com.divan.entity.Reserva;
import com.divan.repository.ApartamentoRepository;
import com.divan.repository.ReservaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservaScheduler {

    @Autowired
    private ReservaRepository reservaRepository;
    
    @Autowired
    private ApartamentoRepository apartamentoRepository;

    /**
     * Roda todo dia às 00:01 (1 minuto após meia-noite)
     * Ativa as pré-reservas cujo check-in chegou
     */
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void ativarPreReservas() {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🔄 VERIFICANDO PRÉ-RESERVAS PARA ATIVAR");
        System.out.println("   Data/Hora: " + LocalDateTime.now());
        System.out.println("═══════════════════════════════════════════");
        
        LocalDateTime agora = LocalDateTime.now();
        
        // Buscar PRE_RESERVAS cujo check-in já chegou (hoje ou antes)
        List<Reserva> preReservas = reservaRepository.findByStatus(Reserva.StatusReservaEnum.PRE_RESERVA);
        
        System.out.println("📋 Total de pré-reservas: " + preReservas.size());
        
        int ativadas = 0;
        
        for (Reserva reserva : preReservas) {
            LocalDateTime dataCheckin = reserva.getDataCheckin();
            
            // Se o check-in é HOJE ou JÁ PASSOU
            if (!dataCheckin.isAfter(agora)) {
                System.out.println("───────────────────────────────────────────");
                System.out.println("✅ Ativando Reserva #" + reserva.getId());
                System.out.println("   Apartamento: " + reserva.getApartamento().getNumeroApartamento());
                System.out.println("   Cliente: " + reserva.getCliente().getNome());
                System.out.println("   Check-in: " + dataCheckin.toLocalDate());
                
                // Mudar status da reserva para ATIVA
                reserva.setStatus(Reserva.StatusReservaEnum.ATIVA);
                reservaRepository.save(reserva);
                
                // Mudar status do apartamento para OCUPADO
                Apartamento apartamento = reserva.getApartamento();
                apartamento.setStatus(Apartamento.StatusEnum.OCUPADO);
                apartamentoRepository.save(apartamento);
                
                System.out.println("   ✅ Reserva ativada!");
                System.out.println("   🏨 Apartamento " + apartamento.getNumeroApartamento() + " → OCUPADO");
                
                ativadas++;
            } else {
                System.out.println("⏭️ Reserva #" + reserva.getId() + " ainda é futura (check-in: " + dataCheckin.toLocalDate() + ")");
            }
        }
        
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ VERIFICAÇÃO CONCLUÍDA");
        System.out.println("   Pré-reservas ativadas: " + ativadas);
        System.out.println("═══════════════════════════════════════════");
    }
}