package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.PlayerSummaryService;
import com.youngstersclub.app.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private PlayerSummaryService playerSummaryService;

    @InjectMocks
    private UserController userController;

    @Test
    void searchUsersSkipsTinyQueries() {
        List<UserSearchResultDto> response = userController.searchUsers("pr");

        assertTrue(response.isEmpty());
        verify(userRepository, never()).searchActiveUserSummaries(any(), any(), any());
    }

    @Test
    void searchUsersDelegatesLightweightSummarySearchForValidQuery() {
        List<UserSearchResultDto> expected = List.of(
                new UserSearchResultDto(15, "Pragyesh Yadav", "pragyesh.yadav@gmail.com",
                        "105912950685681442825", null, "9876543210", true, "CUSTOMER"));
        when(userRepository.searchActiveUserSummaries(
                eq("prag"),
                eq(""),
                eq(PageRequest.of(0, 10))))
                .thenReturn(expected);

        List<UserSearchResultDto> response = userController.searchUsers("prag");

        assertSame(expected, response);
    }
}
