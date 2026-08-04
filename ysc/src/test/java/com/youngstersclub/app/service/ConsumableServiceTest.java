package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.dto.ConsumableStockCreateRequest;
import com.youngstersclub.app.dto.ConsumableStockCreateResponseDto;
import com.youngstersclub.app.dto.ConsumableStockReportRowDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.ConsumableItem;
import com.youngstersclub.app.entity.ConsumableItemStock;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableItemRepository;
import com.youngstersclub.app.repository.ConsumableItemStockRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class ConsumableServiceTest {

    @Mock
    private ConsumableItemRepository consumableItemRepository;

    @Mock
    private ConsumableItemStockRepository consumableItemStockRepository;

    @Mock
    private ConsumableOrderRepository consumableOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDueService userDueService;

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

    private User actor;
    private User customer;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("manager@test.com");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        customer = new User();
        customer.setId(25);
        customer.setName("Rahul");
        customer.setIsActive(true);

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
        membership.setUser(actor);
        membership.setOrganization(organization);
        membership.setBaseBranch(branch);
        membership.setRole(UserRole.MANAGER);
        membership.setIsActive(true);
        membership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void searchActiveItemsUsesCurrentBranch() {
        ConsumableItem item = buildItem(101L, "Tea", BigDecimal.valueOf(15));
        mockAuthorizedContext();
        when(consumableItemRepository.findTop10ByBranch_IdAndIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(
                branch.getId(),
                "tea"))
                .thenReturn(List.of(item));

        List<ConsumableItemOptionDto> results = consumableService.searchActiveItems("tea", "manager@test.com");

        assertEquals(1, results.size());
        assertEquals("Tea", results.get(0).getName());
        verify(consumableItemRepository)
                .findTop10ByBranch_IdAndIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(branch.getId(), "tea");
    }

    @Test
    void createOrderAssignsCurrentBranchAndSyncsThatBranchDue() {
        mockAuthorizedContext();
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        ConsumableItem item = buildItem(101L, "Tea", BigDecimal.valueOf(15));
        when(consumableItemRepository.findByIdInAndBranch_IdAndIsActiveTrue(List.of(101L), branch.getId()))
                .thenReturn(List.of(item));
        when(consumableOrderRepository.save(any(ConsumableOrder.class)))
                .thenAnswer(invocation -> {
                    ConsumableOrder order = invocation.getArgument(0);
                    order.setId(501L);
                    return order;
                });

        ConsumableOrderCreateRequest request = new ConsumableOrderCreateRequest();
        request.setUserId(customer.getId());
        ConsumableOrderCreateRequest.ItemRequest itemRequest = new ConsumableOrderCreateRequest.ItemRequest();
        itemRequest.setItemId(101L);
        itemRequest.setQuantity(2);
        request.setItems(List.of(itemRequest));

        ConsumableOrderResponseDto response = consumableService.createOrder(request, "manager@test.com");

        ArgumentCaptor<ConsumableOrder> captor = ArgumentCaptor.forClass(ConsumableOrder.class);
        verify(consumableOrderRepository).save(captor.capture());
        ConsumableOrder savedOrder = captor.getValue();

        assertSame(branch, savedOrder.getBranch());
        assertSame(customer, savedOrder.getUser());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(savedOrder.getTotalAmount()));
        assertEquals(501L, response.getOrderId());
        assertEquals(0, BigDecimal.valueOf(30).compareTo(response.getTotalAmount()));
        verify(userDueService).syncBranchDue(customer, branch);
    }

    @Test
    void createOrderRejectsCrossBranchItemsAndDoesNotTouchCurrentBranchInventory() {
        mockAuthorizedContext();
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(consumableItemRepository.findByIdInAndBranch_IdAndIsActiveTrue(List.of(101L), branch.getId()))
                .thenReturn(List.of());

        ConsumableOrderCreateRequest request = new ConsumableOrderCreateRequest();
        request.setUserId(customer.getId());
        ConsumableOrderCreateRequest.ItemRequest itemRequest = new ConsumableOrderCreateRequest.ItemRequest();
        itemRequest.setItemId(101L);
        itemRequest.setQuantity(1);
        request.setItems(List.of(itemRequest));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> consumableService.createOrder(request, "manager@test.com"));

        assertEquals("One or more consumable items are unavailable", exception.getMessage());
        verify(consumableOrderRepository, never()).save(any(ConsumableOrder.class));
        verify(userDueService, never()).syncBranchDue(customer, branch);
    }

    @Test
    void addStockUsesCurrentBranchAndAuthenticatedActor() {
        mockAuthorizedContext();
        ConsumableItem item = buildItem(101L, "Tea", BigDecimal.valueOf(15));
        when(consumableItemRepository.findByIdAndBranch_Id(101L, branch.getId())).thenReturn(Optional.of(item));
        when(consumableItemStockRepository.save(any(ConsumableItemStock.class)))
                .thenAnswer(invocation -> {
                    ConsumableItemStock stock = invocation.getArgument(0);
                    stock.setId(601L);
                    return stock;
                });

        ConsumableStockCreateRequest request = new ConsumableStockCreateRequest();
        request.setItemId(101L);
        request.setQuantityAdded(5);

        ConsumableStockCreateResponseDto response = consumableService.addStock(request, "manager@test.com");

        ArgumentCaptor<ConsumableItemStock> captor = ArgumentCaptor.forClass(ConsumableItemStock.class);
        verify(consumableItemStockRepository).save(captor.capture());
        ConsumableItemStock stock = captor.getValue();

        assertSame(branch, stock.getBranch());
        assertSame(actor, stock.getAddedBy());
        assertSame(item, stock.getItem());
        assertEquals(601L, response.getStockEntryId());
    }

    @Test
    void stockReportIsLoadedForCurrentBranchOnly() {
        mockAuthorizedContext();
        ConsumableItemRepository.ConsumableStockReportProjection projection =
                new ConsumableItemRepository.ConsumableStockReportProjection() {
                    @Override
                    public Long getItemId() {
                        return 101L;
                    }

                    @Override
                    public String getItemName() {
                        return "Tea";
                    }

                    @Override
                    public Long getStockAdded() {
                        return 20L;
                    }

                    @Override
                    public Long getSoldQuantity() {
                        return 8L;
                    }

                    @Override
                    public Long getAvailableStock() {
                        return 12L;
                    }
                };
        when(consumableItemRepository.getConsumableStockReport(branch.getId(), 7, 2026))
                .thenReturn(List.of(projection));

        List<ConsumableStockReportRowDto> rows = consumableService.getStockReport(7, 2026, "manager@test.com");

        assertEquals(1, rows.size());
        assertEquals(101L, rows.get(0).getItemId());
        assertEquals(12L, rows.get(0).getAvailableStock());
        verify(consumableItemRepository).getConsumableStockReport(branch.getId(), 7, 2026);
    }

    private void mockAuthorizedContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
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
