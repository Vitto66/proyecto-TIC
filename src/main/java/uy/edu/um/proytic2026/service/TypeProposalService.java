package uy.edu.um.proytic2026.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uy.edu.um.proytic2026.dto.ProposalForm;
import uy.edu.um.proytic2026.dto.ProposalView;
import uy.edu.um.proytic2026.dto.TypeRow;
import uy.edu.um.proytic2026.entity.*;
import uy.edu.um.proytic2026.exception.*;
import uy.edu.um.proytic2026.repository.AdminRepository;
import uy.edu.um.proytic2026.repository.ProviderRepository;
import uy.edu.um.proytic2026.repository.TypeProposalRepository;

import java.util.List;
import java.util.Optional;

/**
 * CU-12 Proponer un tipo de actividad o de equipamiento, y su resolución por un administrador
 * (CU-13 2a).
 * <p>
 * El prestador aprobado propone un tipo con nombre y descripción; queda PENDING hasta que un
 * administrador lo acepta (el tipo se crea y queda disponible para todos) o lo rechaza (el tipo
 * no se crea). El prestador ve el resultado en su lista de propuestas (3a: notificación).
 */
@Service
@RequiredArgsConstructor
public class TypeProposalService {

    private final TypeProposalRepository proposalRepository;
    private final ProviderRepository providerRepository;
    private final AdminRepository adminRepository;
    private final TypeManagementService typeService;

    /** Precondición: el prestador está aprobado. */
    @Transactional(readOnly = true)
    public boolean canPropose(Long providerId) {
        return providerRepository.findById(providerId)
                .map(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElse(false);
    }

    /**
     * Pasos 2 y 3: registra la propuesta en estado PENDING para que la vea el administrador.
     *
     * @throws ProposalNotAllowedException      el prestador no está aprobado.
     * @throws TypeAlreadyExistsException       2a: ya existe un tipo con ese nombre.
     * @throws ProposalAlreadyPendingException  ya propuso lo mismo y sigue pendiente.
     * @throws IllegalArgumentException         el nombre queda vacío o es demasiado largo.
     */
    @Transactional
    public ProposalView propose(Long providerId, ProposalForm form) {
        Provider provider = providerRepository.findById(providerId)
                .filter(p -> p.getStatus() == ProviderStatus.APPROVED)
                .orElseThrow(ProposalNotAllowedException::new);

        String name = TypeManagementService.normalize(form.getName());
        Optional<TypeRow> existing = typeService.findByName(form.getCategory(), name);
        if (existing.isPresent()) {
            throw new TypeAlreadyExistsException(existing.get().name(), existing.get().active());
        }
        if (proposalRepository.existsByProviderIdAndCategoryAndStatusAndNameIgnoreCase(
                providerId, form.getCategory(), ProposalStatus.PENDING, name)) {
            throw new ProposalAlreadyPendingException();
        }

        TypeProposal proposal = proposalRepository.save(TypeProposal.builder()
                .provider(provider)
                .category(form.getCategory())
                .name(name)
                .description(form.getDescription().strip())
                .build());
        return ProposalView.from(proposal);
    }

    /** Las propuestas del prestador con su estado: así se entera si se aceptaron o rechazaron (3a). */
    @Transactional(readOnly = true)
    public List<ProposalView> myProposals(Long providerId) {
        return proposalRepository.findByProviderIdOrderByIdDesc(providerId).stream()
                .map(ProposalView::from).toList();
    }

    /** CU-13 2a: las propuestas pendientes de una lista. */
    @Transactional(readOnly = true)
    public List<ProposalView> pending(TypeCategory category) {
        return proposalRepository.findWithProvider(category, ProposalStatus.PENDING).stream()
                .map(ProposalView::from).toList();
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return proposalRepository.countByStatus(ProposalStatus.PENDING);
    }

    /**
     * CU-13 2a: al aceptarla, el tipo se crea activo y queda disponible para todos los prestadores.
     *
     * @throws TypeNameTakenException mientras tanto se creó un tipo con ese nombre: se rechaza o se deja.
     */
    @Transactional
    public ProposalView accept(Long adminId, Long proposalId) {
        TypeProposal proposal = pendingProposal(proposalId);
        typeService.create(proposal.getCategory(), proposal.getName());
        return resolve(proposal, adminId, ProposalStatus.ACCEPTED);
    }

    /** CU-13 2a / CU-12 3a: al rechazarla, el tipo no se crea y el prestador lo ve en sus propuestas. */
    @Transactional
    public ProposalView reject(Long adminId, Long proposalId) {
        return resolve(pendingProposal(proposalId), adminId, ProposalStatus.REJECTED);
    }

    private TypeProposal pendingProposal(Long proposalId) {
        TypeProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(ProposalNotFoundException::new);
        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new ProposalAlreadyResolvedException();
        }
        return proposal;
    }

    private ProposalView resolve(TypeProposal proposal, Long adminId, ProposalStatus status) {
        proposal.setStatus(status);
        proposal.setResolvedBy(adminRepository.getReferenceById(adminId));
        return ProposalView.from(proposal);
    }
}
