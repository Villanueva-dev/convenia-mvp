package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.CreateUserRequest;
import com.uniremington.api.convenia.model.dto.UserSummaryResponse;
import com.uniremington.api.convenia.model.vo.JwtUser;

public interface UserService {

    UserSummaryResponse createManagedUser(CreateUserRequest request, JwtUser currentUser);
}
