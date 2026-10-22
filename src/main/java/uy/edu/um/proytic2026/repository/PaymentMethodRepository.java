package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uy.edu.um.proytic2026.entity.PaymentMethod;

import java.util.List;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    /** El id del turista es el de su cuenta (User). */
    List<PaymentMethod> findByTouristId(Long touristId);
}
