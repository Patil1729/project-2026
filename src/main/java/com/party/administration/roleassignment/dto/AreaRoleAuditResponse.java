package com.party.administration.roleassignment.dto;

import java.time.Instant;

public record AreaRoleAuditResponse(Long id, String action, Instant eventAt, String actorUsername, String reason) { }
