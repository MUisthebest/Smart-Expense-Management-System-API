package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The reset gate deletes CHAT MESSAGES, never financial data — and it sits upstream of the
 * guardrails, so a false positive silently destroys a user's context instead of erroring. Both
 * directions therefore matter equally here: the phrasings that must clear the window, and the
 * financial-delete phrasings ("xóa giao dịch", "xóa dự án") that must fall through untouched.
 */
@DisplayName("ResetConversationUseCase")
class ResetConversationUseCaseTest {

    private final ChatHistoryCacheService historyCache = mock(ChatHistoryCacheService.class);
    private final ChatPendingActionPort pendingActionPort = mock(ChatPendingActionPort.class);
    private final ResetConversationUseCase useCase =
            new ResetConversationUseCase(historyCache, pendingActionPort);

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("matches a conversation-reset request")
    class Matches {

        @ParameterizedTest
        @ValueSource(strings = {
                "hãy xóa đoạn hội thoại",
                "xóa đoạn hội thoại",
                "xoá hội thoại đi",
                "xoa doan hoi thoai",
                "cho tôi xóa lịch sử trò chuyện với",
                "xóa hết tin nhắn trong đoạn chat này nhé",
                "reset cuộc trò chuyện",
                "clear the conversation",
                "delete our chat history",
                "làm mới đoạn hội thoại",
                // reverse word order
                "hội thoại này xóa đi giúp tôi",
                "conversation, please clear it",
        })
        void clearsWindow(String message) {
            Optional<ChatResponse> result = useCase.tryReset(userId, message);

            assertThat(result).isPresent();
            verify(historyCache).clear(userId);
        }

        @Test
        @DisplayName("also clears the latest-pending-action pointer so a stale \"có\" can't confirm anything")
        void clearsPendingActionPointer() {
            // A fresh context that still pointed at a pending mutation would let the next bare
            // "có" confirm something the user can no longer see in their history.
            useCase.tryReset(userId, "xóa đoạn hội thoại");

            verify(pendingActionPort).clearLatestId(userId);
        }

        @Test
        @DisplayName("replies in Vietnamese and states that financial data is untouched")
        void vietnameseReplyReassuresAboutData() {
            ChatResponse response = useCase.tryReset(userId, "xóa đoạn hội thoại").orElseThrow();

            assertThat(response.getReply()).contains("Dữ liệu tài chính của bạn vẫn nguyên vẹn");
            assertThat(response.isEnglish()).isFalse();
            // A blank slate must not be immediately re-seeded with follow-up chips.
            assertThat(response.getRelatedQuestions()).isEmpty();
        }

        @Test
        @DisplayName("replies in English for an English request")
        void englishReply() {
            ChatResponse response = useCase.tryReset(userId, "please clear the conversation")
                    .orElseThrow();

            assertThat(response.isEnglish()).isTrue();
            assertThat(response.getReply()).contains("cleared our conversation");
        }

        @Test
        @DisplayName("a Redis failure on the pending-action pointer still completes the reset")
        void pointerFailureDoesNotFailReset() {
            doThrow(new RuntimeException("redis down")).when(pendingActionPort).clearLatestId(userId);

            assertThat(useCase.tryReset(userId, "xóa đoạn hội thoại")).isPresent();
            verify(historyCache).clear(userId);
        }
    }

    @Nested
    @DisplayName("falls through for everything else")
    class DoesNotMatch {

        @ParameterizedTest
        @ValueSource(strings = {
                // Financial deletes — these belong to TRANSACTION_DELETE / PROJECT_DELETE and must
                // never be swallowed by this gate.
                "xóa giao dịch hôm qua",
                "xóa dự án mua xe",
                "xoá khoản chi 50000 đi",
                "xóa các dự án đã hoàn thành",
                "delete that transaction",
                "delete my project",
                // Ordinary questions that merely mention chatting
                "tôi có thể trò chuyện với bạn về ngân sách không",
                "tháng này tôi chi bao nhiêu",
                "",
        })
        void returnsEmpty(String message) {
            assertThat(useCase.tryReset(userId, message)).isEmpty();
            verifyNoInteractions(historyCache);
        }

        @Test
        @DisplayName("null message is safe")
        void nullMessage() {
            assertThat(useCase.tryReset(userId, null)).isEmpty();
            verify(historyCache, never()).clear(userId);
        }
    }
}
