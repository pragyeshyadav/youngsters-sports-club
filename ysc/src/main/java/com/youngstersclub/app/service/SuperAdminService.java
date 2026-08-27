package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CustomerOnboardingCandidateDto;
import com.youngstersclub.app.dto.CustomerMembershipSummaryDto;
import com.youngstersclub.app.dto.OnboardingBranchDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.SuperAdminBranchDto;
import com.youngstersclub.app.dto.SuperAdminBranchRequest;
import com.youngstersclub.app.dto.SuperAdminOrganizationDto;
import com.youngstersclub.app.dto.SuperAdminOrganizationRequest;
import com.youngstersclub.app.dto.SuperAdminPortalContextDto;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentRequest;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentResponseDto;
import com.youngstersclub.app.dto.SuperAdminUserSearchResultDto;
import com.youngstersclub.app.dto.UserSearchResultDto;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperAdminService {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");
  private static final List<UserRole> ASSIGNABLE_ROLES = List.of(UserRole.ADMIN, UserRole.MANAGER);

  private final SuperAdminAuthorizationService superAdminAuthorizationService;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final BranchRepository branchRepository;
  private final OrganizationUserRepository organizationUserRepository;
  private final UserBranchAccessRepository userBranchAccessRepository;

  public SuperAdminService(
      SuperAdminAuthorizationService superAdminAuthorizationService,
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      BranchRepository branchRepository,
      OrganizationUserRepository organizationUserRepository,
      UserBranchAccessRepository userBranchAccessRepository) {
    this.superAdminAuthorizationService = superAdminAuthorizationService;
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.branchRepository = branchRepository;
    this.organizationUserRepository = organizationUserRepository;
    this.userBranchAccessRepository = userBranchAccessRepository;
  }

  @Transactional(readOnly = true)
  public SuperAdminPortalContextDto getPortalContext(String actorEmail) {
    superAdminAuthorizationService.requireSuperAdmin(actorEmail);
    SuperAdminPortalContextDto response = new SuperAdminPortalContextDto();
    response.setOrganizations(organizationRepository.findByIsActiveTrueOrderByNameAsc().stream()
        .map(this::toOrganizationOption)
        .toList());
    response.setAssignableRoles(ASSIGNABLE_ROLES.stream().map(Enum::name).toList());
    return response;
  }

  @Transactional(readOnly = true)
  public List<SuperAdminOrganizationDto> getOrganizations(String actorEmail) {
    superAdminAuthorizationService.requireSuperAdmin(actorEmail);
    return organizationRepository.findAllByOrderByNameAsc().stream()
        .map(this::toOrganizationDto)
        .toList();
  }

  @Transactional
  public SuperAdminOrganizationDto createOrganization(SuperAdminOrganizationRequest request) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(request == null ? null : request.getActorEmail()));
    Organization organization = new Organization();
    applyOrganizationRequest(organization, request);
    organization.setIsActive(true);
    organization.setCreatedAt(LocalDateTime.now());
    organization.setUpdatedAt(LocalDateTime.now());
    return toOrganizationDto(organizationRepository.save(organization));
  }

  @Transactional
  public SuperAdminOrganizationDto updateOrganization(Long organizationId, SuperAdminOrganizationRequest request) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(request == null ? null : request.getActorEmail()));
    Organization organization = organizationRepository.findById(organizationId)
        .orElseThrow(() -> new NoSuchElementException("Organization not found"));
    applyOrganizationRequest(organization, request);
    organization.setUpdatedAt(LocalDateTime.now());
    return toOrganizationDto(organizationRepository.save(organization));
  }

  @Transactional
  public void deactivateOrganization(Long organizationId, String actorEmail) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(actorEmail));
    Organization organization = organizationRepository.findById(organizationId)
        .orElseThrow(() -> new NoSuchElementException("Organization not found"));
    organization.setIsActive(false);
    organization.setUpdatedAt(LocalDateTime.now());
    organizationRepository.save(organization);
  }

  @Transactional(readOnly = true)
  public List<SuperAdminBranchDto> getBranches(String actorEmail, Long organizationId) {
    superAdminAuthorizationService.requireSuperAdmin(actorEmail);
    if (organizationId == null) {
      return List.of();
    }
    return branchRepository.findByOrganizationIdOrderByNameAsc(organizationId).stream()
        .map(this::toBranchDto)
        .toList();
  }

  @Transactional
  public SuperAdminBranchDto createBranch(SuperAdminBranchRequest request) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(request == null ? null : request.getActorEmail()));
    if (request == null || request.getOrganizationId() == null) {
      throw new IllegalArgumentException("Organization is required");
    }
    Organization organization = organizationRepository.findByIdAndIsActiveTrue(request.getOrganizationId())
        .orElseThrow(() -> new NoSuchElementException("Organization not found"));
    Branch branch = new Branch();
    branch.setOrganization(organization);
    applyBranchRequest(branch, request);
    branch.setIsActive(Boolean.TRUE.equals(request.getIsActive()) || request.getIsActive() == null);
    branch.setCreatedAt(LocalDateTime.now());
    branch.setUpdatedAt(LocalDateTime.now());
    return toBranchDto(branchRepository.save(branch));
  }

  @Transactional
  public SuperAdminBranchDto updateBranch(Long branchId, SuperAdminBranchRequest request) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(request == null ? null : request.getActorEmail()));
    if (request == null || request.getOrganizationId() == null) {
      throw new IllegalArgumentException("Organization is required");
    }
    Branch branch = branchRepository.findByIdAndOrganizationId(branchId, request.getOrganizationId())
        .orElseThrow(() -> new NoSuchElementException("Branch not found"));
    Organization organization = organizationRepository.findById(request.getOrganizationId())
        .orElseThrow(() -> new NoSuchElementException("Organization not found"));
    branch.setOrganization(organization);
    applyBranchRequest(branch, request);
    branch.setIsActive(request.getIsActive() == null ? branch.getIsActive() : request.getIsActive());
    branch.setUpdatedAt(LocalDateTime.now());
    return toBranchDto(branchRepository.save(branch));
  }

  @Transactional
  public void deactivateBranch(Long branchId, Long organizationId, String actorEmail) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(actorEmail));
    if (organizationId == null) {
      throw new IllegalArgumentException("Organization is required");
    }
    Branch branch = branchRepository.findByIdAndOrganizationId(branchId, organizationId)
        .orElseThrow(() -> new NoSuchElementException("Branch not found"));
    if (organizationUserRepository.countByBaseBranchIdAndIsActiveTrue(branchId) > 0) {
      throw new IllegalStateException("Branch is still assigned as a base branch for active users");
    }
    branch.setIsActive(false);
    branch.setUpdatedAt(LocalDateTime.now());
    branchRepository.save(branch);
  }

  @Transactional(readOnly = true)
  public List<SuperAdminUserSearchResultDto> searchCustomers(String actorEmail, Long organizationId, String query) {
    superAdminAuthorizationService.requireSuperAdmin(actorEmail);
    if (organizationId == null) {
      throw new IllegalArgumentException("Organization is required");
    }
    String normalizedQuery = normalizeText(query);
    if (normalizedQuery == null || normalizedQuery.length() < 3) {
      return List.of();
    }
    String digitsQuery = normalizedQuery.replaceAll("[^0-9]", "");
    List<UserSearchResultDto> matches = userRepository.searchActiveUserSummariesForOrganizationScopeAndRole(
        normalizedQuery,
        digitsQuery,
        PageRequest.of(0, 10),
        organizationId,
        null,
        UserRole.CUSTOMER);
    return matches.stream()
        .map(this::toUserSearchResult)
        .toList();
  }

  @Transactional(readOnly = true)
  public CustomerOnboardingCandidateDto getUserAssignments(String actorEmail, Integer userId) {
    superAdminAuthorizationService.requireSuperAdmin(actorEmail);
    User user = userRepository.findById(userId)
        .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
        .orElseThrow(() -> new NoSuchElementException("User not found"));

    CustomerOnboardingCandidateDto dto = new CustomerOnboardingCandidateDto();
    dto.setUserId(user.getId());
    dto.setName(user.getName());
    dto.setEmail(user.getEmail());
    dto.setPhone(user.getPhone());
    dto.setMemberships(organizationUserRepository.findByUserId(userId).stream()
        .sorted(Comparator.comparing(
            membership -> membership.getOrganization().getName(),
            String.CASE_INSENSITIVE_ORDER))
        .map(this::toMembershipSummary)
        .toList());
    return dto;
  }

  @Transactional
  public SuperAdminStaffAssignmentResponseDto upsertStaffAssignment(SuperAdminStaffAssignmentRequest request) {
    superAdminAuthorizationService.requireSuperAdmin(getRequiredActorEmail(request == null ? null : request.getActorEmail()));
    if (request == null) {
      throw new IllegalArgumentException("Assignment request is required");
    }
    if (request.getUserId() == null) {
      throw new IllegalArgumentException("User selection is required");
    }
    if (request.getOrganizationId() == null) {
      throw new IllegalArgumentException("Organization is required");
    }
    UserRole role = resolveAssignableRole(request.getRole());
    List<Long> requestedBranchIds = sanitizeBranchIds(request.getBranchIds());
    if (requestedBranchIds.isEmpty()) {
      throw new IllegalArgumentException("At least one branch is required");
    }

    User targetUser = userRepository.findById(request.getUserId())
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
        .orElseThrow(() -> new NoSuchElementException("User not found"));
    Organization organization = organizationRepository.findByIdAndIsActiveTrue(request.getOrganizationId())
        .orElseThrow(() -> new NoSuchElementException("Organization not found"));
    Map<Long, Branch> requestedBranchesById = branchRepository
        .findByOrganizationIdAndIdInAndIsActiveTrue(organization.getId(), requestedBranchIds)
        .stream()
        .collect(Collectors.toMap(Branch::getId, branch -> branch));
    if (requestedBranchesById.size() != requestedBranchIds.size()) {
      throw new NoSuchElementException("One or more branches were not found");
    }

    Optional<OrganizationUser> existingMembershipOptional =
        organizationUserRepository.findByUserIdAndOrganizationId(targetUser.getId(), organization.getId());

    boolean membershipCreated = false;
    boolean membershipReactivated = false;
    Branch baseBranch;
    OrganizationUser membership;

    if (existingMembershipOptional.isEmpty()) {
      if (request.getBaseBranchId() == null) {
        throw new IllegalArgumentException("Base branch is required");
      }
      baseBranch = requestedBranchesById.get(request.getBaseBranchId());
      if (baseBranch == null) {
        throw new IllegalArgumentException("Base branch must be one of the selected branches");
      }
      membership = new OrganizationUser();
      membership.setOrganization(organization);
      membership.setUser(targetUser);
      membership.setRole(role);
      membership.setBaseBranch(baseBranch);
      membership.setIsActive(true);
      membership.setCreatedAt(LocalDateTime.now());
      membership = organizationUserRepository.save(membership);
      membershipCreated = true;
    } else {
      membership = existingMembershipOptional.get();
      if (!Boolean.TRUE.equals(membership.getIsActive())) {
        membership.setIsActive(true);
        membershipReactivated = true;
      }
      baseBranch = membership.getBaseBranch();
      if (request.getBaseBranchId() != null) {
        baseBranch = requestedBranchesById.get(request.getBaseBranchId());
        if (baseBranch == null) {
          throw new IllegalArgumentException("Base branch must be one of the selected branches");
        }
        membership.setBaseBranch(baseBranch);
      } else if (baseBranch == null) {
        throw new IllegalArgumentException("Base branch is required");
      }
      membership.setRole(role);
      membership = organizationUserRepository.save(membership);
    }

    syncBranchAccess(membership, requestedBranchIds, requestedBranchesById);
    syncGlobalUserRole(targetUser);

    SuperAdminStaffAssignmentResponseDto response = new SuperAdminStaffAssignmentResponseDto();
    response.setUserId(targetUser.getId());
    response.setUserName(targetUser.getName());
    response.setOrganizationId(organization.getId());
    response.setOrganizationName(organization.getName());
    response.setOrganizationUserId(membership.getId());
    response.setAssignedRole(role.name());
    response.setMembershipCreated(membershipCreated);
    response.setMembershipReactivated(membershipReactivated);
    response.setBaseBranchId(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getId());
    response.setBaseBranchName(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getName());
    response.setActiveBranches(loadAccessibleBranches(membership).stream()
        .map(this::toOnboardingBranch)
        .toList());
    return response;
  }

  protected void applyOrganizationRequest(Organization organization, SuperAdminOrganizationRequest request) {
    if (organization == null || request == null) {
      throw new IllegalArgumentException("Organization request is required");
    }
    organization.setName(requireText(request.getName(), "Organization name is required"));
    organization.setLogoUrl(normalizeNullableText(request.getLogoUrl()));
    organization.setPhone(normalizePhone(request.getPhone()));
    organization.setEmail(normalizeEmail(request.getEmail()));
    organization.setAddress(normalizeNullableText(request.getAddress()));
    organization.setCity(normalizeNullableText(request.getCity()));
    organization.setState(normalizeNullableText(request.getState()));
    organization.setCountry(normalizeNullableText(request.getCountry()));
  }

  protected void applyBranchRequest(Branch branch, SuperAdminBranchRequest request) {
    if (branch == null || request == null) {
      throw new IllegalArgumentException("Branch request is required");
    }
    branch.setName(requireText(request.getName(), "Branch name is required"));
    branch.setBranchCode(normalizeNullableText(request.getBranchCode()));
    branch.setAddress(normalizeNullableText(request.getAddress()));
    branch.setCity(normalizeNullableText(request.getCity()));
    branch.setState(normalizeNullableText(request.getState()));
    branch.setPhone(normalizePhone(request.getPhone()));
    branch.setEmail(normalizeEmail(request.getEmail()));
    branch.setLatitude(request.getLatitude());
    branch.setLongitude(request.getLongitude());
  }

  protected void syncBranchAccess(
      OrganizationUser membership,
      List<Long> requestedBranchIds,
      Map<Long, Branch> branchesById) {
    List<UserBranchAccess> existingAccessRows = userBranchAccessRepository.findByOrganizationUserId(membership.getId());
    Map<Long, UserBranchAccess> existingAccessByBranchId = existingAccessRows.stream()
        .filter(access -> access.getBranch() != null)
        .collect(Collectors.toMap(
            access -> access.getBranch().getId(),
            access -> access,
            (left, right) -> left,
            HashMap::new));

    Set<Long> requestedBranchIdSet = new LinkedHashSet<>(requestedBranchIds);
    if (membership.getBaseBranch() != null) {
      requestedBranchIdSet.add(membership.getBaseBranch().getId());
    }
    LocalDateTime now = LocalDateTime.now();

    for (Long branchId : requestedBranchIdSet) {
      Branch branch = branchesById.get(branchId);
      if (branch == null && membership.getBaseBranch() != null && membership.getBaseBranch().getId().equals(branchId)) {
        branch = membership.getBaseBranch();
      }
      if (branch == null) {
        continue;
      }
      UserBranchAccess existingAccess = existingAccessByBranchId.get(branchId);
      if (existingAccess == null) {
        UserBranchAccess access = new UserBranchAccess();
        access.setOrganizationUser(membership);
        access.setBranch(branch);
        access.setIsActive(true);
        access.setGrantedAt(now);
        access.setCreatedAt(now);
        userBranchAccessRepository.save(access);
        continue;
      }
      if (!Boolean.TRUE.equals(existingAccess.getIsActive())) {
        existingAccess.setIsActive(true);
        if (existingAccess.getGrantedAt() == null) {
          existingAccess.setGrantedAt(now);
        }
        userBranchAccessRepository.save(existingAccess);
      }
    }

    for (UserBranchAccess existingAccess : existingAccessRows) {
      Branch branch = existingAccess.getBranch();
      if (branch == null || branch.getId() == null) {
        continue;
      }
      if (membership.getBaseBranch() != null && branch.getId().equals(membership.getBaseBranch().getId())) {
        if (!Boolean.TRUE.equals(existingAccess.getIsActive())) {
          existingAccess.setIsActive(true);
          userBranchAccessRepository.save(existingAccess);
        }
        continue;
      }
      if (!requestedBranchIdSet.contains(branch.getId()) && Boolean.TRUE.equals(existingAccess.getIsActive())) {
        existingAccess.setIsActive(false);
        userBranchAccessRepository.save(existingAccess);
      }
    }
  }

  protected void syncGlobalUserRole(User user) {
    if (user == null || user.getId() == null) {
      return;
    }
    if (user.getRole() == UserRole.SUPER_ADMIN) {
      return;
    }
    List<OrganizationUser> activeMemberships =
        organizationUserRepository.findByUserIdAndIsActiveTrue(user.getId());
    UserRole resolvedRole = UserRole.CUSTOMER;
    boolean hasAdminMembership =
        activeMemberships.stream().anyMatch(membership -> membership.getRole() == UserRole.ADMIN);
    boolean hasManagerMembership =
        activeMemberships.stream().anyMatch(membership -> membership.getRole() == UserRole.MANAGER);
    if (hasAdminMembership) {
      resolvedRole = UserRole.ADMIN;
    } else if (hasManagerMembership) {
      resolvedRole = UserRole.MANAGER;
    }
    if (user.getRole() != resolvedRole) {
      user.setRole(resolvedRole);
      userRepository.save(user);
    }
  }

  protected List<Branch> loadAccessibleBranches(OrganizationUser membership) {
    if (membership == null) {
      return List.of();
    }
    Map<Long, Branch> branches = new LinkedHashMap<>();
    if (membership.getBaseBranch() != null && membership.getBaseBranch().getId() != null) {
      branches.put(membership.getBaseBranch().getId(), membership.getBaseBranch());
    }
    userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(membership.getId()).stream()
        .map(UserBranchAccess::getBranch)
        .filter(branch -> branch != null && Boolean.TRUE.equals(branch.getIsActive()))
        .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
        .forEach(branch -> branches.put(branch.getId(), branch));
    return new ArrayList<>(branches.values());
  }

  protected CustomerMembershipSummaryDto toMembershipSummary(OrganizationUser membership) {
    CustomerMembershipSummaryDto dto = new CustomerMembershipSummaryDto();
    dto.setOrganizationId(membership.getOrganization() == null ? null : membership.getOrganization().getId());
    dto.setOrganizationName(membership.getOrganization() == null ? null : membership.getOrganization().getName());
    dto.setRole(membership.getRole() == null ? null : membership.getRole().name());
    dto.setActive(Boolean.TRUE.equals(membership.getIsActive()));
    dto.setBaseBranchId(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getId());
    dto.setBaseBranchName(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getName());
    dto.setAccessibleBranches(loadAccessibleBranches(membership).stream()
        .map(this::toOnboardingBranch)
        .toList());
    return dto;
  }

  protected SuperAdminOrganizationDto toOrganizationDto(Organization organization) {
    SuperAdminOrganizationDto dto = new SuperAdminOrganizationDto();
    dto.setId(organization.getId());
    dto.setName(organization.getName());
    dto.setLogoUrl(organization.getLogoUrl());
    dto.setPhone(organization.getPhone());
    dto.setEmail(organization.getEmail());
    dto.setAddress(organization.getAddress());
    dto.setCity(organization.getCity());
    dto.setState(organization.getState());
    dto.setCountry(organization.getCountry());
    dto.setActive(Boolean.TRUE.equals(organization.getIsActive()));
    return dto;
  }

  protected SuperAdminBranchDto toBranchDto(Branch branch) {
    SuperAdminBranchDto dto = new SuperAdminBranchDto();
    dto.setId(branch.getId());
    dto.setOrganizationId(branch.getOrganization() == null ? null : branch.getOrganization().getId());
    dto.setOrganizationName(branch.getOrganization() == null ? null : branch.getOrganization().getName());
    dto.setName(branch.getName());
    dto.setBranchCode(branch.getBranchCode());
    dto.setAddress(branch.getAddress());
    dto.setCity(branch.getCity());
    dto.setState(branch.getState());
    dto.setPhone(branch.getPhone());
    dto.setEmail(branch.getEmail());
    dto.setLatitude(branch.getLatitude());
    dto.setLongitude(branch.getLongitude());
    dto.setActive(Boolean.TRUE.equals(branch.getIsActive()));
    return dto;
  }

  protected OrganizationOptionDto toOrganizationOption(Organization organization) {
    return new OrganizationOptionDto(organization.getId(), organization.getName(), organization.getLogoUrl());
  }

  protected OnboardingBranchDto toOnboardingBranch(Branch branch) {
    return new OnboardingBranchDto(branch.getId(), branch.getName());
  }

  protected SuperAdminUserSearchResultDto toUserSearchResult(UserSearchResultDto user) {
    SuperAdminUserSearchResultDto dto = new SuperAdminUserSearchResultDto();
    dto.setId(user.getId());
    dto.setName(user.getName());
    dto.setEmail(user.getEmail());
    dto.setPhone(user.getPhone());
    return dto;
  }

  protected UserRole resolveAssignableRole(String roleValue) {
    String normalizedRole = normalizeText(roleValue);
    if (normalizedRole == null) {
      throw new IllegalArgumentException("Assigned role is required");
    }
    UserRole role;
    try {
      role = UserRole.valueOf(normalizedRole.toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Assigned role is invalid");
    }
    if (!ASSIGNABLE_ROLES.contains(role)) {
      throw new IllegalArgumentException("Assigned role is invalid");
    }
    return role;
  }

  protected List<Long> sanitizeBranchIds(Collection<Long> branchIds) {
    if (branchIds == null) {
      return List.of();
    }
    return branchIds.stream()
        .filter(id -> id != null)
        .distinct()
        .toList();
  }

  protected String getRequiredActorEmail(String actorEmail) {
    String normalized = normalizeText(actorEmail);
    if (normalized == null) {
      throw new IllegalArgumentException("Actor email is required");
    }
    return normalized;
  }

  protected String requireText(String value, String message) {
    String normalized = normalizeText(value);
    if (normalized == null) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  protected String normalizeEmail(String email) {
    String normalized = normalizeNullableText(email);
    if (normalized == null) {
      return null;
    }
    if (!EMAIL_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Email address is invalid");
    }
    return normalized.toLowerCase();
  }

  protected String normalizePhone(String phone) {
    String normalized = normalizeNullableText(phone);
    if (normalized == null) {
      return null;
    }
    if (!PHONE_PATTERN.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Phone number must be exactly 10 digits");
    }
    return normalized;
  }

  protected String normalizeNullableText(String value) {
    String normalized = normalizeText(value);
    return normalized == null ? null : normalized;
  }

  protected String normalizeText(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
