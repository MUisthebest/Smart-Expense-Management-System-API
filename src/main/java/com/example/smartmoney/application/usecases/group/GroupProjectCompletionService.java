package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Drives group-project completion/expiry <b>without</b> the (deprecated) settlement cron
 * — see docs/redesign-technical-implementation.md §5. Evaluated lazily on read
 * ({@code GetGroupProjectDetailUseCase}) and synchronously when a sub-personal project is
 * contributed to ({@code AddProjectContributionUseCase}).
 *
 * <p>Completion model (unchanged): only still-<b>participating</b> members count — a member
 * whose sub-project is EXPIRED/ABANDONED drops out of <b>both</b> the required target and the
 * aggregate, so the goal shrinks and the rest can still finish. The group COMPLETES when the
 * participating aggregate reaches the participating required target, and EXPIRES when no
 * participating members remain.
 */
@Service
@RequiredArgsConstructor
public class GroupProjectCompletionService {

    private final ProjectRepository projectRepository;
    private final GroupProjectRepository groupProjectRepository;

    /** Loads sub-projects, applies lazy expiry to them, then evaluates the group project. */
    @Transactional
    public GroupProject evaluateById(java.util.UUID groupProjectId) {
        GroupProject gp = groupProjectRepository.findById(
                com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId.of(groupProjectId.toString()))
                .orElse(null);
        if (gp == null) return null;
        List<Project> subs = loadAndExpireSubProjects(gp);
        return evaluate(gp, subs);
    }

    /**
     * Lazily expires each sub-project past its deadline (persisting the transition) and
     * returns the up-to-date list. Shared so callers that already hold the list stay consistent.
     */
    @Transactional
    public List<Project> loadAndExpireSubProjects(GroupProject gp) {
        List<Project> subs = projectRepository.findAllByGroupProjectId(gp.getId().value());
        subs.forEach(p -> {
            if (p.expireIfDeadlinePassed()) {
                projectRepository.save(p);
            }
        });
        return subs;
    }

    /**
     * Applies completion/expiry to an ACTIVE group project given its (already lazily-expired)
     * sub-projects. Persists and returns the updated group project; a no-op for terminal
     * group projects or one with no sub-projects yet.
     */
    @Transactional
    public GroupProject evaluate(GroupProject gp, List<Project> subProjects) {
        if (gp.getStatus() != GroupProjectStatus.ACTIVE || subProjects.isEmpty()) {
            return gp;
        }

        List<Project> participating = subProjects.stream()
                .filter(p -> p.getStatus() != ProjectStatus.EXPIRED
                        && p.getStatus() != ProjectStatus.ABANDONED)
                .toList();

        if (participating.isEmpty()) {
            gp.markExpired();
            return groupProjectRepository.save(gp);
        }

        BigDecimal requiredTarget = participating.stream()
                .map(p -> p.getTargetAmount().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal aggregate = participating.stream()
                .map(Project::netSavedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (requiredTarget.compareTo(BigDecimal.ZERO) > 0
                && aggregate.compareTo(requiredTarget) >= 0) {
            gp.markCompleted();
            return groupProjectRepository.save(gp);
        }

        return gp;
    }
}
