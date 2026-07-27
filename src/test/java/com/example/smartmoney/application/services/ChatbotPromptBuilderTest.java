package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Structural tests for the chatbot system prompt.
 *
 * <p>These tests deliberately don't run the LLM — they only verify that the prompt CONTAINS
 * the invariants downstream logic assumes: section anchors, the safe_spending formula, the
 * group-project isolation clause, the response format's four parts, and the profile-driven
 * tone rules. That way, a refactor of the prompt that accidentally deletes a clause (as
 * happened in the first rewrite pass) breaks a test instead of silently regressing the LLM.
 *
 * <p>Style: string-contains rather than exact-match, because rephrasings of an existing rule
 * are fine but removing the rule shouldn't be.
 */
@DisplayName("ChatbotPromptBuilder")
class ChatbotPromptBuilderTest {

    private final ChatbotPromptBuilder builder = new ChatbotPromptBuilder();

    // ── Base prompt structure ────────────────────────────────────────────────

    @Nested
    @DisplayName("null user (onboarding not complete) → base prompt only")
    class NullUser {

        @Test
        @DisplayName("null user still returns the full base prompt without the §9 tone block")
        void nullUserReturnsBasePromptWithoutToneBlock() {
            String prompt = builder.build(null);

            // Every top-level section anchor must be present so downstream rules can be found
            assertThat(prompt)
                    .contains("=== §1. MÔ HÌNH NGHIỆP VỤ CỦA ỨNG DỤNG ===")
                    .contains("=== §2. TỪ ĐIỂN DỮ LIỆU")
                    .contains("=== §3. KIỂM TRA CHÉO BẮT BUỘC")
                    .contains("=== §4. CATALOGUE THAO TÁC + DTO")
                    .contains("=== §5. QUY TẮC DỰ ÁN NHÓM")
                    .contains("=== §6. PHÂN LOẠI INTENT")
                    .contains("=== §7. CẤU TRÚC \"reply\"")
                    .contains("=== §8. GIỚI HẠN TUYỆT ĐỐI ===");

            // A null user must NOT get the personalisation block — sending "Saving_Pace:
            // BALANCED" (etc.) as a default lie to a not-yet-onboarded user would let the LLM
            // start giving advice tuned to a persona the user never chose.
            assertThat(prompt).doesNotContain("§9. Phong cách giao tiếp");
        }
    }

    // ── Data dictionary rules — the anti-hallucination anchors ──────────────

    @Nested
    @DisplayName("§2 data dictionary states the safe_spending formula unambiguously")
    class DataDictionary {

        @Test
        @DisplayName("safe_spending is explicitly labelled a PLAN headroom, not a running cash balance")
        void safeSpendingFormulaIsExplicit() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("safe_spending = MAX(0, \"Thu nhập hàng tháng\" − Σ monthlySaving của các dự án ACTIVE)")
                    .contains("HẠN MỨC CHI TIÊU THÁNG THEO KẾ HOẠCH")
                    .contains("KHÔNG phải")
                    // Text block wraps this sentence across two lines — check for the load-bearing
                    // half that fits on one line rather than a phrase that would straddle the break.
                    .contains("safe_spending KHÔNG trừ chi tiêu thực");
        }

