package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationSummaryRecipientServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationUserRepository organizationUserRepository;

    @Test
    void resolveRecipientsForOrganizationPreservesGlobalRecipientsAndAddsOnlyCurrentOrganizationRecipients() {
        OrganizationSummaryRecipientService service =
                new OrganizationSummaryRecipientService(userRepository, organizationUserRepository);

        when(userRepository.findByRoleAndIsActiveTrue(UserRole.SUPER_ADMIN))
                .thenReturn(List.of(user("Pragyesh.Yadav@gmail.com")));
        when(organizationUserRepository.findActiveRecipientEmailsByOrganizationIdAndRoles(
                1L,
                List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN)))
                .thenReturn(Arrays.asList(
                        "youngsterssportsclub@gmail.com",
                        "PRAGYESH.YADAV@gmail.com",
                        "   ",
                        null));

        List<String> recipients = service.resolveRecipientsForOrganization(1L);

        assertIterableEquals(
                List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"),
                recipients);
    }

    @Test
    void resolveOrganizationSummaryRecipientsReturnsEmptyWhenOrganizationIdMissing() {
        OrganizationSummaryRecipientService service =
                new OrganizationSummaryRecipientService(userRepository, organizationUserRepository);

        assertEquals(List.of(), service.resolveOrganizationSummaryRecipients(null));
    }

    @Test
    void normalizeHelpersTrimLowercaseAndRejectBlankEmails() {
        OrganizationSummaryRecipientService service =
                new OrganizationSummaryRecipientService(userRepository, organizationUserRepository);

        assertEquals("admin@example.com", service.normalizeEmail("  Admin@Example.com  "));
        assertEquals(true, service.isUsableEmail("user@example.com"));
        assertEquals(false, service.isUsableEmail("   "));
        assertEquals(false, service.isUsableEmail(null));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setIsActive(true);
        return user;
    }
}
