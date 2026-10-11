package com.party.committee.service;

import com.party.committee.dto.CommitteeRequest;
import com.party.committee.dto.CommitteeResponse;
import com.party.committee.entity.Committee;

public interface CommitteeService {

    CommitteeResponse createCommittee(CommitteeRequest request);
}
