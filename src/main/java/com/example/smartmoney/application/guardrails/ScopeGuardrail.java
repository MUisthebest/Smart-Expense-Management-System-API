package com.example.smartmoney.application.guardrails;

import jakarta.annotation.PostConstruct;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @Order(1) — fast, deterministic scope guardrail (regex only, no network call).
 *
 * Gambling-advice blocklist first (highest priority — overrides the finance
 * allowlist, since "trúng"/"xổ số" also appear there for windfall detection).
 * Then finance allowlist (keywords → pass immediately, prevents false positives).
 * Then off-topic blocklist with early termination. Ambiguous messages pass through.
 *
 * This layer only catches domains/patterns enumerated in advance. It is a
 * cheap first line of defense (zero latency, catches the most common abuse
 * pattern — lottery-number requests — instantly). It does NOT generalise to
 * arbitrary off-topic drift; {@link SemanticScopeGuardrail} (@Order 3) is the
 * general-purpose net that catches everything this layer misses.
 */
@Component
@Order(1)
public class ScopeGuardrail implements PromptGuardrail {

    private Pattern[] gamblingAdvicePatterns;
    private Pattern[] allowlistPatterns;
    private Pattern[] blocklistPatterns;

    @PostConstruct
    void init() {
        // Gambling-advice — asking HOW to win/pick numbers, not the financial
        // impact of a hypothetical win. Must be checked before the allowlist
        // below, because "trúng"/"xổ số"/"lottery" are also allowlist terms
        // (needed to let genuine windfall-planning questions through).
        String[] gambling = {
            "nên (mua|chọn) (tờ )?(vé số|con số|số) (nào|gì|mấy)",
            "chọn (con )?số nào",
            "(làm (sao|thế nào)|cách|làm gì) (để )?(trúng|thắng)",
            "bí quyết (trúng|chọn số|xổ số|thắng)",
            "dự đoán (kết quả|số) (xổ số|trúng|may mắn)",
            "soi cầu",
            "phong thủy chọn số",
            "số (nào|mấy) (dễ|hay|thường|sẽ) trúng",
            "mua (tờ )?(vé số|con số|số) (nào|gì|mấy)",
            "lô đề|đánh đề|đánh lô",
            "cờ bạc|cá độ|gambling|betting odds",
            "how (to|do i) (pick|choose|win) (the )?(lottery|numbers)",
            "which (lottery )?numbers (should i|to) (pick|buy|choose)",
            "predict (the )?(lottery|winning) numbers"
        };
        gamblingAdvicePatterns = toPatterns(gambling);

        // Finance allowlist — any match bypasses the off-topic blocklist entirely
        String[] allow = {
            "ngân sách", "budget", "chi tiêu", "tài chính", "finance",
            "tiết kiệm", "saving", "thu nhập", "income", "giao dịch", "transaction",
            "dự án", "project", "đầu tư", "invest", "nợ", "debt", "vay", "loan",
            "lãi", "interest", "chi phí", "expense", "tiền", "money",
            "nếu", "what if", "mô phỏng", "simulation", "giả sử", "if i spend",
            "ngân hàng", "bank", "thẻ", "card", "quỹ", "fund",
            "cắt", "giảm", "tăng", "adjust", "reduce", "cut",
            // Windfall income keywords
            "học bổng", "scholarship", "xổ số", "lottery", "trúng thưởng", "trúng",
            "thưởng", "bonus", "kpi", "windfall", "tiền thưởng", "nhận tiền",
            "tiền bất ngờ", "thu nhập bất ngờ", "tiền thừa kế", "inheritance",
            // Large expense keywords
            "chi lớn", "khoản lớn", "chi đột xuất", "mua xe", "mua nhà",
            "sửa chữa", "viện phí", "y tế", "large expense", "big purchase",
            "khoản tiền lớn", "thanh toán lớn", "chi phí đột xuất",
            // In-app project/budget management verbs — a request to create/update/extend/
            // shorten/delete the user's OWN project or budget, even phrased as a short
            // command ("xóa dự án du lịch giúp tôi", "delete my car project") rather than a
            // question. ".*" between the verb and the entity word so words in between (a
            // project name, "my", "the", ...) don't break the match; compound with the
            // entity word (dự án/ngân sách/project/budget) to avoid over-broadening the
            // allowlist with bare verbs like "xóa"/"delete" alone.
            "xóa.*(dự án|ngân sách)", "delete.*(project|budget)", "remove.*(project|budget)",
            "hủy.*dự án", "cancel.*project",
            "tạo.*(dự án|ngân sách)", "create.*(project|budget)",
            "cập nhật.*(dự án|ngân sách)", "update.*(project|budget)",
            "rút ngắn.*(dự án|thời hạn)", "kéo dài.*(dự án|thời hạn)",
            "shorten.*(project|deadline)", "extend.*(project|deadline)",
            // Transaction bookkeeping — recording/editing/deleting the user's own ledger
            // entries. "giao dịch"/"transaction" is already a bare allowlist term above, but
            // the natural short phrasings for this don't contain that word at all ("ghi chi
            // 50k", "log this expense"), so the verbs get their own entries. Kept compound
            // with a money/ledger noun where the bare verb would be too broad.
            "ghi (nhận|lại|chép|sổ)", "nhập (khoản|giao dịch|chi|thu)",
            "thêm (khoản|giao dịch)", "lưu (khoản|giao dịch)",
            "ghi (chi|thu|khoản)", "vừa (chi|tiêu|trả|mua|nhận)",
            "sửa.*(giao dịch|khoản)", "xóa.*(giao dịch|khoản)", "nhập nhầm", "ghi nhầm",
            "record.*(expense|income|transaction|spending)", "log.*(expense|income|transaction)",
            "add.*(expense|income|transaction)", "edit.*transaction", "delete.*transaction",
            "remove.*transaction", "i (just )?(spent|paid|received|earned)"
        };
        allowlistPatterns = toPatterns(allow);

        // Off-topic blocklist — only checked if allowlist doesn't match
        String[] block = {
            "bóng đá|football|soccer|basketball|tennis|thể thao|sport|cricket|baseball",
            "chính trị|politics|bầu cử|election|chính phủ|government|đảng|party",
            "nấu ăn|cooking|recipe|công thức nấu|how to cook|cách nấu",
            "lập trình|programming|code|software|developer|máy tính|computer hardware",
            "phim|movie|âm nhạc|music|game|trò chơi|entertainment show|drama series",
            "thời tiết|weather|forecast|du lịch|travel|tourism|khách sạn|hotel"
        };
        blocklistPatterns = toPatterns(block);
    }

