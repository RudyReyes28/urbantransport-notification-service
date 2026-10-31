package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipientResolverImplTest {

    private static final UUID BRANCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CUSTOMER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private UserDirectoryRepository userDirectoryRepository;

    private RecipientResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new RecipientResolverImpl(userDirectoryRepository);
    }

    @Nested
    class BranchAdmins {

        @Test
        void branchWithAdmins_returnsTheirDistinctEmails() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of(user("a@mail.com", true), user("b@mail.com", true), user("a@mail.com", true)));

            assertEquals(List.of("a@mail.com", "b@mail.com"), resolver.branchAdmins(BRANCH_ID));

            verify(userDirectoryRepository, never()).findByRoleAndActiveTrue(any());
        }

        @Test
        void branchWithoutAdmins_fallsBackToSystemAdmins() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of());
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN))
                    .thenReturn(List.of(user("root@mail.com", true)));

            assertEquals(List.of("root@mail.com"), resolver.branchAdmins(BRANCH_ID));
        }

        @Test
        void withoutBranch_goesStraightToSystemAdmins() {
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN))
                    .thenReturn(List.of(user("root@mail.com", true)));

            assertEquals(List.of("root@mail.com"), resolver.branchAdmins(null));

            verify(userDirectoryRepository, never()).findByBranchIdAndRoleAndActiveTrue(any(), any());
        }

        @Test
        void nobodyInDirectory_returnsEmpty() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of());
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN)).thenReturn(List.of());

            assertTrue(resolver.branchAdmins(BRANCH_ID).isEmpty());
        }
    }

    @Nested
    class UserEmail {

        @Test
        void activeUser_returnsEmail() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.of(user("ana@mail.com", true)));

            assertEquals(List.of("ana@mail.com"), resolver.userEmail(USER_ID));
        }

        @Test
        void inactiveUser_returnsEmpty() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.of(user("ana@mail.com", false)));

            assertTrue(resolver.userEmail(USER_ID).isEmpty());
        }

        @Test
        void unknownUser_returnsEmpty() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertTrue(resolver.userEmail(USER_ID).isEmpty());
        }

        @Test
        void nullUser_returnsEmptyWithoutQuerying() {
            assertTrue(resolver.userEmail(null).isEmpty());

            verifyNoInteractions(userDirectoryRepository);
        }
    }

    @Nested
    class CustomerEmail {

        @Test
        void activeCustomer_returnsEmail() {
            when(userDirectoryRepository.findByCustomerId(CUSTOMER_ID))
                    .thenReturn(Optional.of(user("cliente@mail.com", true)));

            assertEquals(List.of("cliente@mail.com"), resolver.customerEmail(CUSTOMER_ID));
        }

        @Test
        void inactiveCustomer_returnsEmpty() {
            when(userDirectoryRepository.findByCustomerId(CUSTOMER_ID))
                    .thenReturn(Optional.of(user("cliente@mail.com", false)));

            assertTrue(resolver.customerEmail(CUSTOMER_ID).isEmpty());
        }

        @Test
        void unknownCustomer_returnsEmpty() {
            when(userDirectoryRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            assertTrue(resolver.customerEmail(CUSTOMER_ID).isEmpty());
        }

        @Test
        void nullCustomer_returnsEmptyWithoutQuerying() {
            assertTrue(resolver.customerEmail(null).isEmpty());

            verifyNoInteractions(userDirectoryRepository);
        }
    }

    private static UserDirectoryEntity user(String email, boolean active) {
        return UserDirectoryEntity.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .fullName("Usuario")
                .role(Role.BRANCH_ADMIN)
                .active(active)
                .lastEventAt(Instant.now())
                .build();
    }
}
