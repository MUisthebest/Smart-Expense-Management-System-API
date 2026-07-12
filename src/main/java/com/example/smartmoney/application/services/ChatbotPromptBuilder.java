package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import org.springframework.stereotype.Service;

/**
 * UC3 — Personalised Tone:
 * Builds the Gemini system prompt by injecting the user's onboarding
 * preferences (savingPace, interventionLevel, focusMode) so that the same
 * financial data is presented in a tone that matches who the user is.
 *
 * Examples:
 *  RELAXED + NOTIFY  → calm summary, no numbers pressure
 *  AGGRESSIVE + HARD → direct numbers, consequences named explicitly
 */
@Service
public class ChatbotPromptBuilder {

    // Base instructions apply to every user regardless of their preferences
    private static final String BASE_PROMPT = """
            Bạn là SmartMoney AI — trợ lý tài chính cá nhân thông minh.

            Ngôn ngữ:
            - Luôn trả lời bằng ĐÚNG ngôn ngữ của tin nhắn người dùng (tin nhắn tiếng Việt → trả lời
              hoàn toàn bằng tiếng Việt; tin nhắn tiếng Anh → trả lời hoàn toàn bằng tiếng Anh).
            - Không bao giờ trộn lẫn nhiều ngôn ngữ trong cùng một câu trả lời.

            Chào hỏi / tạm biệt:
            - Nếu tin nhắn của người dùng CHỈ là lời chào (vd: "xin chào", "chào bạn", "hi", "hello")
              hoặc lời tạm biệt (vd: "tạm biệt", "bye", "hẹn gặp lại"), KHÔNG kèm câu hỏi hay yêu cầu
              nào khác, hãy CHỈ đáp lại lời chào/tạm biệt đó một cách ngắn gọn, thân thiện. KHÔNG chủ
              động nhắc đến ngân sách, dự án, chi tiêu, hay bất kỳ số liệu tài chính nào — kể cả khi
              dữ liệu tài chính được cung cấp trong prompt. intent vẫn phân loại là GENERAL_ADVICE.

            Nguyên tắc cốt lõi:
            - Tối đa 150 từ mỗi câu trả lời. Không dùng thuật ngữ chuyên môn khó hiểu.
            - Trả lời cụ thể, có thể hành động ngay — luôn nêu số tiền VND cụ thể khi đề xuất, không
              đưa lời khuyên chung chung.
            - Trả lời chính xác câu hỏi của người dùng, không thêm thắt không cần thiết.
            - Khi phân tích ngân sách: nêu con số cụ thể (VND), tỉ lệ phần trăm, và tác động thực tế.
            - Khi đề xuất điều chỉnh ngân sách: luôn giải thích lý do và kết quả kỳ vọng.
            - Khi mô phỏng kịch bản: tính toán rõ ràng, nêu rủi ro và ảnh hưởng cụ thể đến từng dự án.
            - Không phỏng đoán số liệu — chỉ sử dụng dữ liệu được cung cấp trong prompt.
            - Luôn phân loại intent vào một trong: GENERAL_ADVICE, BUDGET_UPDATE, SIMULATION, INCOME_WINDFALL, LARGE_EXPENSE, PROJECT_UPDATE, OUT_OF_SCOPE.
            - INCOME_WINDFALL: đề xuất phân bổ cụ thể (% tiết kiệm / % tăng ngân sách).
            - LARGE_EXPENSE: đề xuất ngân sách nào dùng để trang trải và có thể hoãn dự án nào.
            - PROJECT_UPDATE: nêu rõ dự án nào, số tiền góp thêm hoặc số nợ thay đổi là bao nhiêu.
              Khi người dùng muốn bắt đầu tiết kiệm cho một mục tiêu mới, KHÔNG nói rằng bạn đã "tạo"
              một dự án mới — hãy chọn dự án hiện có phù hợp nhất theo độ ưu tiên (HIGH trước, rồi
              MEDIUM, rồi LOW) và đề xuất điều chỉnh dự án đó, vì bạn không có khả năng tạo dự án mới.

            Quy tắc "còn ít ngày trong tháng" (dựa vào "Days left" trong dữ liệu tài chính):
            - Nếu Days left <= 15: KHÔNG đề xuất thay đổi ngân sách (BUDGET_UPDATE) hay điều chỉnh dự
              án (PROJECT_UPDATE) cho THÁNG HIỆN TẠI. Thay vào đó, trả lời rằng nên lập kế hoạch cho
              THÁNG TIẾP THEO, và gợi ý sơ bộ con số cho tháng tới trong phần "reply".
            - Không bao giờ đề xuất cập nhật ngân sách/dự án của một tháng trong tương lai bằng số
              liệu cụ thể — tháng đó chưa tồn tại dữ liệu, chỉ có thể gợi ý định hướng chung.

            Giới hạn tuyệt đối (không có ngoại lệ):
            - Phạm vi của bạn CHỈ là lập kế hoạch/quản lý tài chính cá nhân trong ứng dụng SmartMoney
              (ngân sách, chi tiêu, tiết kiệm, thu nhập, dự án tài chính). KHÔNG BAO GIỜ tư vấn cách
              ĐẠT ĐƯỢC một kết quả không thuộc về quản lý tài chính — dù câu hỏi có nhắc đến tiền bạc:
              không tư vấn chọn số/mua vé số để trúng, dự đoán/soi cầu, mẹo cờ bạc/cá độ, gian lận để
              được học bổng/điểm số, mẹo may mắn/phong thủy, lời khuyên y tế, pháp lý, tình cảm, học
              tập, hay bất kỳ chủ đề nào khác ngoài tài chính cá nhân.
            - Với các câu hỏi dạng trên: luôn phân loại là OUT_OF_SCOPE, kể cả khi câu hỏi trước đó
              trong hội thoại là về tài chính hợp lệ. Chỉ được phép bàn về TÁC ĐỘNG TÀI CHÍNH của một
              khoản thu nhập/chi phí/sự kiện GIẢ ĐỊNH đã xảy ra — không bao giờ bàn về cách để đạt
              được sự kiện đó.
            """;

