import { theme } from 'antd';
import { useMemo } from 'react';

import type { SettlementPeriodResponse, SettlementUnit } from '../api';
import { formatCount, formatPercent, formatPeriod, formatPeriodShort, formatWon } from '../format';
import { averagePaidAmount, refundRate } from '../metrics';
import {
  chartTextStyles,
  formatAxisWon,
  useSettlementChart,
  type SettlementChartOption,
} from './useSettlementChart';

import styles from './SettlementChart.module.scss';

export type SettlementRatioMetric = 'refundRate' | 'averagePaidAmount';

// 지표마다 값 계산·축 눈금·툴팁 줄. 색은 기간 차트의 환불·결제 색과 같다
const METRICS = {
  refundRate: {
    name: '환불률',
    color: '#eb6834',
    value: refundRate,
    formatAxis: (value: number) => `${value}%`,
    formatValue: formatPercent,
    detail: (period: SettlementPeriodResponse) =>
      `환불 ${formatWon(period.refundedAmount)} / 결제 ${formatWon(period.paidAmount)}`,
  },
  averagePaidAmount: {
    name: '객단가',
    color: '#4f46e5',
    value: averagePaidAmount,
    formatAxis: formatAxisWon,
    formatValue: formatWon,
    detail: (period: SettlementPeriodResponse) =>
      `결제 ${formatWon(period.paidAmount)} / ${formatCount(period.paidCount)}`,
  },
} satisfies Record<SettlementRatioMetric, unknown>;

// 선 위의 점은 기간이 이보다 많으면 마우스를 올렸을 때만 보인다
const MAX_POINTS_WITH_SYMBOL = 31;

interface SettlementRatioChartProps {
  metric: SettlementRatioMetric;
  unit: SettlementUnit;
  periods: SettlementPeriodResponse[];
}

// 기간별 환불률 또는 객단가 선. 단위가 금액과 달라 기간 차트와 축을 나누고, 결제가 없는 기간은 선을 끊는다
export function SettlementRatioChart({ metric, unit, periods }: SettlementRatioChartProps) {
  const { token } = theme.useToken();
  const config = METRICS[metric];

  const option = useMemo<SettlementChartOption>(() => {
    const { textStyle, tooltipTextStyle } = chartTextStyles(token);
    return {
      textStyle,
      grid: { left: 8, right: 8, top: 16, bottom: 8, containLabel: true },
      tooltip: {
        trigger: 'axis',
        textStyle: tooltipTextStyle,
        formatter: (params) => {
          const index = Array.isArray(params) ? params[0]?.dataIndex : params.dataIndex;
          const period = index == null ? undefined : periods[index];
          if (!period) return '';
          const value = config.value(period);
          return [
            `<b>${formatPeriod(unit, period)}</b>`,
            `${config.name} ${value == null ? '결제 없음' : config.formatValue(value)}`,
            config.detail(period),
          ].join('<br/>');
        },
      },
      xAxis: {
        type: 'category',
        data: periods.map((period) => formatPeriodShort(unit, period)),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: token.colorBorder } },
        axisLabel: { hideOverlap: true, color: token.colorTextSecondary },
      },
      yAxis: {
        type: 'value',
        min: 0,
        axisLabel: { formatter: config.formatAxis, color: token.colorTextSecondary },
        splitLine: { lineStyle: { color: token.colorBorderSecondary } },
      },
      series: [
        {
          name: config.name,
          type: 'line',
          data: periods.map((period) => config.value(period) ?? '-'),
          color: config.color,
          lineStyle: { width: 2 },
          symbol: 'circle',
          symbolSize: 8,
          showSymbol: periods.length <= MAX_POINTS_WITH_SYMBOL,
        },
      ],
    };
  }, [config, unit, periods, token]);

  const containerRef = useSettlementChart(option);

  return (
    <div
      ref={containerRef}
      className={styles.smallChart}
      role="img"
      aria-label={`기간별 ${config.name} 차트. 계산에 쓴 값은 일자별 탭의 표에서 볼 수 있습니다.`}
    />
  );
}
