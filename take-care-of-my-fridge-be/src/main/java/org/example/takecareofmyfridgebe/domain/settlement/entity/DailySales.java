package org.example.takecareofmyfridgebe.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.time.LocalDate;

// 하루(한국 시간) 매출 집계. 결제는 결제 완료일, 환불은 환불 완료일에 넣으므로 순매출은 음수일 수 있다.
// DB의 net_amount는 MySQL이 계산하는 생성 컬럼이라 매핑하지 않고 getNetAmount()로 계산한다
@Entity
@Table(name = "daily_sales",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_sales_sales_date", columnNames = "sales_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailySales extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "paid_amount", nullable = false)
    private long paidAmount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(name = "paid_count", nullable = false)
    private int paidCount;

    @Builder
    private DailySales(LocalDate salesDate, long paidAmount, long refundedAmount, int paidCount) {
        this.salesDate = salesDate;
        this.paidAmount = paidAmount;
        this.refundedAmount = refundedAmount;
        this.paidCount = paidCount;
    }

    // 이미 집계한 날을 다시 집계하면 새로 계산한 합계로 바꾼다.
    // 스케줄러가 매번 그날 결제·환불을 처음부터 다시 세므로 몇 번 돌려도 결과가 같다
    public void reaggregate(long paidAmount, long refundedAmount, int paidCount) {
        this.paidAmount = paidAmount;
        this.refundedAmount = refundedAmount;
        this.paidCount = paidCount;
    }

    public long getNetAmount() {
        return paidAmount - refundedAmount;
    }
}