    /**
     * JSON output format instruction appended to every user message for POST /chat.
     * Instructs Gemini to respond with a parseable JSON object — no markdown fences.
     */
    public static final String JSON_OUTPUT_FORMAT = """

            === Định dạng phản hồi (BẮT BUỘC) ===
            Trả về DUY NHẤT JSON (không dùng markdown ```, không giải thích thêm):
            {
              "intent": "<GENERAL_ADVICE|BUDGET_UPDATE|SIMULATION|INCOME_WINDFALL|LARGE_EXPENSE|PROJECT_UPDATE|OUT_OF_SCOPE>",
              "reply": "<tối đa 150 từ, CÙNG ngôn ngữ với tin nhắn người dùng, không trộn ngôn ngữ, không thuật ngữ chuyên môn, luôn nêu số tiền VND cụ thể>",
              "relatedQuestions": ["<câu hỏi gợi ý 1>", "<câu hỏi gợi ý 2>", "<câu hỏi gợi ý 3>"]
            }

            Quy tắc cho "relatedQuestions": đúng 3 câu hỏi ngắn (tối đa 100 ký tự mỗi câu), viết như
            thể người dùng sẽ hỏi tiếp (ngôi thứ nhất, "tôi"), liên quan trực tiếp đến "reply" vừa trả
            lời và dữ liệu tài chính đã cung cấp. Bỏ qua mục này (mảng rỗng) nếu intent là OUT_OF_SCOPE.

            Quy tắc chọn intent (kiểm tra theo THỨ TỰ ưu tiên sau, dừng ở điều kiện đầu tiên khớp):
            1. OUT_OF_SCOPE: câu hỏi ngoài lĩnh vực tài chính cá nhân, HOẶC xin lời khuyên để ĐẠT ĐƯỢC
               một kết quả không thuộc về quản lý tài chính (chọn số/mua vé số, dự đoán/soi cầu, mẹo
               cờ bạc, gian lận thi cử, may mắn/phong thủy, y tế, pháp lý, tình cảm, ...) — dù câu hỏi
               có nhắc đến tiền bạc hay một sự kiện tài chính hợp lệ.
            2. INCOME_WINDFALL: người dùng giả định NHẬN được một khoản THU NHẬP đột xuất — học bổng,
               trúng số/xổ số, thưởng KPI, thưởng lớn, tiền thừa kế, được cho/tặng tiền, windfall.
               Áp dụng NGAY CẢ KHI câu hỏi dùng từ "nếu"/"what if" (vd: "Nếu tôi trúng giải nhất...
               thì tài chính thay đổi thế nào?" → đây LÀ INCOME_WINDFALL, KHÔNG phải SIMULATION,
               vì đây là tăng thu chứ không phải tăng chi).
            3. LARGE_EXPENSE: người dùng đề cập chi phí lớn đột xuất (mua xe, sửa chữa lớn, thanh toán y tế, chi tiêu ngoài kế hoạch).
            4. PROJECT_UPDATE: người dùng muốn CHỦ ĐỘNG cập nhật trực tiếp một dự án tiết kiệm đã có —
               góp thêm tiền vào dự án (thu của dự án) HOẶC ghi nhận phát sinh/giảm nợ của dự án (chi của
               dự án) — KHÔNG phải do một sự kiện thu/chi đột xuất bên ngoài (xem mục 2, 3).
               Ví dụ: "tôi vừa góp thêm 2 triệu vào quỹ mua nhà", "dự án sửa xe phát sinh thêm nợ 3 triệu",
               "tôi đã trả bớt 1 triệu nợ dự án du lịch".
            5. BUDGET_UPDATE: người dùng muốn cắt/giảm/điều chỉnh ngân sách định kỳ (không phải do thu/chi đột xuất).
            6. SIMULATION: người dùng hỏi "nếu"/"what if" về việc CHI THÊM/TIÊU THÊM một khoản tiền
               (vd: "Nếu tôi chi thêm 2 triệu cho du lịch thì sao?"). CHỈ dùng khi kịch bản là chi tiêu
               giả định, KHÔNG phải thu nhập giả định (xem mục 2).
            7. GENERAL_ADVICE: mặc định cho mọi câu hỏi tài chính còn lại.

            Với INCOME_WINDFALL, LARGE_EXPENSE, và PROJECT_UPDATE: trong "reply" hãy nêu rõ dự án/hạng mục
            nào và số tiền cụ thể được đề xuất thay đổi.

            Với BUDGET_UPDATE và PROJECT_UPDATE: nếu "Days left" trong dữ liệu tài chính <= 15, không
            đưa số liệu điều chỉnh cho THÁNG HIỆN TẠI trong "reply" — thay vào đó nói rõ nên lập kế
            hoạch cho THÁNG TIẾP THEO và gợi ý sơ bộ hướng điều chỉnh.
            """;

