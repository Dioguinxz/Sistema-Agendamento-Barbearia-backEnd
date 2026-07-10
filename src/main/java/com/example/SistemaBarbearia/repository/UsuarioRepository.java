package com.example.SistemaBarbearia.repository;

import com.example.SistemaBarbearia.entity.TipoUsuario;
import com.example.SistemaBarbearia.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByTelefone(String telefone);


    Optional<Usuario> findByEmailAndAtivoTrue(String email);
    Optional<Usuario> findByTelefoneAndAtivoTrue(String telefone);
    Optional<Usuario> findByIdAndAtivoTrue(String id);
    List<Usuario> findAllByAtivoTrue();
    List<Usuario> findAllByTipoAndAtivoTrue(TipoUsuario tipo);

}


