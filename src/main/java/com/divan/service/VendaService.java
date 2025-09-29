package com.divan.service;

import com.divan.entity.*;
import com.divan.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class VendaService {
    
    @Autowired
    private NotaVendaRepository notaVendaRepository;
    
    @Autowired
    private ItemVendaRepository itemVendaRepository;
    
    @Autowired
    private ProdutoService produtoService;
    
    @Autowired
    private ExtratoReservaRepository extratoRepository;
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    public NotaVenda processarVenda(NotaVenda notaVenda, List<ItemVenda> itens) {
        BigDecimal total = BigDecimal.ZERO;
        
        // Salvar nota primeiro
        notaVenda.setDataHoraVenda(LocalDateTime.now());
        NotaVenda notaSalva = notaVendaRepository.save(notaVenda);
        
        // Processar itens
        for (ItemVenda item : itens) {
            item.setNotaVenda(notaSalva);
            
            // Calcular total do item
            BigDecimal totalItem = item.getValorUnitario()
                .multiply(BigDecimal.valueOf(item.getQuantidade()));
            item.setTotalItem(totalItem);
            
            // Baixar estoque
            produtoService.baixarEstoque(item.getProduto().getId(), item.getQuantidade());
            
            // Salvar item
            itemVendaRepository.save(item);
            
            total = total.add(totalItem);
        }
        
        // Atualizar total da nota
        notaSalva.setTotal(total);
        notaSalva = notaVendaRepository.save(notaSalva);
        
        // Se for venda para apartamento, criar extrato
        if (notaVenda.getTipoVenda() == NotaVenda.TipoVendaEnum.APARTAMENTO 
            && notaVenda.getReserva() != null) {
            criarExtratoVenda(notaSalva, itens);
            atualizarTotalProdutoReserva(notaVenda.getReserva().getId(), total);
        }
        
        return notaSalva;
    }
    
    private void criarExtratoVenda(NotaVenda nota, List<ItemVenda> itens) {
        for (ItemVenda item : itens) {
            ExtratoReserva extrato = new ExtratoReserva();
            extrato.setReserva(nota.getReserva());
            extrato.setDataHoraLancamento(nota.getDataHoraVenda());
            extrato.setStatusLancamento(ExtratoReserva.StatusLancamentoEnum.PRODUTO);
            extrato.setQuantidade(item.getQuantidade());
            extrato.setValorUnitario(item.getValorUnitario());
            extrato.setTotalLancamento(item.getTotalItem());
            extrato.setDescricao(item.getProduto().getNomeProduto());
            extrato.setNotaVendaId(nota.getId());
            
            extratoRepository.save(extrato);
        }
    }
    
    private void atualizarTotalProdutoReserva(Long reservaId, BigDecimal valorVenda) {
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isPresent()) {
            Reserva reserva = reservaOpt.get();
            reserva.setTotalProduto(reserva.getTotalProduto().add(valorVenda));
            reserva.setTotalHospedagem(reserva.getTotalDiaria().add(reserva.getTotalProduto()));
            reserva.setTotalApagar(reserva.getTotalHospedagem()
                .subtract(reserva.getTotalRecebido())
                .subtract(reserva.getDesconto()));
            
            reservaRepository.save(reserva);
        }
    }
    
    @Transactional(readOnly = true)
    public List<NotaVenda> buscarVendasDoDia(LocalDateTime data) {
        return notaVendaRepository.findVendasDoDia(data);
    }
    
    @Transactional(readOnly = true)
    public List<NotaVenda> buscarVendasPorPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        return notaVendaRepository.findVendasPorPeriodo(inicio, fim);
    }
    
    @Transactional(readOnly = true)
    public List<NotaVenda> buscarVendasVistaDoDia(LocalDateTime data) {
        return notaVendaRepository.findVendasVistaDelDia(data);
    }
}
