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
import org.springframework.stereotype.Service;

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

    public GoogleAuthenticationResponse loginWithGoogle(String idTokenString) throws GeneralSecurityException, IOException {
        //Prepara o verificador de tokens do Google
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        //Verifica o token
        GoogleIdToken idToken = verifier.verify(idTokenString);
        if (idToken == null) {
            throw new IllegalArgumentException("Token do Google inválido.");
        }

        // Extrai as informações do usuário
        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String nome = (String) payload.get("name");
        String telefone = (String) payload.get("telefone");

        // Lógica "Upsert": Encontra ou cria o usuário
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseGet(() -> {
                    logger.info("Criando novo usuário via Google Login para o e-mail: {}", email);
                    Usuario novoUsuario = Usuario.builder()
                            .email(email)
                            .nome(nome)
                            .telefone(telefone)
                            .tipo(TipoUsuario.CLIENTE)
                            .ativo(true)
                            .build();

                    Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);
                    emailService.enviarEmailBoasVindas(usuarioSalvo);
                    return usuarioSalvo;
                });

        //Gera e retorna o token JWT da SUA aplicação
        var jwtToken = tokenService.gerarToken(usuario);
        return new GoogleAuthenticationResponse(jwtToken);
    }
}