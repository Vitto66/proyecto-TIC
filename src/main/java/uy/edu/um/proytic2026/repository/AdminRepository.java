package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.Admin;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}
