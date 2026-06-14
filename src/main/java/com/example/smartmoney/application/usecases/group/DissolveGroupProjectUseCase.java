package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DissolveGroupProjectUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public void execute(String actorId, String groupProjectId) {
        GroupProject gp = groupProjectRepository.findById(GroupProjectId.of(groupProjectId))
                .orElseThrow(() -> new ProjectException("Group project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can dissolve the group project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        if (gp.getStatus() == GroupProjectStatus.DISSOLVED) {
            throw new ProjectException("Group project is already dissolved",
                    ProjectErrorCode.PROJECT_ALREADY_DISSOLVED);
        }

        List<Project> subProjects = projectRepository.findAllByGroupProjectId(gp.getId().value());

        // Refund and delete only ACTIVE/FROZEN sub-projects.
        // COMPLETED and ABANDONED sub-projects are preserved so members retain their history.
        Set<UserId> ownersToRecalculate = new LinkedHashSet<>();
        for (Project sub : subProjects) {
            if (sub.getStatus() == ProjectStatus.ACTIVE || sub.getStatus() == ProjectStatus.FROZEN) {
                for (ProjectContribution c : sub.getContributions()) {
                    try {
                        Transaction refund = Transaction.create(
                                c.getUserId().value(),
                                new Amount(c.getMoney().amount()),
                                TransactionType.INCOME,
                                Category.PROJECT_REFUND,
                                "Refund: sub-project '" + sub.getName() + "' deleted due to group project dissolution",
                                LocalDateTime.now()
                        );
                        transactionRepository.save(refund);
                    } catch (Exception ex) {
                        log.warn("Failed to issue PROJECT_REFUND for contributor {} on sub-project {}: {}",
                                c.getUserId().value(), sub.getId().value(), ex.getMessage());
                    }
                }
                projectRepository.deleteById(sub.getId());
                ownersToRecalculate.add(sub.getOwnerId());
            }
        }

        // Recalculate safe spending once per owner after all deletions are complete
        for (UserId ownerId : ownersToRecalculate) {
            safeSpendingService.recalculateAndSaveSafeSpending(ownerId);
        }

        gp.dissolve();
        groupProjectRepository.save(gp);

        group.dissolve();
        groupRepository.save(group);
    }
}
