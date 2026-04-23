package com.uniremington.api.convenia.model.entity;

import com.uniremington.api.convenia.shared.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A file uploaded by a user (student or company representative) in support
 * of a practice agreement.
 *
 * <p>One live row per {@code (agreement_id, document_type)} pair; re-uploading
 * the same type performs an UPSERT at the repository level. System-generated
 * artefacts (signed PDFs, completion certificates) are not represented here —
 * they live directly on {@link Agreement}.</p>
 */
@Entity
@Table(
        name = "agreement_documents",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agreement_documents",
                columnNames = {"agreement_id", "document_type"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgreementDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false)
    private Agreement agreement;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    @Column(name = "file_key", nullable = false, length = 500)
    private String fileKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;
}
