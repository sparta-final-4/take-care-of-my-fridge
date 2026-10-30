import { theme } from 'antd';
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts';
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components';
import { init, use as registerModules, type ComposeOption, type EChartsType } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { useEffect, useRef } from 'react';

import type { SettlementPeriodResponse, SettlementUnit } from '../api';
import { formatCount, formatPeriod, formatPeriodShort, formatWon } from '../format';

import styles from './SettlementChart.module.scss';

registerModules([
  BarChart,
  LineChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

type SettlementChartOption = ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
>;

// 결제는 테마 primary, 환불·순매출은 그다음 범주 색. 색약 구분과 대비는 dataviz 검증 스크립트로 확인했다
const SERIES_COLORS = { paid: '#4f46e5', refunded: '#eb6834', net: '#1baf7a' };
// 점이 이보다 많으면 선 위의 점은 마우스를 올렸을 때만 보인다
const MAX_POINTS_WITH_SYMBOL = 31;

// 축 눈금은 만 원 단위로 줄인다
const formatAxisWon = (value: number) =>
  Math.abs(value) >= 10_000
    ? `${(value / 10_000).toLocaleString('ko-KR')}만`
    : value.toLocaleString('ko-KR');

interface SettlementChartProps {
  unit: SettlementUnit;
  periods: SettlementPeriodResponse[];
}

// 기간별 결제·환불(막대)과 순매출(선). 금액이 모두 원이라 축은 하나만 쓴다
export function SettlementChart({ unit, periods }: SettlementChartProps) {
  const { token } = theme.useToken();
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<EChartsType | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const chart = init(container);
    chartRef.current = chart;
    const observer = new ResizeObserver(() => chart.resize());
    observer.observe(container);
    return () => {
      observer.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    const textStyle = { color: token.colorTextSecondary, fontFamily: token.fontFamily };
    const option: SettlementChartOption = {
      textStyle,
      grid: { left: 8, right: 8, top: 40, bottom: 8, containLabel: true },
      legend: { top: 0, left: 0, itemGap: token.margin, textStyle },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        textStyle: { color: token.colorText, fontFamily: token.fontFamily },
        formatter: (params) => {
          const index = Array.isArray(params) ? params[0]?.dataIndex : params.dataIndex;
          const period = index == null ? undefined : periods[index];
          if (!period) return '';
          return [
            `<b>${formatPeriod(unit, period)}</b>`,
            `결제 ${formatWon(period.paidAmount)} (${formatCount(period.paidCount)})`,
            `환불 ${formatWon(period.refundedAmount)}`,
            `순매출 ${formatWon(period.netAmount)}`,
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
        axisLabel: { formatter: formatAxisWon, color: token.colorTextSecondary },
        splitLine: { lineStyle: { color: token.colorBorderSecondary } },
      },
      series: [
        {
          name: '결제금액',
          type: 'bar',
          data: periods.map((period) => period.paidAmount ?? 0),
          color: SERIES_COLORS.paid,
          barMaxWidth: 24,
          barGap: '10%',
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
        {
          name: '환불금액',
          type: 'bar',
          data: periods.map((period) => period.refundedAmount ?? 0),
          color: SERIES_COLORS.refunded,
          barMaxWidth: 24,
          itemStyle: { borderRadius: [4, 4, 0, 0] },
        },
        {
          name: '순매출',
          type: 'line',
          data: periods.map((period) => period.netAmount ?? 0),
          color: SERIES_COLORS.net,
          lineStyle: { width: 2 },
          symbol: 'circle',
          symbolSize: 8,
          showSymbol: periods.length <= MAX_POINTS_WITH_SYMBOL,
        },
      ],
    };
    chartRef.current?.setOption(option, { notMerge: true });
  }, [unit, periods, token]);

  return (
    <div
      ref={containerRef}
      className={styles.chart}
      role="img"
      aria-label="기간별 결제금액, 환불금액, 순매출 차트. 같은 값은 아래 표에서 볼 수 있습니다."
    />
  );
}
