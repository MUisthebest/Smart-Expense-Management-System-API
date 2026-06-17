package com.example.smartmoney.domain.exceptions;

import java.math.BigDecimal;

/**
 * Raised when a user attempts to take on a new monthly financial obligation
 * (new project, raised monthly saving, accepting a group invite, etc.) that
 * their current safe spending cannot absorb.
 *
 * <p>Privacy note: when thrown during {@code AcceptProjectInviteUseCase},
 * the financial details ({@link #getRequested()}, {@link #getAvailable()})
 * must only ever be surfaced to the invitee themselves, never to the project
 * admin who issued the invite.
 */
public class InsufficientSafeSpendingException extends BusinessException {

    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientSafeSpendingException(BigDecimal requested, BigDecimal available) {
        super(
                "Insufficient safe spending: requested " + requested + ", available " + available,
                ProjectErrorCode.INSUFFICIENT_SAFE_SPENDING.name()
        );
        this.requested = requested;
        this.available = available;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }
}
