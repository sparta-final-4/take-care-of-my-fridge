package org.example.takecareofmyfridgebe.domain.chat.dto;

public record ChatDoneEventResponse(
        Long userMessageId,
        Long assistantMessageId
) {
}
