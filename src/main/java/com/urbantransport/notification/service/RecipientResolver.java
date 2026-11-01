package com.urbantransport.notification.service;

import java.util.List;
import java.util.UUID;

public interface RecipientResolver {

    /** Administradores activos de la sucursal; si no hay ninguno, los SYSTEM_ADMIN activos. */
    List<String> branchAdmins(UUID branchId);

    /** Correo de un usuario activo del directorio (cliente o personal); lista vacía si no se conoce. */
    List<String> userEmail(UUID userId);

    /** Correo de un cliente activo por su id en customer-service; lista vacía si no se conoce. */
    List<String> customerEmail(UUID customerId);
}