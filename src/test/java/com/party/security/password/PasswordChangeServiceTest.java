package com.party.security.password;

import com.party.security.password.dto.ChangePasswordRequest;
import com.party.security.password.service.PasswordChangeService;
import com.party.user.entity.AppUser;
import com.party.user.exception.InvalidUserRequestException;
import com.party.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordChangeServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final PasswordChangeService service = new PasswordChangeService(users, encoder);
    private AppUser user;

    @BeforeEach void setup() {
        user = AppUser.builder().id(7L).enabled(true).password(encoder.encode("OldPassword123!"))
                .passwordChangeRequired(true).build();
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
    }
    @Test void savesEncodedPasswordForLockedAuthenticatedUser() {
        service.change(7L, new ChangePasswordRequest("OldPassword123!", "NewPassword123!", "NewPassword123!"));
        assertTrue(encoder.matches("NewPassword123!", user.getPassword()));
        assertFalse(encoder.matches("OldPassword123!", user.getPassword()));
        assertFalse(user.isPasswordChangeRequired());
        verify(users).findByIdForUpdate(7L); verify(users).saveAndFlush(user);
    }
    @Test void wrongCurrentPasswordDoesNotChangeAccount() {
        String old = user.getPassword();
        assertThrows(InvalidUserRequestException.class, () -> service.change(7L,
                new ChangePasswordRequest("IncorrectPassword", "NewPassword123!", "NewPassword123!")));
        assertEquals(old, user.getPassword()); verify(users, never()).saveAndFlush(any());
    }
    @Test void preventsPasswordReuseAndDisabledAccountChanges() {
        assertThrows(InvalidUserRequestException.class, () -> service.change(7L,
                new ChangePasswordRequest("OldPassword123!", "OldPassword123!", "OldPassword123!")));
        user.setEnabled(false);
        assertThrows(InvalidUserRequestException.class, () -> service.change(7L,
                new ChangePasswordRequest("OldPassword123!", "NewPassword123!", "NewPassword123!")));
        verify(users, never()).saveAndFlush(any());
    }
    @Test void rejectsMismatchAndUnicodeByteOverflowBeforeWriting() {
        assertThrows(InvalidUserRequestException.class, () -> service.change(7L,
                new ChangePasswordRequest("OldPassword123!", "NewPassword123!", "OtherPassword")));
        assertThrows(InvalidUserRequestException.class, () -> service.change(7L,
                new ChangePasswordRequest("OldPassword123!", "अ".repeat(25), "अ".repeat(25))));
        verify(users, never()).saveAndFlush(any());
    }
}
