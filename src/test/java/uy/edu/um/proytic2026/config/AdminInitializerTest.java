package uy.edu.um.proytic2026.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import uy.edu.um.proytic2026.entity.Admin;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.AdminRepository;
import uy.edu.um.proytic2026.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tests unitarios de la creación del administrador inicial. */
@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AdminInitializer initializer(String email, String password) {
        AdminInitializer initializer = new AdminInitializer(adminRepository, userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "email", email);
        ReflectionTestUtils.setField(initializer, "password", password);
        ReflectionTestUtils.setField(initializer, "name", "Administrador");
        return initializer;
    }

    @Test
    void run_adminAlreadyExists_doesNothing() {
        when(adminRepository.count()).thenReturn(1L);

        initializer("admin@mail.com", "secreta123").run(null);

        verify(userRepository, never()).save(any());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void run_notConfigured_doesNothing() {
        when(adminRepository.count()).thenReturn(0L);

        initializer("", "").run(null);

        verify(userRepository, never()).save(any());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void run_passwordTooShort_doesNothing() {
        when(adminRepository.count()).thenReturn(0L);

        initializer("admin@mail.com", "corta").run(null);

        verify(adminRepository, never()).save(any());
    }

    @Test
    void run_noAdmin_createsAccountWithHashedPassword() {
        when(adminRepository.count()).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("admin@mail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer("  Admin@Mail.com ", "secreta123").run(null);

        verify(userRepository).save(argThat(u -> u.getEmail().equals("admin@mail.com")
                && passwordEncoder.matches("secreta123", u.getPasswordHash())));
        verify(adminRepository).save(argThat(a -> a.getName().equals("Administrador")));
    }

    @Test
    void run_existingUser_addsAdminProfileAndKeepsPassword() {
        User existing = User.builder().id(5L).email("admin@mail.com").passwordHash("hash-original").build();
        when(adminRepository.count()).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("admin@mail.com")).thenReturn(Optional.of(existing));
        when(adminRepository.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));

        initializer("admin@mail.com", "secreta123").run(null);

        verify(userRepository, never()).save(any());
        assertThat(existing.getAdmin()).isNotNull();
        assertThat(existing.getPasswordHash()).isEqualTo("hash-original");
    }
}
