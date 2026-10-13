package com.party.committee.service;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.entity.Committee;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CommitteeService {

    CommitteeResponse createCommittee(CommitteeRequest request);


    List<CommitteeResponse> getAllCommittees();

    CommitteeResponse getCommitteeById(Long id);

    CommitteeResponse getCommitteeByOrganizationUnitId(Long organizationUnitId);

    CommitteeResponse updateCommittee(Long id ,CommitteeRequest request);

    void deactivateCommittee(Long id);

    void activateCommittee(Long id);
}
