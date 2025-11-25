package com.divan.service;

import com.divan.dto.FechamentoCaixaDTO;
import com.divan.dto.FechamentoCaixaDetalheDTO;
import com.divan.entity.FechamentoCaixa;
import com.divan.entity.FechamentoCaixaDetalhe;
import com.divan.entity.Pagamento;
import com.divan.entity.Usuario;
import com.divan.repository.FechamentoCaixaRepository;
import com.divan.repository.FechamentoCaixaDetalheRepository;
import com.divan.repository.PagamentoRepository;
import com.divan.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.divan.dto.ProdutoVendidoDTO;
import com.divan.dto.VendaDetalhadaDTO;
import com.divan.dto.RelatorioVendasCaixaDTO;
import com.divan.entity.NotaVenda;
import com.divan.entity.ItemVenda;
import com.divan.repository.NotaVendaRepository;
import com.divan.repository.ItemVendaRepository;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class FechamentoCaixaService {
    
    @Autowired
    private FechamentoCaixaRepository fechamentoCaixaRepository;
    
    @Autowired
    private FechamentoCaixaDetalheRepository fechamentoCaixaDetalheRepository;
    
    @Autowired
    private PagamentoRepository pagamentoRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private NotaVendaRepository notaVendaRepository;

    @Autowired
    private ItemVendaRepository itemVendaRepository;
    
    /**
     * ✅ ABRIR CAIXA
     */
    @Transactional
    public FechamentoCaixaDTO abrirCaixa(Long usuarioId, String turno, String observacoes) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🔓 SERVICE - ABRINDO CAIXA");
        System.out.println("   Usuário ID: " + usuarioId);
        System.out.println("   Turno: " + turno);
        System.out.println("═══════════════════════════════════════════");
        
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado com ID: " + usuarioId));
        
        System.out.println("✅ Usuário encontrado: " + usuario.getNome());
        
        Optional<FechamentoCaixa> caixaAbertoOpt = fechamentoCaixaRepository
            .findByUsuarioAndStatus(usuario, FechamentoCaixa.StatusCaixaEnum.ABERTO);
        
        if (caixaAbertoOpt.isPresent()) {
            FechamentoCaixa caixaExistente = caixaAbertoOpt.get();
            System.err.println("❌ Já existe um caixa aberto!");
            System.err.println("   ID: " + caixaExistente.getId());
            System.err.println("   Turno: " + caixaExistente.getTurno());
            System.err.println("   Aberto em: " + caixaExistente.getDataHoraAbertura());
            
            throw new RuntimeException(
                "Você já possui um caixa aberto! " +
                "Caixa #" + caixaExistente.getId() + 
                " - Turno: " + caixaExistente.getTurno()
            );
        }
        
        System.out.println("✅ Nenhum caixa aberto encontrado. Criando novo...");
        
        FechamentoCaixa caixa = new FechamentoCaixa();
        caixa.setUsuario(usuario);
        caixa.setTurno(turno);
        caixa.setDataHoraAbertura(LocalDateTime.now());
        caixa.setStatus(FechamentoCaixa.StatusCaixaEnum.ABERTO);
        caixa.setObservacoes(observacoes);
        
        caixa.setTotalDinheiro(BigDecimal.ZERO);
        caixa.setTotalPix(BigDecimal.ZERO);
        caixa.setTotalCartaoDebito(BigDecimal.ZERO);
        caixa.setTotalCartaoCredito(BigDecimal.ZERO);
        caixa.setTotalTransferencia(BigDecimal.ZERO);
        caixa.setTotalFaturado(BigDecimal.ZERO);
        caixa.setTotalDiarias(BigDecimal.ZERO);
        caixa.setTotalProdutos(BigDecimal.ZERO);
        caixa.setTotalBruto(BigDecimal.ZERO);
        caixa.setTotalLiquido(BigDecimal.ZERO);
        caixa.setTotalDescontos(BigDecimal.ZERO);
        caixa.setTotalEstornos(BigDecimal.ZERO);
        
        FechamentoCaixa caixaSalvo = fechamentoCaixaRepository.save(caixa);
        
        System.out.println("✅ Caixa criado com sucesso!");
        System.out.println("   ID: " + caixaSalvo.getId());
        System.out.println("   Turno: " + caixaSalvo.getTurno());
        System.out.println("   Data/Hora: " + caixaSalvo.getDataHoraAbertura());
        System.out.println("   Todos os totais: R$ 0,00");
        System.out.println("═══════════════════════════════════════════");
        
        return converterParaDTOCompleto(caixaSalvo, new ArrayList<>());
    }
    
    /**
     * ✅ BUSCAR CAIXA ABERTO
     */
    @Transactional(readOnly = true)
    public FechamentoCaixaDTO buscarCaixaAberto(Long usuarioId) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🔍 SERVICE - BUSCANDO CAIXA ABERTO");
        System.out.println("   Usuário ID: " + usuarioId);
        
        Usuario usuario = usuarioRepository.findById(usuarioId)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado com ID: " + usuarioId));
        
        System.out.println("✅ Usuário encontrado: " + usuario.getNome());
        
        Optional<FechamentoCaixa> caixaOpt = fechamentoCaixaRepository
            .findByUsuarioAndStatus(usuario, FechamentoCaixa.StatusCaixaEnum.ABERTO);
        
        if (caixaOpt.isPresent()) {
            FechamentoCaixa caixa = caixaOpt.get();
            System.out.println("✅ Caixa aberto encontrado!");
            System.out.println("   ID: " + caixa.getId());
            System.out.println("   Turno: " + caixa.getTurno());
            System.out.println("   Aberto em: " + caixa.getDataHoraAbertura());
            System.out.println("═══════════════════════════════════════════");
            
            return converterParaDTO(caixa);
        } else {
            System.out.println("📭 Nenhum caixa aberto encontrado");
            System.out.println("═══════════════════════════════════════════");
            
            return null;
        }
    }
    
    /**
     * ✅ BUSCAR POR ID (COM RECÁLCULO DE TOTAIS E DETALHES)
     */
    @Transactional
    public FechamentoCaixaDTO buscarPorId(Long id) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("📦 BUSCANDO CAIXA ID: " + id);
        
        FechamentoCaixa caixa = fechamentoCaixaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Caixa não encontrado com ID: " + id));
        
        System.out.println("🔄 Recalculando totais do caixa #" + id);
        
        recalcularTotais(caixa);
        fechamentoCaixaRepository.save(caixa);
        
        System.out.println("💰 Totais recalculados:");
        System.out.println("   Diárias: R$ " + (caixa.getTotalDiarias() != null ? caixa.getTotalDiarias() : BigDecimal.ZERO));
        System.out.println("   Produtos: R$ " + (caixa.getTotalProdutos() != null ? caixa.getTotalProdutos() : BigDecimal.ZERO));
        System.out.println("   Total Bruto: R$ " + (caixa.getTotalBruto() != null ? caixa.getTotalBruto() : BigDecimal.ZERO));
        System.out.println("   Total Líquido: R$ " + (caixa.getTotalLiquido() != null ? caixa.getTotalLiquido() : BigDecimal.ZERO));
        
        // ✅ CORRIGIDO: USA O ID PARA FILTRAR
        List<FechamentoCaixaDetalhe> detalhes = fechamentoCaixaDetalheRepository
            .findByFechamentoCaixaIdOrderByDataHoraDesc(caixa.getId());
        
        System.out.println("✅ Caixa encontrado!");
        System.out.println("   Total de detalhes: " + detalhes.size());
        System.out.println("═══════════════════════════════════════════");
        
        return converterParaDTOCompleto(caixa, detalhes);
    }
    
    /**
     * 🔄 RECALCULAR TOTAIS DO CAIXA
     */
    private void recalcularTotais(FechamentoCaixa caixa) {
        System.out.println("🔄 Recalculando totais do caixa #" + caixa.getId());
        
        List<Pagamento> pagamentos = pagamentoRepository.findByCaixa(caixa);
        
        System.out.println("📊 Total de pagamentos encontrados: " + pagamentos.size());
        
        BigDecimal totalDinheiro = BigDecimal.ZERO;
        BigDecimal totalPix = BigDecimal.ZERO;
        BigDecimal totalCartaoDebito = BigDecimal.ZERO;
        BigDecimal totalCartaoCredito = BigDecimal.ZERO;
        BigDecimal totalTransferencia = BigDecimal.ZERO;
        BigDecimal totalFaturado = BigDecimal.ZERO;
        BigDecimal totalDiarias = BigDecimal.ZERO;
        BigDecimal totalProdutos = BigDecimal.ZERO;
        
        for (Pagamento pagamento : pagamentos) {
            BigDecimal valor = pagamento.getValor() != null ? pagamento.getValor() : BigDecimal.ZERO;
            
            if (pagamento.getFormaPagamento() != null) {
                String formaPagamento = pagamento.getFormaPagamento().toString();
                
                switch (formaPagamento) {
                    case "DINHEIRO":
                        totalDinheiro = totalDinheiro.add(valor);
                        break;
                    case "PIX":
                        totalPix = totalPix.add(valor);
                        break;
                    case "CARTAO_DEBITO":
                        totalCartaoDebito = totalCartaoDebito.add(valor);
                        break;
                    case "CARTAO_CREDITO":
                        totalCartaoCredito = totalCartaoCredito.add(valor);
                        break;
                    case "TRANSFERENCIA":
                        totalTransferencia = totalTransferencia.add(valor);
                        break;
                    case "FATURADO":
                        totalFaturado = totalFaturado.add(valor);
                        break;
                }
            }
            
            if (pagamento.getTipo() != null) {
                String tipo = pagamento.getTipo();
                
                if ("DIARIA".equals(tipo)) {
                    totalDiarias = totalDiarias.add(valor);
                } else if ("PRODUTO".equals(tipo)) {
                    totalProdutos = totalProdutos.add(valor);
                }
            }
        }
        
        caixa.setTotalDinheiro(totalDinheiro);
        caixa.setTotalPix(totalPix);
        caixa.setTotalCartaoDebito(totalCartaoDebito);
        caixa.setTotalCartaoCredito(totalCartaoCredito);
        caixa.setTotalTransferencia(totalTransferencia);
        caixa.setTotalFaturado(totalFaturado);
        caixa.setTotalDiarias(totalDiarias);
        caixa.setTotalProdutos(totalProdutos);
        
        BigDecimal totalBruto = totalDinheiro
            .add(totalPix)
            .add(totalCartaoDebito)
            .add(totalCartaoCredito)
            .add(totalTransferencia)
            .add(totalFaturado);
        
        caixa.setTotalBruto(totalBruto);
        
        BigDecimal totalLiquido = totalDinheiro
            .add(totalPix)
            .add(totalCartaoDebito)
            .add(totalCartaoCredito)
            .add(totalTransferencia);
        
        caixa.setTotalLiquido(totalLiquido);
        
        System.out.println("💰 Totais recalculados:");
        System.out.println("   Diárias: R$ " + totalDiarias);
        System.out.println("   Produtos: R$ " + totalProdutos);
        System.out.println("   Total Bruto: R$ " + totalBruto);
        System.out.println("   Total Líquido: R$ " + totalLiquido);
    }
    
    /**
     * ✅ FECHAR CAIXA
     */
    @Transactional
    public FechamentoCaixaDTO fecharCaixa(Long caixaId, String observacoesFechamento) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🔒 SERVICE - FECHANDO CAIXA");
        System.out.println("   Caixa ID: " + caixaId);
        
        FechamentoCaixa caixa = fechamentoCaixaRepository.findById(caixaId)
            .orElseThrow(() -> new RuntimeException("Caixa não encontrado com ID: " + caixaId));
        
        if (caixa.getStatus() == FechamentoCaixa.StatusCaixaEnum.FECHADO) {
            throw new RuntimeException("Caixa já está fechado!");
        }
        
        recalcularTotais(caixa);
        
        caixa.setDataHoraFechamento(LocalDateTime.now());
        caixa.setStatus(FechamentoCaixa.StatusCaixaEnum.FECHADO);
        
        if (observacoesFechamento != null && !observacoesFechamento.isEmpty()) {
            String obsAtual = caixa.getObservacoes() != null ? caixa.getObservacoes() : "";
            caixa.setObservacoes(obsAtual + "\n\nFECHAMENTO: " + observacoesFechamento);
        }
        
        FechamentoCaixa caixaFechado = fechamentoCaixaRepository.save(caixa);
        
        System.out.println("✅ Caixa fechado com sucesso!");
        System.out.println("   ID: " + caixaFechado.getId());
        System.out.println("   Fechado em: " + caixaFechado.getDataHoraFechamento());
        System.out.println("   Total Líquido: R$ " + caixaFechado.getTotalLiquido());
        System.out.println("═══════════════════════════════════════════");
        
        // ✅ CORRIGIDO: USA O ID PARA FILTRAR
        List<FechamentoCaixaDetalhe> detalhes = fechamentoCaixaDetalheRepository
            .findByFechamentoCaixaIdOrderByDataHoraDesc(caixaFechado.getId());
        
        return converterParaDTOCompleto(caixaFechado, detalhes);
    }
    
    /**
     * ✅ LISTAR TODOS
     */
    @Transactional(readOnly = true)
    public List<FechamentoCaixaDTO> listarTodos() {
        List<FechamentoCaixa> caixas = fechamentoCaixaRepository.findAll();
        return caixas.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * ✅ LISTAR POR PERÍODO
     */
    @Transactional(readOnly = true)
    public List<FechamentoCaixaDTO> listarPorPeriodo(LocalDateTime dataInicio, LocalDateTime dataFim) {
        List<FechamentoCaixa> caixas = fechamentoCaixaRepository
            .findByDataHoraAberturaBetween(dataInicio, dataFim);
        
        return caixas.stream()
            .map(this::converterParaDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * 🔄 CONVERTER PARA DTO (BÁSICO)
     */
    private FechamentoCaixaDTO converterParaDTO(FechamentoCaixa caixa) {
        FechamentoCaixaDTO dto = new FechamentoCaixaDTO();
        dto.setId(caixa.getId());
        
        if (caixa.getUsuario() != null) {
            dto.setUsuarioId(caixa.getUsuario().getId());
            dto.setUsuarioNome(caixa.getUsuario().getNome());
        }
        
        dto.setTurno(caixa.getTurno());
        dto.setDataHoraAbertura(caixa.getDataHoraAbertura());
        dto.setDataHoraFechamento(caixa.getDataHoraFechamento());
        
        if (caixa.getStatus() != null) {
            dto.setStatus(caixa.getStatus().name());
        }
        
        dto.setObservacoes(caixa.getObservacoes());
        
        return dto;
    }
    
    /**
     * 🔄 CONVERTER PARA DTO COMPLETO (COM TOTAIS E DETALHES)
     */
    private FechamentoCaixaDTO converterParaDTOCompleto(FechamentoCaixa caixa, List<FechamentoCaixaDetalhe> detalhes) {
        FechamentoCaixaDTO dto = converterParaDTO(caixa);
        
        dto.setTotalDinheiro(caixa.getTotalDinheiro() != null ? caixa.getTotalDinheiro() : BigDecimal.ZERO);
        dto.setTotalPix(caixa.getTotalPix() != null ? caixa.getTotalPix() : BigDecimal.ZERO);
        dto.setTotalCartaoDebito(caixa.getTotalCartaoDebito() != null ? caixa.getTotalCartaoDebito() : BigDecimal.ZERO);
        dto.setTotalCartaoCredito(caixa.getTotalCartaoCredito() != null ? caixa.getTotalCartaoCredito() : BigDecimal.ZERO);
        dto.setTotalTransferencia(caixa.getTotalTransferencia() != null ? caixa.getTotalTransferencia() : BigDecimal.ZERO);
        dto.setTotalFaturado(caixa.getTotalFaturado() != null ? caixa.getTotalFaturado() : BigDecimal.ZERO);
        dto.setTotalDiarias(caixa.getTotalDiarias() != null ? caixa.getTotalDiarias() : BigDecimal.ZERO);
        dto.setTotalProdutos(caixa.getTotalProdutos() != null ? caixa.getTotalProdutos() : BigDecimal.ZERO);
        dto.setTotalBruto(caixa.getTotalBruto() != null ? caixa.getTotalBruto() : BigDecimal.ZERO);
        dto.setTotalLiquido(caixa.getTotalLiquido() != null ? caixa.getTotalLiquido() : BigDecimal.ZERO);
        dto.setTotalDescontos(caixa.getTotalDescontos() != null ? caixa.getTotalDescontos() : BigDecimal.ZERO);
        dto.setTotalEstornos(caixa.getTotalEstornos() != null ? caixa.getTotalEstornos() : BigDecimal.ZERO);
        
        if (detalhes != null && !detalhes.isEmpty()) {
            List<FechamentoCaixaDetalheDTO> detalhesDTO = detalhes.stream()
                .map(this::converterDetalheParaDTO)
                .collect(Collectors.toList());
            dto.setDetalhes(detalhesDTO);
        } else {
            dto.setDetalhes(new ArrayList<>());
        }
        
        dto.setResumoApartamentos(new ArrayList<>());
        
        return dto;
    }
    
    /**
     * ✅ BUSCAR VENDAS DETALHADAS DO CAIXA
     * Mostra todos os produtos vendidos organizados por forma de pagamento
     */
    /**
     * ✅ BUSCAR VENDAS DETALHADAS DO CAIXA
     * Mostra todos os produtos vendidos organizados por forma de pagamento
     */
    @Transactional(readOnly = true)
    public RelatorioVendasCaixaDTO buscarVendasDetalhadas(Long caixaId) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("🛒 BUSCANDO VENDAS DETALHADAS DO CAIXA #" + caixaId);
        
        FechamentoCaixa caixa = fechamentoCaixaRepository.findById(caixaId)
            .orElseThrow(() -> new RuntimeException("Caixa não encontrado"));
        
        RelatorioVendasCaixaDTO relatorio = new RelatorioVendasCaixaDTO(caixaId);
        
        // 1. Buscar TODOS os pagamentos do caixa
        List<Pagamento> todosPagamentos = pagamentoRepository.findByCaixa(caixa);
        
        System.out.println("📊 Total de pagamentos no caixa: " + todosPagamentos.size());
        
        // Mostrar os tipos encontrados
        System.out.println("📋 Tipos de pagamento encontrados:");
        todosPagamentos.forEach(p -> System.out.println("   - " + p.getTipo() + " = R$ " + p.getValor()));
        
        // 2. Filtrar apenas pagamentos de VENDAS (não hospedagem/diária)
        List<Pagamento> pagamentos = todosPagamentos.stream()
            .filter(p -> p.getTipo() != null && 
                        (p.getTipo().equalsIgnoreCase("PRODUTO") || 
                         p.getTipo().equalsIgnoreCase("VENDA") ||
                         p.getTipo().equalsIgnoreCase("VENDA_AVULSA_FATURADA") ||
                         p.getTipo().equalsIgnoreCase("CONSUMO")))
            .collect(Collectors.toList());
        
        System.out.println("📊 Total de pagamentos de produtos/vendas: " + pagamentos.size());
        
        // 3. Para cada pagamento, buscar a nota de venda e seus itens
        for (Pagamento pagamento : pagamentos) {
            System.out.println("🔍 Processando pagamento #" + pagamento.getId() + 
                              " - Tipo: " + pagamento.getTipo() + 
                              " - Valor: R$ " + pagamento.getValor());
            
            // Verificar se o pagamento tem reserva associada
            if (pagamento.getReserva() != null) {
                System.out.println("   → Tem reserva #" + pagamento.getReserva().getId());
                
                // Buscar notas de venda pela reserva
                List<NotaVenda> notas = notaVendaRepository.findByReserva(pagamento.getReserva());
                System.out.println("   → Notas encontradas: " + notas.size());
                
                for (NotaVenda nota : notas) {
                    processarNotaVendaRelatorio(relatorio, nota, pagamento);
                }
            } else {
                System.out.println("   → Venda à vista (sem reserva)");
                
                // Venda à vista - buscar pela descrição ou período
                LocalDateTime inicioCaixa = caixa.getDataHoraAbertura();
                LocalDateTime fimCaixa = caixa.getDataHoraFechamento() != null 
                    ? caixa.getDataHoraFechamento() 
                    : LocalDateTime.now();
                
                // Buscar todas as notas do período
                List<NotaVenda> notasPeriodo = notaVendaRepository.findByDataHoraVendaBetween(
                    inicioCaixa, 
                    fimCaixa
                );
                
                System.out.println("   → Notas no período: " + notasPeriodo.size());
                
                for (NotaVenda nota : notasPeriodo) {
                    // Verificar se o valor bate (aproximadamente)
                    if (nota.getTotal().compareTo(pagamento.getValor()) == 0) {
                        System.out.println("   → Nota #" + nota.getId() + " corresponde ao valor!");
                        processarNotaVendaRelatorio(relatorio, nota, pagamento);
                        break;
                    }
                }
            }
        }
        
        // 4. Calcular totais gerais
        calcularTotaisGeraisRelatorio(relatorio);
        
        System.out.println("═══════════════════════════════════════════");
        System.out.println("✅ Relatório gerado com sucesso!");
        System.out.println("   Total de vendas: " + relatorio.getTotalVendas());
        System.out.println("   Total de produtos: " + relatorio.getTotalProdutos());
        System.out.println("   Total geral: R$ " + relatorio.getTotalGeral());
        System.out.println("═══════════════════════════════════════════");
        
        return relatorio;
    }
    /**
     * 🔄 PROCESSAR NOTA DE VENDA E ADICIONAR AO RELATÓRIO
     */
    private void processarNotaVendaRelatorio(RelatorioVendasCaixaDTO relatorio, NotaVenda nota, Pagamento pagamento) {
        // Buscar itens da nota com os produtos
        List<ItemVenda> itens = itemVendaRepository.findByNotaVendaIdWithProduto(nota.getId());
        
        if (!itens.isEmpty()) {
            VendaDetalhadaDTO vendaDTO = criarVendaDetalhadaDTO(nota, itens);
            String formaPagamento = pagamento.getFormaPagamento().toString();
            
            // Adicionar ao mapa de vendas por forma de pagamento
            relatorio.getVendasPorFormaPagamento()
                .get(formaPagamento)
                .add(vendaDTO);
            
            // Atualizar totais
            atualizarTotaisRelatorio(relatorio, formaPagamento, vendaDTO);
        }
    }

    /**
     * 🔄 CRIAR VENDA DETALHADA DTO A PARTIR DA NOTA E ITENS
     */
    private VendaDetalhadaDTO criarVendaDetalhadaDTO(NotaVenda nota, List<ItemVenda> itens) {
        VendaDetalhadaDTO vendaDTO = new VendaDetalhadaDTO();
        vendaDTO.setNotaVendaId(nota.getId());
        vendaDTO.setDataHora(nota.getDataHoraVenda());
        vendaDTO.setValorTotal(nota.getTotal());
        vendaDTO.setTipoVenda(nota.getTipoVenda().toString());
        
        List<ProdutoVendidoDTO> produtosDTO = new ArrayList<>();
        
        for (ItemVenda item : itens) {
            ProdutoVendidoDTO produtoDTO = new ProdutoVendidoDTO();
            produtoDTO.setProdutoId(item.getProduto().getId());
            produtoDTO.setNomeProduto(item.getProduto().getNomeProduto());
            produtoDTO.setQuantidade(item.getQuantidade());
            produtoDTO.setValorUnitario(item.getValorUnitario());
            produtoDTO.setTotalItem(item.getTotalItem());
            
            produtosDTO.add(produtoDTO);
        }
        
        vendaDTO.setProdutos(produtosDTO);
        
        return vendaDTO;
    }

    /**
     * 🔄 ATUALIZAR TOTAIS DO RELATÓRIO
     */
    private void atualizarTotaisRelatorio(RelatorioVendasCaixaDTO relatorio, String formaPagamento, VendaDetalhadaDTO venda) {
        // Atualizar total por forma de pagamento
        BigDecimal totalAtual = relatorio.getTotaisPorFormaPagamento().get(formaPagamento);
        relatorio.getTotaisPorFormaPagamento().put(
            formaPagamento, 
            totalAtual.add(venda.getValorTotal())
        );
        
        // Atualizar quantidade de vendas
        Integer qtdVendas = relatorio.getQuantidadeVendasPorFormaPagamento().get(formaPagamento);
        relatorio.getQuantidadeVendasPorFormaPagamento().put(formaPagamento, qtdVendas + 1);
        
        // Atualizar quantidade de produtos
        Integer qtdProdutos = relatorio.getQuantidadeProdutosPorFormaPagamento().get(formaPagamento);
        int totalProdutosVenda = venda.getProdutos().stream()
            .mapToInt(ProdutoVendidoDTO::getQuantidade)
            .sum();
        relatorio.getQuantidadeProdutosPorFormaPagamento().put(formaPagamento, qtdProdutos + totalProdutosVenda);
    }

    /**
     * 🔄 CALCULAR TOTAIS GERAIS DO RELATÓRIO
     */
    private void calcularTotaisGeraisRelatorio(RelatorioVendasCaixaDTO relatorio) {
        BigDecimal totalGeral = BigDecimal.ZERO;
        int totalVendas = 0;
        int totalProdutos = 0;
        
        for (BigDecimal total : relatorio.getTotaisPorFormaPagamento().values()) {
            totalGeral = totalGeral.add(total);
        }
        
        for (Integer qtd : relatorio.getQuantidadeVendasPorFormaPagamento().values()) {
            totalVendas += qtd;
        }
        
        for (Integer qtd : relatorio.getQuantidadeProdutosPorFormaPagamento().values()) {
            totalProdutos += qtd;
        }
        
        relatorio.setTotalGeral(totalGeral);
        relatorio.setTotalVendas(totalVendas);
        relatorio.setTotalProdutos(totalProdutos);
    }
    
    
    /**
     * 🔄 CONVERTER DETALHE PARA DTO
     */
    private FechamentoCaixaDetalheDTO converterDetalheParaDTO(FechamentoCaixaDetalhe detalhe) {
        FechamentoCaixaDetalheDTO dto = new FechamentoCaixaDetalheDTO();
        dto.setId(detalhe.getId());
        dto.setDataHora(detalhe.getDataHora());
        dto.setTipo(detalhe.getTipo());
        dto.setDescricao(detalhe.getDescricao());
        dto.setApartamentoNumero(detalhe.getApartamentoNumero());
        dto.setValor(detalhe.getValor() != null ? detalhe.getValor() : BigDecimal.ZERO);
        dto.setFormaPagamento(detalhe.getFormaPagamento());
        
        return dto;
    }
}