    @Override
    public void validate(String message, UUID userId) {
        String lower = message.toLowerCase();

        // Gambling-advice check first — takes priority over the allowlist below,
        // since finance keywords like "trúng"/"xổ số" would otherwise let it pass.
        for (Pattern p : gamblingAdvicePatterns) {
            if (p.matcher(lower).find()) {
                throw new GuardrailViolationException(
                        "Tôi chỉ hỗ trợ lập kế hoạch tài chính, không tư vấn cách chọn số " +
                        "hay dự đoán kết quả xổ số/cờ bạc.");
            }
        }

        // Allowlist — finance context passes immediately
        for (Pattern p : allowlistPatterns) {
            if (p.matcher(lower).find()) return;
        }

        // Blocklist — early termination on first match
        for (Pattern p : blocklistPatterns) {
            if (p.matcher(lower).find()) {
                throw new GuardrailViolationException(
                        "Tin nhắn nằm ngoài phạm vi trợ lý tài chính SmartMoney.");
            }
        }
        // Ambiguous → pass
    }

    private Pattern[] toPatterns(String[] expressions) {
        Pattern[] result = new Pattern[expressions.length];
        for (int i = 0; i < expressions.length; i++) {
            result[i] = Pattern.compile(expressions[i], Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        return result;
    }
}
