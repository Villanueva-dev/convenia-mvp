package com.uniremington.api.convenia.model.dto;

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
