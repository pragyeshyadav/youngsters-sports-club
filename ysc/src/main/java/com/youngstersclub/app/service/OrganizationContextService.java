package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationContextService {

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final BranchRepository branchRepository;
  private final OrganizationUserRepository organizationUserRepository;
  private final UserBranchAccessRepository userBranchAccessRepository;

  public OrganizationContextService(
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      BranchRepository branchRepository,
      OrganizationUserRepository organizationUserRepository,
      UserBranchAccessRepository userBranchAccessRepository) {
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.branchRepository = branchRepository;
    this.organizationUserRepository = organizationUserRepository;
    this.userBranchAccessRepository = userBranchAccessRepository;
  }

  @Transactional(readOnly = true)
  public OrganizationContextDto resolveContext(String email) {
    User user = findUserByEmail(email);
    return buildContext(user, null);
  }

  @Transactional(readOnly = true)
  public List<OrganizationOptionDto> getAvailableOrganizations(String email) {
    User user = findUserByEmail(email);
    return resolveOrganizations(user);
  }

  @Transactional(readOnly = true)
  public List<BranchOptionDto> getBranchesForOrganization(String email, Long organizationId) {
    User user = findUserByEmail(email);
    return resolveBranchOptions(user, organizationId);
  }

  @Transactional
  public OrganizationContextDto changeContext(String email, Long organizationId, Long branchId) {
    User user = findUserByEmail(email);
    ensureContextForResolvedUser(user, organizationId, branchId);
    return buildContext(user, organizationId);
  }

  @Transactional
  public OrganizationContextDto ensureContextForResolvedUser(User user, Long organizationId, Long branchId) {
    if (user == null || organizationId == null || branchId == null) {
      return buildContext(user, null);
    }

    OrganizationUser selectedMembership = resolveOrCreateMembership(user, organizationId, branchId);
    List<OrganizationUser> memberships = organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());
    for (OrganizationUser membership : memberships) {
      membership.setLastSelectedOrganizationId(organizationId);
      if (membership.getId().equals(selectedMembership.getId())) {
        membership.setLastSelectedBranchId(branchId);
      }
    }
    organizationUserRepository.saveAll(memberships);
    return buildContext(user, organizationId);
  }

  private User findUserByEmail(String email) {
    String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
    if (normalizedEmail.isEmpty()) {
      throw new IllegalArgumentException("Email is required");
    }
    return userRepository.findByEmail(normalizedEmail)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  private OrganizationContextDto buildContext(User user, Long preferredOrganizationId) {
    OrganizationContextDto dto = new OrganizationContextDto();
    if (user == null) {
      dto.setRequiresSelection(true);
      dto.setAvailableOrganizations(List.of());
      dto.setAccessibleBranches(List.of());
      return dto;
    }

    dto.setUserId(user.getId());
    List<OrganizationUser> memberships = organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());

    if (memberships.isEmpty()) {
      dto.setHasPersistedContext(false);
      dto.setRequiresSelection(true);
      dto.setAvailableOrganizations(resolveOrganizations(user));
      dto.setAccessibleBranches(List.of());
      dto.setCurrentRole(user.getRole() == null ? UserRole.CUSTOMER.name() : user.getRole().name());
      return dto;
    }

    OrganizationUser currentMembership = resolveCurrentMembership(memberships, preferredOrganizationId);
    List<BranchOptionDto> accessibleBranches = resolveAccessibleBranches(currentMembership);
    BranchOptionDto currentBranch = resolveCurrentBranch(currentMembership, accessibleBranches);

    dto.setHasPersistedContext(true);
    dto.setRequiresSelection(false);
    dto.setCurrentRole(currentMembership.getRole() == null ? null : currentMembership.getRole().name());
    dto.setCurrentOrganization(toOrganizationOption(currentMembership.getOrganization()));
    dto.setCurrentBranch(currentBranch);
    dto.setAvailableOrganizations(resolveOrganizations(user));
    dto.setAccessibleBranches(accessibleBranches);
    return dto;
  }

  private OrganizationUser resolveCurrentMembership(
      List<OrganizationUser> memberships, Long preferredOrganizationId) {
    if (preferredOrganizationId != null) {
      Optional<OrganizationUser> preferredMembership = memberships.stream()
          .filter(membership -> membership.getOrganization() != null
              && preferredOrganizationId.equals(membership.getOrganization().getId()))
          .findFirst();
      if (preferredMembership.isPresent()) {
        return preferredMembership.get();
      }
    }

    Optional<Long> rememberedOrganizationId = memberships.stream()
        .map(OrganizationUser::getLastSelectedOrganizationId)
        .filter(id -> id != null)
        .findFirst();

    if (rememberedOrganizationId.isPresent()) {
      Optional<OrganizationUser> rememberedMembership = memberships.stream()
          .filter(membership -> membership.getOrganization() != null
              && rememberedOrganizationId.get().equals(membership.getOrganization().getId()))
          .findFirst();
      if (rememberedMembership.isPresent()) {
        return rememberedMembership.get();
      }
    }

    return memberships.stream()
        .sorted(Comparator.comparing(membership -> membership.getOrganization().getName(), String.CASE_INSENSITIVE_ORDER))
        .findFirst()
        .orElseThrow();
  }

  private List<OrganizationOptionDto> resolveOrganizations(User user) {
    List<OrganizationUser> memberships = organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());
    if (memberships.isEmpty()) {
      return organizationRepository.findByIsActiveTrueOrderByNameAsc().stream()
          .map(this::toOrganizationOption)
          .toList();
    }

    Map<Long, OrganizationOptionDto> organizations = new LinkedHashMap<>();
    memberships.stream()
        .map(OrganizationUser::getOrganization)
        .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER))
        .forEach(org -> organizations.putIfAbsent(org.getId(), toOrganizationOption(org)));
    return new ArrayList<>(organizations.values());
  }

  private List<BranchOptionDto> resolveBranchOptions(User user, Long organizationId) {
    List<OrganizationUser> memberships = organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());
    Optional<OrganizationUser> membership = memberships.stream()
        .filter(item -> item.getOrganization() != null && organizationId.equals(item.getOrganization().getId()))
        .findFirst();

    if (membership.isPresent()) {
      return resolveAccessibleBranches(membership.get());
    }

    if (!memberships.isEmpty()) {
      return List.of();
    }

    return branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId).stream()
        .map(this::toBranchOption)
        .toList();
  }

  private List<BranchOptionDto> resolveAccessibleBranches(OrganizationUser membership) {
    Map<Long, BranchOptionDto> branchMap = new LinkedHashMap<>();
    if (membership.getBaseBranch() != null
        && Boolean.TRUE.equals(membership.getBaseBranch().getIsActive())) {
      branchMap.put(membership.getBaseBranch().getId(), toBranchOption(membership.getBaseBranch()));
    }

    userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(membership.getId()).stream()
        .map(UserBranchAccess::getBranch)
        .filter(branch -> branch != null && Boolean.TRUE.equals(branch.getIsActive()))
        .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
        .forEach(branch -> branchMap.putIfAbsent(branch.getId(), toBranchOption(branch)));

    if (branchMap.isEmpty() && membership.getOrganization() != null) {
      branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(membership.getOrganization().getId()).stream()
          .map(this::toBranchOption)
          .forEach(branch -> branchMap.putIfAbsent(branch.getId(), branch));
    }

    return new ArrayList<>(branchMap.values());
  }

  private BranchOptionDto resolveCurrentBranch(
      OrganizationUser membership, List<BranchOptionDto> accessibleBranches) {
    if (accessibleBranches.isEmpty()) {
      return null;
    }

    if (membership.getLastSelectedBranchId() != null) {
      Optional<BranchOptionDto> rememberedBranch = accessibleBranches.stream()
          .filter(branch -> membership.getLastSelectedBranchId().equals(branch.getId()))
          .findFirst();
      if (rememberedBranch.isPresent()) {
        return rememberedBranch.get();
      }
    }

    if (membership.getBaseBranch() != null) {
      Optional<BranchOptionDto> baseBranch = accessibleBranches.stream()
          .filter(branch -> membership.getBaseBranch().getId().equals(branch.getId()))
          .findFirst();
      if (baseBranch.isPresent()) {
        return baseBranch.get();
      }
    }

    return accessibleBranches.get(0);
  }

  private OrganizationUser resolveOrCreateMembership(User user, Long organizationId, Long branchId) {
    Organization organization = organizationRepository.findById(organizationId)
        .filter(org -> Boolean.TRUE.equals(org.getIsActive()))
        .orElseThrow(() -> new IllegalArgumentException("Selected organization is not available"));
    Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branchId, organizationId)
        .orElseThrow(() -> new IllegalArgumentException("Selected branch is not available"));

    List<OrganizationUser> memberships = organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());
    Optional<OrganizationUser> existingMembership = memberships.stream()
        .filter(membership -> membership.getOrganization() != null
            && organizationId.equals(membership.getOrganization().getId()))
        .findFirst();

    if (existingMembership.isPresent()) {
      OrganizationUser membership = existingMembership.get();
      boolean branchAccessible = membership.getBaseBranch() != null
          && branchId.equals(membership.getBaseBranch().getId());
      if (!branchAccessible) {
        branchAccessible = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
            membership.getId(), branchId);
      }
      if (!branchAccessible) {
        throw new IllegalArgumentException("You do not have access to the selected branch");
      }
      return membership;
    }

    if (!memberships.isEmpty()) {
      throw new IllegalArgumentException("You do not have access to the selected organization");
    }

    LocalDateTime now = LocalDateTime.now();
    OrganizationUser membership = new OrganizationUser();
    membership.setOrganization(organization);
    membership.setUser(user);
    membership.setRole(user.getRole() == null ? UserRole.CUSTOMER : user.getRole());
    membership.setBaseBranch(branch);
    membership.setLastSelectedOrganizationId(organizationId);
    membership.setLastSelectedBranchId(branchId);
    membership.setIsActive(true);
    membership.setCreatedAt(now);
    OrganizationUser savedMembership = organizationUserRepository.save(membership);

    UserBranchAccess branchAccess = new UserBranchAccess();
    branchAccess.setOrganizationUser(savedMembership);
    branchAccess.setBranch(branch);
    branchAccess.setIsActive(true);
    branchAccess.setGrantedAt(now);
    branchAccess.setCreatedAt(now);
    userBranchAccessRepository.save(branchAccess);
    return savedMembership;
  }

  private OrganizationOptionDto toOrganizationOption(Organization organization) {
    return new OrganizationOptionDto(organization.getId(), organization.getName());
  }

  private BranchOptionDto toBranchOption(Branch branch) {
    return new BranchOptionDto(branch.getId(), branch.getName());
  }
}
