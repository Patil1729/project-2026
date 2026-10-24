package com.party.committee.service;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.entity.Committee;
import com.party.committee.entity.CommitteePosition;
import com.party.committee.entity.CommitteePositionType;
import com.party.committee.entity.CommitteeType;
import com.party.committee.exception.CommitteeInUseException;
import com.party.committee.exception.CommitteeNotFoundException;
import com.party.committee.exception.DuplicateCommitteeException;
import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.committee.repository.CommitteePositionRepository;
import com.party.committee.repository.CommitteeRepository;
import com.party.organization.entity.OrganizationType;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.InvalidOrganizationHierarchyException;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
@RequiredArgsConstructor
public class CommitteeServiceImpl implements CommitteeService {

    private final CommitteeRepository committeeRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final CommitteePositionRepository committeePositionRepository;
    private final AdvocateAssignmentRepository advocateAssignmentRepository;
    private final RegistrationApplicationRepository registrationApplicationRepository;


    @Override
    public CommitteeResponse createCommittee(CommitteeRequest request) {

        String code = request.getCode().trim().toUpperCase(Locale.ROOT);

       // 1. Duplicate code
        if(committeeRepository.existsByCode(code)){
            throw new DuplicateCommitteeException("Committee with code '" + code + "' already exists");
        }


        //2. OrganizationUnit existOrNot
        OrganizationUnit organizationUnit = organizationUnitRepository.findByIdForUpdate(request.getOrganizationUnitId())
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + request.getOrganizationUnitId() + " not found"));



        //3. Selected OrganizationUnit activeOrNot
        validateActiveOrganizationHierarchy(organizationUnit);

//        if(!Boolean.TRUE.equals(organizationUnit.getActive())){
//            throw new InvalidOrganizationHierarchyException(
//                    "Selected organization unit with id " + request.getOrganizationUnitId() + " is not active");
//        }


        //4.committee type validation
