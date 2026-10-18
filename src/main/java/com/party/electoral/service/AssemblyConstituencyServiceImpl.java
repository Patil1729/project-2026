package com.party.electoral.service;

import com.party.electoral.dto.AssemblyConstituencyRequest;
import com.party.electoral.dto.AssemblyConstituencyResponse;
import com.party.electoral.entity.AssemblyConstituency;
import com.party.electoral.exception.AssemblyConstituencyNotFoundException;
import com.party.electoral.exception.DuplicateAssemblyConstituencyException;
import com.party.electoral.repository.AssemblyConstituencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class AssemblyConstituencyServiceImpl implements AssemblyConstituencyService {

    private final AssemblyConstituencyRepository constituencyRepository;

    @Override
    public AssemblyConstituencyResponse createConstituency(
            AssemblyConstituencyRequest request) {

        String stateCode = request.getStateCode().strip().toUpperCase(Locale.ROOT);

        if (constituencyRepository.existsByStateCodeAndConstituencyNumber(
                stateCode, request.getConstituencyNumber())) {
            throw new DuplicateAssemblyConstituencyException(
                    "Constituency number " + request.getConstituencyNumber()
                            + " already exists for state " + stateCode);
        }

        AssemblyConstituency constituency = AssemblyConstituency.builder()
                .stateCode(stateCode)
                .name(request.getName().strip())
                .constituencyNumber(request.getConstituencyNumber())
                .active(true)
                .build();

        // uk_assembly_state_number also protects simultaneous duplicate inserts.
        // Let the existing integrity handler return 409 if that constraint wins a race.
        AssemblyConstituency saved = constituencyRepository.saveAndFlush(constituency);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssemblyConstituencyResponse getConstituencyById(Long id) {
        return constituencyRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new AssemblyConstituencyNotFoundException(
                        "Assembly constituency with id " + id + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssemblyConstituencyResponse> getActiveConstituencies(String stateCode) {
        List<AssemblyConstituency> constituencies;

        if (stateCode == null || stateCode.isBlank()) {
            constituencies = constituencyRepository
                    .findByActiveTrueOrderByStateCodeAscConstituencyNumberAsc();
        } else {
            String normalizedCode = stateCode.strip().toUpperCase(Locale.ROOT);
            constituencies = constituencyRepository
                    .findByStateCodeAndActiveTrueOrderByConstituencyNumberAsc(normalizedCode);
        }

        return constituencies.stream()
                .map(this::mapToResponse)
                .toList();
    }

    private AssemblyConstituencyResponse mapToResponse(AssemblyConstituency constituency) {
        return AssemblyConstituencyResponse.builder()
                .id(constituency.getId())
                .stateCode(constituency.getStateCode())
                .name(constituency.getName())
                .constituencyNumber(constituency.getConstituencyNumber())
                .active(constituency.isActive())
                .createdAt(constituency.getCreatedAt())
                .updatedAt(constituency.getUpdatedAt())
                .build();
    }
}
