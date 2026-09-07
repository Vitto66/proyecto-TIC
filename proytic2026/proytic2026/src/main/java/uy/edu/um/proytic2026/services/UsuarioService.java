package uy.edu.um.proytic2026.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uy.edu.um.proytic2026.entities.Usuario;
import uy.edu.um.proytic2026.repository.UsuarioRepository;

@Service
public interface UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    public boolean altaUsuario(String nombre, String apellido){
        if (nombre == null || apellido == null)
            return false;
        }

        String userName = nombre + "." + apellido;
        Usuario nuevoUsuario = Usuario.builder()
                .name(nombre)
                .lastName(apellido)
                .userName(nombre)
                .build();

        nuevoUsuario = usuarioRepository.save(nuevoUsuario)

    }
}
