package uy.edu.um.proytic2026.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entity.Admin;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.AdminRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Locale;

/**
 * Crea el primer administrador al arrancar la aplicación, si todavía no hay ninguno.
 * <p>
 * No existe un registro público de administradores: si cualquiera pudiera registrarse como
 * admin, podría aprobar prestadores a su gusto. El primero se crea acá, con los datos de las
 * propiedades {@code app.admin.*} (variables de entorno ADMIN_EMAIL, ADMIN_PASSWORD y
 * ADMIN_NAME). Si ya existe al menos un administrador, no hace nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.name:Administrador}")
    private String name;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("No hay ningún administrador. Definí ADMIN_EMAIL y ADMIN_PASSWORD y reiniciá la app "
                    + "para crear el primero (ver README).");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD debe tener al menos {} caracteres. No se creó el administrador.",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user == null) {
            user = userRepository.save(User.builder()
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(password))
                    .build());
        } else {
            // La cuenta ya existe (por ejemplo, como turista): se le suma el perfil de admin
            // y se mantiene su contraseña actual.
            log.warn("El correo {} ya tenía una cuenta: se le agrega el perfil de administrador "
                    + "y conserva su contraseña.", normalizedEmail);
        }

        user.setAdmin(adminRepository.save(Admin.builder()
                .user(user)
                .name(name == null || name.isBlank() ? "Administrador" : name.trim())
                .build()));
        log.info("Administrador inicial creado: {}", normalizedEmail);
    }
}
