package uy.edu.um.proytic2026;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.entity.User;
import uy.edu.um.proytic2026.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class Proytic2026ApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void contextLoads() {
	}

	/** Con H2, al arrancar la app queda creado el administrador de desarrollo. */
	@Test
	@Transactional
	void startup_createsInitialAdmin() {
		User admin = userRepository.findByEmailIgnoreCase("admin@mnatural.uy").orElseThrow();

		assertThat(admin.getAdmin()).isNotNull();
		assertThat(passwordEncoder.matches("admin1234", admin.getPasswordHash())).isTrue();
	}

}
