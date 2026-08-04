package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.PlayerSummaryDto;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.PlayerSummaryService;
import com.youngstersclub.app.service.UserService;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerPlayerSummaryTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private PlayerSummaryService playerSummaryService;

    @Test
    void getPlayerSummaryUsesActorHeaderAndReturnsPage() {
        UserController controller = new UserController(
                userRepository,
                userService,
                organizationContextService,
                playerSummaryService);

        Page<PlayerSummaryDto> expected = new PageImpl<>(List.of(
                new PlayerSummaryDto(101, "Rahul", "rahul@test.com", 12L, BigDecimal.valueOf(350))));
        when(playerSummaryService.getPlayerSummaries(PageRequest.of(0, 20), "manager@test.com"))
                .thenReturn(expected);

        ResponseEntity<Page<PlayerSummaryDto>> response = controller.getPlayerSummary("manager@test.com", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertSame(expected, response.getBody());
        verify(playerSummaryService).getPlayerSummaries(PageRequest.of(0, 20), "manager@test.com");
    }
}
