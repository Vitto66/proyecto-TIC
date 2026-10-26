package uy.edu.um.proytic2026.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uy.edu.um.proytic2026.entity.ProposalStatus;
import uy.edu.um.proytic2026.entity.TypeCategory;
import uy.edu.um.proytic2026.entity.TypeProposal;

import java.util.List;

public interface TypeProposalRepository extends JpaRepository<TypeProposal, Long> {

    /** CU-12: las propuestas de un prestador, de la más nueva a la más vieja. */
    List<TypeProposal> findByProviderIdOrderByIdDesc(Long providerId);

    /** CU-13 2a: las propuestas pendientes de una lista, de la más vieja a la más nueva, con el prestador. */
    @Query("""
            select p from TypeProposal p
            join fetch p.provider
            where p.category = :category and p.status = :status
            order by p.id
            """)
    List<TypeProposal> findWithProvider(@Param("category") TypeCategory category,
                                        @Param("status") ProposalStatus status);

    /** El prestador ya propuso ese mismo nombre y sigue pendiente. */
    boolean existsByProviderIdAndCategoryAndStatusAndNameIgnoreCase(Long providerId, TypeCategory category,
                                                                    ProposalStatus status, String name);

    long countByStatus(ProposalStatus status);
}
