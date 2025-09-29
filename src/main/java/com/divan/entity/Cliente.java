package com.divan.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "clientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false, length = 100)
    private String nome;
    
    @CPF(message = "CPF inválido")
    @Column(unique = true, nullable = false)
    private String cpf;
    
    @Pattern(regexp = "\\(\\d{2}\\)\\s\\d{4,5}-\\d{4}", message = "Celular deve estar no formato (XX) XXXXX-XXXX")
    @Column(nullable = false)
    private String celular;
    
    @Column(length = 200)
    private String endereco;
    
    @Pattern(regexp = "\\d{5}-\\d{3}", message = "CEP deve estar no formato XXXXX-XXX")
    private String cep;
    
    @Column(length = 50)
    private String cidade;
    
    @Column(length = 2)
    private String estado;
    
    @NotNull(message = "Data de nascimento é obrigatória")
    @Column(nullable = false)
    private LocalDate dataNascimento;
    
    @ManyToOne
    @JoinColumn(name = "empresa_id")
    private Empresa empresa;
    
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL)
    private List<Reserva> reservas;
}
