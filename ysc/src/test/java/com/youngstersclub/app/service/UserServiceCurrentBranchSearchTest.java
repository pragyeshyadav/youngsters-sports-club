package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class UserServiceCurrentBranchSearchTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;
    @Mock private BranchRepository branchRepository;

    @InjectMocks private UserService userService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser actorMembership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(15);
        actor.setEmail("admin@test.com");
        actor.setRole(UserRole.ADMIN);
        actor.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Rewa");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        actorMembership = new OrganizationUser();
        actorMembership.setId(30L);
        actorMembership.setUser(actor);
        actorMembership.setOrganization(organization);
        actorMembership.setRole(UserRole.ADMIN);
        actorMembership.setBaseBranch(branch);
        actorMembership.setIsActive(true);
    }

    @Test
    void searchUsersForCurrentBranchSkipsTinyQueries() {
        List<UserSearchResultDto> response = userService.searchUsersForCurrentBranch("pr", "admin@test.com");

        assertTrue(response.isEmpty());
        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).searchActiveUserSummariesForOrganizationBranch(any(), any(), any(), any(), any());
    }

    @Test
    void searchUsersForCurrentBranchUsesResolvedOrganizationAndBranch() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext());
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));

        List<UserSearchResultDto> expected = List.of(
                new UserSearchResultDto(101, "Prince Singh", "prince@test.com", "gid", null, "9999999999", true, "CUSTOMER"));
        when(userRepository.searchActiveUserSummariesForOrganizationBranch(
                eq("prin"),
                eq(""),
                eq(PageRequest.of(0, 10)),
                eq(organization.getId()),
                eq(branch.getId())))
                .thenReturn(expected);

        List<UserSearchResultDto> response = userService.searchUsersForCurrentBranch("prin", "admin@test.com");

        assertSame(expected, response);
    }

    @Test
    void searchUsersForCurrentBranchRejectsUnauthorizedCurrentBranch() {
        actorMembership.setBaseBranch(null);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(buildContext());
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
        when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(actorMembership.getId(), branch.getId()))
                .thenReturn(false);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> userService.searchUsersForCurrentBranch("prince", "admin@test.com"));

        assertEquals("You do not have access to the current branch", exception.getMessage());
        verify(userRepository, never()).searchActiveUserSummariesForOrganizationBranch(any(), any(), any(), any(), any());
    }

    private OrganizationContextDto buildContext() {
        OrganizationContextDto dto = new OrganizationContextDto();
        dto.setCurrentRole(UserRole.ADMIN.name());

        OrganizationOptionDto orgOption = new OrganizationOptionDto();
        orgOption.setId(organization.getId());
        orgOption.setName(organization.getName());
        dto.setCurrentOrganization(orgOption);

        BranchOptionDto branchOption = new BranchOptionDto();
        branchOption.setId(branch.getId());
        branchOption.setName(branch.getName());
        dto.setCurrentBranch(branchOption);
        return dto;
    }
}
