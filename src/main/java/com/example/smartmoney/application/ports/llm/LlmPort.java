package com.example.smartmoney.application.ports.llm;

/**
 * Port (output) that any LLM adapter must implement.
 * The application layer calls this; the infrastructure layer provides the implementation.
 */
public interface LlmPort {

    /**
     * Send a prompt to the underlying LLM and return the raw text reply.
     *
     * @param prompt full prompt string already assembled by the caller
     * @return the model's text response
     */
    String call(String prompt);
}
