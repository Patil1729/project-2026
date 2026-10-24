package com.party.user.service;

import com.party.committee.repository.AdvocateAssignmentRepository;
import com.party.organization.entity.OrganizationUnit;
import com.party.organization.exception.OrganizationUnitNotFoundException;
import com.party.organization.repository.OrganizationUnitRepository;
import com.party.registration.approval.entity.ApprovalStatus;
import com.party.registration.application.entity.RegistrationStatus;
import com.party.registration.approval.repository.ApprovalStepRepository;
import com.party.registration.application.repository.RegistrationApplicationRepository;
import com.party.user.dto.UserRequest;
import com.party.user.dto.UserResponse;
import com.party.user.dto.UserUpdateRequest;
import com.party.user.entity.AppUser;
import com.party.user.entity.Role;
import com.party.user.entity.RoleType;
import com.party.user.exception.DuplicateUserException;
import com.party.user.exception.InvalidUserRequestException;
import com.party.user.exception.UserInUseException;
import com.party.user.exception.UserNotFoundException;
import com.party.user.repository.RoleRepository;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Validated
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final AdvocateAssignmentRepository advocateAssignmentRepository;
    private final RegistrationApplicationRepository registrationApplicationRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final PasswordEncoder passwordEncoder;

    private static final List<RegistrationStatus> PENDING_APPLICATION_STATUSES = List.of(
            RegistrationStatus.AWAITING_RECOMMENDATION,
            RegistrationStatus.UNDER_REVIEW
    );

    private static final List<ApprovalStatus> OPEN_REVIEW_STATUSES = List.of(
            ApprovalStatus.PENDING,
            ApprovalStatus.OVERDUE);

    @Override
    public UserResponse createUser(UserRequest request) {

        String username = request.getUsername().strip().toLowerCase(Locale.ROOT);
        String mobileNumber = request.getMobile().strip();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("User with username '" + request.getUsername() + "' already exists"
            );
        }

        if(userRepository.existsByMobile(mobileNumber)){
            throw new DuplicateUserException("User with mobile number '" + mobileNumber + "' already exists"
            );
        }

        // BCrypt's input limit is measured in UTF-8 bytes, not Java characters.
        validatePasswordLength(request.getPassword());
        String passwordHash = passwordEncoder.encode(request.getPassword());

        Role applicantRole = roleRepository.findByName(RoleType.APPLICANT)
                .orElseThrow(() -> new IllegalStateException(
                        "APPLICANT role is missing. Run the role seed migration before creating users."));
        // Keep write-lock order consistent: organization unit, then user.
        OrganizationUnit homeUnit = resolveActiveHomeUnit(request.getOrganizationUnitId());

        AppUser user = AppUser.builder()
                .username(username)
                .password(passwordHash)
                .mobile(mobileNumber)
                .email(normalizeEmail(request.getEmail()))
                .fullName(request.getFullName().strip())
                .organizationUnit(homeUnit)
                .roles(new HashSet<>(Set.of(applicantRole)))
                .enabled(true)
                .passwordChangeRequired(false)
                .build();

        // Flush here so database uniqueness conflicts reach the existing 409 handler.
        AppUser saved = userRepository.saveAndFlush(user);
        return mapToResponse(saved);
    }

    @Override
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        OrganizationUnit homeUnit = resolveActiveHomeUnit(request.getOrganizationUnitId());
        AppUser user = findUserForUpdate(userId);
        String username = request.getUsername().strip().toLowerCase(Locale.ROOT);
        String mobile = request.getMobile().strip();

        if (userRepository.existsByUsernameAndIdNot(username, userId)) {
            throw new DuplicateUserException("User with username '" + username + "' already exists");
        }
        if (userRepository.existsByMobileAndIdNot(mobile, userId)) {
            throw new DuplicateUserException("User with this mobile number already exists");
        }

        user.setUsername(username);
        user.setFullName(request.getFullName().strip());
        user.setMobile(mobile);
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setOrganizationUnit(homeUnit);

        // Full update: a null home unit or blank/null email clears that field.
        // Password, roles and account flags have their own authorized operations.
        return mapToResponse(userRepository.saveAndFlush(user));
    }

    @Override
    public UserResponse deactivateUser(Long userId) {
        AppUser user = findUserForUpdate(userId);
        if (!user.isEnabled()) {
            return mapToResponse(user);
        }

        if (advocateAssignmentRepository.existsByAdvocate_IdAndActiveTrue(userId)) {
            throw new UserInUseException(
                    "Cannot deactivate user: end or transfer active advocate appointments first.");
        }
        if (registrationApplicationRepository.existsByApplicant_IdAndStatusIn(
                userId, PENDING_APPLICATION_STATUSES)) {
            throw new UserInUseException(
                    "Cannot deactivate user: resolve their pending registration applications first.");
        }
        if (approvalStepRepository.countOpenResponsibilities(userId, OPEN_REVIEW_STATUSES) > 0) {
            throw new UserInUseException(
                    "Cannot deactivate user: resolve or reassign pending review and escalation responsibilities first.");
        }

        user.setEnabled(false);
        return mapToResponse(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        AppUser user = userRepository.findWithDetailsById(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User with ID " + userId + " not found"));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidUserRequestException("Password must not exceed 72 UTF-8 bytes");
        }
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.strip();
    }

    private AppUser findUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException(
                        "User with ID " + userId + " not found"));
    }

    private OrganizationUnit resolveActiveHomeUnit(Long organizationUnitId) {
        if (organizationUnitId == null) {
            return null;
        }
        OrganizationUnit unit = organizationUnitRepository.findByIdForUpdate(organizationUnitId)
                .orElseThrow(() -> new OrganizationUnitNotFoundException(
                        "Organization unit with id " + organizationUnitId + " not found"));
        if (!Boolean.TRUE.equals(unit.getActive())) {
            throw new InvalidUserRequestException("Home organization unit must be active");
        }
        return unit;
    }

    private UserResponse mapToResponse(AppUser user) {
        OrganizationUnit homeUnit = user.getOrganizationUnit();
        Set<RoleType> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .enabled(user.isEnabled())
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .organizationUnitId(homeUnit == null ? null : homeUnit.getId())
                .organizationUnitName(homeUnit == null ? null : homeUnit.getName())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }


}
