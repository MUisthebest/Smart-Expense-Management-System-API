package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.application.usecases.group.GroupProjectCompletionService;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddProjectContributionUseCase — press-to-contribute")
class AddProjectContributionUseCaseTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private GroupProjectCompletionService groupProjectCompletionService;

    private AddProjectContributionUseCase useCase;

    private UUID ownerUuid;
    private String ownerId;

    @BeforeEach
    void setUp() {
        useCase = new AddProjectContributionUseCase(projectRepository, transactionRepository, groupProjectCompletionService);
        ownerUuid = UUID.randomUUID();
        ownerId = ownerUuid.toString();
        lenient().when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Project activeProject(BigDecimal target) {
        return Project.createNew(UserId.of(ownerId), ProjectType.PERSONAL, ProjectPriority.MEDIUM,
                "Trip", null, new Money(target, "VND"), LocalDate.now().plusMonths(6));
    }

    private AddProjectContributionRequest req(BigDecimal amount, String note) {
        AddProjectContributionRequest r = new AddProjectContributionRequest();
        r.setAmount(amount);
        r.setNote(note);
        return r;
    }

    @Test
    @DisplayName("Records a real EXPENSE/PROJECT_CONTRIBUTION transaction carrying the project id and moves progress")
    void recordsLedgerTransactionAndAdvancesProgress() {
        Project project = activeProject(new BigDecimal("1000"));
        String projectId = project.getId().toString();
        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));

        ProjectDetailResponse res = useCase.execute(ownerId, projectId, req(new BigDecimal("250"), "first"));

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction tx = txCaptor.getValue();
        assertEquals(TransactionType.EXPENSE, tx.getType());
        assertEquals(Category.PROJECT_CONTRIBUTION, tx.getCategory());
        assertEquals(UUID.fromString(projectId), tx.getProjectId());
        assertEquals(ownerUuid, tx.getUserId());
        assertEquals(0, tx.getAmount().getValue().compareTo(new BigDecimal("250")));

        assertEquals(0, res.getTotalContributed().compareTo(new BigDecimal("250")));
        assertEquals(0, res.getRemaining().compareTo(new BigDecimal("750")));
    }

    @Test
    @DisplayName("Completes the project synchronously when the target is reached")
    void completesWhenTargetReached() {
        Project project = activeProject(new BigDecimal("500"));
        String projectId = project.getId().toString();
        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));

        ProjectDetailResponse res = useCase.execute(ownerId, projectId, req(new BigDecimal("500"), null));

        assertEquals("COMPLETED", res.getStatus());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    @DisplayName("Rejects a non-owner and writes no ledger transaction")
    void rejectsNonOwner() {
        Project project = activeProject(new BigDecimal("1000"));
        String projectId = project.getId().toString();
        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));

        String strangerId = UUID.randomUUID().toString();
        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(strangerId, projectId, req(new BigDecimal("10"), null)));
        assertEquals(ProjectErrorCode.PROJECT_PERMISSION_DENIED.name(), ex.getErrorCode());
        verify(transactionRepository, never()).save(any());
        verify(projectRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lazily expires and rejects contributing to a project past its deadline")
    void rejectsExpiredByDeadline() {
        // Build a project with a future deadline, then force the deadline into the past
        // via reflection-free rehydrate so validateNew() doesn't block construction.
        Project project = Project.rehydrate(
                ProjectId.newId(),
                UserId.of(ownerId),
                ProjectType.PERSONAL,
                com.example.smartmoney.domain.models.project.enums.ProjectStatus.ACTIVE,
                ProjectPriority.MEDIUM,
                "Trip", null,
                new Money(new BigDecimal("1000"), "VND"),
                LocalDate.now().minusDays(1),
                null, java.util.List.of(), null, java.time.Instant.now());
        String projectId = project.getId().toString();
        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(ownerId, projectId, req(new BigDecimal("10"), null)));
        assertEquals(ProjectErrorCode.PROJECT_EXPIRED.name(), ex.getErrorCode());
        // The expiry transition is persisted; no ledger transaction is written.
        verify(projectRepository).save(project);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects contributing to a frozen project")
    void rejectsFrozenProject() {
        Project project = activeProject(new BigDecimal("1000"));
        project.freeze(UserId.of(ownerId));
        String projectId = project.getId().toString();
        when(projectRepository.findById(ProjectId.of(projectId))).thenReturn(Optional.of(project));

        ProjectException ex = assertThrows(ProjectException.class,
                () -> useCase.execute(ownerId, projectId, req(new BigDecimal("10"), null)));
        assertEquals(ProjectErrorCode.PROJECT_FROZEN.name(), ex.getErrorCode());
        verify(transactionRepository, never()).save(any());
    }
}
