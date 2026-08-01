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

        @Test
        @DisplayName("§0 states the full-permission principle and the execute-vs-guide split")
        void fullPermissionPrincipleStated() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("NGƯỜI DÙNG TOÀN QUYỀN VỚI DỮ LIỆU CỦA CHÍNH HỌ")
                    .contains("LUÔN CHẤP NHẬN yêu cầu")
                    // The distinction the whole rewrite hinges on: not-executable-here is a
                    // statement about mechanism, never about permission.
                    .contains("khác biệt về CÁCH THỰC HIỆN")
                    .contains("KHÔNG tự suy đoán rằng một thao tác \"không");
        }

        @Test
        @DisplayName("§8 keeps exactly four real limits and lists what is explicitly NOT a limit")
        void absoluteLimitsNarrowedToFour() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Chỉ còn ĐÚNG BỐN giới hạn")
                    .contains("RIÊNG TƯ CỦA NGƯỜI KHÁC")
                    // "Don't claim credit" replaces the old blanket refusals: the assistant may
                    // guide through anything, it just can't say it did the thing itself.
                    .contains("KHÔNG BỊA VÀ KHÔNG NHẬN CÔNG")
                    .contains("KHÔNG PHẢI giới hạn")
                    .contains("MỜI THÀNH VIÊN QUA EMAIL");
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

        @Test
        @DisplayName("the transaction block's line shape is documented, txId included")
        void transactionBlockDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Khối \"Giao dịch tháng M/Y\"")
                    .contains("txId=")
                    .contains("locked=true|false")
                    .contains("TUYỆT ĐỐI KHÔNG bịa ra");
        }

        @Test
        @DisplayName("the transaction list is capped, so the LLM is told not to treat it as the whole month")
        void transactionListIsExplicitlyPartial() {
            String prompt = builder.build(null);
            // buildChatContext caps at 15 rows. Without this the model would happily sum the
            // visible rows and present the result as the month's total spending.
            // Fragments are kept within a single source line — the prompt is a text block, so
            // asserting across its wrap points would break on any reflow.
            assertThat(prompt)
                    .contains("giao dịch gần nhất")
                    .contains("được khẳng định đây là toàn bộ giao dịch trong tháng")
                    .contains("dùng \"Chi tiêu thực tế tháng này\"");
        }
    }

    // ── Cross-check rules — the anti-conflict engine ─────────────────────────

    @Nested
    @DisplayName("§3 Rules A/B/C/D each cover their distinct failure mode")
    class CrossCheckRules {

        @Test
        @DisplayName("Rule A bans PROACTIVE saving-boost suggestions on DEFICIT months")
        void ruleABansSavingBoostOnDeficit() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Rule A")
                    .contains("DEFICIT")
                    .contains("KHÔNG tự khuyên góp thêm cho dự án tiết kiệm");
        }

        @Test
        @DisplayName("Rule E overrides Rule A: an explicit user request is allowed on DEFICIT, with a warning")
        void ruleEAllowsExplicitRequestsOnDeficitWithWarning() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("Rule E")
                    // Scoped to what the user explicitly asks for, not to proactive advice.
                    .contains("THÂM HỤT KHÔNG PHẢI LỆNH CẤM")
                    .contains("CẢNH BÁO NHẮC NHỞ")
                    // The warning is a reminder, never a precondition for doing the thing.
                    .contains("Cảnh báo là NHẮC NHỞ, KHÔNG phải điều kiện");
        }

        @Test
        @DisplayName("Rule E has no exceptions left — even a budget INCREASE proceeds on DEFICIT")
        void ruleEHasNoExceptionsLeft() {
            String prompt = builder.build(null);
            // The budget-increase carve-out was removed when the user was given full permission
            // over their own data: a DEFICIT month now produces a warning, never a refusal. The
            // section anchor is kept (rather than deleted) so the prompt states the reversal
            // explicitly instead of leaving a reader to infer it from silence.
            assertThat(prompt)
                    .contains("NGOẠI LỆ của Rule E: KHÔNG CÓ")
                    .contains("kể cả TĂNG hạn mức một danh mục ngân sách");
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

        @Test
        @DisplayName("A1 splits the legal categories per transaction type so the LLM can't pair SALARY with an EXPENSE")
        void createTransactionCategoriesArePerType() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("* EXPENSE: FOOD, TRANSPORTATION")
                    .contains("* INCOME: SALARY, BONUS, INVESTMENT, GIFT, OTHER")
                    // The two system-generated categories are the project ledger's, not the
                    // user's — TransactionChangeService rejects them, so the prompt says so too.
                    .contains("KHÔNG dùng category PROJECT_CONTRIBUTION hoặc PROJECT_REFUND");
        }

        @Test
        @DisplayName("A1 lets an omitted date default to now rather than costing the user an extra step")
        void createTransactionDateIsOptional() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("bỏ trống nghĩa là THỜI ĐIỂM HIỆN TẠI");
        }

        @Test
        @DisplayName("CreateProjectRequest also states the four limits that make the real POST fail")
        void createProjectStatesBackendConstraints() {
            String prompt = builder.build(null);
            // CreateProjectUseCase throws on each of these before anything is persisted. Without
            // them in the prompt the assistant happily proposes a fourth project or a duplicate
            // priority and the confirm step fails with a raw error the user can't act on.
            assertThat(prompt)
                    .contains("Tối đa 3 dự án đang chạy cùng lúc")
                    .contains("chỉ được dùng cho MỘT dự án đang chạy")
                    .contains("deadline không quá 60 tháng")
                    .contains("hoàn tất thiết lập tài chính");
        }

        @Test
        @DisplayName("group DTOs are catalogued with their real field constraints")
        void groupDtoFieldsListed() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("CreateGroupRequest")
                    .contains("InviteGroupMemberRequest")
                    .contains("CreateGroupProjectRequest")
                    .contains("totalMonths (1-60)");
        }

        @Test
        @DisplayName("A2/A3 require a real txId and forbid guessing between ambiguous matches")
        void editAndDeleteTransactionRequireTxId() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("UpdateTransactionRequest")
                    .contains("DELETE /transactions/{id}")
                    .contains("KHÔNG bịa txId")
                    // The failure this prevents is deleting the wrong row on a vague reference
                    // like "xóa khoản ăn uống hôm qua" when two of them exist.
                    .contains("TUYỆT ĐỐI KHÔNG tự chọn hộ")
                    .contains("locked=true");
        }
    }

    // ── Group-project isolation ─────────────────────────────────────────────

    @Nested
    @DisplayName("§5 group-project isolation allows own-share contributions, blocks the rest")
    class GroupProjectRule {

        @Test
        @DisplayName("§5 (A) permits the user to pay their own share into a group project")
        void groupRuleAllowsOwnShareContribution() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("ĐƯỢC PHÉP (A)")
                    .contains("đóng quỹ (góp tiền) vào dự án nhóm của họ qua chat");
        }

        @Test
        @DisplayName("§5 (B) accepts group creation + email invites but never claims to have done them")
        void groupRuleAllowsCreationAdviceOnly() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("ĐƯỢC PHÉP (B)")
                    // Inviting members by email is explicitly a supported user task, not a refusal.
                    .contains("MỜI THÀNH VIÊN QUA EMAIL")
                    // The chat has no GroupProject create action, so it must not pretend otherwise.
                    .contains("KHÔNG nói \"tôi đã tạo dự án nhóm\"");
        }

        @Test
        @DisplayName("§5 (C) hands back step-by-step guidance instead of refusing group-commitment edits")
        void groupProjectRuleGuidesInsteadOfRefusing() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    // Reversal of the old "KHÔNG ĐƯỢC PHÉP (C)" heading: the user is entitled to
                    // these, the chat turn simply can't execute them, so it explains the steps.
                    .contains("HƯỚNG DẪN THAY VÌ THẺ (C)")
                    .contains("Sửa hoặc XOÁ chính GroupProject")
                    .contains("Người dùng CÓ QUYỀN làm những việc này")
                    .contains("KHÔNG được viết như một lời từ chối");
        }

        @Test
        @DisplayName("§5 (D) keeps other members' private data as the one real limit")
        void groupRuleKeepsOtherMembersPrivate() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("GIỚI HẠN THẬT DUY NHẤT (D)")
                    .contains("THÀNH VIÊN KHÁC")
                    // Privacy, unlike permission, is not negotiable by insisting.
                    .contains("kể cả người dùng nói \"có\" hay khăng khăng");
        }

        @Test
        @DisplayName("§5 directs users to the Group screen for structural changes")
        void groupRuleDirectsToGroupScreen() {
            String prompt = builder.build(null);
            // "màn hình\nNhóm" wraps across two lines — check just "màn hình" which alone would
            // never appear in a personal-finance chat context except when referring to the app UI.
            assertThat(prompt).contains("màn hình");
        }
    }

    // ── §4B in-app operation guides — the "accept and explain how" contract ──

    @Nested
    @DisplayName("§4B turns every non-executable operation into an accepted request + steps")
    class AppOperationGuide {

        @Test
        @DisplayName("§4B opens by forbidding a refusal and requiring numbered steps")
        void guideForbidsRefusalAndRequiresNumberedSteps() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("=== §4B. HƯỚNG DẪN THAO TÁC TRONG ỨNG DỤNG")
                    .contains("CHẤP NHẬN yêu cầu ngay từ câu đầu")
                    .contains("TUYỆT ĐỐI KHÔNG mở đầu bằng \"tôi không thể\"")
                    .contains("HƯỚNG DẪN TỪNG BƯỚC được ĐÁNH SỐ");
        }

        @Test
        @DisplayName("budget create and delete both have their own guide, since neither is executable in-chat")
        void budgetCreateAndDeleteGuidesExist() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("G1) TẠO NGÂN SÁCH MỚI")
                    .contains("G2) XOÁ MỘT NGÂN SÁCH");
        }

        @Test
        @DisplayName("the five group operations are executable in chat, NOT screen walkthroughs")
        void groupOperationsAreExecutableNotGuided() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("G3) TẠO NHÓM, MỜI THÀNH VIÊN, KHOÁ NHÓM, TẠO DỰ ÁN NHÓM, THAM GIA DỰ ÁN NHÓM")
                    .contains("CHẠY ĐƯỢC NGAY trong trò chuyện qua \"groupDraft\"")
                    // The email invite is the operation the user specifically asked for; it must
                    // be named as something the assistant performs, with its real expiry stated.
                    .contains("INVITE_MEMBER — mời từng thành viên bằng email")
                    .contains("hết hạn sau 7 ngày")
                    .contains("PHẢI đã có tài khoản SmartMoney");
        }

        @Test
        @DisplayName("an unmet precondition produces the missing step, not a refusal")
        void unmetPreconditionOffersTheMissingStep() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("G4) KHI MỘT ĐIỀU KIỆN CHƯA ĐỦ Ở G3")
                    .contains("Không từ chối")
                    // The two hard rejections inside CreateGroupProjectUseCase, each paired with
                    // the step that clears it rather than left as a dead end.
                    .contains("đề nghị khoá nhóm trước")
                    .contains("mỗi nhóm chỉ gắn một dự án");
        }

        @Test
        @DisplayName("the guide forbids inventing UI labels it can't verify")
        void guideForbidsInventingUiLabels() {
            String prompt = builder.build(null);
            assertThat(prompt).contains("KHÔNG bịa nhãn nút");
        }
    }

    // ── Intent taxonomy — all 8 intents with their nuances ──────────────────

    @Nested
    @DisplayName("§6 intent taxonomy has all 9 intents")
    class IntentTaxonomy {

        @Test
        @DisplayName("all 9 intents are listed with their trigger conditions")
        void allNineIntentsPresent() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("OUT_OF_SCOPE")
                    .contains("INCOME_WINDFALL")
                    .contains("LARGE_EXPENSE")
                    .contains("PROJECT_HYPOTHESIS")
                    .contains("PROJECT_UPDATE")
                    .contains("BUDGET_UPDATE")
                    .contains("SIMULATION")
                    .contains("TRANSACTION_UPDATE")
                    .contains("GENERAL_ADVICE");
        }

        @Test
        @DisplayName("TRANSACTION_UPDATE documents the step-by-step collect-one-field-per-turn loop")
        void transactionUpdateStepByStepDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("QUY TRÌNH TỪNG BƯỚC")
                    // One question per turn is the whole point — asking for three fields at once
                    // is what this rule exists to prevent.
                    .contains("hỏi ĐÚNG MỘT trường thiếu trong lượt này")
                    // ...and the draft must be re-emitted in full each turn, since the backend
                    // keeps no partial state of its own between turns.
                    .contains("GHI NHỚ QUA CÁC LƯỢT")
                    .contains("KHÔNG hỏi lại trường đã có");
        }

        @Test
        @DisplayName("TRANSACTION_UPDATE is separated from the assess-only windfall/large-expense intents")
        void transactionUpdateDistinguishedFromAssessmentIntents() {
            String prompt = builder.build(null);
            // "tôi vừa được thưởng 10 triệu, nên làm gì?" describes money but asks for advice —
            // it must not be hijacked into a bookkeeping flow.
            assertThat(prompt)
                    .contains("PHÂN BIỆT với INCOME_WINDFALL / LARGE_EXPENSE")
                    .contains("chỉ MÔ TẢ")
                    .contains("khi người dùng muốn SỔ SÁCH thay đổi");
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
        @DisplayName("SIMULATION documents both no-shortfall and shortfall branches, neither carrying a card")
        void simulationBranchesDocumented() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("newSafeSpending >= 0")
                    .contains("newSafeSpending < 0")
                    // The card-attach phrase wraps in the text block; check the load-bearing
                    // "cắt giảm ngân sách" which appears on one line in the shortfall branch.
                    .contains("cắt giảm ngân sách")
                    // The shortfall cut is still computed and still explained — it's demoted to
                    // a suggestion, not removed.
                    .contains("như một GỢI Ý");
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
        @DisplayName("SIMULATION never carries a card — it is a hypothetical, shortfall or not")
        void simulationNeverCardCarrying() {
            String prompt = builder.build(null);
            // Reverses the earlier "shortfall may close with a confirmation question" rule.
            // A simulation is a "nếu"/what-if by construction: the user is weighing an option,
            // not asking for it to be applied, so the shortfall cut is offered as a suggestion
            // to read rather than a decision to make. ChatWithAIUseCase's advisoryOnly gate
            // enforces the same thing server-side by withholding pendingActionId.
            assertThat(prompt)
                    .contains("SIMULATION KHÔNG BAO GIỜ có thẻ xác nhận")
                    .contains("SIMULATION (mọi kịch bản)")
                    .doesNotContain("SIMULATION khi kịch bản gây thiếu hụt (newSafeSpending < 0) — backend đính kèm thẻ");
        }

        @Test
        @DisplayName("hypothetical phrasing suppresses the confirm card across every change intent")
        void hypotheticalPhrasingSuppressesCardEverywhere() {
            String prompt = builder.build(null);
            assertThat(prompt)
                    .contains("YÊU CẦU GIẢ ĐỊNH KHÔNG BAO GIỜ CÓ THẺ XÁC NHẬN")
                    // The rule has to name the intents explicitly, otherwise it reads as advice
                    // about SIMULATION only — which is the one intent that was already exempt.
                    .contains("BUDGET_UPDATE, PROJECT_UPDATE, PROJECT_HYPOTHESIS, TRANSACTION_UPDATE")
                    // ...and it must NOT swallow the budget-cut next-month plan, whose entire
                    // purpose is to act on a future month the user explicitly named.
                    .contains("NGOẠI LỆ DUY NHẤT là nhánh CẮT GIẢM của");
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
                    // SIMULATION is now unconditionally suppressed (no longer "only when there's
                    // no shortfall"), and a half-filled transaction draft is too: that turn is a
                    // question waiting for one specific answer, so offering other things to ask
                    // would pull the user off the flow they're in.
                    .contains("SIMULATION,")
                    .contains("complete = false");
        }

        @Test
        @DisplayName("projectDraft is specified, so a multi-turn create actually produces a project")
        void projectDraftContractIsSpecified() {
            // Without this block the model has no slot-fill channel for projects, and a
            // conversational "tạo dự án mới" ends with a reply that sounds successful while
            // nothing reaches CreateProjectUseCase — the reported bug.
            assertThat(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT)
                    .contains("\"projectDraft\": null")
                    .contains("Quy tắc \"projectDraft\"")
                    .contains("CREATE cần ĐỦ: name, targetAmount, deadline")
                    .contains("TUYỆT ĐỐI KHÔNG viết \"đã tạo dự án\" trong \"reply\" khi complete = false");
        }

        @Test
        @DisplayName("groupDraft is specified with one operation per turn and no invented ids")
        void groupDraftContractIsSpecified() {
            assertThat(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT)
                    .contains("\"groupDraft\": null")
                    .contains("Quy tắc \"groupDraft\"")
                    .contains("CREATE_GROUP|INVITE_MEMBER|LOCK_GROUP|CREATE_GROUP_PROJECT|JOIN_GROUP_PROJECT")
                    // Chaining two mutations in one turn can't work: the second needs an id that
                    // only exists after the first is confirmed.
                    .contains("MỖI LƯỢT CHỈ MỘT thao tác")
                    .contains("KHÔNG bịa id");
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