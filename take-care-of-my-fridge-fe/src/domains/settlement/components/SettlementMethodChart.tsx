import { theme } from 'antd';
import { useMemo } from 'react';

import type { PgProvider } from '@/domains/payment/pgProvider';

import type { SettlementMethodResponse } from '../api';
import { formatPaymentMethod, formatPercent, formatWon } from '../format';
import {
  chartTextStyles,
  useSettlementChart,
  type SettlementChartOption,
} from './useSettlementChart';

import styles from './SettlementChart.module.scss';

// 색은 결제 수단마다 고정한다(금액 순위가 바뀌어도 같은 수단은 같은 색). 범주 색 순서를 따르고 dataviz 검증 스크립트로 확인했다
const REGISTERED_CARD_COLOR = '#4f46e5';
const PG_PROVIDER_COLORS: Record<PgProvider, string> = {
  KAKAOPAY: '#eb6834',
  TOSSPAY: '#1baf7a',
  KICC: '#eda100',
  INICIS: '#e87ba4',
  TOSSPAYMENTS: '#008300',
};
// 백엔드에 새로 생긴 결제대행사
const UNKNOWN_METHOD_COLOR = '#898781';

const methodColor = (method: SettlementMethodResponse) => {
  if (method.registeredCard) return REGISTERED_CARD_COLOR;
  return (method.pgProvider && PG_PROVIDER_COLORS[method.pgProvider]) ?? UNKNOWN_METHOD_COLOR;
};

interface SettlementMethodChartProps {
  methods: SettlementMethodResponse[];
}

// 결제 수단별 결제금액 비중 도넛. 순매출은 음수가 될 수 있어 비중은 결제금액으로 본다
export function SettlementMethodChart({ methods }: SettlementMethodChartProps) {
  const { token } = theme.useToken();

  const option = useMemo<SettlementChartOption>(() => {
    const { textStyle, tooltipTextStyle } = chartTextStyles(token);
    const paidMethods = methods.filter((method) => (method.paidAmount ?? 0) > 0);
    const totalPaid = paidMethods.reduce((total, method) => total + (method.paidAmount ?? 0), 0);
    // 조각 바깥 글자는 좁은 화면에서 잘려서, 비중은 범례에 붙인다
    const shareByName = new Map(
      paidMethods.map((method) => [
        formatPaymentMethod(method),
        formatPercent(((method.paidAmount ?? 0) / totalPaid) * 100),
      ]),
    );
    return {
      textStyle,
      legend: {
        bottom: 0,
        left: 'center',
        itemGap: token.margin,
        textStyle,
        formatter: (name) => `${name} ${shareByName.get(name) ?? ''}`,
      },
      tooltip: {
        trigger: 'item',
        textStyle: tooltipTextStyle,
        formatter: (params) => {
          const item = Array.isArray(params) ? params[0] : params;
          if (!item) return '';
          return `<b>${item.name}</b><br/>결제 ${formatWon(Number(item.value))} (${item.percent}%)`;
        },
      },
      series: [
        {
          name: '결제 수단별 결제금액',
          type: 'pie',
          radius: ['36%', '58%'],
          center: ['50%', '38%'],
          itemStyle: { borderColor: token.colorBgContainer, borderWidth: 2, borderRadius: 4 },
          label: { show: false },
          data: paidMethods.map((method) => ({
            name: formatPaymentMethod(method),
            value: method.paidAmount ?? 0,
            itemStyle: { color: methodColor(method) },
          })),
        },
      ],
    };
  }, [methods, token]);

  const containerRef = useSettlementChart(option);

  return (
    <div
      ref={containerRef}
      className={styles.chart}
      role="img"
      aria-label="결제 수단별 결제금액 비중 도넛 차트. 같은 값은 옆의 결제 수단별 표에서 볼 수 있습니다."
    />
  );
}
