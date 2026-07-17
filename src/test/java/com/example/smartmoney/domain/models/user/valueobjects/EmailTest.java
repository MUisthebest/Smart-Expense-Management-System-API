package com.example.smartmoney.domain.models.user.valueobjects;

import com.example.smartmoney.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void shouldAllowEmailWithinAllowedLength() {
        String email = "a".repeat(64) + "@" + "b".repeat(189);

        assertThatCode(() -> new Email(email)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmailLongerThan254Characters() {
        String email = "a".repeat(64) + "@" + "b".repeat(190);

        assertThatThrownBy(() -> new Email(email))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Email must not exceed 254 characters");
    }

    @Test
    void shouldRejectLocalPartLongerThan64Characters() {
        String email = "a".repeat(65) + "@example.com";

        assertThatThrownBy(() -> new Email(email))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Email local part must not exceed 64 characters");
    }

    @Test
    void shouldRejectDomainPartLongerThan190Characters() {
        String email = "user@" + "a".repeat(191) + ".com";

        assertThatThrownBy(() -> new Email(email))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Email domain part must not exceed 190 characters");
    }
}
