package org.example.takecareofmyfridgebe.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(
    name = "chat_messages",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_chat_messages_chat_session_id_sequence",
            columnNames = {"chat_session_id", "sequence"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_session_id", nullable = false)
    private ChatSession session;

    @Column(name = "sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ChatMessageRole role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder
    private ChatMessage(
        ChatSession session,
        int sequence,
        ChatMessageRole role,
        String content
    ) {
        this.session = session;
        this.sequence = sequence;
        this.role = role;
        this.content = content;
    }
}
