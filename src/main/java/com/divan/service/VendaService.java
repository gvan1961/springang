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
    private ProdutoRepository produtoRepository;
    
    @Autowired
    private ExtratoReservaRepository extratoRepository;
    
    @Autowired
    private ReservaRepository reservaRepository;
    
    public NotaVenda adicionarVendaParaReserva(Long reservaId, List<ItemVenda> itens) {
        // Buscar reserva
        Optional<Reserva> reservaOpt = reservaRepository.findById(reservaId);
        if (reservaOpt.isEmpty()) {
            throw new RuntimeException("Reserva não encontrada");
        }
        
        Reserva reserva = reservaOpt.get();
        
        if (reserva.getStatus() != Reserva.StatusReservaEnum.ATIVA) {
            throw new RuntimeException("Não é possível adicionar itens a uma reserva não ativa");
        }
        
        BigDecimal total = BigDecimal.ZERO;
        
        // Criar nota de venda - CORREÇÃO: INICIALIZAR TOTAL COM ZERO
        NotaVenda notaVenda = new NotaVenda();
        notaVenda.setDataHoraVenda(LocalDateTime.now());
        notaVenda.setTipoVenda(NotaVenda.TipoVendaEnum.APARTAMENTO);
        notaVenda.setReserva(reserva);
        notaVenda.setTotal(BigDecimal.ZERO); // ← CORREÇÃO AQUI!
        
        NotaVenda notaSalva = notaVendaRepository.save(notaVenda);
        
        // Processar itens
        for (ItemVenda item : itens) {
            // Buscar produto
            Optional<Produto> produtoOpt = produtoRepository.findById(item.getProduto().getId());
            if (produtoOpt.isEmpty()) {
                throw new RuntimeException("Produto não encontrado: " + item.getProduto().getId());
            }
            
            Produto produto = produtoOpt.get();
            
            // Verificar estoque
            if (produto.getQuantidade() < item.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getNomeProduto());
            }
            
            item.setNotaVenda(notaSalva);
            item.setProduto(produto);
            item.setValorUnitario(produto.getValorVenda());
            
            // Calcular total do item
            BigDecimal totalItem = item.getValorUnitario()
                .multiply(BigDecimal.valueOf(item.getQuantidade()));
            item.setTotalItem(totalItem);
            
            // Baixar estoque
            produto.setQuantidade(produto.getQuantidade() - item.getQuantidade());
            produtoRepository.save(produto);
            
            // Salvar item
            itemVendaRepository.save(item);
            
            total = total.add(totalItem);
        }
        
        // Atualizar total da nota
        notaSalva.setTotal(total);
        notaSalva = notaVendaRepository.save(notaSalva);
        
        // Criar extratos
        criarExtratoVenda(notaSalva, itens);
        
        // Atualizar totais da reserva
        atualizarTotalProdutoReserva(reservaId, total);
        
        return notaSalva;
    }
    
    public NotaVenda processarVendaVista(List<ItemVenda> itens) {
        BigDecimal total = BigDecimal.ZERO;
        
        // Criar nota de venda à vista - CORREÇÃO: INICIALIZAR TOTAL COM ZERO
        NotaVenda notaVenda = new NotaVenda();
        notaVenda.setDataHoraVenda(LocalDateTime.now());
        notaVenda.setTipoVenda(NotaVenda.TipoVendaEnum.VISTA);
        notaVenda.setTotal(BigDecimal.ZERO); // ← CORREÇÃO AQUI!
        
        NotaVenda notaSalva = notaVendaRepository.save(notaVenda);
        
        // Processar itens
        for (ItemVenda item : itens) {
            // Buscar produto
            Optional<Produto> produtoOpt = produtoRepository.findById(item.getProduto().getId());
            if (produtoOpt.isEmpty()) {
                throw new RuntimeException("Produto não encontrado: " + item.getProduto().getId());
            }
            
            Produto produto = produtoOpt.get();
            
            // Verificar estoque
            if (produto.getQuantidade() < item.getQuantidade()) {
                throw new RuntimeException("Estoque insuficiente para o produto: " + produto.getNomeProduto());
            }
            
            item.setNotaVenda(notaSalva);
            item.setProduto(produto);
            item.setValorUnitario(produto.getValorVenda());
            
            // Calcular total do item
            BigDecimal totalItem = item.getValorUnitario()
                .multiply(BigDecimal.valueOf(item.getQuantidade()));
            item.setTotalItem(totalItem);
            
            // Baixar estoque
            produto.setQuantidade(produto.getQuantidade() - item.getQuantidade());
            produtoRepository.save(produto);
            
            // Salvar item
            itemVendaRepository.save(item);
            
            total = total.add(totalItem);
        }
        
        // Atualizar total da nota
        notaSalva.setTotal(total);
        return notaVendaRepository.save(notaSalva);
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
    public List<NotaVenda> buscarVendasDaReserva(Long reservaId) {
        Optional<Reserva> reserva = reservaRepository.findById(reservaId);
        return reserva.map(notaVendaRepository::findByReserva).orElse(List.of());
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
