package com.divan.config;

import com.divan.entity.Perfil;
import com.divan.entity.Permissao;
import com.divan.entity.Usuario;
import com.divan.repository.PerfilRepository;
import com.divan.repository.PermissaoRepository;
import com.divan.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    
    @Autowired
    private PermissaoRepository permissaoRepository;
    
    @Autowired
    private PerfilRepository perfilRepository;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Override
    public void run(String... args) throws Exception {
        // Criar permissões se não existirem
        criarPermissoes();
        
        // Criar perfis se não existirem
        criarPerfis();
        
        // Criar usuário admin se não existir
        criarUsuarioAdmin();
    }
    
    private void criarPermissoes() {
        String[][] permissoes = {
            // APARTAMENTOS
            {"APARTAMENTO_CREATE", "Criar apartamentos", "APARTAMENTOS"},
            {"APARTAMENTO_READ", "Visualizar apartamentos", "APARTAMENTOS"},
            {"APARTAMENTO_UPDATE", "Atualizar apartamentos", "APARTAMENTOS"},
            {"APARTAMENTO_DELETE", "Deletar apartamentos", "APARTAMENTOS"},
            
            // CLIENTES
            {"CLIENTE_CREATE", "Criar clientes", "CLIENTES"},
            {"CLIENTE_READ", "Visualizar clientes", "CLIENTES"},
            {"CLIENTE_UPDATE", "Atualizar clientes", "CLIENTES"},
            {"CLIENTE_DELETE", "Deletar clientes", "CLIENTES"},
            
            // EMPRESAS
            {"EMPRESA_CREATE", "Criar empresas", "EMPRESAS"},
            {"EMPRESA_READ", "Visualizar empresas", "EMPRESAS"},
            {"EMPRESA_UPDATE", "Atualizar empresas", "EMPRESAS"},
            {"EMPRESA_DELETE", "Deletar empresas", "EMPRESAS"},
            
            // RESERVAS
            {"RESERVA_CREATE", "Criar reservas", "RESERVAS"},
            {"RESERVA_READ", "Visualizar reservas", "RESERVAS"},
            {"RESERVA_UPDATE", "Atualizar reservas", "RESERVAS"},
            {"RESERVA_CANCEL", "Cancelar reservas", "RESERVAS"},
            {"RESERVA_FINALIZE", "Finalizar reservas", "RESERVAS"},
            
            // PRODUTOS
            {"PRODUTO_CREATE", "Criar produtos", "PRODUTOS"},
            {"PRODUTO_READ", "Visualizar produtos", "PRODUTOS"},
            {"PRODUTO_UPDATE", "Atualizar produtos", "PRODUTOS"},
            {"PRODUTO_DELETE", "Deletar produtos", "PRODUTOS"},
            
            // VENDAS
            {"VENDA_CREATE", "Registrar vendas", "VENDAS"},
            {"VENDA_READ", "Visualizar vendas", "VENDAS"},
            
            // PAGAMENTOS
            {"PAGAMENTO_CREATE", "Registrar pagamentos", "PAGAMENTOS"},
            {"PAGAMENTO_READ", "Visualizar pagamentos", "PAGAMENTOS"},
            
            // DIÁRIAS
            {"DIARIA_CREATE", "Criar diárias", "DIARIAS"},
            {"DIARIA_READ", "Visualizar diárias", "DIARIAS"},
            {"DIARIA_UPDATE", "Atualizar diárias", "DIARIAS"},
            {"DIARIA_DELETE", "Deletar diárias", "DIARIAS"},
            
            // RELATÓRIOS
            {"RELATORIO_READ", "Visualizar relatórios", "RELATORIOS"},
            {"RELATORIO_DASHBOARD", "Visualizar dashboard", "RELATORIOS"},
            
            // EXTRATOS
            {"EXTRATO_READ", "Visualizar extratos", "EXTRATOS"},
        };
        
        for (String[] perm : permissoes) {
            if (!permissaoRepository.existsByNome(perm[0])) {
                Permissao permissao = new Permissao();
                permissao.setNome(perm[0]);
                permissao.setDescricao(perm[1]);
                permissao.setCategoria(perm[2]);
                permissaoRepository.save(permissao);
            }
        }
        
        System.out.println("✓ Permissões inicializadas");
    }
    
    private void criarPerfis() {
        // Perfil ADMIN - Todas as permissões
        if (!perfilRepository.existsByNome("ADMIN")) {
            Perfil admin = new Perfil();
            admin.setNome("ADMIN");
            admin.setDescricao("Administrador do sistema com todas as permissões");
            admin.setPermissoes(permissaoRepository.findAll());
            perfilRepository.save(admin);
            System.out.println("✓ Perfil ADMIN criado");
        }
        
        // Perfil GERENTE
        if (!perfilRepository.existsByNome("GERENTE")) {
            Perfil gerente = new Perfil();
            gerente.setNome("GERENTE");
            gerente.setDescricao("Gerente com acesso a relatórios e visualização completa");
            
            List<Permissao> permissoesGerente = new ArrayList<>();
            permissoesGerente.addAll(permissaoRepository.findByCategoria("RELATORIOS"));
            permissoesGerente.addAll(permissaoRepository.findByCategoria("EXTRATOS"));
            permissoesGerente.addAll(Arrays.asList(
                permissaoRepository.findByNome("APARTAMENTO_READ").orElse(null),
                permissaoRepository.findByNome("CLIENTE_READ").orElse(null),
                permissaoRepository.findByNome("RESERVA_READ").orElse(null),
                permissaoRepository.findByNome("PRODUTO_READ").orElse(null),
                permissaoRepository.findByNome("VENDA_READ").orElse(null),
                permissaoRepository.findByNome("PAGAMENTO_READ").orElse(null)
            ));
            permissoesGerente.removeIf(p -> p == null);
            
            gerente.setPermissoes(permissoesGerente);
            perfilRepository.save(gerente);
            System.out.println("✓ Perfil GERENTE criado");
        }
        
        // Perfil RECEPCIONISTA
        if (!perfilRepository.existsByNome("RECEPCIONISTA")) {
            Perfil recepcionista = new Perfil();
            recepcionista.setNome("RECEPCIONISTA");
            recepcionista.setDescricao("Recepcionista com permissões para reservas e clientes");
            
            List<Permissao> permissoesRecepcionista = Arrays.asList(
                permissaoRepository.findByNome("CLIENTE_CREATE").orElse(null),
                permissaoRepository.findByNome("CLIENTE_READ").orElse(null),
                permissaoRepository.findByNome("CLIENTE_UPDATE").orElse(null),
                permissaoRepository.findByNome("RESERVA_CREATE").orElse(null),
                permissaoRepository.findByNome("RESERVA_READ").orElse(null),
                permissaoRepository.findByNome("RESERVA_UPDATE").orElse(null),
                permissaoRepository.findByNome("APARTAMENTO_READ").orElse(null),
                permissaoRepository.findByNome("PAGAMENTO_CREATE").orElse(null),
                permissaoRepository.findByNome("PAGAMENTO_READ").orElse(null),
                permissaoRepository.findByNome("EXTRATO_READ").orElse(null)
            );
            permissoesRecepcionista.removeIf(p -> p == null);
            
            recepcionista.setPermissoes(permissoesRecepcionista);
            perfilRepository.save(recepcionista);
            System.out.println("✓ Perfil RECEPCIONISTA criado");
        }
        
        // Perfil VENDEDOR
        if (!perfilRepository.existsByNome("VENDEDOR")) {
            Perfil vendedor = new Perfil();
            vendedor.setNome("VENDEDOR");
            vendedor.setDescricao("Vendedor com permissões para produtos e vendas");
            
            List<Permissao> permissoesVendedor = Arrays.asList(
                permissaoRepository.findByNome("PRODUTO_READ").orElse(null),
                permissaoRepository.findByNome("VENDA_CREATE").orElse(null),
                permissaoRepository.findByNome("VENDA_READ").orElse(null),
                permissaoRepository.findByNome("RESERVA_READ").orElse(null)
            );
            permissoesVendedor.removeIf(p -> p == null);
            
            vendedor.setPermissoes(permissoesVendedor);
            perfilRepository.save(vendedor);
            System.out.println("✓ Perfil VENDEDOR criado");
        }
    }
    
    private void criarUsuarioAdmin() {
        if (!usuarioRepository.existsByUsername("admin")) {
            Usuario admin = new Usuario();
            admin.setNome("Administrador");
            admin.setUsername("admin");
            admin.setEmail("admin@divan.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setAtivo(true);
            
            Perfil perfilAdmin = perfilRepository.findByNome("ADMIN").orElse(null);
            if (perfilAdmin != null) {
                admin.setPerfis(Arrays.asList(perfilAdmin));
            }
            
            usuarioRepository.save(admin);
            System.out.println("✓ Usuário admin criado - Username: admin | Senha: admin123");
        }
    }
}