//        if(request.getCommitteeType().getOrganizationType() != organizationUnit.getType()){
//            throw new InvalidOrganizationHierarchyException(
//                    "Committee type " + request.getCommitteeType() + " does not match the organization unit type " + organizationUnit.getType());
//        }
        validateCommitteeType(request.getCommitteeType(), organizationUnit);

        //5.Only one committee per organizationUnit
        if(committeeRepository.existsByOrganizationUnitId(organizationUnit.getId())){
            throw new DuplicateCommitteeException(
                    "A committee already exists for the organization unit with id " + organizationUnit.getId());
        }

        // Validate the state VP responsibility divisions before inserting anything.
        List<OrganizationUnit> stateDivisions = findStateResponsibilityDivisions(
                request.getCommitteeType(), organizationUnit);

        Committee toBeSavedCommittee = Committee.builder()
                .name(request.getName())
                .code(code)
                .committeeType(request.getCommitteeType())
                .organizationUnit(organizationUnit)
                .active(true)
                .build();

        Committee savedCommittee ;


        try {
            savedCommittee = committeeRepository.saveAndFlush(toBeSavedCommittee);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCommitteeException("Committee code or organization unit already in use");
        }

        createDefaultPositions(savedCommittee, stateDivisions);

        return mapToDto(savedCommittee);
    }

    private void createDefaultPositions(
            Committee committee, List<OrganizationUnit> stateDivisions) {

        List<CommitteePosition> positions = new ArrayList<>();

        for (Map.Entry<CommitteePositionType, Integer> entry
                : committee.getCommitteeType().getPositionTemplate().entrySet()) {

            for (int number = 1; number <= entry.getValue(); number++) {
                OrganizationUnit responsibilityUnit = null;

                if (committee.getCommitteeType() == CommitteeType.STATE_COMMITTEE
                        && entry.getKey() == CommitteePositionType.VICE_PRESIDENT) {
                    responsibilityUnit = stateDivisions.get(number - 1);
                }

                positions.add(CommitteePosition.builder()
                        .committee(committee)
                        .positionType(entry.getKey())
                        .positionNumber(number)
                        .responsibilityUnit(responsibilityUnit)
                        .active(true)
                        .build());
            }
        }

        committee.getPositions().addAll(positions);
        committeePositionRepository.saveAllAndFlush(positions);
    }

    private List<OrganizationUnit> findStateResponsibilityDivisions(
            CommitteeType type,
            OrganizationUnit unit) {

        if (type != CommitteeType.STATE_COMMITTEE) {
            return List.of();
        }

        int requiredDivisions = type.getPositionTemplate()
                .get(CommitteePositionType.VICE_PRESIDENT);

        // Read and lock the currently active divisions.
        List<OrganizationUnit> divisions = organizationUnitRepository
                .findActiveDivisionsForUpdate(
                        unit.getId(),
                        OrganizationType.DIVISION);

        // Validate the count after acquiring the locks.
        if (divisions.size() != requiredDivisions) {
            throw new InvalidOrganizationHierarchyException(
                    "A state committee requires exactly "
                            + requiredDivisions
                            + " active divisions under its state "
                            + "for vice-president seats; found "
                            + divisions.size());
        }

        return divisions;
    }


    @Override
    @Transactional(readOnly = true)
    public List<CommitteeResponse> getAllCommittees(){

        return committeeRepository.findAll()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CommitteeResponse getCommitteeById(Long id){
        return committeeRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new CommitteeNotFoundException("Committee with id " + id + " not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public CommitteeResponse getCommitteeByOrganizationUnitId(
            Long organizationUnitId) {

        // 1. Check whether the organization unit exists.
        if (!organizationUnitRepository.existsById(organizationUnitId)) {
            throw new OrganizationUnitNotFoundException(
                    "Organization unit with id "
                            + organizationUnitId + " not found");
        }

        // 2. Find its committee and map it to the response.
        return committeeRepository
                .findByOrganizationUnitId(organizationUnitId)
                .map(this::mapToDto)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "No committee found for organization unit with id "
                                + organizationUnitId));
    }

    @Override
    @Transactional
    public CommitteeResponse updateCommittee(
            Long id, CommitteeRequest request) {

        // 1. Find the existing committee.
        Committee committee = committeeRepository.findById(id)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "Committee with id " + id + " not found"));

        OrganizationUnit unit = committee.getOrganizationUnit();

        // 2. committees structure.
        if (request.getCommitteeType() != committee.getCommitteeType()
                || !unit.getId().equals(request.getOrganizationUnitId())) {

            throw new InvalidOrganizationHierarchyException(
                    "Committee type and organization unit cannot be "
                            + "changed through this update");
        }

        // 3. Normalize the code and exclude this committee from the check.
        String code = request.getCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        validateUniqueCode(code, id);

        // 4. Confirm the type still matches its organization unit.
        validateCommitteeType(request.getCommitteeType(), unit);

        // 5. Update the editable fields.
        committee.setName(request.getName().trim());
        committee.setCode(code);

        // 6. Save and flush before mapping the response.
        Committee updatedCommittee;

        try {
             updatedCommittee =
                    committeeRepository.saveAndFlush(committee);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateCommitteeException("Committee code or organization unit already in use");
        }
        return mapToDto(updatedCommittee);
    }

    @Override
    public void deactivateCommittee(Long id) {

        Committee committee = committeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "Committee with id " + id + " not found"));

        // Repeating a successful deactivation is harmless.
        if (!Boolean.TRUE.equals(committee.getActive())) {
            return;
        }

        if (advocateAssignmentRepository
                .existsByCommitteePosition_Committee_IdAndActiveTrue(id)) {
            throw new CommitteeInUseException(
                    "Cannot deactivate committee: active advocate appointments exist. "
                            + "End those appointments first.");
        }

        if (registrationApplicationRepository
                .existsByRequestedPosition_Committee_IdAndStatusIn(id,PENDING_STATUSES )) {
            throw new CommitteeInUseException(
                    "Cannot deactivate committee: pending registration applications exist. "
                            + "Resolve those applications first.");
        }

        committee.setActive(false);
        // @Transactional persists the change at commit.
    }

    private static final List<RegistrationStatus> PENDING_STATUSES = List.of(
            RegistrationStatus.AWAITING_RECOMMENDATION,
            RegistrationStatus.UNDER_REVIEW);

    @Override
    public void activateCommittee(Long id) {

        Committee committee = committeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CommitteeNotFoundException(
                        "Committee with id " + id + " not found"));

        OrganizationUnit unit = organizationUnitRepository.findByIdForUpdate(
                        committee.getOrganizationUnit().getId())
                .orElseThrow(() -> new OrganizationUnitNotFoundException("Organization unit not found"));



        validateCommitteeType(committee.getCommitteeType(), unit);
        validateActiveOrganizationHierarchy(unit);

        committee.setActive(true);
        // Appointments and position flags are managed by their own services.
    }


    private void validateCommitteeType(CommitteeType committeeType, OrganizationUnit unit) {
        if (committeeType == null || unit == null || unit.getType() == null
                || committeeType.getOrganizationType() != unit.getType()) {
            throw new InvalidOrganizationHierarchyException(
                    "Committee type " + committeeType
                            + " does not match the organization unit type "
                            + (unit == null ? null : unit.getType()));
        }
    }

    private void validateUniqueCode(String code, Long excludedId) {

        if (committeeRepository.existsByCodeAndIdNot(code, excludedId)) {
            throw new DuplicateCommitteeException(
                    "Committee with code '" + code + "' already exists");
        }
    }

    private void validateActiveOrganizationHierarchy(OrganizationUnit unit) {
        Set<Long> visitedIds = new HashSet<>();
        OrganizationUnit current = unit;

        while (current != null) {
            if (!visitedIds.add(current.getId())) {
                throw new InvalidOrganizationHierarchyException(
                        "Organization hierarchy contains a cycle at unit " + current.getId());
            }

            if (!Boolean.TRUE.equals(current.getActive())) {
                throw new InvalidOrganizationHierarchyException(
                        "Organization unit '" + current.getName() + "' with id "
                                + current.getId() + " is inactive. Activate it first.");
            }

            current = current.getParent();
        }
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
