package com.party.committee.appointment.dto;

/** Scalar lookup before acquiring user -> committee -> unit -> seat -> assignment locks. */
public record AppointmentReference(Long advocateUserId, Long positionId, Long committeeId) { }
