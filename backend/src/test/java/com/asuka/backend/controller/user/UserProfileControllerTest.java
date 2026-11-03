package com.asuka.backend.controller.user;

import com.asuka.backend.common.context.UserContext;
import com.asuka.backend.common.exception.BusinessException;
import com.asuka.backend.pojo.dto.UserProfileUpdateDTO;
import com.asuka.backend.pojo.dto.UserUpdateDTO;
import com.asuka.backend.pojo.vo.UserVO;
import com.asuka.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserProfileControllerTest {
    private final UserService users = mock(UserService.class);
    private final UserProfileController controller = new UserProfileController(users);

    @AfterEach void clearContext() { UserContext.clear(); }

    @Test void editsOnlyAuthenticatedUserAndNeverUpdatesRoleOrStatus() {
        UserContext.setUserId(42L);
        var profile = new UserProfileUpdateDTO();
        profile.setNickname("New name");
        profile.setEmail("user@example.com");
        when(users.getDetail(42L)).thenReturn(new UserVO());
        controller.update(profile);
        var captor = ArgumentCaptor.forClass(UserUpdateDTO.class);
        verify(users).updateUser(eq(42L), captor.capture());
        assertEquals("New name", captor.getValue().getNickname());
        assertEquals("user@example.com", captor.getValue().getEmail());
        assertNull(captor.getValue().getRole());
        assertNull(captor.getValue().getStatus());
        verify(users).getDetail(42L);
    }

    @Test void refusesRequestsWithoutAuthenticatedContext() {
        assertThrows(BusinessException.class, () -> controller.get());
        assertThrows(BusinessException.class, () -> controller.update(new UserProfileUpdateDTO()));
        verifyNoInteractions(users);
    }
}
