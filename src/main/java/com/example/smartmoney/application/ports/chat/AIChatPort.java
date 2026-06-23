package com.example.smartmoney.application.ports.chat;
import java.util.List;
import java.util.function.Consumer;


public interface AIChatPort {

    String chat(String message, String userId);

    List<String> generateSuggestions(String userData, String userId);

    void chatStream(String message, String userId, 
                Consumer<String> onNext, 
                Consumer<Throwable> onError, 
                Runnable onComplete);
}