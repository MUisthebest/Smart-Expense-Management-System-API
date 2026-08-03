package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Gives back the money a cancelled project was holding, by writing a single {@code PROJECT_REFUND}
 * INCOME row that reverses the project's {@code PROJECT_CONTRIBUTION} expenses.
 *
 * <p>This is an <b>accounting</b> refund, not a transfer: the app never held the cash. Pressing
 * "contribute" books a real EXPENSE, which represents money the user moved out of spendable and
 * toward a goal. If the goal is then cancelled, that money is free again in real life — so without a
 * reversing row the ledger would claim it was spent forever, distorting net worth and every expense
 * report.
 *
 * <h3>Refund base = the ledger, not {@code sum(contributions)}</h3>
 * Only <b>manual</b> contributions write an EXPENSE row. Settlement's monthly auto-saving appends a
 * {@code ProjectContribution} with <b>no</b> matching ledger row (see
 * docs/settlement-insights-followup.md item 0a), because that money is already implied by
 * {@code income − expenses}. Refunding against {@code sum(contributions)} would therefore mint INCOME
 * out of nothing for every auto-saved unit. Summing the actual {@code PROJECT_CONTRIBUTION} rows
 * sidesteps the problem entirely — no per-contribution "was this auto?" flag needed.
 *
 * <h3>COMPLETED projects are not refunded</h3>
 * Reaching the target means the money did its job: the user saved it up and spent it on the thing.
 * The EXPENSE was already booked at contribution time, so reversing it would double-count the
 * purchase as income. Only cancellation before the goal was met returns money.
 *
 * <p>Settlement already excludes {@code PROJECT_REFUND} from its income sum
 * ({@code PerformMonthlySettlementUseCase}), so a refund never reads as fresh earnings and cannot
 * inflate next month's surplus.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectRefundService {

    private final TransactionRepository transactionRepository;

    /**
     * Reverses this project's contribution expenses, if any remain unrefunded.
     *
     * <p>Runs in the caller's transaction ({@code REQUIRED}) so the refund row and the cancellation
     * that triggered it commit or roll back together — a refund must never outlive a delete that
     * failed. The read-then-write is not guarded against two concurrent cancellations of the same
     * project; both callers delete or abandon the project in the same transaction, which serialises
     * them in practice.
     *
     * @return the amount refunded; {@link BigDecimal#ZERO} when the project is COMPLETED, wrote no
     *         contribution rows, or was already refunded.
     */
    @Transactional
    public BigDecimal refundOnCancellation(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            return BigDecimal.ZERO;
        }

        UUID projectId = project.getId().value();

        // Rows whose projectId is null (pre-press-to-contribute data) are invisible here and so are
        // never refunded. Under-refunding is the safe direction: it can only leave money booked as
        // spent, never invent income.
        List<Transaction> ledger = transactionRepository.findByProjectId(projectId);

        BigDecimal contributed = sumOf(ledger, Category.PROJECT_CONTRIBUTION);
        // Idempotency: a retried cancellation must not refund twice.
        BigDecimal alreadyRefunded = sumOf(ledger, Category.PROJECT_REFUND);
        // Deliberately unrounded. This reverses specific ledger rows, so it must equal them exactly:
        // rounding up could refund a hair more than was ever contributed, and rounding down would
        // strand a remainder that a later call would try to refund again.
        BigDecimal refundable = contributed.subtract(alreadyRefunded);

        if (refundable.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        UUID ownerId = project.getOwnerId().value();
        Transaction refund = Transaction.create(
                ownerId,
                new Amount(refundable),
                TransactionType.INCOME,
                Category.PROJECT_REFUND,
                "Refund from cancelled project " + project.getName(),
                LocalDateTime.now(),
                projectId);
        transactionRepository.save(refund);

        log.info("Refunded {} to user {} from cancelled project {} (contributed {}, previously refunded {})",
                refundable, ownerId, projectId, contributed, alreadyRefunded);

        return refundable;
    }

    private static BigDecimal sumOf(List<Transaction> ledger, Category category) {
        return ledger.stream()
                .filter(t -> t.getCategory() == category)
                .map(t -> t.getAmount().getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
