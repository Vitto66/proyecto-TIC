package uy.edu.um.proytic2026.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import uy.edu.um.proytic2026.entity.Admin;
import uy.edu.um.proytic2026.entity.Tourist;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.AdminRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios de la creación del administrador inicial. */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminRepository adminRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, adminRepository, passwordEncoder, email, password, "Administrador");
    }

    @Test
    void noAdminYet_createsAccountWithHashedPassword() {
        when(userRepository.findByEmailIgnoreCase("admin@mnatural.uy")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        bootstrap(" Admin@Mnatural.uy ", "clave-segura").run(null);

        verify(userRepository).save(argThat(u -> u.getEmail().equals("admin@mnatural.uy")
                && passwordEncoder.matches("clave-segura", u.getPasswordHash())));
        verify(adminRepository).save(argThat(a -> a.getName().equals("Administrador")));
    }

    @Test
    void adminAlreadyExists_doesNothing() {
        User existing = User.builder().id(1L).email("admin@mnatural.uy").passwordHash("x").build();
        existing.setAdmin(Admin.builder().user(existing).name("Administrador").build());
        when(userRepository.findByEmailIgnoreCase("admin@mnatural.uy")).thenReturn(Optional.of(existing));

        bootstrap("admin@mnatural.uy", "otra-clave").run(null);

        verify(userRepository, never()).save(any());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void emailBelongsToTourist_addsAdminProfileKeepingPassword() {
        User existing = User.builder().id(2L).email("admin@mnatural.uy").passwordHash("hash-original").build();
        existing.setTourist(Tourist.builder().user(existing).build());
        when(userRepository.findByEmailIgnoreCase("admin@mnatural.uy")).thenReturn(Optional.of(existing));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        bootstrap("admin@mnatural.uy", "clave-segura").run(null);

        verify(userRepository, never()).save(any());
        verify(adminRepository).save(argThat(a -> a.getUser() == existing));
    }

    @Test
    void missingPassword_doesNotCreateAnything() {
        bootstrap("admin@mnatural.uy", "").run(null);

        verifyNoInteractions(userRepository, adminRepository);
    }
}
