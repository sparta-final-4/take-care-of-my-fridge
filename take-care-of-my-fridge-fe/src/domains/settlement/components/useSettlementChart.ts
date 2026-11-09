import { theme } from 'antd';
import { BarChart, LineChart, PieChart } from 'echarts/charts';
import type { BarSeriesOption, LineSeriesOption, PieSeriesOption } from 'echarts/charts';
import {
  DataZoomInsideComponent,
  DataZoomSliderComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type DataZoomComponentOption,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components';
import { init, use as registerModules, type ComposeOption, type EChartsType } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { useEffect, useRef } from 'react';

registerModules([
  BarChart,
  LineChart,
  PieChart,
  DataZoomInsideComponent,
  DataZoomSliderComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
]);

export type SettlementChartOption = ComposeOption<
  | BarSeriesOption
  | LineSeriesOption
  | PieSeriesOption
  | DataZoomComponentOption
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
>;

type Token = ReturnType<typeof theme.useToken>['token'];

// 정산 차트가 같이 쓰는 글자·툴팁 모양
export function chartTextStyles(token: Token) {
  return {
    textStyle: { color: token.colorTextSecondary, fontFamily: token.fontFamily },
    tooltipTextStyle: { color: token.colorText, fontFamily: token.fontFamily },
  };
}

// 축 눈금은 만 원 단위로 줄인다
export const formatAxisWon = (value: number) =>
  Math.abs(value) >= 10_000
    ? `${(value / 10_000).toLocaleString('ko-KR')}만`
    : value.toLocaleString('ko-KR');

// 차트를 한 번 만들고 컨테이너 크기를 따라가게 한다. option이 바뀌면 통째로 다시 그린다
export function useSettlementChart(option: SettlementChartOption) {
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
    chartRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  return containerRef;
}
