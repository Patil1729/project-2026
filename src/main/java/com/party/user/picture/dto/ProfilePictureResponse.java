package com.party.user.picture.dto;
import java.time.Instant;
public record ProfilePictureResponse(Long userId, boolean hasProfilePicture, String profilePictureVersion,
        Long sizeBytes, Integer width, Integer height, Instant uploadedAt) { }
