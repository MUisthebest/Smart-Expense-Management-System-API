package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class SponsorshipAllocationService {

    public static class AllocationResult {
        private final boolean feasible;
        private final BigDecimal originalShare;
        private final BigDecimal totalDeficit;
        private final Map<UUID, BigDecimal> proposedShares;

        public AllocationResult(boolean feasible, BigDecimal originalShare, BigDecimal totalDeficit, Map<UUID, BigDecimal> proposedShares) {
            this.feasible = feasible;
            this.originalShare = originalShare;
            this.totalDeficit = totalDeficit;
            this.proposedShares = proposedShares;
        }

        public boolean isFeasible() { return feasible; }
        public BigDecimal getOriginalShare() { return originalShare; }
        public BigDecimal getTotalDeficit() { return totalDeficit; }
        public Map<UUID, BigDecimal> getProposedShares() { return proposedShares; }
    }

    public AllocationResult allocate(BigDecimal targetAmount, int totalMonths, List<GroupMember> members) {
        if (members == null || members.isEmpty() || totalMonths <= 0) {
            return new AllocationResult(false, BigDecimal.ZERO, BigDecimal.ZERO, Map.of());
        }

        int N = members.size();
        BigDecimal originalShare = targetAmount.divide(
                BigDecimal.valueOf(totalMonths).multiply(BigDecimal.valueOf(N)),
                2, RoundingMode.CEILING
        );

        BigDecimal totalDeficit = BigDecimal.ZERO;
        List<GroupMember> helpers = new ArrayList<>();
        Map<UUID, BigDecimal> proposedShares = new HashMap<>();

        for (GroupMember member : members) {
            BigDecimal cap = member.getCapacitySnapshot() != null ? member.getCapacitySnapshot() : BigDecimal.ZERO;
            if (cap.compareTo(originalShare) < 0) {
                // Member has a deficit
                BigDecimal def = originalShare.subtract(cap);
                totalDeficit = totalDeficit.add(def);
                proposedShares.put(member.getUserId().value(), cap); // Contributes their max capacity
            } else {
                helpers.add(member);
                proposedShares.put(member.getUserId().value(), originalShare); // Initial share
            }
        }

        if (totalDeficit.compareTo(BigDecimal.ZERO) == 0) {
            return new AllocationResult(true, originalShare, BigDecimal.ZERO, proposedShares);
        }

        if (helpers.isEmpty()) {
            return new AllocationResult(false, originalShare, totalDeficit, proposedShares);
        }

        // Bounded Equal Split with Spillover algorithm
        BigDecimal D_rem = totalDeficit;
        Map<UUID, BigDecimal> surplusMap = new HashMap<>();
        Map<UUID, BigDecimal> extraContributions = new HashMap<>();
        List<UUID> activeHelpers = new ArrayList<>();

        for (GroupMember h : helpers) {
            BigDecimal cap = h.getCapacitySnapshot() != null ? h.getCapacitySnapshot() : BigDecimal.ZERO;
            surplusMap.put(h.getUserId().value(), cap.subtract(originalShare));
            extraContributions.put(h.getUserId().value(), BigDecimal.ZERO);
            activeHelpers.add(h.getUserId().value());
        }

        while (D_rem.compareTo(BigDecimal.ZERO) > 0 && !activeHelpers.isEmpty()) {
            BigDecimal d_avg = D_rem.divide(BigDecimal.valueOf(activeHelpers.size()), 2, RoundingMode.CEILING);
            List<UUID> cappedThisRound = new ArrayList<>();

            for (UUID helperId : activeHelpers) {
                BigDecimal maxSurplus = surplusMap.get(helperId);
                BigDecimal currentExtra = extraContributions.get(helperId);
                BigDecimal available = maxSurplus.subtract(currentExtra);

                if (available.compareTo(d_avg) < 0) {
                    cappedThisRound.add(helperId);
                }
            }

            if (cappedThisRound.isEmpty()) {
                for (UUID helperId : activeHelpers) {
                    BigDecimal currentExtra = extraContributions.get(helperId);
                    extraContributions.put(helperId, currentExtra.add(d_avg));
                }
                D_rem = BigDecimal.ZERO;
            } else {
                for (UUID helperId : cappedThisRound) {
                    BigDecimal maxSurplus = surplusMap.get(helperId);
                    BigDecimal currentExtra = extraContributions.get(helperId);
                    BigDecimal addable = maxSurplus.subtract(currentExtra);

                    extraContributions.put(helperId, maxSurplus);
                    D_rem = D_rem.subtract(addable);
                }
                activeHelpers.removeAll(cappedThisRound);
            }
        }

        boolean feasible = D_rem.compareTo(BigDecimal.ZERO) <= 0;

        for (GroupMember h : helpers) {
            BigDecimal extra = extraContributions.get(h.getUserId().value());
            proposedShares.put(h.getUserId().value(), originalShare.add(extra));
        }

        return new AllocationResult(feasible, originalShare, totalDeficit, proposedShares);
    }
}