        @Test
        @DisplayName("Cash-flow enum values SURPLUS/BALANCED/DEFICIT/UNKNOWN are all defined")
        void cashFlowStatusValuesDefined() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("SURPLUS").contains("BALANCED").contains("DEFICIT");
            // Documented ±10% band matches classifier — keep the number in the prompt so a
            // future prompt reader can see WHY BALANCED covers small overshoots.
            assertThat(prompt).contains("10%");
        }

        @Test
        @DisplayName("linkedToGroup marker is documented in the projects section")
        void linkedToGroupDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("linkedToGroup=true|false");
            assertThat(prompt).contains("SUB-PERSONAL");
        }
    }

    // ── Cross-check rules — the anti-conflict engine ─────────────────────────

    @Nested
    @DisplayName("§3 Rules A/B/C/D each cover their distinct failure mode")
    class CrossCheckRules {

        @Test
        @DisplayName("Rule A explicitly bans saving-boost suggestions on DEFICIT months")
        void ruleABansSavingBoostOnDeficit() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Rule A")
                    .contains("DEFICIT")
                    .contains("KHÔNG khuyên góp thêm cho dự án tiết kiệm");
        }

        @Test
        @DisplayName("Rule B tells the LLM to cross-check safe_spending against actual expense")
        void ruleBCrossChecksAgainstActualExpense() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Rule B")
                    // MIN formula wraps across two lines in the text block — check for the
                    // load-bearing opening that fits on one line.
                    .contains("MIN(safe_spending, Thu nhập");
        }

        @Test
        @DisplayName("Rule C bans non-ACTIVE project suggestions (with delete exception documented separately)")
        void ruleCBansNonActiveProjectSuggestions() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Rule C")
                    .contains("Chỉ đề xuất thao tác lên dự án ACTIVE");
        }

        @Test
        @DisplayName("Rule D references §5 for group-project isolation")
        void ruleDReferencesGroupSection() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("Rule D").contains("linkedToGroup=true").contains("§5");
        }
    }

    // ── DTO catalogue — makes it possible for the LLM to describe changes accurately ─

    @Nested
    @DisplayName("§4 DTO catalogue lists all 8 use cases with real field names")
    class DtoCatalogue {

        @Test
        @DisplayName("BUDGET_UPDATE catalogue mentions UpdateBudgetRequest and its only field amountLimit")
        void budgetUpdateRequestFieldsListed() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("UpdateBudgetRequest")
                    .contains("amountLimit")
                    // Emphasise that spent CAN'T be edited via chat — the LLM used to
                    // occasionally suggest "we can adjust the spent amount" which is not a
                    // supported operation.
                    .contains("KHÔNG dùng chatbot để sửa spent");
        }

        @Test
        @DisplayName("CreateProjectRequest states the deadline-must-be-future constraint")
        void createProjectDeadlineConstraint() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("CreateProjectRequest")
                    .contains("targetAmount")
                    .contains("HÔM NAY HOẶC SAU HÔM NAY");
        }

        @Test
        @DisplayName("AddProjectContributionRequest states amount > 0 and optional note")
        void addProjectContributionFields() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("AddProjectContributionRequest")
                    .contains("amount")
                    .contains("note");
        }

        @Test
        @DisplayName("CreateTransactionRequest states the strict date format")
        void createTransactionDateFormat() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("CreateTransactionRequest")
                    .contains("dd/MM/yyyy HH:mm");
        }
    }

    // ── Group-project isolation ─────────────────────────────────────────────

    @Nested
    @DisplayName("§5 group-project isolation is absolute")
    class GroupProjectRule {

        @Test
        @DisplayName("§5 forbids PROJECT_UPDATE and PROJECT_HYPOTHESIS for linkedToGroup projects")
        void groupProjectRuleForbidsBothIntents() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("KHÔNG đề xuất PROJECT_HYPOTHESIS")
                    .contains("KHÔNG đề xuất PROJECT_UPDATE");
        }

        @Test
        @DisplayName("§5 directs users to the Group screen for structural changes")
        void groupRuleDirectsToGroupScreen() {
            String prompt = builder.build(null);
            // "màn hình\nNhóm" wraps across two lines — check just "màn hình" which alone would
            // never appear in a personal-finance chat context except when referring to the app UI.
            assertThat(prompt).contains("màn hình");
        }

        @Test
        @DisplayName("§5 rule stands even if the user insists")
        void groupRuleIgnoresUserInsistence() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("kể cả người dùng nói \"có\" hay khăng khăng");
        }
    }

    // ── Intent taxonomy — all 8 intents with their nuances ──────────────────

    @Nested
    @DisplayName("§6 intent taxonomy has all 8 intents")
    class IntentTaxonomy {

        @Test
        @DisplayName("all 8 intents are listed with their trigger conditions")
        void allEightIntentsPresent() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("OUT_OF_SCOPE")
                    .contains("INCOME_WINDFALL")
                    .contains("LARGE_EXPENSE")
                    .contains("PROJECT_HYPOTHESIS")
                    .contains("PROJECT_UPDATE")
                    .contains("BUDGET_UPDATE")
                    .contains("SIMULATION")
                    .contains("GENERAL_ADVICE");
        }

        @Test
        @DisplayName("INCOME_WINDFALL (2c) documents the three card shapes A/B/C (savings-only, budget-only, split)")
        void windfallCardShapesDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("(2c)")
                    .contains("Chỉ SAVING")
                    .contains("Chỉ BUDGET boost")
                    .contains("CẢ HAI");
            // Emphasise the "don't invent 60/40 language" rule that was previously implicit
            assertThat(prompt).contains("KHÔNG mặc định phát biểu \"chia 60/40\"");
        }

        @Test
        @DisplayName("LARGE_EXPENSE X > Y is split into 'income enough' vs 'income not enough' with tone escalation")
        void largeExpenseSplitSubCases() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("X > Y")
                    .contains("THU NHẬP THÁNG ĐỦ")
                    .contains("THU NHẬP THÁNG CŨNG KHÔNG ĐỦ")
                    // Never refuse — LARGE_EXPENSE is advisory, not gate-keeping
                    .contains("KHÔNG TỪ CHỐI");
        }

        @Test
        @DisplayName("PROJECT_HYPOTHESIS distinguishes 'tháng sau as new deadline' vs 'wait until next month to decide'")
        void projectHypothesisDeferralNuance() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("CHỜ tới tháng sau MỚI quyết định")
                    .contains("THỜI HẠN MỚI của dự án");
        }

        @Test
        @DisplayName("SIMULATION documents both no-shortfall (no card) and shortfall (card) branches")
        void simulationBranchesDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("newSafeSpending >= 0")
                    .contains("newSafeSpending < 0")
                    .contains("KHÔNG có thẻ đề xuất")
                    // The card-attach phrase wraps in the text block; check the load-bearing
                    // "cắt giảm ngân sách" which appears on one line in the shortfall branch.
                    .contains("cắt giảm ngân sách");
        }

        @Test
        @DisplayName("BUDGET_UPDATE cut branch always targets next month regardless of days-left")
        void budgetUpdateCutTargetsNextMonth() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Cắt giảm")
                    .contains("THÁNG TIẾP THEO");
        }
    }

    // ── Response format — one canonical structure ───────────────────────────

    @Nested
    @DisplayName("§7 response format enforces Tổng quan / Chi tiết / Đề xuất / Kết luận")
    class ResponseFormat {

        @Test
        @DisplayName("all four canonical sections are named")
        void fourSectionsNamed() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Tổng quan")
                    .contains("Chi tiết")
                    .contains("Đề xuất")
                    .contains("Kết luận");
        }

        @Test
        @DisplayName("each section must be its own paragraph — no more seamless single-block prose")
        void sectionsMustBeSeparateParagraphs() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("MỖI PHẦN LÀ MỘT ĐOẠN VĂN RIÊNG")
                    .contains("TUYỆT ĐỐI KHÔNG viết gộp 4 phần thành một khối văn xuôi liền mạch");
        }

        @Test
        @DisplayName("future-hypothetical turns must carry the 'not real data yet' disclaimer in Kết luận")
        void hypotheticalTurnsCarryDisclaimer() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("GIẢ ĐỊNH TƯƠNG LAI")
                    .contains("Đây là giả định tương lai")
                    .contains("cần phải tham khảo số liệu thực tế")
                    .contains("This is a future")
                    .contains("LUÔN đi kèm")
                    .contains("không tuỳ chọn");
        }

        @Test
        @DisplayName("closing confirmation question is gated on 'card actually attached', not on intent name")
        void closingQuestionGatedOnCardPresence() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("CHỈ khi và chỉ khi lượt này thực sự có thẻ")
                    .contains("pendingActionId + actionRequired=true");
        }

        @Test
        @DisplayName("LARGE_EXPENSE and hypothetical/deferred windfall are explicitly listed as no-question")
        void largeExpenseAndHypotheticalWindfallForbidClosingQuestion() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("LARGE_EXPENSE (mọi trạng thái con)");
            assertThat(prompt).contains("INCOME_WINDFALL (2a) giả định tương lai và (2b) đã nhận nhưng hoãn");
        }

        @Test
        @DisplayName("SIMULATION-with-shortfall is listed as card-carrying (fix for R1 regression)")
        void simulationShortfallIsCardCarrying() {
            String prompt = builder.build(null);
            // This is the critical fix from the second audit pass — SIMULATION was previously
            // in the "no question" list, but the code auto-attaches a budget cut card on
            // shortfall. Prompt now allows the closing question in that case only.
            assertThat(prompt)
                    .contains("SIMULATION khi kịch bản gây thiếu hụt")
                    .contains("SIMULATION khi KHÔNG có thiếu hụt (không có thẻ)");
        }

        @Test
        @DisplayName("Đề xuất section is optional for NOTIFY/TRACK_ONLY when no card and no direct ask")
        void adviceSectionOptionalForLowInterventionProfiles() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Focus_Mode = TRACK_ONLY hoặc Intervention_Level = NOTIFY")
                    .contains("BỎ QUA phần Đề xuất");
        }

        @Test
        @DisplayName("Reply length caps: 250 default, 150 for NOTIFY/TRACK_ONLY")
        void replyLengthCaps() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("tối đa 250 từ").contains("tối đa 150 từ");
        }
    }

    // ── Tone block (§9) — profile-driven, appended only when user is non-null ───

    @Nested
    @DisplayName("§9 tone block reacts independently to each profile axis")
    class ToneBlock {

        // Helpers so tests only vary the axis under test.
        private User userWith(Saving_Pace pace, Intervention_Level intervention, Focus_Mode focus,
                              boolean autoInvest) {
            User user = mock(User.class);
            when(user.getSavingPace()).thenReturn(pace);
            when(user.getInterventionLevel()).thenReturn(intervention);
            when(user.getFocusMode()).thenReturn(focus);
            when(user.isAutoInvestSurplus()).thenReturn(autoInvest);
            when(user.getIncome()).thenReturn(BigDecimal.valueOf(10_000_000));
            return user;
        }

        @Test
        @DisplayName("Non-null user gets the §9 tone block appended")
        void nonNullUserGetsToneBlock() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.SAVE_MORE, false));
            assertThat(prompt).contains("§9. Phong cách giao tiếp dành riêng cho người dùng này");
        }

        @Test
        @DisplayName("Saving_Pace.RELAXED produces gentle tone + light LARGE_EXPENSE warning hint")
        void relaxedPaceTone() {
            String prompt = builder.build(
                    userWith(Saving_Pace.RELAXED, Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY, false));
            assertThat(prompt)
                    .contains("Tốc độ tiết kiệm: RELAXED")
                    .contains("nhẹ nhàng")
                    // LARGE_EXPENSE tone hook — the prompt tells the LLM which register to use
                    // when it hits §6 LARGE_EXPENSE X > Y branch.
                    .contains("cảnh báo nhẹ nhàng nhưng đủ số liệu");
        }

        @Test
        @DisplayName("Saving_Pace.AGGRESSIVE produces direct tone + escalated LARGE_EXPENSE warning hint")
        void aggressivePaceTone() {
            String prompt = builder.build(
                    userWith(Saving_Pace.AGGRESSIVE, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, false));
            assertThat(prompt)
                    .contains("Tốc độ tiết kiệm: AGGRESSIVE")
                    .contains("thẳng thắn")
                    .contains("nêu thẳng rủi ro nếu chi vượt tái diễn");
        }

        @Test
        @DisplayName("Intervention_Level.NOTIFY shortens reply and allows dropping Đề xuất")
        void notifyInterventionReplyShortening() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.NOTIFY, Focus_Mode.TRACK_ONLY, false));
            assertThat(prompt)
                    .contains("Mức can thiệp: NOTIFY")
                    .contains("<=150 từ")
                    .contains("bỏ phần Đề xuất theo §7");
        }

        @Test
        @DisplayName("Focus_Mode.REDUCE_SPENDING signals savings-only preference for windfall")
        void reduceSpendingFocusWindfallHint() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.REDUCE_SPENDING, false));
            assertThat(prompt)
                    .contains("Chế độ tập trung: GIẢM CHI TIÊU")
                    .contains("ưu tiên đưa windfall vào tiết kiệm khi có dư địa");
        }

        @Test
        @DisplayName("Focus_Mode.SAVE_MORE signals savings-only preference for windfall")
        void saveMoreFocusWindfallHint() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.SAVE_MORE, false));
            assertThat(prompt)
                    .contains("Chế độ tập trung: TIẾT KIỆM NHIỀU HƠN")
                    .contains("ưu tiên đẩy toàn bộ vào tiết kiệm khi có dư địa");
        }

        @Test
        @DisplayName("Focus_Mode.TRACK_ONLY explicitly allows dropping Đề xuất")
        void trackOnlyFocusDropsAdvice() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY, false));
            assertThat(prompt)
                    .contains("Chế độ tập trung: CHỈ THEO DÕI")
                    .contains("Được phép bỏ phần Đề xuất");
        }

        @Test
        @DisplayName("autoInvestSurplus=true adds its own block referring to windfall preference")
        void autoInvestSurplusTrueAddsBlock() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY, true));
            assertThat(prompt)
                    .contains("Tự động đầu tư phần dư: BẬT")
                    .contains("đẩy về ")
                    .contains("tiết kiệm khi safe_spending có dư địa thoải mái");
        }

        @Test
        @DisplayName("autoInvestSurplus=false does NOT emit an auto-invest block (prevents null-noise for opted-out users)")
        void autoInvestSurplusFalseOmitsBlock() {
            String prompt = builder.build(
                    userWith(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY, false));
            assertThat(prompt).doesNotContain("Tự động đầu tư phần dư: BẬT");
        }
    }

    // ── JSON output format ──────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON_OUTPUT_FORMAT")
    class JsonOutputFormat {

        @Test
        @DisplayName("relatedQuestions rule lists all intents that should return an empty array")
        void relatedQuestionsSuppressionRule() {
            assertThat(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT)
                    .contains("OUT_OF_SCOPE")
                    .contains("LARGE_EXPENSE (mọi trạng thái con")
                    .contains("INCOME_WINDFALL loại (2a)")
                    .contains("(2b)")
                    .contains("SIMULATION không có thiếu hụt");
        }

        @Test
        @DisplayName("JSON schema still names the same three fields expected by JsonSchemaGuardrail")
        void schemaFieldsUnchanged() {
            // Downstream JsonSchemaGuardrail expects exactly these three keys — a rename here
            // would break parsing without any type-level warning.
            assertThat(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT)
                    .contains("\"intent\":")
                    .contains("\"reply\":")
                    .contains("\"relatedQuestions\":");
        }
    }
}