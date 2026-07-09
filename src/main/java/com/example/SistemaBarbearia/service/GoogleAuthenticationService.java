package com.example.SistemaBarbearia.service;


import com.example.SistemaBarbearia.dto.GoogleAuthenticationResponse;
import com.example.SistemaBarbearia.entity.TipoUsuario;
import com.example.SistemaBarbearia.entity.Usuario;
import com.example.SistemaBarbearia.repository.UsuarioRepository;
import com.example.SistemaBarbearia.security.TokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class GoogleAuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthenticationService.class);

    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final EmailService emailService;

    @Value("${google.client-id}")
    private String googleClientId;

    // Método centralizado para validar o token
    private GoogleIdToken.Payload validarToken(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google inválido.");
        }
        return idToken.getPayload();
    }

    // Rota 1: Exclusiva para CADASTRO
    public GoogleAuthenticationResponse cadastrarComGoogle(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdToken.Payload payload = validarToken(idTokenString);
        String email = payload.getEmail();

        // VALIDAÇÃO: Se o e-mail já existe, trava a operação e devolve 409 Conflict
        if (usuarioRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Conta já existente. Por favor, faça login.");
        }

        logger.info("Criando novo usuário via Google Login para o e-mail: {}", email);
        Usuario novoUsuario = Usuario.builder()
                .email(email)
                .nome((String) payload.get("name"))
                .telefone((String) payload.get("telefone"))
                .tipo(TipoUsuario.CLIENTE)
                .ativo(true)
                .build();

        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);
        emailService.enviarEmailBoasVindas(usuarioSalvo);

        var jwtToken = tokenService.gerarToken(usuarioSalvo);
        return new GoogleAuthenticationResponse(jwtToken);
    }

    // Rota 2: Exclusiva para LOGIN
    public GoogleAuthenticationResponse loginComGoogle(String idTokenString) throws GeneralSecurityException, IOException {
        GoogleIdToken.Payload payload = validarToken(idTokenString);
        String email = payload.getEmail();

        // VALIDAÇÃO: Se o e-mail NÃO existe, avisa que a conta não foi encontrada (404 Not Found)
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada. Cadastre-se primeiro."));

        var jwtToken = tokenService.gerarToken(usuario);
        return new GoogleAuthenticationResponse(jwtToken);
    }
}
