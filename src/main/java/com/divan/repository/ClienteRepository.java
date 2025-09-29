package com.divan.repository;

import com.divan.entity.Cliente;
import com.divan.entity.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    
    Optional<Cliente> findByCpf(String cpf);
    
    List<Cliente> findByNomeContainingIgnoreCase(String nome);
    
    List<Cliente> findByEmpresa(Empresa empresa);
    
    List<Cliente> findByEmpresaId(Long empresaId);
    
    @Query("SELECT c FROM Cliente c WHERE c.nome LIKE %:nome% OR c.cpf LIKE %:cpf%")
    List<Cliente> buscarPorNomeOuCpf(String nome, String cpf);
    
    boolean existsByCpf(String cpf);
    
    @Query("SELECT c FROM Cliente c JOIN c.reservas r WHERE r.apartamento.numeroApartamento = :numeroApartamento")
    List<Cliente> findByApartamento(String numeroApartamento);
}
