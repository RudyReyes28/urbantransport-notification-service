package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import com.urbantransport.notification.service.RecipientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecipientResolverImpl implements RecipientResolver {

    private final UserDirectoryRepository userDirectoryRepository;

    @Override
    @Transactional(readOnly = true)
    public List<String> branchAdmins(UUID branchId) {
        List<UserDirectoryEntity> admins = branchId == null
                ? List.of()
                : userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(branchId, Role.BRANCH_ADMIN);
        if (admins.isEmpty()) {
            admins = userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN);
        }
        return admins.stream().map(UserDirectoryEntity::getEmail).distinct().toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> userEmail(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return userDirectoryRepository.findById(userId)
                .filter(UserDirectoryEntity::isActive)
                .map(user -> List.of(user.getEmail()))
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> customerEmail(UUID customerId) {
        if (customerId == null) {
            return List.of();
        }
        return userDirectoryRepository.findByCustomerId(customerId)
                .filter(UserDirectoryEntity::isActive)
                .map(user -> List.of(user.getEmail()))
                .orElse(List.of());
    }
}