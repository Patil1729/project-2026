package com.party.committee.service;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.entity.Committee;
import com.party.committee.exception.DuplicateCommitteeException;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.InvalidOrganizationHierarchyException;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.repository.OrganizationUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CommitteeServiceImpl implements CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

//    CommitteeServiceImpl(CommitteeRepository committeeRepository,OrganizationUnitRepository organizationUnitRepository) {
//        this.committeeRepository = committeeRepository;
//        this.organizationUnitRepository = organizationUnitRepository;
//    }

    @Override
    public CommitteeResponse createCommittee(CommitteeRequest request) {

        String code = request.getCode().trim().toUpperCase();

        //1. Duplicate code
        if(committeeRepository.existsByCode(code)){
            throw new DuplicateCommitteeException("Committee with code '" + code + "' already exists");
        }

        //2. OrganizationUnit existOrNot
        OrganizationUnit organizationUnit = organizationUnitRepository.findById(request.getOrganizationUnitId())
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + request.getOrganizationUnitId() + " not found"));

        //3. Selected OrganizationUnit activeOrNot
        if(!Boolean.TRUE.equals(organizationUnit.getActive())){
            throw new InvalidOrganizationHierarchyException(
                    "Selected organization unit with id " + request.getOrganizationUnitId() + " is not active");
        }


        //4.committee type validation
        if(request.getCommitteeType().getOrganizationType() != organizationUnit.getType()){
            throw new InvalidOrganizationHierarchyException(
                    "Committee type " + request.getCommitteeType() + " does not match the organization unit type " + organizationUnit.getType());
        }

        //5.Only one committee per organizationUnit
        if(committeeRepository.existsByOrganizationUnitId(organizationUnit.getId())){
            throw new DuplicateCommitteeException(
                    "A committee already exists for the organization unit with id " + organizationUnit.getId());
        }

        Committee toBeSavedCommittee = Committee.builder()
                .name(request.getName())
                .code(code)
                .committeeType(request.getCommitteeType())
                .organizationUnit(organizationUnit)
                //.active(true)
                .build();

        Committee savedCommittee ;

        try {
            savedCommittee = committeeRepository.saveAndFlush(toBeSavedCommittee);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCommitteeException("Committee code or organization unit already in use");
        }

        return mapToDto(savedCommittee);
    }


    private CommitteeResponse mapToDto(Committee committee) {

        OrganizationUnit unit = committee.getOrganizationUnit();
        return CommitteeResponse.builder()
                .id(committee.getId())
                .name(committee.getName())
                .code(committee.getCode())
                .committeeType(committee.getCommitteeType())
                .organizationUnitId( unit.getId())
                .organizationUnitName(unit.getName())
                .active(committee.getActive())
                .createdAt(committee.getCreatedAt())
                .updatedAt(committee.getUpdatedAt())
                .build();
    }
}
