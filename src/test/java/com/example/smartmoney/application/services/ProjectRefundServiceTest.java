package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectRefundService")
class ProjectRefundServiceTest {

    @Mock private TransactionRepository transactionRepository;

    private ProjectRefundService service;

    private final UUID ownerUuid = UUID.randomUUID();
    private final ProjectId pid = ProjectId.newId();

    @BeforeEach
    void setUp() {
        service = new ProjectRefundService(transactionRepository);
    }

    private Project project(ProjectStatus status) {
        return Project.rehydrate(pid, new UserId(ownerUuid), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Trip", null, new Money(new BigDecimal("3000"), "USD"),
                LocalDate.now().plusMonths(5), null, List.of(), null, Instant.now());
    }

    private Transaction ledgerRow(BigDecimal amount, TransactionType type, Category category) {
        return Transaction.create(ownerUuid, new Amount(amount), type, category,
                "row", LocalDateTime.now(), pid.value());
    }

    private Transaction contribution(String amount) {
        return ledgerRow(new BigDecimal(amount), TransactionType.EXPENSE, Category.PROJECT_CONTRIBUTION);
    }

    private Transaction refund(String amount) {
        return ledgerRow(new BigDecimal(amount), TransactionType.INCOME, Category.PROJECT_REFUND);
    }

    @Test
    @DisplayName("refunds the summed contribution rows as a single PROJECT_REFUND income")
    void refundsSumOfContributions() {
        when(transactionRepository.findByProjectId(pid.value()))
                .thenReturn(List.of(contribution("200"), contribution("350.50")));

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.ACTIVE));

        assertThat(refunded).isEqualByComparingTo("550.50");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction written = captor.getValue();
        assertThat(written.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(written.getCategory()).isEqualTo(Category.PROJECT_REFUND);
        assertThat(written.getUserId()).isEqualTo(ownerUuid);
        assertThat(written.getProjectId()).isEqualTo(pid.value());
        assertThat(written.getAmount().getValue()).isEqualByComparingTo("550.50");
    }

    @Test
    @DisplayName("settlement auto-savings are not refunded — only rows that hit the ledger count")
    void ignoresAutoSavingsWithNoLedgerRow() {
        // The project's contribution list may total far more than the ledger (settlement's
        // recordAutoSaving writes no transaction). Only the one manual row is refundable.
        when(transactionRepository.findByProjectId(pid.value()))
                .thenReturn(List.of(contribution("100")));

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.ACTIVE));

        assertThat(refunded).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("COMPLETED project is not refunded — the money was spent on the goal")
    void completedProjectNotRefunded() {
        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.COMPLETED));

        assertThat(refunded).isEqualByComparingTo("0");
        verify(transactionRepository, never()).findByProjectId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a project with no contribution rows writes nothing")
    void noContributions_writesNothing() {
        when(transactionRepository.findByProjectId(pid.value())).thenReturn(List.of());

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.ACTIVE));

        assertThat(refunded).isEqualByComparingTo("0");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("an already-refunded project is not refunded twice")
    void idempotent_whenAlreadyRefunded() {
        when(transactionRepository.findByProjectId(pid.value()))
                .thenReturn(List.of(contribution("200"), refund("200")));

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.ABANDONED));

        assertThat(refunded).isEqualByComparingTo("0");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("only the unrefunded remainder is returned after a partial refund")
    void refundsOnlyTheRemainder() {
        when(transactionRepository.findByProjectId(pid.value()))
                .thenReturn(List.of(contribution("500"), refund("200")));

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.FROZEN));

        assertThat(refunded).isEqualByComparingTo("300");
        verify(transactionRepository).save(any());
    }

    @Test
    @DisplayName("unrelated categories on the project's ledger are ignored")
    void ignoresUnrelatedCategories() {
        when(transactionRepository.findByProjectId(pid.value()))
                .thenReturn(List.of(
                        contribution("100"),
                        ledgerRow(new BigDecimal("999"), TransactionType.EXPENSE, Category.FOOD)));

        BigDecimal refunded = service.refundOnCancellation(project(ProjectStatus.ACTIVE));

        assertThat(refunded).isEqualByComparingTo("100");
    }
}
