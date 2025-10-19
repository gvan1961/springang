package com.divan.service;

import com.divan.entity.Produto;
import com.divan.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProdutoService {
    
    @Autowired
    private ProdutoRepository produtoRepository;
    
    public Produto salvar(Produto produto) {
        return produtoRepository.save(produto);
    }
    
    public Produto atualizar(Long id, Produto produto) {
        Optional<Produto> produtoExistente = produtoRepository.findById(id);
        if (produtoExistente.isEmpty()) {
            throw new RuntimeException("Produto não encontrado");
        }
        
        produto.setId(id);
        return produtoRepository.save(produto);
    }
    
    public List<Produto> listarDisponiveis() {
        return produtoRepository.findProdutosDisponiveis();
    }
    
    public Produto atualizarEstoque(Long id, Integer novaQuantidade) {
        Optional<Produto> produtoOpt = produtoRepository.findById(id);
        if (produtoOpt.isEmpty()) {
            throw new RuntimeException("Produto não encontrado");
        }
        
        Produto produto = produtoOpt.get();
        produto.setQuantidade(novaQuantidade);
        return produtoRepository.save(produto);
    }
    
    public void baixarEstoque(Long produtoId, Integer quantidade) {
        Optional<Produto> produtoOpt = produtoRepository.findById(produtoId);
        if (produtoOpt.isEmpty()) {
            throw new RuntimeException("Produto não encontrado");
        }
        
        Produto produto = produtoOpt.get();
        if (produto.getQuantidade() < quantidade) {
            throw new RuntimeException("Estoque insuficiente");
        }
        
        produto.setQuantidade(produto.getQuantidade() - quantidade);
        produtoRepository.save(produto);
    }
    
    @Transactional(readOnly = true)
    public Optional<Produto> buscarPorId(Long id) {
        return produtoRepository.findById(id);
    }
    
    @Transactional(readOnly = true)
    public List<Produto> listarTodos() {
        return produtoRepository.findAllOrderByNome();
    }
    
    @Transactional(readOnly = true)
    public List<Produto> buscarPorNome(String nome) {
        return produtoRepository.findByNomeProdutoContainingIgnoreCase(nome);
    }
    
    @Transactional(readOnly = true)
    public List<Produto> buscarComEstoqueBaixo() {
        return produtoRepository.findProdutosComEstoqueBaixo();
    }
    
    @Transactional(readOnly = true)
    public List<Produto> buscarSemEstoque() {
        return produtoRepository.findProdutosSemEstoque();
    }
}