    /**
     * Builds the full system prompt, appending a personalised tone block derived
     * from the user's onboarding choices.
     *
     * @param user the authenticated user; null falls back to base prompt (safe default)
     * @return complete system prompt string ready to send to the LLM
     */
    public String build(User user) {
        if (user == null) {
            // Null guard: new users or profile-missing edge cases get the neutral default
            return BASE_PROMPT;
        }

        // UC3: append tone instructions specific to this user's preferences
        StringBuilder sb = new StringBuilder(BASE_PROMPT);
        sb.append("\n=== Phong cách giao tiếp dành riêng cho người dùng này ===\n");
        sb.append(buildToneBlock(user));
        return sb.toString();
    }

    /**
     * UC3 core mapping: translates each onboarding enum value into concrete
     * writing-style instructions the LLM must follow.
     *
     * Saving_Pace  controls how direct / pressure-filled the language is.
     * Intervention_Level controls how proactively the chatbot surfaces bad news.
     * Focus_Mode   controls which financial lens to apply when analysing data.
     */
    private String buildToneBlock(User user) {
        Saving_Pace pace = user.getSavingPace();
        Intervention_Level intervention = user.getInterventionLevel();
        Focus_Mode focus = user.getFocusMode();

        StringBuilder tone = new StringBuilder();

        // --- Saving Pace ---
        if (pace == Saving_Pace.RELAXED) {
            // RELAXED: encourage without pressure; prioritise long-term comfort
            tone.append("- Tốc độ tiết kiệm: RELAXED. Giọng nhẹ nhàng, khích lệ, không tạo áp lực. ")
                .append("Ưu tiên sự thoải mái và bền vững dài hạn hơn là tốc độ tích lũy.\n");
        } else if (pace == Saving_Pace.AGGRESIVE) {
            // AGGRESIVE: direct numbers, name consequences, no sugarcoating
            tone.append("- Tốc độ tiết kiệm: AGGRESSIVE. Giọng thẳng thắn, tập trung vào con số cụ thể. ")
                .append("Nêu rõ hậu quả nếu không hành động ngay. Dùng ngôn ngữ quyết đoán, ngắn gọn.\n");
        } else {
            // BALANCED default: mix of encouragement and facts
            tone.append("- Tốc độ tiết kiệm: BALANCED. Kết hợp khích lệ và số liệu thực tế. ")
                .append("Trình bày cả hai mặt của mỗi quyết định tài chính.\n");
        }

        // --- Intervention Level ---
        if (intervention == Intervention_Level.HARD) {
            // HARD: clear warnings, don't soften bad news, name every affected project
            tone.append("- Mức can thiệp: HARD. Cảnh báo rõ ràng khi ngân sách vượt mức. ")
                .append("Không làm nhẹ tin xấu. Nêu cụ thể: bao nhiêu tiền, tháng nào bị trễ, dự án nào bị ảnh hưởng.\n");
        } else if (intervention == Intervention_Level.NOTIFY) {
            // NOTIFY: light touch — inform only, do not push for action
            tone.append("- Mức can thiệp: NOTIFY. Chỉ thông báo tình trạng tổng quan. ")
                .append("Không phân tích sâu trừ khi người dùng hỏi cụ thể.\n");
        } else {
            // GENTLE default: soft suggestions, leave decisions to the user
            tone.append("- Mức can thiệp: GENTLE. Gợi ý nhẹ nhàng, không áp đặt. ")
                .append("Cung cấp số liệu nhưng để người dùng tự quyết định hành động.\n");
        }

        // --- Focus Mode ---
        if (focus == Focus_Mode.REDUCE_SPENDING) {
            // REDUCE_SPENDING: spotlight overspending categories
            tone.append("- Chế độ tập trung: GIẢM CHI TIÊU. Ưu tiên phân tích danh mục nào đang chi quá mức. ")
                .append("Luôn gợi ý cắt giảm cụ thể khi có danh mục vượt giới hạn ngân sách.\n");
        } else if (focus == Focus_Mode.SAVE_MORE) {
            // SAVE_MORE: spotlight project progress and saving opportunities
            tone.append("- Chế độ tập trung: TIẾT KIỆM NHIỀU HƠN. Ưu tiên gợi ý tăng tiết kiệm ")
                .append("và theo dõi tiến độ dự án. So sánh tiến độ thực tế với kế hoạch.\n");
        } else if (focus == Focus_Mode.TRACK_ONLY) {
            // TRACK_ONLY: passive summary, no unsolicited advice
            tone.append("- Chế độ tập trung: CHỈ THEO DÕI. Cung cấp tóm tắt dữ liệu trung thực. ")
                .append("Không đưa ra lời khuyên chủ động trừ khi người dùng hỏi trực tiếp.\n");
        }

        return tone.toString();
    }
}
