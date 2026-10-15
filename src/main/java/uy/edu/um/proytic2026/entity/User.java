package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cuenta de acceso a la plataforma. Guarda solo las credenciales y datos comunes.
 * Los datos propios de cada rol viven en los perfiles Tourist, Provider y Admin,
 * que comparten el id de esta entidad. Así un mismo correo puede ser turista
 * y prestador a la vez, como pidió el cliente.
 */
@Entity
@Table(name = "users") // "user" es palabra reservada en PostgreSQL
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private Tourist tourist;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private Provider provider;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private Admin admin;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
