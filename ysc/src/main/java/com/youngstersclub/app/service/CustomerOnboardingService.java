package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CustomerMembershipSummaryDto;
import com.youngstersclub.app.dto.CustomerOnboardingCandidateDto;
import com.youngstersclub.app.dto.CustomerOnboardingContextDto;
import com.youngstersclub.app.dto.CustomerOnboardingRequest;
import com.youngstersclub.app.dto.CustomerOnboardingResponseDto;
import com.youngstersclub.app.dto.OnboardingBranchDto;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerOnboardingService {
  private static final Logger log = LoggerFactory.getLogger(CustomerOnboardingService.class);

  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;
  private final BranchRepository branchRepository;
  private final OrganizationUserRepository organizationUserRepository;
  private final UserBranchAccessRepository userBranchAccessRepository;
  private final OrganizationContextService organizationContextService;

  public CustomerOnboardingService(
      UserRepository userRepository,
      OrganizationRepository organizationRepository,
      BranchRepository branchRepository,
      OrganizationUserRepository organizationUserRepository,
      UserBranchAccessRepository userBranchAccessRepository,
      OrganizationContextService organizationContextService) {
    this.userRepository = userRepository;
    this.organizationRepository = organizationRepository;
    this.branchRepository = branchRepository;
    this.organizationUserRepository = organizationUserRepository;
    this.userBranchAccessRepository = userBranchAccessRepository;
    this.organizationContextService = organizationContextService;
  }

  @Transactional(readOnly = true)
  public CustomerOnboardingContextDto getOnboardingContext(String actorEmail, Long requestedOrganizationId) {
    ActorContext actorContext = resolveActorContext(actorEmail);
    CustomerOnboardingContextDto response = new CustomerOnboardingContextDto();
    response.setActorRole(actorContext.role().name());
    response.setCurrentOrganizationId(actorContext.currentOrganizationId());
    response.setCurrentOrganizationName(actorContext.currentOrganizationName());
    response.setCurrentBranchId(actorContext.currentBranchId());
    response.setCurrentBranchName(actorContext.currentBranchName());

    if (actorContext.role() == UserRole.SUPER_ADMIN) {
      List<OrganizationOptionDto> organizations = organizationRepository.findByIsActiveTrueOrderByNameAsc().stream()
          .map(this::toOrganizationOption)
          .toList();
      response.setOrganizations(organizations);
      response.setOrganizationSelectable(true);
      response.setMultipleBranchSelectionAllowed(true);
      Long selectedOrganizationId = requestedOrganizationId != null
          ? requestedOrganizationId
          : actorContext.currentOrganizationId();
      if (selectedOrganizationId != null) {
        response.setBranches(loadActiveBranchesForOrganization(selectedOrganizationId));
      }
      return response;
    }

    if (actorContext.currentOrganizationId() == null) {
      throw new IllegalStateException("Current organization context is missing");
    }

    response.setOrganizations(List.of(new OrganizationOptionDto(
        actorContext.currentOrganizationId(),
        actorContext.currentOrganizationName())));
    response.setOrganizationSelectable(false);

    if (actorContext.role() == UserRole.ADMIN) {
      response.setMultipleBranchSelectionAllowed(true);
      response.setBranches(loadActiveBranchesForOrganization(actorContext.currentOrganizationId()));
      return response;
    }

    if (actorContext.role() == UserRole.MANAGER) {
      response.setMultipleBranchSelectionAllowed(false);
      response.setBranches(loadManagerBranches(actorContext.membership()));
      return response;
    }

    throw new SecurityException("You are not authorized to onboard customers");
  }

  @Transactional(readOnly = true)
  public CustomerOnboardingCandidateDto getCandidateDetails(Integer userId) {
    User user = userRepository.findById(userId)
        .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
        .orElseThrow(() -> new java.util.NoSuchElementException("Customer not found"));

    CustomerOnboardingCandidateDto response = new CustomerOnboardingCandidateDto();
    response.setUserId(user.getId());
    response.setName(user.getName());
    response.setEmail(user.getEmail());
    response.setPhone(user.getPhone());

    List<OrganizationUser> memberships = organizationUserRepository.findByUserId(user.getId()).stream()
        .sorted(Comparator.comparing(
            membership -> membership.getOrganization().getName(),
            String.CASE_INSENSITIVE_ORDER))
        .toList();

    List<CustomerMembershipSummaryDto> membershipSummaries = new ArrayList<>();
    for (OrganizationUser membership : memberships) {
      membershipSummaries.add(toMembershipSummary(membership));
    }
    response.setMemberships(membershipSummaries);
    return response;
  }

  @Transactional
  public CustomerOnboardingResponseDto onboardCustomer(CustomerOnboardingRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("Onboarding request is required");
    }
    if (request.getActorEmail() == null || request.getActorEmail().trim().isEmpty()) {
      throw new IllegalArgumentException("Actor email is required");
    }
    if (request.getUserId() == null) {
      throw new IllegalArgumentException("Customer selection is required");
    }
    if (request.getBranchIds() == null || request.getBranchIds().isEmpty()) {
      throw new IllegalArgumentException("At least one branch is required");
    }

    ActorContext actorContext = resolveActorContext(request.getActorEmail());
    User targetUser = userRepository.findById(request.getUserId())
        .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
        .orElseThrow(() -> new java.util.NoSuchElementException("Customer not found"));

    Long targetOrganizationId = resolveTargetOrganizationId(actorContext, request.getOrganizationId());
    Organization targetOrganization = organizationRepository.findByIdAndIsActiveTrue(targetOrganizationId)
        .orElseThrow(() -> new java.util.NoSuchElementException("Organization not found"));

    List<Long> requestedBranchIds = request.getBranchIds().stream()
        .filter(id -> id != null)
        .distinct()
        .toList();
    if (requestedBranchIds.isEmpty()) {
      throw new IllegalArgumentException("At least one branch is required");
    }

    List<Branch> requestedBranches = branchRepository.findByOrganizationIdAndIdInAndIsActiveTrue(
        targetOrganizationId, requestedBranchIds);
    if (requestedBranches.size() != requestedBranchIds.size()) {
      throw new java.util.NoSuchElementException("One or more branches were not found");
    }

    validateActorBranchAuthorization(actorContext, targetOrganizationId, requestedBranchIds);

    Map<Long, Branch> branchesById = requestedBranches.stream()
        .collect(Collectors.toMap(Branch::getId, branch -> branch));

    Optional<OrganizationUser> existingMembershipOptional =
        organizationUserRepository.findByUserIdAndOrganizationId(targetUser.getId(), targetOrganizationId);

    boolean membershipCreated = false;
    boolean membershipReactivated = false;
    Branch baseBranch;
    OrganizationUser membership;

    if (existingMembershipOptional.isEmpty()) {
      if (request.getBaseBranchId() == null) {
        throw new IllegalArgumentException("Base branch is required for first-time onboarding");
      }
      baseBranch = branchesById.get(request.getBaseBranchId());
      if (baseBranch == null) {
        throw new IllegalArgumentException("Base branch must be one of the selected branches");
      }
      membership = new OrganizationUser();
      membership.setOrganization(targetOrganization);
      membership.setUser(targetUser);
      membership.setRole(UserRole.CUSTOMER);
      membership.setBaseBranch(baseBranch);
      membership.setIsActive(true);
      membership.setCreatedAt(LocalDateTime.now());
      membership = organizationUserRepository.save(membership);
      membershipCreated = true;
    } else {
      membership = existingMembershipOptional.get();
      baseBranch = membership.getBaseBranch();
      if (!Boolean.TRUE.equals(membership.getIsActive())) {
        membership.setIsActive(true);
        membership = organizationUserRepository.save(membership);
        membershipReactivated = true;
      }

      if (membership.getBaseBranch() == null) {
        if (request.getBaseBranchId() == null) {
          throw new IllegalArgumentException("Base branch is required for memberships without a base branch");
        }
        Branch resolvedBaseBranch = branchesById.get(request.getBaseBranchId());
        if (resolvedBaseBranch == null) {
          throw new IllegalArgumentException("Base branch must be one of the selected branches");
        }
        membership.setBaseBranch(resolvedBaseBranch);
        membership = organizationUserRepository.save(membership);
        baseBranch = resolvedBaseBranch;
      }
    }

    List<UserBranchAccess> existingAccessRows = userBranchAccessRepository.findByOrganizationUserId(membership.getId());
    Map<Long, UserBranchAccess> existingAccessByBranchId = existingAccessRows.stream()
        .filter(access -> access.getBranch() != null)
        .collect(Collectors.toMap(
            access -> access.getBranch().getId(),
            access -> access,
            (left, right) -> left,
            HashMap::new));

    List<OnboardingBranchDto> branchesAdded = new ArrayList<>();
    List<OnboardingBranchDto> alreadyAccessibleBranches = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();

    for (Long branchId : requestedBranchIds) {
      Branch branch = branchesById.get(branchId);
      if (branch == null) {
        continue;
      }
      UserBranchAccess existingAccess = existingAccessByBranchId.get(branchId);
      if (existingAccess == null) {
        UserBranchAccess branchAccess = new UserBranchAccess();
        branchAccess.setOrganizationUser(membership);
        branchAccess.setBranch(branch);
        branchAccess.setIsActive(true);
        branchAccess.setGrantedAt(now);
        branchAccess.setCreatedAt(now);
        userBranchAccessRepository.save(branchAccess);
        branchesAdded.add(toOnboardingBranch(branch));
      } else if (!Boolean.TRUE.equals(existingAccess.getIsActive())) {
        existingAccess.setIsActive(true);
        if (existingAccess.getGrantedAt() == null) {
          existingAccess.setGrantedAt(now);
        }
        userBranchAccessRepository.save(existingAccess);
        branchesAdded.add(toOnboardingBranch(branch));
      } else {
        alreadyAccessibleBranches.add(toOnboardingBranch(branch));
      }
    }

    CustomerOnboardingResponseDto response = new CustomerOnboardingResponseDto();
    response.setUserId(targetUser.getId());
    response.setCustomerName(targetUser.getName());
    response.setOrganizationId(targetOrganization.getId());
    response.setOrganizationName(targetOrganization.getName());
    response.setOrganizationUserId(membership.getId());
    response.setMembershipCreated(membershipCreated);
    response.setMembershipReactivated(membershipReactivated);
    response.setBaseBranchId(baseBranch == null ? null : baseBranch.getId());
    response.setBaseBranchName(baseBranch == null ? null : baseBranch.getName());
    response.setBranchesAdded(branchesAdded);
    response.setAlreadyAccessibleBranches(alreadyAccessibleBranches);

    log.info(
        "Customer onboarding completed. actorEmail: {}, targetUserId: {}, organizationId: {}, branchIds: {}, membershipCreated: {}, membershipReactivated: {}",
        request.getActorEmail(),
        targetUser.getId(),
        targetOrganizationId,
        requestedBranchIds,
        membershipCreated,
        membershipReactivated);

    return response;
  }

  private ActorContext resolveActorContext(String actorEmail) {
    User actor = userRepository.findByEmail(actorEmail == null ? "" : actorEmail.trim().toLowerCase())
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
        .orElseThrow(() -> new java.util.NoSuchElementException("Actor not found"));

    OrganizationContextDto context = organizationContextService.resolveContext(actorEmail);
    UserRole role = resolveActorRole(context, actor);

    OrganizationUser membership = null;
    if (context.getCurrentOrganization() != null) {
      membership = organizationUserRepository
          .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
          .orElse(null);
    }

    return new ActorContext(
        actor,
        role,
        membership,
        context.getCurrentOrganization() == null ? null : context.getCurrentOrganization().getId(),
        context.getCurrentOrganization() == null ? null : context.getCurrentOrganization().getName(),
        context.getCurrentBranch() == null ? null : context.getCurrentBranch().getId(),
        context.getCurrentBranch() == null ? null : context.getCurrentBranch().getName());
  }

  private UserRole resolveActorRole(OrganizationContextDto context, User actor) {
    String contextRole = context.getCurrentRole();
    if (contextRole != null && !contextRole.isBlank()) {
      return UserRole.valueOf(contextRole);
    }
    if (actor.getRole() != null) {
      return actor.getRole();
    }
    throw new SecurityException("Actor role could not be resolved");
  }

  private Long resolveTargetOrganizationId(ActorContext actorContext, Long requestedOrganizationId) {
    if (actorContext.role() == UserRole.SUPER_ADMIN) {
      if (requestedOrganizationId == null) {
        throw new IllegalArgumentException("Organization is required");
      }
      return requestedOrganizationId;
    }

    if (actorContext.currentOrganizationId() == null) {
      throw new SecurityException("Current organization context is required");
    }

    if (requestedOrganizationId != null && !actorContext.currentOrganizationId().equals(requestedOrganizationId)) {
      throw new SecurityException("You are not authorized to onboard customers into another organization");
    }

    return actorContext.currentOrganizationId();
  }

  private void validateActorBranchAuthorization(
      ActorContext actorContext,
      Long targetOrganizationId,
      List<Long> requestedBranchIds) {
    if (actorContext.role() == UserRole.SUPER_ADMIN) {
      return;
    }

    if (actorContext.role() == UserRole.ADMIN) {
      return;
    }

    if (actorContext.role() != UserRole.MANAGER) {
      throw new SecurityException("You are not authorized to onboard customers");
    }

    if (requestedBranchIds.size() > 1) {
      throw new SecurityException("Managers can onboard a customer into only one branch at a time");
    }

    if (actorContext.membership() == null) {
      throw new SecurityException("Manager organization context is missing");
    }

    Set<Long> managerBranchIds = userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(actorContext.membership().getId())
        .stream()
        .map(UserBranchAccess::getBranch)
        .filter(branch -> branch != null
            && Boolean.TRUE.equals(branch.getIsActive())
            && branch.getOrganization() != null
            && targetOrganizationId.equals(branch.getOrganization().getId()))
        .map(Branch::getId)
        .collect(Collectors.toCollection(LinkedHashSet::new));

    if (!managerBranchIds.containsAll(requestedBranchIds)) {
      throw new SecurityException("You are not authorized to assign one or more selected branches");
    }
  }

  private List<OnboardingBranchDto> loadActiveBranchesForOrganization(Long organizationId) {
    return branchRepository.findByOrganizationIdAndIsActiveTrueOrderByNameAsc(organizationId).stream()
        .map(this::toOnboardingBranch)
        .toList();
  }

  private List<OnboardingBranchDto> loadManagerBranches(OrganizationUser membership) {
    if (membership == null) {
      return List.of();
    }
    return userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(membership.getId()).stream()
        .map(UserBranchAccess::getBranch)
        .filter(branch -> branch != null && Boolean.TRUE.equals(branch.getIsActive()))
        .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toOnboardingBranch)
        .toList();
  }

  private CustomerMembershipSummaryDto toMembershipSummary(OrganizationUser membership) {
    CustomerMembershipSummaryDto dto = new CustomerMembershipSummaryDto();
    dto.setOrganizationId(membership.getOrganization() == null ? null : membership.getOrganization().getId());
    dto.setOrganizationName(membership.getOrganization() == null ? null : membership.getOrganization().getName());
    dto.setRole(membership.getRole() == null ? null : membership.getRole().name());
    dto.setActive(Boolean.TRUE.equals(membership.getIsActive()));
    dto.setBaseBranchId(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getId());
    dto.setBaseBranchName(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getName());

    List<OnboardingBranchDto> branches = userBranchAccessRepository.findByOrganizationUserId(membership.getId()).stream()
        .map(UserBranchAccess::getBranch)
        .filter(branch -> branch != null)
        .sorted(Comparator.comparing(Branch::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toOnboardingBranch)
        .toList();
    dto.setAccessibleBranches(branches);
    return dto;
  }

  private OrganizationOptionDto toOrganizationOption(Organization organization) {
    return new OrganizationOptionDto(organization.getId(), organization.getName());
  }

  private OnboardingBranchDto toOnboardingBranch(Branch branch) {
    return new OnboardingBranchDto(branch.getId(), branch.getName());
  }

  private record ActorContext(
      User actor,
      UserRole role,
      OrganizationUser membership,
      Long currentOrganizationId,
      String currentOrganizationName,
      Long currentBranchId,
      String currentBranchName) {}
}
