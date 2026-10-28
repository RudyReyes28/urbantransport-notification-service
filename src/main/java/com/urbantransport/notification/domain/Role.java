package com.urbantransport.notification.domain;

/** Mismos roles que identity. CUSTOMER solo existe para reconocerlo y descartarlo: no entra al directorio. */
public enum Role {
    SYSTEM_ADMIN, BRANCH_ADMIN, CASHIER, DRIVER, CUSTOMER
}