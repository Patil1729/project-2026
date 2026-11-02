package com.party.security.password.service;

import com.party.security.password.dto.ChangePasswordRequest;
import com.party.user.exception.InvalidUserRequestException;
import com.party.user.exception.UserNotFoundException;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @Transactional
    public void change(Long userId, ChangePasswordRequest request) {
        var next = request.newPassword();
        if (next == null || next.isBlank() || next.length() < 8 || next.getBytes(StandardCharsets.UTF_8).length > 72
                || !Objects.equals(next, request.confirmPassword())) {
            throw new InvalidUserRequestException("Use a matching new password of at least 8 characters and at most 72 UTF-8 bytes");
        }
        var user = users.findByIdForUpdate(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!user.isEnabled()) throw new InvalidUserRequestException("This account is disabled");
        if (request.currentPassword() == null || request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidUserRequestException("Current password is incorrect");
        }
        if (encoder.matches(next, user.getPassword())) {
            throw new InvalidUserRequestException("Choose a new password different from your current password");
        }
        user.setPassword(encoder.encode(next));
        user.setPasswordChangeRequired(false);
        users.saveAndFlush(user);
    }
}
