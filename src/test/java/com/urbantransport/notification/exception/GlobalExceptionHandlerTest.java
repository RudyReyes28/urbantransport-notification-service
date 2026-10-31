package com.urbantransport.notification.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private static final String PATH = "/emails/11111111-1111-1111-1111-111111111111";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);

    @Test
    void apiException_keepsItsStatusAndMessage() {
        ApiException exception = new ApiException(HttpStatus.CONFLICT,
                "Solo se pueden reintentar correos FAILED (estado actual: SENT)");

        ResponseEntity<ErrorResponse> response = handler.handleApi(exception, request);

        assertError(response, HttpStatus.CONFLICT, "Solo se pueden reintentar correos FAILED (estado actual: SENT)");
    }

    @Test
    void notFoundApiException_returnsNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleApi(
                new ApiException(HttpStatus.NOT_FOUND, "Correo no encontrado"), request);

        assertError(response, HttpStatus.NOT_FOUND, "Correo no encontrado");
    }

    @Test
    void typeMismatch_returnsBadRequestWithParameterName() {
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("status");

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(exception, request);

        assertError(response, HttpStatus.BAD_REQUEST, "Parámetro inválido: status");
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String message) {
        assertEquals(status, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.getTimestamp());
        assertEquals(status.value(), body.getStatus());
        assertEquals(message, body.getMessage());
        assertEquals(PATH, body.getPath());
    }
}
