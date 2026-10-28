package uy.edu.um.proytic2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * La foto de portada de una actividad (CU-08 y CU-10). Va en una tabla aparte para que al cargar
 * actividades no se traigan también los bytes de la imagen. La clave es el id de la actividad:
 * cada actividad tiene como mucho una foto.
 * <p>
 * La foto puede ser la que subió el prestador (image + contentType) o una dirección externa
 * (externalUrl), como las fotos de Wikimedia Commons de las actividades de ejemplo: así no pesan
 * en la base.
 */
@Entity
@Table(name = "activity_photo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityPhoto {

    /** activity_id referencia a activity(id). */
    @Id
    @Column(name = "activity_id")
    private Long activityId;

    /** image/jpeg, image/png o image/webp. Vacío si la foto es una dirección externa. */
    @Column(name = "content_type", length = 30)
    private String contentType;

    /** Los bytes de la imagen. En Supabase la columna es BYTEA. Vacío si la foto es externa. */
    @Column(name = "image", length = 4_000_000)
    private byte[] image;

    /** Dirección de una foto publicada en otro sitio (https). Si está, se usa en lugar de image. */
    @Column(name = "external_url", length = 500)
    private String externalUrl;

    /** Cuándo se subió; se usa en la dirección de la foto para que el navegador no muestre una vieja. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
