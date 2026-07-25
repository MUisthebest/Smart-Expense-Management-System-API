package com.example.smartmoney.presentation.exceptions;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A malformed {@code @RequestBody} (wrong JSON type, unknown enum literal) fails during Spring's
 * message-conversion step, before the controller method (and its own try/catch) ever runs — so
 * only a dedicated {@link org.springframework.http.converter.HttpMessageNotReadableException}
 * handler here stands between it and the catch-all {@code Exception.class} handler, which used
 * to turn every one of these into a 500 instead of the 400 the client actually caused.
 */
@DisplayName("GlobalExceptionHandler — malformed request body")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    @DisplayName("wrong JSON type for a field (e.g. \"amountLimit\": \"aa\") returns 400, not 500")
    void wrongFieldTypeReturns400() {
        InvalidFormatException cause = InvalidFormatException.from(
                mock(JsonParser.class), "Cannot deserialize value", "aa", BigDecimal.class);
        cause.prependPath(new JsonMappingException.Reference(null, "amountLimit"));
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", cause, null);

        ResponseEntity<CheckResponse<ErrorDetails>> response = handler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(response.getBody().getMessage()).contains("amountLimit").contains("aa");
    }

    @Test
    @DisplayName("unknown enum literal (invalid category) returns 400, not 500")
    void invalidEnumValueReturns400() {
        InvalidFormatException cause = InvalidFormatException.from(
                mock(JsonParser.class), "not a valid Category", "NOT_A_CATEGORY", Category.class);
        cause.prependPath(new JsonMappingException.Reference(null, "category"));
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", cause, null);

        ResponseEntity<CheckResponse<ErrorDetails>> response = handler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("INVALID_REQUEST_BODY");
        assertThat(response.getBody().getMessage()).contains("category").contains("NOT_A_CATEGORY");
    }

    @Test
    @DisplayName("unreadable JSON with no specific Jackson cause still returns 400 with a generic message")
    void genericUnreadableBodyReturns400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON parse error", (Throwable) null, null);

        ResponseEntity<CheckResponse<ErrorDetails>> response = handler.handleMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body");
    }
}
