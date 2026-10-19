package uy.edu.um.proytic2026.config;

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
 * Crea la cuenta del administrador inicial al arrancar la app, si todavía no existe.
 * <p>
 * Los administradores no se registran desde la plataforma (no hay formulario público):
 * Uruguay Mnatural configura el primero con app.admin.email y app.admin.password.
 * En el perfil "postgres" la contraseña sale de la variable de entorno ADMIN_PASSWORD
 * y nunca se escribe en el repo. Si no está definida, no se crea nada.
 * <p>
 * Si el administrador ya existe no se toca, ni siquiera su contraseña.
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository userRepository,
                          AdminRepository adminRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password,
                          @Value("${app.admin.name:Administrador}") String name) {
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            log.warn("No se creó el administrador inicial: faltan app.admin.email o ADMIN_PASSWORD");
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (user != null && user.getAdmin() != null) {
            log.info("Administrador inicial {} ya existe", normalizedEmail);
            return;
        }

        if (user == null) {
            user = userRepository.save(User.builder()
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(password))
                    .build());
        }
        // Si el correo ya era de un turista o prestador, se le suma el perfil de admin a esa cuenta
        // y conserva su contraseña.
        Admin admin = adminRepository.save(Admin.builder().user(user).name(name).build());
        user.setAdmin(admin);
        log.info("Administrador inicial {} creado", normalizedEmail);
    }
}
