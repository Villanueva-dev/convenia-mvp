package com.uniremington.api.convenia.model.entity;

/**
 * Classification of the documents that can be uploaded for an agreement.
 *
 * <p>Persisted as the {@code document_type} VARCHAR column of
 * {@code agreement_documents}, with a DB-level CHECK constraint that mirrors
 * these values. Order must not be relied upon (stored as STRING).</p>
 */
public enum DocumentType {
    // Student documents — uploaded in DRAFT or PENDING_SIGNATURE stage
    CV,
    CONTRACT,
    NATIONAL_ID,
    EPS,
    ARL,
    WORK_PLAN,

    // Company legal documents — uploaded by COMPANY_TUTOR in DRAFT stage
    NIT,
    RUT,
    CAMARA_COMERCIO
}
