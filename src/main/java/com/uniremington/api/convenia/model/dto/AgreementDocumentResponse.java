package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.DocumentType;

import java.time.LocalDateTime;

/**
 * Metadata for a single file uploaded against an agreement. The raw bytes
 * are served by {@code GET /agreements/{id}/documents/{type}}.
 *
 * <p>The internal R2 storage key is intentionally <strong>not</strong> exposed
 * here — clients should never reference keys directly, and returning them
 * would leak the bucket's naming pattern and enable enumeration.</p>
 *
 * @param id             Unique identifier of the document record.
 * @param documentType   Type of document (CV, NIT, ARL, …).
 * @param contentType    Declared MIME type at upload time.
 * @param originalName   Original filename at upload time.
 * @param fileSizeBytes  Size in bytes, if captured.
 * @param uploadedById   User who last uploaded the document.
 * @param uploadedAt     Timestamp of the last upload or replacement.
 */
public record AgreementDocumentResponse(
        Long id,
        DocumentType documentType,
        String contentType,
        String originalName,
        Long fileSizeBytes,
        Long uploadedById,
        LocalDateTime uploadedAt
) {}
