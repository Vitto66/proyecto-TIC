package uy.edu.um.proytic2026.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uy.edu.um.proytic2026.service.ActivityPhotoService;

import java.net.URI;
import java.time.Duration;

/** La foto de portada de una actividad. Es pública, como la vidriera. */
@Controller
@RequiredArgsConstructor
public class ActivityPhotoController {

    private final ActivityPhotoService photoService;

    @GetMapping("/activities/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photoService.find(id)
                .filter(p -> ActivityPhotoService.isExternal(p.getExternalUrl()) || p.getImage() != null)
                .map(p -> ActivityPhotoService.isExternal(p.getExternalUrl())
                        // Foto externa (por ejemplo, de Wikimedia Commons): se redirige a ella
                        ? ResponseEntity.status(302).location(URI.create(p.getExternalUrl())).<byte[]>build()
                        : ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        // La dirección cambia con cada foto nueva, así que el navegador la puede guardar
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(p.getImage()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
