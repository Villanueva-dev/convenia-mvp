package com.uniremington.api.convenia.repository;

import com.uniremington.api.convenia.model.entity.AgreementDocument;
import com.uniremington.api.convenia.model.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgreementDocumentRepository extends JpaRepository<AgreementDocument, Long> {

    Optional<AgreementDocument> findByAgreementIdAndDocumentType(Long agreementId, DocumentType documentType);

    List<AgreementDocument> findAllByAgreementId(Long agreementId);

    /**
     * Atomic upsert on {@code (agreement_id, document_type)}. Eliminates the
     * TOCTOU window of a find-then-insert-or-update pattern. Updates
     * {@code updated_at} on every call to reflect the last upload time, and
     * sets {@code created_at} to {@code NOW()} only on INSERT.
     *
     * @return the number of rows affected (always 1 for INSERT or UPDATE).
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO agreement_documents (
                        agreement_id, document_type, file_key, content_type,
                        original_name, file_size_bytes, uploaded_by_id,
                        created_at, updated_at
                    )
                    VALUES (
                        :agreementId, :documentType, :fileKey, :contentType,
                        :originalName, :fileSizeBytes, :uploadedById,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT ON CONSTRAINT uk_agreement_documents
                    DO UPDATE SET
                        file_key        = EXCLUDED.file_key,
                        content_type    = EXCLUDED.content_type,
                        original_name   = EXCLUDED.original_name,
                        file_size_bytes = EXCLUDED.file_size_bytes,
                        uploaded_by_id  = EXCLUDED.uploaded_by_id,
                        updated_at      = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true
    )
    int upsert(
            @Param("agreementId")   Long   agreementId,
            @Param("documentType")  String documentType,
            @Param("fileKey")       String fileKey,
            @Param("contentType")   String contentType,
            @Param("originalName")  String originalName,
            @Param("fileSizeBytes") Long   fileSizeBytes,
            @Param("uploadedById")  Long   uploadedById
    );
}
