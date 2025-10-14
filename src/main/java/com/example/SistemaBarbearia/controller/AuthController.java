package com.example.SistemaBarbearia.controller;

import com.example.SistemaBarbearia.dto.*;
import com.example.SistemaBarbearia.entity.Usuario;
import com.example.SistemaBarbearia.exceptions.UsuarioComEmailRegistradoException;
import com.example.SistemaBarbearia.exceptions.UsuarioComTelefoneRegistradoException;
import com.example.SistemaBarbearia.repository.UsuarioRepository;
import com.example.SistemaBarbearia.security.TokenService;
import com.example.SistemaBarbearia.service.EmailService;
import com.example.SistemaBarbearia.service.GoogleAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final GoogleAuthenticationService googleAuthenticationService;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Validated AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.senha());
        var auth = this.authenticationManager.authenticate(usernamePassword);
        var token = tokenService.gerarToken((Usuario) auth.getPrincipal());
        return ResponseEntity.ok(new EmailResponseDTO(token));
    }

    @PostMapping("/register")
    public ResponseEntity register(@RequestBody @Validated RegisterDTO data) {

        if (this.usuarioRepository.findByEmail(data.email()).isPresent()) {
            throw new UsuarioComEmailRegistradoException(data.email());
        }

        if (this.usuarioRepository.findByTelefone(data.telefone()).isPresent()) {
            throw new UsuarioComTelefoneRegistradoException(data.telefone());

        }

        String senhaCriptografada = passwordEncoder.encode(data.senha());
        Usuario novoUsuario = new Usuario(data.nome(), data.email(), data.telefone(), senhaCriptografada, data.tipoUsuario());

        this.usuarioRepository.save(novoUsuario);
        emailService.enviarEmailBoasVindas(novoUsuario);
        return ResponseEntity.ok().build();
    }


    @PostMapping("/google")
    public ResponseEntity<GoogleAuthenticationResponse> googleLogin(@RequestBody @Valid GoogleLoginRequestDTO data) {
        try {
            GoogleAuthenticationResponse response = googleAuthenticationService.loginWithGoogle(data.idToken());
            return ResponseEntity.ok(response);
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            logger.error("Erro na autenticação com Google: {}", e.getMessage());
            logger.error("Stack trace:", e);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}