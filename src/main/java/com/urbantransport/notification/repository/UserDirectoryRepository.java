package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserDirectoryRepository extends JpaRepository<UserDirectoryEntity, UUID> {

    /** Para avisar a los administradores de una sucursa. */
    List<UserDirectoryEntity> findByBranchIdAndRoleAndActiveTrue(UUID branchId, Role role);

    /** Respaldo cuando una sucursal no tiene administradores en el directorio. */
    List<UserDirectoryEntity> findByRoleAndActiveTrue(Role role);
}