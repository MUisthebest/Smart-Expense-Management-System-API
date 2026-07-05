package com.example.smartmoney.application.guardrails;

import jakarta.annotation.PostConstruct;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @Order(1) — scope guardrail.
 * Allowlist first (finance keywords → pass immediately, prevents false positives).
 * Then blocklist (off-topic domains) with early termination.
 * Ambiguous messages pass through.
 */
@Component
@Order(1)
public class ScopeGuardrail implements PromptGuardrail {

    private Pattern[] allowlistPatterns;
    private Pattern[] blocklistPatterns;

    @PostConstruct
    void init() {
        // Finance allowlist — any match bypasses the blocklist entirely
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
            "khoản tiền lớn", "thanh toán lớn", "chi phí đột xuất"
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

        // Allowlist first — finance context passes immediately
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
