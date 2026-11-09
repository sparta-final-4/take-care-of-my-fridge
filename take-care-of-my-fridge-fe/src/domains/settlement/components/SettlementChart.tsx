import { theme } from 'antd';
import { useMemo } from 'react';

import type { SettlementPeriodResponse, SettlementUnit } from '../api';
import { formatCount, formatPeriod, formatPeriodShort, formatWon } from '../format';
import {
  chartTextStyles,
  formatAxisWon,
  useSettlementChart,
  type SettlementChartOption,
} from './useSettlementChart';

import styles from './SettlementChart.module.scss';

// 결제는 테마 primary, 환불·순매출은 그다음 범주 색. 색약 구분과 대비는 dataviz 검증 스크립트로 확인했다
const SERIES_COLORS = { paid: '#4f46e5', refunded: '#eb6834', net: '#1baf7a' };
// 기간이 이보다 많으면 선 위의 점은 마우스를 올렸을 때만 보이고, 아래에 구간 확대 막대를 둔다
const MAX_POINTS_WITHOUT_ZOOM = 31;
// 확대 막대를 처음 열 때 보이는 마지막 기간 수
const ZOOM_INITIAL_POINTS = 31;

interface SettlementChartProps {
  unit: SettlementUnit;
  periods: SettlementPeriodResponse[];
}

// 기간별 결제(0 위 막대)·환불(0 아래 막대)과 순매출(선). 환불이 결제보다 훨씬 작아도 보이게 아래로 뺀다.
// 금액이 모두 원이라 축은 하나만 쓴다
export function SettlementChart({ unit, periods }: SettlementChartProps) {
  const { token } = theme.useToken();

  const option = useMemo<SettlementChartOption>(() => {
    const { textStyle, tooltipTextStyle } = chartTextStyles(token);
    const zoomable = periods.length > MAX_POINTS_WITHOUT_ZOOM;
    const zoomStart = zoomable ? (1 - ZOOM_INITIAL_POINTS / periods.length) * 100 : 0;
    return {
      textStyle,
      grid: { left: 8, right: 8, top: 40, bottom: zoomable ? 48 : 8, containLabel: true },
      legend: { top: 0, left: 0, itemGap: token.margin, textStyle },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        textStyle: tooltipTextStyle,
        formatter: (params) => {
          const index = Array.isArray(params) ? params[0]?.dataIndex : params.dataIndex;
          const period = index == null ? undefined : periods[index];
          if (!period) return '';
          return [
            `<b>${formatPeriod(unit, period)}</b>`,
            `결제 ${formatWon(period.paidAmount)} (${formatCount(period.paidCount)})`,
            `환불 ${formatWon(period.refundedAmount)} (${formatCount(period.refundedCount)})`,
            `순매출 ${formatWon(period.netAmount)}`,
          ].join('<br/>');
        },
      },
      dataZoom: zoomable
        ? [
            { type: 'inside', start: zoomStart, end: 100 },
            { type: 'slider', start: zoomStart, end: 100, bottom: 8, height: 24 },
          ]
        : [],
      xAxis: {
        type: 'category',
        data: periods.map((period) => formatPeriodShort(unit, period)),
        axisTick: { show: false },
        axisLine: { onZero: true, lineStyle: { color: token.colorBorder } },
        axisLabel: { hideOverlap: true, color: token.colorTextSecondary },
      },
      yAxis: {
        type: 'value',
        axisLabel: { formatter: formatAxisWon, color: token.colorTextSecondary },
        splitLine: { lineStyle: { color: token.colorBorderSecondary } },
      },
      series: [
        {
          name: '결제금액',
          type: 'bar',
          stack: 'amount',
          data: periods.map((period) => period.paidAmount ?? 0),
          color: SERIES_COLORS.paid,
          barMaxWidth: 24,
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
        {
          // 같은 기둥에서 0 아래로 그린다. 툴팁·표는 양수 그대로 보여 준다
          name: '환불금액',
          type: 'bar',
          stack: 'amount',
          data: periods.map((period) => -(period.refundedAmount ?? 0)),
          color: SERIES_COLORS.refunded,
          barMaxWidth: 24,
          itemStyle: { borderRadius: [0, 0, 4, 4] },
        },
        {
          name: '순매출',
          type: 'line',
          data: periods.map((period) => period.netAmount ?? 0),
          color: SERIES_COLORS.net,
          lineStyle: { width: 2 },
          symbol: 'circle',
          symbolSize: 8,
          showSymbol: !zoomable,
        },
      ],
    };
  }, [unit, periods, token]);

  const containerRef = useSettlementChart(option);

  return (
    <div
      ref={containerRef}
      className={styles.chart}
      role="img"
      aria-label="기간별 결제금액, 환불금액, 순매출 차트. 환불은 0 아래 막대로 그립니다. 같은 값은 일자별 탭의 표에서 볼 수 있습니다."
    />
  );
}
