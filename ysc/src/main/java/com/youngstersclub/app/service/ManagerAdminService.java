package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.BranchAccessUpdateRequest;
import com.youngstersclub.app.dto.ManagerAdminDto;
import com.youngstersclub.app.dto.ManagerBranchAccessDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.PromoteManagerRequest;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerAdminService {

  private static final Logger log = LoggerFactory.getLogger(ManagerAdminService.class);
  private static final List<UserRole> STAFF_ROLES = List.of(UserRole.MANAGER, UserRole.ADMIN, UserRole.SUPER_ADMIN);

  private final UserRepository userRepository;
  private final OrganizationUserRepository organizationUserRepository;
  private final UserBranchAccessRepository userBranchAccessRepository;
  private final BranchRepository branchRepository;
  private final OrganizationContextService organizationContextService;

  public ManagerAdminService(
      UserRepository userRepository,
      OrganizationUserRepository organizationUserRepository,
      UserBranchAccessRepository userBranchAccessRepository,
      BranchRepository branchRepository,
      OrganizationContextService organizationContextService) {
    this.userRepository = userRepository;
    this.organizationUserRepository = organizationUserRepository;
    this.userBranchAccessRepository = userBranchAccessRepository;
    this.branchRepository = branchRepository;
    this.organizationContextService = organizationContextService;
  }

  @Transactional(readOnly = true)
  public List<ManagerAdminDto> getCurrentBranchManagers(String actorEmail) {
    ManagerAdminContext context = resolveManagerAdminContext(actorEmail);

    List<OrganizationUser> memberships =
        organizationUserRepository.findByOrganization_IdAndRoleAndIsActiveTrue(
            context.organizationId(), UserRole.MANAGER);

    List<ManagerAdminDto> managers = new ArrayList<>();
    for (OrganizationUser membership : memberships) {
      User memberUser = membership.getUser();
      if (memberUser == null || !Boolean.TRUE.equals(memberUser.getIsActive())) {
        continue;
      }

      boolean hasCurrentBranchAccess =
          membership.getBaseBranch() != null
              && Objects.equals(membership.getBaseBranch().getId(), context.branch().getId());
      if (!hasCurrentBranchAccess) {
        hasCurrentBranchAccess =
            userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                membership.getId(), context.branch().getId());
      }
      if (!hasCurrentBranchAccess) {
        continue;
      }

      managers.add(toManagerAdminDto(membership));
    }

    log.info(
        "action=LIST_BRANCH_MANAGERS organizationId={} branchId={} actorUserId={} managerCount={}",
        context.organizationId(),
        context.branch().getId(),
        context.actor().getId(),
        managers.size());
    return managers;
  }

  @Transactional
  public ManagerAdminDto promoteManager(PromoteManagerRequest request, String actorEmail) {
    ManagerAdminContext context = resolveManagerAdminContext(actorEmail);
    if (request == null || request.getUserId() == null) {
      throw new IllegalArgumentException("User is required");
    }

    User targetUser = userRepository.findById(request.getUserId())
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
        .orElseThrow(() -> new IllegalArgumentException("User not found"));

    OrganizationUser membership =
        organizationUserRepository
            .findByUserIdAndOrganizationId(targetUser.getId(), context.organizationId())
            .orElseThrow(() ->
                new IllegalArgumentException("User does not belong to this organization"));

    requireMutableStaffMembership(membership, "promoted");

    membership.setRole(UserRole.MANAGER);
    membership.setIsActive(true);
    if (membership.getBaseBranch() == null) {
      membership.setBaseBranch(context.branch());
    }

    grantBranchAccess(membership, context.branch());

    if (targetUser.getRole() == null || targetUser.getRole() == UserRole.CUSTOMER) {
      targetUser.setRole(UserRole.MANAGER);
      userRepository.save(targetUser);
    }

    organizationUserRepository.save(membership);

    log.info(
        "action=PROMOTE_MANAGER organizationId={} branchId={} targetUserId={} actorUserId={}",
        context.organizationId(),
        context.branch().getId(),
        targetUser.getId(),
        context.actor().getId());
    return toManagerAdminDto(membership);
  }

  @Transactional
  public ManagerAdminDto demoteManager(Long organizationUserId, String actorEmail) {
    ManagerAdminContext context = resolveManagerAdminContext(actorEmail);
    OrganizationUser membership = requireBranchManagerMembership(organizationUserId, context);

    membership.setRole(UserRole.CUSTOMER);
    organizationUserRepository.save(membership);
    revertGlobalStaffRoleIfNeeded(membership.getUser(), membership.getId());

    log.info(
        "action=DEMOTE_MANAGER organizationId={} branchId={} membershipId={} actorUserId={}",
        context.organizationId(),
        context.branch().getId(),
        membership.getId(),
        context.actor().getId());
    return toManagerAdminDto(membership);
  }

  @Transactional
  public void deactivateManager(Long organizationUserId, String actorEmail) {
    ManagerAdminContext context = resolveManagerAdminContext(actorEmail);
    OrganizationUser membership = requireBranchManagerMembership(organizationUserId, context);

    membership.setIsActive(false);
    organizationUserRepository.save(membership);
    revertGlobalStaffRoleIfNeeded(membership.getUser(), membership.getId());

    log.info(
        "action=DEACTIVATE_MANAGER organizationId={} branchId={} membershipId={} actorUserId={}",
        context.organizationId(),
        context.branch().getId(),
        membership.getId(),
        context.actor().getId());
  }

  @Transactional
  public ManagerBranchAccessDto setStaffBranchAccess(
      Long organizationUserId, BranchAccessUpdateRequest request, String actorEmail) {
    ManagerAdminContext context = resolveManagerAdminContext(actorEmail);
    if (request == null || request.getBranchId() == null || request.getGranted() == null) {
      throw new IllegalArgumentException("Branch and granted flag are required");
    }

    OrganizationUser membership = requireMutableStaffMembershipById(organizationUserId, context.organizationId());

    Branch targetBranch =
        branchRepository
            .findByIdAndOrganizationIdAndIsActiveTrue(request.getBranchId(), context.organizationId())
            .orElseThrow(() -> new NoSuchElementException("Branch not found"));

    boolean callerHasBranchAccess =
        context.membership().getBaseBranch() != null
            && Objects.equals(context.membership().getBaseBranch().getId(), targetBranch.getId());
    if (!callerHasBranchAccess) {
      callerHasBranchAccess =
          userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
              context.membership().getId(), targetBranch.getId());
    }
    if (!callerHasBranchAccess) {
      throw new SecurityException("You do not have access to manage this branch");
    }

    boolean isBaseBranch =
        membership.getBaseBranch() != null
            && Objects.equals(membership.getBaseBranch().getId(), targetBranch.getId());
    if (isBaseBranch && !request.getGranted()) {
      throw new IllegalArgumentException("Cannot revoke the base branch");
    }

    UserBranchAccess access = grantBranchAccess(membership, targetBranch);
    access.setIsActive(request.getGranted());
    userBranchAccessRepository.save(access);

    log.info(
        "action=SET_STAFF_BRANCH_ACCESS organizationId={} branchId={} membershipId={} granted={} actorUserId={}",
        context.organizationId(),
        targetBranch.getId(),
        membership.getId(),
        request.getGranted(),
        context.actor().getId());
    return new ManagerBranchAccessDto(
        targetBranch.getId(),
        targetBranch.getName(),
        isBaseBranch,
        Boolean.TRUE.equals(access.getIsActive()));
  }

  private OrganizationUser requireBranchManagerMembership(Long organizationUserId, ManagerAdminContext context) {
    OrganizationUser membership = requireMutableStaffMembershipById(organizationUserId, context.organizationId());
    if (membership.getRole() != UserRole.MANAGER) {
      throw new IllegalArgumentException("Only managers can be updated here");
    }
    return membership;
  }

  private OrganizationUser requireMutableStaffMembershipById(Long organizationUserId, Long organizationId) {
    OrganizationUser membership = organizationUserRepository.findById(organizationUserId)
        .filter(member -> Objects.equals(
            member.getOrganization() == null ? null : member.getOrganization().getId(), organizationId))
        .orElseThrow(() -> new NoSuchElementException("Manager membership not found"));
    return requireMutableStaffMembership(membership, "modified");
  }

  private OrganizationUser requireMutableStaffMembership(OrganizationUser membership, String action) {
    if (membership.getRole() == UserRole.ADMIN || membership.getRole() == UserRole.SUPER_ADMIN) {
      throw new SecurityException("Administrator memberships cannot be " + action);
    }
    return membership;
  }

  private UserBranchAccess grantBranchAccess(OrganizationUser membership, Branch branch) {
    UserBranchAccess access =
        userBranchAccessRepository.findByOrganizationUserIdAndBranchId(membership.getId(), branch.getId())
            .orElseGet(() -> {
              UserBranchAccess created = new UserBranchAccess();
              created.setOrganizationUser(membership);
              created.setBranch(branch);
              created.setIsActive(true);
              return created;
            });
    access.setIsActive(true);
    return userBranchAccessRepository.save(access);
  }

  private void revertGlobalStaffRoleIfNeeded(User user, Long changedMembershipId) {
    if (user == null) {
      return;
    }
    long otherStaffMemberships =
        organizationUserRepository.countOtherActiveMembershipsByRoles(user.getId(), changedMembershipId, STAFF_ROLES);
    if (otherStaffMemberships == 0 && user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUPER_ADMIN) {
      user.setRole(UserRole.CUSTOMER);
      userRepository.save(user);
    }
  }

  private ManagerAdminDto toManagerAdminDto(OrganizationUser membership) {
    User memberUser = membership.getUser();
    ManagerAdminDto dto = new ManagerAdminDto();
    dto.setOrganizationUserId(membership.getId());
    dto.setUserId(memberUser.getId());
    dto.setName(memberUser.getName());
    dto.setEmail(memberUser.getEmail());
    dto.setPhone(memberUser.getPhone());
    dto.setRole(membership.getRole() == null ? null : membership.getRole().name());
    dto.setActive(Boolean.TRUE.equals(membership.getIsActive()));
    dto.setBaseBranchId(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getId());

    if (membership.getBaseBranch() != null) {
      dto.getBranchAccesses()
          .add(new ManagerBranchAccessDto(
              membership.getBaseBranch().getId(),
              membership.getBaseBranch().getName(),
              true,
              true));
    }

    for (UserBranchAccess access : userBranchAccessRepository.findByOrganizationUserIdAndIsActiveTrue(membership.getId())) {
      if (access.getBranch() == null) {
        continue;
      }
      boolean duplicateOfBase =
          membership.getBaseBranch() != null
              && Objects.equals(access.getBranch().getId(), membership.getBaseBranch().getId());
      if (duplicateOfBase) {
        continue;
      }
      dto.getBranchAccesses()
          .add(new ManagerBranchAccessDto(
              access.getBranch().getId(),
              access.getBranch().getName(),
              false,
              Boolean.TRUE.equals(access.getIsActive())));
    }
    return dto;
  }

  private ManagerAdminContext resolveManagerAdminContext(String actorEmail) {
    String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
    if (normalizedEmail.isEmpty()) {
      throw new SecurityException("Authenticated user email is required");
    }

    User actor =
        userRepository
            .findByEmail(normalizedEmail)
            .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
            .orElseThrow(() -> new SecurityException("Authenticated user not found"));

    OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
    if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
      throw new SecurityException("Active organization and branch context are required");
    }
    if (!UserRole.ADMIN.name().equals(context.getCurrentRole())
        && !UserRole.SUPER_ADMIN.name().equals(context.getCurrentRole())) {
      throw new SecurityException("Only admins can manage staff");
    }

    OrganizationUser membership =
        organizationUserRepository
            .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
            .orElseThrow(() -> new NoSuchElementException("Caller organization membership not found"));

    Branch branch =
        branchRepository
            .findByIdAndOrganizationIdAndIsActiveTrue(
                context.getCurrentBranch().getId(), context.getCurrentOrganization().getId())
            .orElseThrow(() -> new NoSuchElementException("Current branch not found"));

    boolean branchAccessible =
        membership.getBaseBranch() != null && branch.getId().equals(membership.getBaseBranch().getId());
    if (!branchAccessible) {
      branchAccessible =
          userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
              membership.getId(), branch.getId());
    }
    if (!branchAccessible) {
      throw new SecurityException("You do not have access to the current branch");
    }

    return new ManagerAdminContext(actor, membership, context.getCurrentOrganization().getId(), branch);
  }

  private record ManagerAdminContext(
      User actor, OrganizationUser membership, Long organizationId, Branch branch) {}
}
