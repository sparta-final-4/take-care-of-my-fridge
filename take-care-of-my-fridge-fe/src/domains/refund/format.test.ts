import { describe, expect, it } from 'vitest';

import type { RefundableItemResponse, RefundHistoryResponse } from '@/common/api/clients';

import {
  getOrderItemRefundState,
  getOverallRefundStatus,
  getRefundRowState,
  groupRefundedItems,
  sumRefundAmountByStatus,
  sumRefundedAmount,
} from './format';

const item = (overrides: Partial<RefundableItemResponse>): RefundableItemResponse => ({
  orderItemId: 1,
  unitPrice: 1000,
  orderedQuantity: 2,
  refundedQuantity: 0,
  refundableQuantity: 2,
  saleType: 'NONE',
  refundable: true,
  ...overrides,
});

const refund = (overrides: Partial<RefundHistoryResponse>): RefundHistoryResponse => ({
  refundId: 1,
  status: 'COMPLETED',
  refundAmount: 1000,
  ...overrides,
});

describe('getRefundRowState', () => {
  it('남은 수량이 있으면 환불할 수 있다', () => {
    expect(getRefundRowState(item({ refundedQuantity: 1, refundableQuantity: 1 }))).toBe(
      'refundable',
    );
  });

  it('남은 수량이 없으면 모두 환불한 상품이다', () => {
    expect(getRefundRowState(item({ refundedQuantity: 2, refundableQuantity: 0 }))).toBe(
      'refunded',
    );
  });

  it('특가·번개 드롭은 환불할 수 없다', () => {
    expect(getRefundRowState(item({ saleType: 'SPECIAL', refundableQuantity: 0 }))).toBe(
      'saleBlocked',
    );
    expect(getRefundRowState(item({ saleType: 'FLASH_DROP', refundableQuantity: 0 }))).toBe(
      'saleBlocked',
    );
  });
});

describe('getOrderItemRefundState', () => {
  it('환불한 수량이 없으면 표시하지 않는다', () => {
    expect(getOrderItemRefundState(item({}))).toBe('none');
  });

  it('일부만 환불했으면 부분 환불이다', () => {
    expect(getOrderItemRefundState(item({ refundedQuantity: 1, refundableQuantity: 1 }))).toBe(
      'partial',
    );
  });

  it('주문 수량을 모두 환불했으면 전체 환불이다', () => {
    expect(getOrderItemRefundState(item({ refundedQuantity: 2, refundableQuantity: 0 }))).toBe(
      'full',
    );
  });

  it('특가·번개 드롭은 환불할 수 없다', () => {
    expect(
      getOrderItemRefundState(
        item({ saleType: 'SPECIAL', refundable: false, refundableQuantity: 0 }),
      ),
    ).toBe('saleBlocked');
  });
});

describe('sumRefundedAmount', () => {
  it('상품마다 환불한 수량에 단가를 곱해 더한다', () => {
    expect(
      sumRefundedAmount([
        item({ unitPrice: 2900, refundedQuantity: 1 }),
        item({ unitPrice: 3000, refundedQuantity: 1 }),
        item({ unitPrice: 6000, refundedQuantity: 0 }),
      ]),
    ).toBe(5900);
  });
});

describe('sumRefundAmountByStatus', () => {
  it('해당 상태의 환불 금액만 더한다', () => {
    const history = [
      refund({ status: 'COMPLETED', refundAmount: 2900 }),
      refund({ status: 'REQUESTED', refundAmount: 6000 }),
      refund({ status: 'COMPLETED', refundAmount: 3000 }),
    ];
    expect(sumRefundAmountByStatus(history, 'COMPLETED')).toBe(5900);
    expect(sumRefundAmountByStatus(history, 'FAILED')).toBe(0);
  });
});

describe('groupRefundedItems', () => {
  it('실패한 환불을 빼고 주문 상품별로 수량·금액을 합친다', () => {
    const history = [
      refund({
        refundId: 3,
        status: 'REQUESTED',
        items: [
          { orderItemId: 10, productName: '대파', unitPrice: 2900, quantity: 1, amount: 2900 },
        ],
      }),
      refund({
        refundId: 2,
        status: 'FAILED',
        items: [
          { orderItemId: 20, productName: '계란', unitPrice: 6000, quantity: 1, amount: 6000 },
        ],
      }),
      refund({
        refundId: 1,
        status: 'COMPLETED',
        items: [
          { orderItemId: 30, productName: '우유', unitPrice: 3000, quantity: 1, amount: 3000 },
          { orderItemId: 10, productName: '대파', unitPrice: 2900, quantity: 1, amount: 2900 },
        ],
      }),
    ];

    expect(groupRefundedItems(history)).toEqual([
      { orderItemId: 30, productName: '우유', unitPrice: 3000, quantity: 1, amount: 3000 },
      { orderItemId: 10, productName: '대파', unitPrice: 2900, quantity: 2, amount: 5800 },
    ]);
  });
});

describe('getOverallRefundStatus', () => {
  it('진행 중인 환불이 있으면 진행 중이다', () => {
    expect(
      getOverallRefundStatus([refund({ status: 'COMPLETED' }), refund({ status: 'REQUESTED' })]),
    ).toBe('REQUESTED');
  });

  it('진행 중이 없고 완료가 있으면 완료다', () => {
    expect(
      getOverallRefundStatus([refund({ status: 'FAILED' }), refund({ status: 'COMPLETED' })]),
    ).toBe('COMPLETED');
  });

  it('실패만 있으면 실패, 없으면 상태가 없다', () => {
    expect(getOverallRefundStatus([refund({ status: 'FAILED' })])).toBe('FAILED');
    expect(getOverallRefundStatus([])).toBeUndefined();
  });
});
