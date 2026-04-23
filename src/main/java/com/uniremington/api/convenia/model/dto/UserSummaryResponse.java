package com.uniremington.api.convenia.model.dto;

import com.uniremington.api.convenia.model.entity.User;

public record UserSummaryResponse(Long id, String fullName, String email, String role) {
    public static UserSummaryResponse from(User u) {
        return new UserSummaryResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name());
    }
}
