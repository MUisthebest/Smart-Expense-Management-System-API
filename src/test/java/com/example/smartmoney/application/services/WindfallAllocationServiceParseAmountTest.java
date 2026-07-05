package com.example.smartmoney.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link WindfallAllocationService#parseAmount(String)}.
 * Fields are unused by this method, so repositories are safely passed as null.
 */
@DisplayName("WindfallAllocationService.parseAmount")
class WindfallAllocationServiceParseAmountTest {

    private final WindfallAllocationService service = new WindfallAllocationService(null, null);

    @Test
    @DisplayName("parses \"tỷ\" (billion) units")
    void parsesBillion() {
        assertThat(service.parseAmount("tôi trúng 2 tỷ hôm nay"))
                .isEqualByComparingTo(new BigDecimal("2000000000"));
    }

    @Test
    @DisplayName("parses \"triệu\" (million) units")
    void parsesMillion() {
        assertThat(service.parseAmount("thưởng 5 triệu"))
                .isEqualByComparingTo(new BigDecimal("5000000"));
    }

    @Test
    @DisplayName("falls back to zero when no parseable amount is present")
    void noAmountFound() {
        assertThat(service.parseAmount("tôi vừa trúng thưởng"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
