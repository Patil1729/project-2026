package com.party.user.service;

import com.party.user.dto.UserProfileRequest;
import com.party.user.dto.UserProfileResponse;

public interface UserProfileService {

    UserProfileResponse createProfile(Long userId, UserProfileRequest request);

    UserProfileResponse getProfileByUserId(Long userId);

    UserProfileResponse updateProfile(Long userId, UserProfileRequest request);
}
