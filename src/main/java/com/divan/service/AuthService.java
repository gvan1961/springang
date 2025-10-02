package com.divan.service;

import com.divan.dto.LoginRequestDTO;
import com.divan.dto.LoginResponseDTO;
import com.divan.entity.Permissao;
import com.divan.entity.Perfil;
import com.divan.entity.Usuario;
import com.divan.repository.UsuarioRepository;
import com.divan.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UsuarioRepository usuarioRepository;
    
    public LoginResponseDTO login(LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtil.generateToken(authentication);
        
        Usuario usuario = usuarioRepository.findByUsername(loginRequest.getUsername())
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        
        // Atualizar último acesso
        usuario.setUltimoAcesso(LocalDateTime.now());
        usuarioRepository.save(usuario);
        
        // Coletar perfis
        List<String> perfis = usuario.getPerfis().stream()
            .map(Perfil::getNome)
            .collect(Collectors.toList());
        
        // Coletar permissões (dos perfis + individuais)
        List<String> permissoes = usuario.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(auth -> !auth.startsWith("ROLE_"))
            .distinct()
            .collect(Collectors.toList());
        
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(jwt);
        response.setId(usuario.getId());
        response.setUsername(usuario.getUsername());
        response.setNome(usuario.getNome());
        response.setEmail(usuario.getEmail());
        response.setPerfis(perfis);
        response.setPermissoes(permissoes);
        
        return response;
    }
    
    @Transactional(readOnly = true)
    public Usuario getUsuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        return usuarioRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("Usuário autenticado não encontrado"));
    }
}
