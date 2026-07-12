package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.responses.ChatActionConfirmResponse;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ResolvePlainAnswerUseCase")
class ResolvePlainAnswerUseCaseTest {

    private final ChatPendingActionPort pendingActionPort = mock(ChatPendingActionPort.class);
    private final ConfirmChatActionUseCase confirmUseCase = mock(ConfirmChatActionUseCase.class);
    private final ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
    private final ResolvePlainAnswerUseCase useCase =
            new ResolvePlainAnswerUseCase(pendingActionPort, confirmUseCase, historyService);

    @Test
    @DisplayName("a bare \"có\" confirms the latest pending action")
    void plainYesConfirmsPendingAction() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-123"));
        when(confirmUseCase.execute(any(), any())).thenReturn(
                ChatActionConfirmResponse.builder().success(true).reply("Đã cập nhật thành công.").build());
        when(historyService.load(any())).thenReturn(List.of());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "Có");

        assertThat(response).isPresent();
        assertThat(response.get().getReply()).isEqualTo("Đã cập nhật thành công.");
        assertThat(response.get().getIntent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        verify(confirmUseCase).execute(any(), argThat((ConfirmChatActionRequest req) ->
                req.isConfirm() && "action-123".equals(req.getActionId())));
        verify(historyService).appendAndCompress(any(), any(), any(), any());
    }

    @Test
    @DisplayName("propagates the pending action's stored language onto the returned ChatResponse — "
            + "a bare yes/no carries no language signal of its own")
    void propagatesLanguageFromTheOriginalAction() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-en"));
        when(confirmUseCase.execute(any(), any())).thenReturn(
                ChatActionConfirmResponse.builder().success(true)
                        .reply("Update applied successfully.").english(true).build());
        when(historyService.load(any())).thenReturn(List.of());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "yes");

        assertThat(response).isPresent();
        assertThat(response.get().isEnglish()).isTrue();
    }

    @Test
    @DisplayName("a bare \"không\" denies the latest pending action")
    void plainNoDeniesPendingAction() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-456"));
        when(confirmUseCase.execute(any(), any())).thenReturn(
                ChatActionConfirmResponse.builder().success(false).reply("Đã hủy thao tác.").build());
        when(historyService.load(any())).thenReturn(List.of());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "không");

        assertThat(response).isPresent();
        assertThat(response.get().getReply()).isEqualTo("Đã hủy thao tác.");
        verify(confirmUseCase).execute(any(), argThat((ConfirmChatActionRequest req) ->
                !req.isConfirm() && "action-456".equals(req.getActionId())));
    }

    @Test
    @DisplayName("\"có\" embedded in a real question does NOT trigger the gate (substring, not whole-message)")
    void substringCoDoesNotTrigger() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-789"));

        Optional<ChatResponse> response =
                useCase.tryResolve(UUID.randomUUID(), "tôi có thể xem báo cáo tháng này không?");

        assertThat(response).isEmpty();
        verifyNoInteractions(confirmUseCase);
    }

    @Test
    @DisplayName("does nothing when there's no pending action, even for a bare yes/no")
    void noPendingActionMeansNoResolution() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.empty());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "có");

        assertThat(response).isEmpty();
        verifyNoInteractions(confirmUseCase);
    }

    @Test
    @DisplayName("recognizes English yes/no too")
    void recognizesEnglishAnswers() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-en"));
        when(confirmUseCase.execute(any(), any())).thenReturn(
                ChatActionConfirmResponse.builder().success(true).reply("Update applied successfully.").build());
        when(historyService.load(any())).thenReturn(List.of());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "Yes");

        assertThat(response).isPresent();
        verify(confirmUseCase).execute(any(), argThat((ConfirmChatActionRequest req) -> req.isConfirm()));
    }

    @Test
    @DisplayName("ignores surrounding punctuation/whitespace when matching a bare answer")
    void normalizesPunctuationAndWhitespace() {
        when(pendingActionPort.loadLatestId(any())).thenReturn(Optional.of("action-punct"));
        when(confirmUseCase.execute(any(), any())).thenReturn(
                ChatActionConfirmResponse.builder().success(true).reply("Đã cập nhật thành công.").build());
        when(historyService.load(any())).thenReturn(List.of());

        Optional<ChatResponse> response = useCase.tryResolve(UUID.randomUUID(), "  Có!  ");

        assertThat(response).isPresent();
    }
}
