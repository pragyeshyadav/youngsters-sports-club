package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.ConsumableItemAdminDto;
import com.youngstersclub.app.dto.ConsumableItemAdminRequest;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.ConsumableItem;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableItemRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsumableItemAdminServiceTest {

    @Mock
    private ConsumableItemRepository consumableItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrganizationUserRepository organizationUserRepository;

    @Mock
    private UserBranchAccessRepository userBranchAccessRepository;

    @InjectMocks
    private ConsumableService consumableService;

    private User admin;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setId(14);
        admin.setEmail("admin@test.com");
        admin.setRole(UserRole.ADMIN);
        admin.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters Sports Club");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(41L);
        membership.setUser(admin);
        membership.setOrganization(organization);
        membership.setBaseBranch(branch);
        membership.setRole(UserRole.ADMIN);
        membership.setIsActive(true);
    }

    @Test
    void adminCreatesItemInCurrentBranchWithDefaults() {
        mockAuthorizedContext(UserRole.ADMIN);
        when(consumableItemRepository.findFirstByBranch_IdAndNameIgnoreCase(branch.getId(), "Cold Coffee"))
                .thenReturn(Optional.empty());
        when(consumableItemRepository.save(any(ConsumableItem.class)))
                .thenAnswer(invocation -> {
                    ConsumableItem item = invocation.getArgument(0);
                    item.setId(501L);
                    return item;
                });

        ConsumableItemAdminDto response =
                consumableService.createItem(buildRequest("  Cold Coffee ", "25"), "admin@test.com");

        ArgumentCaptor<ConsumableItem> captor = ArgumentCaptor.forClass(ConsumableItem.class);
        verify(consumableItemRepository).save(captor.capture());
        ConsumableItem saved = captor.getValue();

        assertSame(branch, saved.getBranch());
        assertEquals("Cold Coffee", saved.getName());
        assertTrue(saved.getIsActive());
        assertEquals(0, BigDecimal.valueOf(25).compareTo(saved.getPrice()));
        assertEquals(501L, response.getId());
    }

    @Test
    void duplicateItemNameWithinBranchIsRejected() {
        mockAuthorizedContext(UserRole.ADMIN);
        ConsumableItem existing = buildItem(101L, "Tea", BigDecimal.valueOf(15));
        when(consumableItemRepository.findFirstByBranch_IdAndNameIgnoreCase(branch.getId(), "Tea"))
                .thenReturn(Optional.of(existing));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumableService.createItem(buildRequest("Tea", "15"), "admin@test.com"));

        assertEquals("A consumable item with this name already exists", exception.getMessage());
        verify(consumableItemRepository, never()).save(any(ConsumableItem.class));
    }

    @Test
    void updateRejectsCrossBranchItemAsNotFound() {
        mockAuthorizedContext(UserRole.ADMIN);
        when(consumableItemRepository.findByIdAndBranch_Id(999L, branch.getId())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumableService.updateItem(999L, buildRequest("Tea", "18"), "admin@test.com"));

        assertEquals("Consumable item not found", exception.getMessage());
    }

    @Test
    void managerRoleCannotManageItems() {
        mockAuthorizedContext(UserRole.MANAGER);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> consumableService.getItemsForAdmin("manager@test.com"));

        assertEquals("Only admins can manage consumable items", exception.getMessage());
    }

    @Test
    void deactivateTogglesItemActiveFlagOnly() {
        mockAuthorizedContext(UserRole.ADMIN);
        ConsumableItem item = buildItem(101L, "Tea", BigDecimal.valueOf(15));
        when(consumableItemRepository.findByIdAndBranch_Id(101L, branch.getId())).thenReturn(Optional.of(item));
        when(consumableItemRepository.save(item)).thenReturn(item);

        ConsumableItemAdminDto response = consumableService.setItemActive(101L, false, "admin@test.com");

        assertFalse(response.getActive());        verify(consumableItemRepository).save(item);
    }

    private void mockAuthorizedContext(UserRole role) {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(role.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        String email = UserRole.ADMIN.equals(role) ? "admin@test.com" : "manager@test.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(admin));
        when(organizationContextService.resolveContext(email)).thenReturn(context);

        if (UserRole.ADMIN.equals(role)) {
            when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(admin.getId(), organization.getId()))
                    .thenReturn(Optional.of(membership));
            when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                    .thenReturn(Optional.of(branch));
        }
    }

    private ConsumableItemAdminRequest buildRequest(String name, String price) {
        ConsumableItemAdminRequest request = new ConsumableItemAdminRequest();
        request.setName(name);
        request.setPrice(new BigDecimal(price));
        return request;
    }

    private ConsumableItem buildItem(Long id, String name, BigDecimal price) {
        ConsumableItem item = new ConsumableItem();
        item.setId(id);
        item.setName(name);
        item.setPrice(price);
        item.setBranch(branch);
        item.setIsActive(true);
        return item;
    }
}
