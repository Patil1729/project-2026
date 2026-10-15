package com.party.committee.service;

import com.party.committee.dto.CommitteePositionResponse;

import java.util.List;

public interface CommitteePositionService {

    List<CommitteePositionResponse> getPositionsByCommitteeId(Long committeeId);
    CommitteePositionResponse getPositionById(Long id);
    List<CommitteePositionResponse> getVacantPositionsByCommitteeId(Long committeeId);
    CommitteePositionResponse activatePosition(Long id);
    CommitteePositionResponse deactivatePosition(Long id);

}
