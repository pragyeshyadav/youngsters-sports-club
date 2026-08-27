package com.youngstersclub.app.service;

import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OrganizationSummaryRecipientService {

    private static final List<UserRole> ORGANIZATION_SUMMARY_ROLES = List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private final UserRepository userRepository;
    private final OrganizationUserRepository organizationUserRepository;

    public OrganizationSummaryRecipientService(
            UserRepository userRepository,
            OrganizationUserRepository organizationUserRepository) {
        this.userRepository = userRepository;
        this.organizationUserRepository = organizationUserRepository;
    }

    public List<String> resolveRecipientsForOrganization(Long organizationId) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.addAll(resolveGlobalSummaryRecipients());
        recipients.addAll(resolveOrganizationSummaryRecipients(organizationId));
        return List.copyOf(recipients);
    }

    protected List<String> resolveGlobalSummaryRecipients() {
        return userRepository.findByRoleAndIsActiveTrue(UserRole.SUPER_ADMIN)
                .stream()
                .map(user -> user == null ? null : user.getEmail())
                .filter(this::isUsableEmail)
                .map(this::normalizeEmail)
                .distinct()
                .toList();
    }

    protected List<String> resolveOrganizationSummaryRecipients(Long organizationId) {
        if (organizationId == null) {
            return List.of();
        }
        return organizationUserRepository.findActiveRecipientEmailsByOrganizationIdAndRoles(
                        organizationId,
                        ORGANIZATION_SUMMARY_ROLES)
                .stream()
                .filter(this::isUsableEmail)
                .map(this::normalizeEmail)
                .distinct()
                .toList();
    }

    protected boolean isUsableEmail(String email) {
        return email != null && !email.trim().isBlank();
    }

    protected String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
