package com.party.user.service;

import com.party.user.dto.UserRequest;
import com.party.user.dto.UserResponse;
import com.party.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public interface UserService {


    UserResponse createUser(@NotNull @Valid UserRequest request);

    UserResponse updateUser(@NotNull @Positive Long userId,
                            @NotNull @Valid UserUpdateRequest request);

    UserResponse deactivateUser(@NotNull @Positive Long userId);

    UserResponse getUserById(@NotNull @Positive Long userId);

    List<UserResponse> getAllUsers();
}