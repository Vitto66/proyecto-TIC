package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/** Perfil de administrador de Uruguay Mnatural. */
@Entity
@Table(name = "administrator")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;
}
