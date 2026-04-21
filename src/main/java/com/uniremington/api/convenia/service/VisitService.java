package com.uniremington.api.convenia.service;

import com.uniremington.api.convenia.model.dto.CreateVisitRequest;
import com.uniremington.api.convenia.model.dto.VisitResponse;
import com.uniremington.api.convenia.model.vo.JwtUser;

import java.util.List;

public interface VisitService {

    List<VisitResponse> listVisits(Long agreementId, JwtUser currentUser);

    VisitResponse registerVisit(Long agreementId, CreateVisitRequest request, JwtUser currentUser);
}
