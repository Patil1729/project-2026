package com.party.user.picture.service;

import com.party.administration.access.service.AreaAccessService;
import com.party.registration.common.exception.RegistrationException;
import com.party.user.entity.AppUser;
import com.party.user.picture.dto.ProfilePictureResponse;
import com.party.user.picture.storage.*;
import com.party.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.NoSuchFileException;
import java.time.Clock;
import java.util.Objects;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class ProfilePictureService {
    private static final Logger log = LoggerFactory.getLogger(ProfilePictureService.class);
    private final UserRepository users;
    private final AreaAccessService access;
    private final ProfilePictureStorage storage;
    private final ProfileImageNormalizer normalizer;
    private final Clock clock;
    public record Content(InputStream input, long size) { }
    public ProfilePictureResponse get(Long userId) { access.requireReadUser(userId); return response(find(userId)); }
    public Content content(Long userId) {
        access.requireReadUser(userId); var user = find(userId);
        if (user.getProfilePictureKey() == null) throw missing();
        try { return new Content(storage.open(user.getProfilePictureKey()), user.getProfilePictureSizeBytes()); }
        catch (NoSuchFileException exception) { throw missing(); }
        catch (IOException exception) { throw storageFailure(); }
    }
    @Transactional
    public ProfilePictureResponse upload(Long userId, MultipartFile file) {
        requireWrite(userId);
        var image = normalizer.normalize(file, storage.maxBytes());
        var user = users.findByIdForUpdate(userId).orElseThrow(this::missingUser);
        if (!user.isEnabled()) throw new RegistrationException(HttpStatus.CONFLICT, "ACCOUNT_DISABLED", "Enable this account before changing its picture.");
        String previous = user.getProfilePictureKey();
        String created = null;
        try {
            var saved = storage.store(userId, image.bytes()); created = saved.storageKey();
            registerCleanup(saved.storageKey(), previous);
            user.setProfilePictureKey(saved.storageKey()); user.setProfilePictureSha256(saved.sha256());
            user.setProfilePictureSizeBytes(saved.sizeBytes()); user.setProfilePictureWidth(image.width()); user.setProfilePictureHeight(image.height());
            user.setProfilePictureUploadedAt(clock.instant());
            users.saveAndFlush(user);
            if (!TransactionSynchronizationManager.isSynchronizationActive() && previous != null) cleanup(previous);
            return response(user);
        } catch (IOException exception) { if (created != null) cleanup(created); throw storageFailure(); }
        catch (RuntimeException exception) { if (created != null) cleanup(created); throw exception; }
    }
    @Transactional
    public void remove(Long userId) {
        requireWrite(userId); var user = users.findByIdForUpdate(userId).orElseThrow(this::missingUser);
        String previous = user.getProfilePictureKey();
        user.setProfilePictureKey(null); user.setProfilePictureSha256(null); user.setProfilePictureSizeBytes(null);
        user.setProfilePictureWidth(null); user.setProfilePictureHeight(null); user.setProfilePictureUploadedAt(null);
        users.saveAndFlush(user);
        if (previous != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { public void afterCommit() { cleanup(previous); } });
            else cleanup(previous);
        }
    }
    private void registerCleanup(String created, String previous) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            public void afterCommit() { if (previous != null) cleanup(previous); }
            public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) cleanup(created); }
        });
    }
    private void requireWrite(Long userId) {
        if (!access.isPartyHead() && !Objects.equals(access.actorId(), userId)) throw new RegistrationException(HttpStatus.FORBIDDEN, "PROFILE_PICTURE_ACCESS_DENIED", "You can change only your own picture. PARTY_HEAD can manage other member pictures.");
    }
    private AppUser find(Long id) { return users.findById(id).orElseThrow(this::missingUser); }
    private ProfilePictureResponse response(AppUser user) { return new ProfilePictureResponse(user.getId(), user.getProfilePictureKey() != null, user.getProfilePictureSha256(), user.getProfilePictureSizeBytes(), user.getProfilePictureWidth(), user.getProfilePictureHeight(), user.getProfilePictureUploadedAt()); }
    private void cleanup(String key) { try { storage.delete(key); } catch (IOException | RuntimeException exception) { log.warn("Could not clean up a stored profile picture; retained for maintenance", exception); } }
    private RegistrationException missing() { return new RegistrationException(HttpStatus.NOT_FOUND, "PROFILE_PICTURE_NOT_FOUND", "No profile picture is available."); }
    private RegistrationException missingUser() { return new RegistrationException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."); }
    private RegistrationException storageFailure() { return new RegistrationException(HttpStatus.INTERNAL_SERVER_ERROR, "PROFILE_PICTURE_STORAGE_FAILURE", "The profile picture could not be stored or loaded. Try again."); }
}
