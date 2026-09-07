package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entities.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario,Long> {

    public boolean existsByUserName(String,username);
}
