package com.party.user.service;

import com.party.electoral.entity.AssemblyConstituency;
import com.party.electoral.repository.AssemblyConstituencyRepository;
import com.party.exception.ResourceInUseException;
import com.party.exception.ResourceNotFoundException;
import com.party.user.dto.AddressRequest;
import com.party.user.dto.AddressResponse;
import com.party.user.dto.UserProfileRequest;
import com.party.user.dto.UserProfileResponse;
import com.party.user.entity.Address;
import com.party.user.entity.AppUser;
import com.party.user.entity.UserProfile;
import com.party.user.exception.*;
import com.party.user.repository.AddressRepository;
import com.party.user.repository.UserProfileRepository;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;


@Service
@Transactional
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final AssemblyConstituencyRepository constituencyRepository;

    @Override
    public UserProfileResponse createProfile(Long userId, UserProfileRequest request) {

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        validateEnabledUser(user);

        if (profileRepository.existsByUser_Id(userId)) {
            throw new DuplicateUserProfileException(
                    "A profile already exists for user with id " + userId);
        }

        AssemblyConstituency constituency = findActiveConstituency(
                request.getAssemblyConstituencyId());

        // UserProfile.address has no cascade: persist its new address explicitly.
        Address address = new Address();
        updateAddress(address, request.getAddress());
        Address savedAddress = addressRepository.save(address);

        UserProfile profile = UserProfile.builder()
                .user(user)
                .address(savedAddress)
                .build();

        updateProfileFields(profile, request, constituency);

        // Flush here so constraint errors occur before returning the response.
        // The database's uk_profile_user also prevents simultaneous duplicates.
        UserProfile savedProfile = profileRepository.saveAndFlush(profile);
        user.setProfile(savedProfile);

        return mapToResponse(savedProfile);

    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByUserId(Long userId) {
        return mapToResponse(findProfile(userId));
    }

    @Override
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        UserProfile profile = findProfile(userId);
        AppUser user = profile.getUser();
        validateEnabledUser(user);

        AssemblyConstituency constituency = findActiveConstituency(
                request.getAssemblyConstituencyId());

        // Modify the same managed address; do not create another address row.
        updateAddress(profile.getAddress(), request.getAddress());
        updateProfileFields(profile, request, constituency);

        // Flush also persists the managed address's changes in this transaction.
        return mapToResponse(profileRepository.saveAndFlush(profile));
    }

    private UserProfile findProfile(Long userId) {
        return profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new UserProfileNotFoundException(
                        "Profile for user with id " + userId + " not found"));
    }


    private void updateProfileFields(UserProfile profile, UserProfileRequest request,
                                     AssemblyConstituency constituency) {
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setEducation(request.getEducation().strip());
        profile.setAssemblyConstituency(constituency);
        profile.setVoterIdNumber(request.getVoterIdNumber().strip().toUpperCase(Locale.ROOT));
        profile.setVoterListPartNumber(request.getVoterListPartNumber());
        profile.setAadhaarLastFour(request.getAadhaarLastFour());
        // aadhaarEncrypted belongs to a separate encryption workflow.
    }


    private void validateEnabledUser(AppUser user) {
        if (!user.isEnabled()) {
            throw new UserInUseException("User with id " + user.getId() + " is not enabled and cannot have a profile.");
        }
    }

    private AssemblyConstituency findActiveConstituency(Long id) {
        AssemblyConstituency constituency = constituencyRepository.findById(id)
                .orElseThrow(() -> new AssemblyConstituencyNotFoundException("Assembly constituency not found with id: " + id));

        if (!constituency.isActive()) {
            throw new AssemblyConstituencyInUseException("Assembly constituency with id " + id + " is not active and cannot be assigned.");
        }

        return constituency;
    }


    private void updateAddress(Address address, AddressRequest request) {
        address.setHouseOrFlatNumber(stripToNull(request.houseOrFlatNumber()));
        address.setBuildingName(stripToNull(request.buildingName()));
        address.setStreet(stripToNull(request.street()));
        address.setArea(stripToNull(request.area()));
        address.setLandmark(stripToNull(request.landmark()));
        address.setVillage(stripToNull(request.village()));
        address.setCity(stripToNull(request.city()));
        address.setTaluka(request.taluka().strip());
        address.setDistrict(request.district().strip());
        address.setState(request.state().strip());
        address.setCountry(request.country().strip());
        address.setPinCode(request.pinCode().strip());
    }

    private String stripToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private UserProfileResponse mapToResponse(UserProfile profile) {
        AssemblyConstituency constituency = profile.getAssemblyConstituency();

        return UserProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUser().getId())
                .fullName(profile.getUser().getFullName())
                .dateOfBirth(profile.getDateOfBirth())
                .age(profile.getAge())
                .education(profile.getEducation())
                .address(mapAddressToResponse(profile.getAddress()))
                .assemblyConstituencyId(constituency.getId())
                .assemblyConstituencyName(constituency.getName())
                .assemblyConstituencyNumber(constituency.getConstituencyNumber())
                .voterIdNumber(profile.getVoterIdNumber())
                .voterListPartNumber(profile.getVoterListPartNumber())
                .aadhaarLastFour(profile.getAadhaarLastFour())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private AddressResponse mapAddressToResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getHouseOrFlatNumber(),
                address.getBuildingName(),
                address.getStreet(),
                address.getArea(),
                address.getLandmark(),
                address.getVillage(),
                address.getCity(),
                address.getTaluka(),
                address.getDistrict(),
                address.getState(),
                address.getCountry(),
                address.getPinCode(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
