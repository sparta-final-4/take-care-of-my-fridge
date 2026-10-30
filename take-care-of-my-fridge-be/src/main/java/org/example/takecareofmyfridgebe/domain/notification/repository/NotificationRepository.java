package org.example.takecareofmyfridgebe.domain.notification.repository;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // beforeId가 없으면 가장 최신부터, 있으면 그보다 오래된 알림부터 읽는다.
    @Query("""
        select n from Notification n
        where n.memberId = :memberId and (:beforeId is null or n.id < :beforeId)
        order by n.id desc
        """)
    List<Notification> findLatestByMemberId(@Param("memberId") Long memberId,
                                            @Param("beforeId") Long beforeId, Pageable pageable);

    long countByMemberIdAndReadFalse(Long memberId);

    // lastNotificationId 이하의 안 읽은 알림을 한 번에 읽음으로 바꾼다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Notification n set n.read = true
        where n.memberId = :memberId and n.read = false and n.id <= :lastNotificationId
        """)
    int markAllReadUpTo(@Param("memberId") Long memberId,
                        @Param("lastNotificationId") Long lastNotificationId);
}
