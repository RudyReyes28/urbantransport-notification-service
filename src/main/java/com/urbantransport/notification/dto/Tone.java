package com.urbantransport.notification.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Color de acento del correo. */
@Getter
@RequiredArgsConstructor
public enum Tone {
    INFO("#2563eb"),
    SUCCESS("#16a34a"),
    WARNING("#d97706"),
    DANGER("#dc2626");

    private final String hex;
}