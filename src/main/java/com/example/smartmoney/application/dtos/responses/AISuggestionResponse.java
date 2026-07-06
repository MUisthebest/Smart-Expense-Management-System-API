// Path: src/main/java/com/example/smartmoney/application/dtos/responses/AISuggestionResponse.java

package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Response for {@code GET /api/v1/assistant/suggestions}.
 *
 * <p>Returns 3–5 contextual question starters that the frontend displays
 * as quick-tap chips below the chat input when the user opens the chat screen.
 *
 * <p>Example chips:
 * <ul>
 *   <li>"Tháng này tôi có đang tiêu quá không?"</li>
 *   <li>"Tôi nên cắt giảm khoản nào?"</li>
 *   <li>"Project du lịch của tôi tiến độ ra sao?"</li>
 * </ul>
 */
@Value
@Builder
public class AISuggestionResponse {

    /**
     * Ordered list of suggested questions (3–5 items).
     * Each string is ≤ 60 characters to fit on a button chip.
     */
    List<String> suggestions;
}