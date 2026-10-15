package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

/** Perfil de turista. Comparte la clave primaria con su User. */
@Entity
@Table(name = "tourists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tourist {

    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    /** Cédula de identidad. */
    @Column(nullable = false, unique = true, length = 20)
    private String nationalId;
}
