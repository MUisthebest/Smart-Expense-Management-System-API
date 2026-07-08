package com.example.smartmoney.domain.models.user;

import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("User.updateFinancialSetup — autoInvestSurplus handling")
class UserTest {

    private void setup(User u, Boolean autoInvestSurplus) {
        u.updateFinancialSetup(new BigDecimal("1000"),
                Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING,
                autoInvestSurplus);
    }

    @Test
    @DisplayName("Never configured → defaults to true")
    void defaultsToTrueWhenNeverSet() {
        User u = User.builder().build();
        setup(u, null);
        assertTrue(u.isAutoInvestSurplus());
    }

    @Test
    @DisplayName("Explicit false is honoured")
    void explicitFalseHonoured() {
        User u = User.builder().build();
        setup(u, false);
        assertFalse(u.isAutoInvestSurplus());
    }

    @Test
    @DisplayName("A later update that omits the flag (null) preserves the existing false preference")
    void nullUpdatePreservesExistingPreference() {
        User u = User.builder().build();
        setup(u, false);
        assertFalse(u.isAutoInvestSurplus());

        setup(u, null); // e.g. an update that changes other fields but omits autoInvestSurplus
        assertFalse(u.isAutoInvestSurplus()); // must NOT silently flip back on
    }
}
