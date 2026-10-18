package com.party.electoral.service;

import com.party.electoral.dto.AssemblyConstituencyRequest;
import com.party.electoral.dto.AssemblyConstituencyResponse;

import java.util.List;

public interface AssemblyConstituencyService {

    AssemblyConstituencyResponse createConstituency(AssemblyConstituencyRequest request);

    AssemblyConstituencyResponse getConstituencyById(Long id);

    List<AssemblyConstituencyResponse> getActiveConstituencies(String stateCode);
}
