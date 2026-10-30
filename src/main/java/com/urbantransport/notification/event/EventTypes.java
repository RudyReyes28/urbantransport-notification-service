package com.urbantransport.notification.event;

public final class EventTypes {

    // identity-events
    public static final String USER_CREATED = "UserCreated";
    public static final String USER_UPDATED = "UserUpdated";
    public static final String USER_DEACTIVATED = "UserDeactivated";

    // ticket-events
    public static final String TICKET_PURCHASED = "TicketPurchased";
    public static final String TICKET_CANCELLED = "TicketCancelled";

    // fleet-events
    public static final String LICENSE_EXPIRING = "LicenseExpiring";
    // fleet-events
    public static final String DRIVER_REGISTERED = "DriverRegistered";
    public static final String DRIVER_UPDATED = "DriverUpdated";
    public static final String DRIVER_DEACTIVATED = "DriverDeactivated";

    // rental-events
    public static final String RENTAL_REQUESTED = "RentalRequested";
    public static final String RENTAL_QUOTED = "RentalQuoted";
    public static final String RENTAL_REJECTED = "RentalRejected";
    public static final String RENTAL_PAID = "RentalPaid";





    private EventTypes() {
    }
}