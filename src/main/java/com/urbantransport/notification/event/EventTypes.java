package com.urbantransport.notification.event;

public final class EventTypes {

    // identity-events
    public static final String USER_CREATED = "UserCreated";
    public static final String USER_UPDATED = "UserUpdated";
    public static final String USER_DEACTIVATED = "UserDeactivated";

    // ticket-events
    public static final String TICKET_PURCHASED = "TicketPurchased";
    public static final String TICKET_CANCELLED = "TicketCancelled";

    private EventTypes() {
    }
}