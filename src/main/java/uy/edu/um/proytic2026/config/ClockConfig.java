package uy.edu.um.proytic2026.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * La hora "actual" de la aplicación, siempre en hora de Uruguay aunque el servidor esté en
 * otra zona (por ejemplo, en UTC). Las fechas de las actividades se guardan sin zona horaria
 * y se interpretan como hora de Uruguay. Los tests pueden usar un Clock fijo.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    @Bean
    public Clock clock() {
        return Clock.system(URUGUAY);
    }
}
