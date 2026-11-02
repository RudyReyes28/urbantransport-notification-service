package com.urbantransport.notification.event;

public final class EventTypes {

    // identity-events
    public static final String USER_CREATED = "UserCreated";
    public static final String USER_UPDATED = "UserUpdated";
    public static final String USER_DEACTIVATED = "UserDeactivated";
    public static final String PASSWORD_SETUP_REQUESTED = "PasswordSetupRequested";

    // ticket-events
    public static final String TICKET_PURCHASED = "TicketPurchased";
    public static final String TICKET_CANCELLED = "TicketCancelled";


    // fleet-events
    public static final String DRIVER_REGISTERED = "DriverRegistered";
    public static final String DRIVER_UPDATED = "DriverUpdated";
    public static final String DRIVER_DEACTIVATED = "DriverDeactivated";

    // rental-events
    public static final String RENTAL_REQUESTED = "RentalRequested";
    public static final String RENTAL_QUOTED = "RentalQuoted";
    public static final String RENTAL_REJECTED = "RentalRejected";
    public static final String RENTAL_PAID = "RentalPaid";

    // trip-events
    public static final String TRIP_SCHEDULED = "TripScheduled";
    public static final String TRIP_UPDATED = "TripUpdated";
    public static final String TRIP_DELETED = "TripDeleted";
    public static final String PRIVATE_TRIP_REJECTED = "PrivateTripRejected";

    // rental-events
    public static final String RENTAL_ASSIGNED = "RentalAssigned";

    // payment-events
    public static final String WALLET_RECHARGED = "WalletRecharged";




    private EventTypes() {
    }
}