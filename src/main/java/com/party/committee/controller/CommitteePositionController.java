package com.party.committee.controller;

import com.party.committee.dto.CommitteePositionResponse;
import com.party.committee.service.CommitteePositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/committee-positions")
@RequiredArgsConstructor
public class CommitteePositionController {

        private final CommitteePositionService positionService;


        @GetMapping("/committees/{committeeId}/positions")
        public ResponseEntity<List<CommitteePositionResponse>> getPositions(
                @PathVariable("committeeId") Long committeeId) {
            return ResponseEntity.ok(positionService.getPositionsByCommitteeId(committeeId));
        }

        @GetMapping("/committee-positions/{id}")
        public ResponseEntity<CommitteePositionResponse> getPosition(
                @PathVariable("id") Long id) {
            return ResponseEntity.ok(positionService.getPositionById(id));
        }

        @GetMapping("/committees/{committeeId}/positions/vacant")
        public ResponseEntity<List<CommitteePositionResponse>> getVacantPositions(
                @PathVariable("committeeId") Long committeeId) {
            return ResponseEntity.ok(positionService.getVacantPositionsByCommitteeId(committeeId));
        }

        @PatchMapping("/committee-positions/{id}/activate")
        public ResponseEntity<CommitteePositionResponse> activatePosition(
                @PathVariable("id") Long id) {
            return ResponseEntity.ok(positionService.activatePosition(id));
        }

        @PatchMapping("/committee-positions/{id}/deactivate")
        public ResponseEntity<CommitteePositionResponse> deactivatePosition(
                @PathVariable("id") Long id) {
            return ResponseEntity.ok(positionService.deactivatePosition(id));
        }
    }
