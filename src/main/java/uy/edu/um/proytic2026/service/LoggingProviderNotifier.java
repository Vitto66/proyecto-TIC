package uy.edu.um.proytic2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uy.edu.um.proytic2026.entity.Provider;

/**
 * Implementación actual de {@link ProviderNotifier}: deja constancia en el log. El prestador
 * se entera dentro de la plataforma al iniciar sesión. Para mandar un correo, reemplazar
 * esta clase por una que use spring-boot-starter-mail.
 */
@Slf4j
@Component
public class LoggingProviderNotifier implements ProviderNotifier {

    @Override
    public void providerReviewed(Provider provider) {
        log.info("Prestador {} ({}) quedó en estado {}",
                provider.getId(), provider.getUser().getEmail(), provider.getStatus());
    }
}
