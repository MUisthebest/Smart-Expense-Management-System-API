package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ProjectHypothesisService")
class ProjectHypothesisServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnvelopeCalculator envelopeCalculator = mock(EnvelopeCalculator.class);
    private final ProjectHypothesisService service =
            new ProjectHypothesisService(projectRepository, userRepository, envelopeCalculator);

    private Project project(String name, BigDecimal moneySaved, BigDecimal monthlySaving,
                             int monthLeft, LocalDate deadline) {
        return project(name, moneySaved, monthlySaving, monthLeft, deadline, ProjectStatus.ACTIVE);
    }

    private Project project(String name, BigDecimal moneySaved, BigDecimal monthlySaving,
                             int monthLeft, LocalDate deadline, ProjectStatus status) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, moneySaved, 1, monthlySaving, monthLeft,
                BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        return Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                status, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                deadline, null, List.of(), tracking, Instant.now());
    }

    @Test
    @DisplayName("previewShortenOrExtend shortens a project's deadline and raises its monthly saving")
    void shortenComputesHigherMonthlySaving() {
        Project p = project("Mua nhà", BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(1_500_000),
                12, LocalDate.now().plusMonths(12));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewShortenOrExtend(UUID.randomUUID(),
                "tôi muốn rút ngắn dự án mua nhà 6 tháng");

        assertThat(result).isPresent();
        var s = result.get();
        assertThat(s.getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.SHORTEN);
        assertThat(s.getNewDeadline()).isBefore(p.getDeadline());
        assertThat(s.getNewMonthlySaving()).isGreaterThan(s.getCurrentMonthlySaving());
    }

    @Test
    @DisplayName("previewShortenOrExtend extends a project's deadline and lowers its monthly saving")
    void extendComputesLowerMonthlySaving() {
        Project p = project("Mua nhà", BigDecimal.valueOf(2_000_000), BigDecimal.valueOf(1_500_000),
                12, LocalDate.now().plusMonths(12));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewShortenOrExtend(UUID.randomUUID(),
                "tôi muốn kéo dài dự án mua nhà thêm 6 tháng");

        assertThat(result).isPresent();
        var s = result.get();
        assertThat(s.getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.EXTEND);
        assertThat(s.getNewDeadline()).isAfter(p.getDeadline());
        assertThat(s.getNewMonthlySaving()).isLessThan(s.getCurrentMonthlySaving());
    }

    @Test
    @DisplayName("previewShortenOrExtend ignores an ABANDONED project even when it's the only one and is named")
    void ignoresAbandonedProjectEvenWhenNamed() {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, BigDecimal.ZERO, 1, BigDecimal.valueOf(1_500_000),
                12, BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        Project abandoned = Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                ProjectStatus.ABANDONED, ProjectPriority.HIGH, "Mua nhà", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(abandoned));

        assertThat(service.previewShortenOrExtend(UUID.randomUUID(),
                "tôi muốn rút ngắn dự án mua nhà 6 tháng")).isEmpty();
    }

    @Test
    @DisplayName("previewShortenOrExtend is empty without a duration or direction keyword")
    void noDurationNoSuggestion() {
        Project p = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(1_000_000),
                12, LocalDate.now().plusMonths(12));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        assertThat(service.previewShortenOrExtend(UUID.randomUUID(), "dự án mua nhà của tôi thế nào?"))
                .isEmpty();
    }

    @Test
    @DisplayName("previewShortenOrExtend bails out when both directions match (ambiguous) instead of guessing")
    void bothDirectionsMatchIsAmbiguousNotAGuess() {
        Project p = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(1_000_000),
                12, LocalDate.now().plusMonths(12));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        // Contains both a SHORTEN keyword ("rút ngắn") and an EXTEND keyword ("kéo dài") for the
        // same single project mention — must not silently pick one and risk proposing the
        // opposite of what the user meant.
        assertThat(service.previewShortenOrExtend(UUID.randomUUID(),
                "tôi không chắc nên rút ngắn hay kéo dài dự án mua nhà 3 tháng")).isEmpty();
    }

    @Test
    @DisplayName("looksLikeStructuralChangeRequest detects shorten/extend/create/delete phrasing")
    void looksLikeStructuralChangeRequestDetectsKeywords() {
        assertThat(service.looksLikeStructuralChangeRequest("tôi muốn rút ngắn dự án mua xe 3 tháng")).isTrue();
        assertThat(service.looksLikeStructuralChangeRequest("kéo dài dự án du lịch thêm 2 tháng")).isTrue();
        assertThat(service.looksLikeStructuralChangeRequest("tạo dự án mới mua xe 200 triệu")).isTrue();
        assertThat(service.looksLikeStructuralChangeRequest("xóa dự án du lịch giúp tôi")).isTrue();
        assertThat(service.looksLikeStructuralChangeRequest("ngân sách tháng này của tôi thế nào?")).isFalse();
    }

    @Test
    @DisplayName("previewDelete frees the project's monthly saving without changing available budget")
    void deleteFreesMonthlySavingBudgetUnchanged() {
        Project p = project("Du lịch", BigDecimal.ZERO, BigDecimal.valueOf(800_000),
                10, LocalDate.now().plusMonths(10));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewDelete(UUID.randomUUID(), "xóa dự án du lịch giúp tôi");

        assertThat(result).isPresent();
        var s = result.get();
        assertThat(s.getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.DELETE);
        assertThat(s.getCurrentMonthlySaving()).isEqualTo(800_000L);
        assertThat(s.getAvailableBudgetBefore()).isEqualTo(s.getAvailableBudgetAfter());
    }

    @Test
    @DisplayName("previewDelete now allows deleting a COMPLETED project (unlike contribute/shorten/extend) — "
            + "deletion is a valid cleanup action regardless of status")
    void deleteAllowsCompletedProject() {
        Project p = project("Mua nhà", BigDecimal.valueOf(20_000_000), BigDecimal.ZERO,
                0, LocalDate.now().plusMonths(1), ProjectStatus.COMPLETED);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewDelete(UUID.randomUUID(), "xóa dự án mua nhà giúp tôi");

        assertThat(result).isPresent();
        assertThat(result.get().getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.DELETE);
        assertThat(result.get().getProjectName()).isEqualTo("Mua nhà");
    }

    @Test
    @DisplayName("previewDelete also matches plural/count phrasing like \"xóa các dự án\" / \"delete the project\"")
    void deleteMatchesPluralPhrasing() {
        Project p = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(500_000),
                10, LocalDate.now().plusMonths(10));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.previewDelete(UUID.randomUUID(), "xóa các dự án mua nhà giúp tôi")).isPresent();
        assertThat(service.previewDelete(UUID.randomUUID(), "please delete the mua nhà project")).isPresent();
    }

    @Test
    @DisplayName("previewDeleteByStatus deletes ALL projects matching a stated status category, "
            + "not just one named project")
    void deleteByStatusDeletesAllMatchingProjects() {
        Project completedA = project("Mua nhà", BigDecimal.valueOf(20_000_000), BigDecimal.ZERO,
                0, LocalDate.now().plusMonths(1), ProjectStatus.COMPLETED);
        Project completedB = project("Mua xe", BigDecimal.valueOf(10_000_000), BigDecimal.ZERO,
                0, LocalDate.now().plusMonths(1), ProjectStatus.COMPLETED);
        Project stillActive = project("Du lịch", BigDecimal.ZERO, BigDecimal.valueOf(500_000),
                10, LocalDate.now().plusMonths(10), ProjectStatus.ACTIVE);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(completedA, completedB, stillActive));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewDeleteByStatus(UUID.randomUUID(), "xóa các dự án đã hoàn thành");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChatResponse.ProjectChangeSuggestion::getProjectName)
                .containsExactlyInAnyOrder("Mua nhà", "Mua xe");
        assertThat(result).allMatch(s -> s.getOperation() == ChatResponse.ProjectChangeOperation.DELETE);
    }

    @Test
    @DisplayName("previewDeleteByStatus is empty when no status word is present (falls through to single-target delete)")
    void deleteByStatusEmptyWithoutStatusWord() {
        Project p = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(500_000),
                10, LocalDate.now().plusMonths(10));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        assertThat(service.previewDeleteByStatus(UUID.randomUUID(), "xóa dự án mua nhà giúp tôi")).isEmpty();
    }

    @Test
    @DisplayName("previewCreate parses amount + duration and computes ceiling monthly saving")
    void createParsesAmountAndDuration() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        var result = service.previewCreate(UUID.randomUUID(),
                "tạo dự án mua xe với mục tiêu 240 triệu trong 24 tháng");

        assertThat(result).isPresent();
        var s = result.get();
        assertThat(s.getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.CREATE);
        assertThat(s.getTargetAmount()).isEqualTo(240_000_000L);
        // monthsToDeadline is inclusive of the current month (same convention as
        // CreateProjectUseCase/UpdateProjectUseCase), so "24 tháng" from today is 25 months.
        assertThat(s.getNewMonthlySaving()).isEqualTo(9_600_000L);
    }

    @Test
    @DisplayName("previewCreate is empty without a create keyword")
    void createRequiresKeyword() {
        assertThat(service.previewCreate(UUID.randomUUID(), "mục tiêu 240 triệu trong 24 tháng"))
                .isEmpty();
    }

    // ── pickAvailablePriorityForNewProject ───────────────────────────────────

    @Test
    @DisplayName("pickAvailablePriorityForNewProject picks the lowest-ordinal priority not already "
            + "used by an ACTIVE project — CreateProjectUseCase rejects a duplicate ACTIVE priority, "
            + "so defaulting every chat-created project to MEDIUM would fail confirm as soon as the "
            + "user already has one ACTIVE MEDIUM project")
    void picksUnusedPriorityAmongActiveProjects() {
        Project mediumActive = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(500_000),
                12, LocalDate.now().plusMonths(12));
        // project() helper hardcodes HIGH — rehydrate one explicitly at MEDIUM to exercise the
        // "MEDIUM already taken" case this bug fix targets.
        Project mediumTaken = Project.rehydrate(
                ProjectId.newId(), UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.MEDIUM, "Mua xe", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(),
                new ProjectTracking(UUID.randomUUID(), ProjectId.newId(), BigDecimal.ZERO, 1,
                        BigDecimal.valueOf(500_000), 12, BigDecimal.ZERO, 0, Instant.now(), Instant.now()),
                Instant.now());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(mediumActive, mediumTaken));

        ProjectPriority picked = service.pickAvailablePriorityForNewProject(UUID.randomUUID());

        assertThat(picked).isNotEqualTo(ProjectPriority.MEDIUM);
        assertThat(picked).isEqualTo(ProjectPriority.LOW);
    }

    @Test
    @DisplayName("pickAvailablePriorityForNewProject ignores a COMPLETED/ABANDONED project's priority "
            + "— only ACTIVE projects occupy a priority slot")
    void ignoresNonActiveProjectPriority() {
        Project completedHigh = project("Cũ", BigDecimal.ZERO, BigDecimal.valueOf(500_000),
                12, LocalDate.now().plusMonths(12), ProjectStatus.COMPLETED);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(completedHigh));

        ProjectPriority picked = service.pickAvailablePriorityForNewProject(UUID.randomUUID());

        // HIGH is the lowest ordinal only if COMPLETED's HIGH priority doesn't count — assert the
        // full unused set is still considered from scratch (LOW is ProjectPriority's ordinal 0).
        assertThat(picked).isEqualTo(ProjectPriority.LOW);
    }

    @Test
    @DisplayName("previewMultiProject applies simultaneous changes and shows one combined budget impact")
    void multiProjectCombinedImpact() {
        Project a = project("Mua nhà", BigDecimal.ZERO, BigDecimal.valueOf(2_000_000),
                12, LocalDate.now().plusMonths(12));
        Project b = project("Du lịch", BigDecimal.ZERO, BigDecimal.valueOf(1_000_000),
                10, LocalDate.now().plusMonths(10));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(a, b));
        User user = mock(User.class);
        when(user.hasCompletedFinancialSetup()).thenReturn(true);
        when(user.calculateSafeSpending(any())).thenAnswer(inv ->
                BigDecimal.valueOf(20_000_000).subtract(inv.getArgument(0)));
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        var result = service.previewMultiProject(UUID.randomUUID(),
                "tôi muốn rút ngắn dự án mua nhà 3 tháng và kéo dài dự án du lịch thêm 2 tháng");

        assertThat(result).hasSize(2);
        // Every entry in the batch reports the same combined before/after numbers.
        Long before = result.get(0).getAvailableBudgetBefore();
        Long after = result.get(0).getAvailableBudgetAfter();
        assertThat(result.get(1).getAvailableBudgetBefore()).isEqualTo(before);
        assertThat(result.get(1).getAvailableBudgetAfter()).isEqualTo(after);
        assertThat(before).isNotNull();
        assertThat(after).isNotNull();
    }
}
