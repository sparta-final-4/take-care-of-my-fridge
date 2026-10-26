package org.example.takecareofmyfridgebe.domain.admin.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog extends BaseTimeEntity {

    public enum Action {
        PRODUCT_CREATE, PRODUCT_DELETE, PRICE_UPDATE, DEAL_REVIEW, EVENT_REQUEST, ROLE_CHANGE
    }

    public static final String TARGET_MEMBER = "Member";
    public static final String TARGET_DEAL_ANALYSIS = "DealAnalysis";
    public static final String TARGET_DEAL_CANDIDATE = "DealCandidate";

    public enum Result {
        SUCCESS, FAILED, DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20)
    private Member.Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Action action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Result result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "json")
    private Map<String, String> beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "json")
    private Map<String, String> afterValue;

    @Builder
    private AdminAuditLog(Member member, Action action, String targetType, Long targetId, Result result,
                          Map<String, String> beforeValue, Map<String, String> afterValue) {
        this.member = member;
        this.actorRole = member.getRole();
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }
}
