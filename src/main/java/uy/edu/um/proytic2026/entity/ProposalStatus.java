package uy.edu.um.proytic2026.entity;

/** Estado de una propuesta de tipo (CU-12). Coincide con el CHECK de type_proposal.status en Supabase. */
public enum ProposalStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
