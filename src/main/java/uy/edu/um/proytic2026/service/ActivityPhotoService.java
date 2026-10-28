package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uy.edu.um.proytic2026.entity.ActivityPhoto;
import uy.edu.um.proytic2026.exception.InvalidPhotoException;
import uy.edu.um.proytic2026.repository.ActivityPhotoRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La foto de portada de las actividades: la que sube el prestador al publicar (CU-08) o al
 * modificar (CU-10), y la que se ve en el inicio, en Explorar (CU-05) y en el detalle (CU-06).
 * <p>
 * Se guarda en la base (tabla activity_photo), así todo el equipo ve las mismas fotos sin
 * configurar un servicio de archivos aparte.
 */
@Service
@RequiredArgsConstructor
public class ActivityPhotoService {

    /** Tamaño máximo de la foto ya guardada. El navegador la achica antes de subirla. */
    public static final int MAX_BYTES = 3 * 1024 * 1024;

    private final ActivityPhotoRepository photoRepository;
    private final Clock clock;

    /** Una foto ya revisada, lista para guardar. */
    public record PhotoUpload(String contentType, byte[] bytes) {
    }

    /**
     * Revisa el archivo que subió el prestador. Solo se aceptan JPG, PNG y WebP, y se
     * reconocen por su contenido (no por el nombre ni por lo que diga el navegador).
     *
     * @return vacío si no eligió ningún archivo.
     * @throws InvalidPhotoException no es una imagen aceptada o pesa más de 3 MB.
     */
    public Optional<PhotoUpload> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidPhotoException("La foto pesa más de 3 MB. Elegí una más liviana.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidPhotoException("No se pudo leer la foto. Probá de nuevo.");
        }
        String type = detectType(bytes);
        if (type == null) {
            throw new InvalidPhotoException("La foto tiene que ser una imagen JPG, PNG o WebP.");
        }
        return Optional.of(new PhotoUpload(type, bytes));
    }

    /** Guarda la foto de la actividad, o la reemplaza si ya tenía una. */
    @Transactional
    public void save(Long activityId, PhotoUpload photo) {
        ActivityPhoto entity = photoRepository.findById(activityId)
                .orElseGet(() -> ActivityPhoto.builder().activityId(activityId).build());
        entity.setContentType(photo.contentType());
        entity.setImage(photo.bytes());
        entity.setExternalUrl(null);
        entity.setUpdatedAt(LocalDateTime.now(clock));
        photoRepository.save(entity);
    }

    /** Saca la foto de la actividad (vuelve a mostrarse la ilustración). */
    @Transactional
    public void remove(Long activityId) {
        photoRepository.findById(activityId).ifPresent(photoRepository::delete);
    }

    @Transactional(readOnly = true)
    public Optional<ActivityPhoto> find(Long activityId) {
        return photoRepository.findById(activityId);
    }

    /**
     * La dirección de la foto de cada actividad que tiene una. Si la subió el prestador, lleva la
     * fecha de subida, así al cambiarla el navegador pide la nueva en lugar de usar la guardada.
     * Si es una foto externa, es directamente su dirección.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> urls() {
        return photoRepository.stamps().stream().collect(Collectors.toMap(
                ActivityPhotoRepository.PhotoStamp::getActivityId,
                s -> isExternal(s.getExternalUrl()) ? s.getExternalUrl() : url(s.getActivityId(), s.getUpdatedAt())));
    }

    /** Solo se aceptan direcciones https, para no mostrar contenido inseguro. */
    public static boolean isExternal(String url) {
        return url != null && url.startsWith("https://");
    }

    /** La dirección de la foto de una actividad, o null si no tiene. */
    @Transactional(readOnly = true)
    public String url(Long activityId) {
        return urls().get(activityId);
    }

    private static String url(Long activityId, LocalDateTime updatedAt) {
        return "/activities/" + activityId + "/photo?v=" + updatedAt.toEpochSecond(ZoneOffset.UTC);
    }

    /** Reconoce JPG, PNG y WebP por sus primeros bytes. */
    static String detectType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        if (b.length >= 8 && Arrays.equals(Arrays.copyOf(b, 8), png)) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
