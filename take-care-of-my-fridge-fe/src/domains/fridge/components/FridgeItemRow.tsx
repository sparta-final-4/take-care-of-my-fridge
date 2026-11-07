import { CheckCircleOutlined, DeleteOutlined, MoreOutlined } from '@ant-design/icons';
import { Button, Checkbox, Dropdown, Flex, Popconfirm, Tag, Tooltip, Typography } from 'antd';
import { memo, useState } from 'react';

import type { FridgeItemResponse } from '../api';
import { formatQuantity, STORAGE_LABELS, withObjectParticle } from '../format';
import { DdayTag } from './DdayTag';
import { IngredientTypeTag } from './IngredientTypeTag';
import styles from './FridgeItemRow.module.scss';

interface FridgeItemRowProps {
  item: FridgeItemResponse;
  checked: boolean;
  removing: boolean;
  // 위 재료 입력에서 방금 등록한 재료
  highlighted: boolean;
  // 목록이 길어도 바뀐 칸만 다시 그리도록(memo) 부모는 매번 같은 함수를 넘기고, 칸이 자기 재료 id·재료를 넣어 부른다.
  onCheck: (fridgeId: number, checked: boolean) => void;
  onEdit: (item: FridgeItemResponse) => void;
  onConsume: (item: FridgeItemResponse) => void;
  onDiscard: (fridgeId: number) => Promise<void>;
  onDelete: (fridgeId: number) => Promise<void>;
}

// 바뀐 칸만 다시 그린다: 체크·삭제 중 표시가 바뀐 칸과 데이터가 바뀐 칸만. 나머지 칸은 그대로 둔다.
export const FridgeItemRow = memo(function FridgeItemRow({
  item,
  checked,
  removing,
  highlighted,
  onCheck,
  onEdit,
  onConsume,
  onDiscard,
  onDelete,
}: FridgeItemRowProps) {
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const fridgeId = item.id ?? 0;

  // 신선도: 기한 지남은 옅은 빨강, 임박은 옅은 노랑 바탕(서버가 준 status 그대로).
  const freshnessClass =
    item.status === 'EXPIRED' ? styles.expired : item.status === 'IMMINENT' ? styles.imminent : '';
  const tileClassName = [styles.row, freshnessClass, highlighted && styles.highlighted]
    .filter(Boolean)
    .join(' ');

  // 재료 칸(카드): [체크 · D-day] → [이름 · 종류·NEW 태그] → [수량 · 보관] → [먹었어요 · 버렸어요 · 더보기]
  return (
    <div className={tileClassName}>
      <div className={styles.top}>
        <Checkbox
          checked={checked}
          onChange={(event) => onCheck(fridgeId, event.target.checked)}
          aria-label={`${item.rawName} 선택`}
          className={styles.check}
        />
        <div className={styles.badge}>
          {item.dDay !== undefined && (
            <DdayTag dDay={item.dDay} expirationDate={item.expirationDate} />
          )}
        </div>
      </div>
      <div className={styles.nameWrapper}>
        <Typography.Text title={item.rawName} className={styles.name}>
          {item.rawName}
        </Typography.Text>
        <IngredientTypeTag type={item.ingredientType} />
        {highlighted && (
          <Tag variant="filled" color="blue" className={styles.newTag}>
            NEW
          </Tag>
        )}
      </div>
      <div className={styles.meta}>
        <Typography.Text className={styles.quantity}>{formatQuantity(item)}</Typography.Text>
        <Typography.Text type="secondary" className={styles.storage}>
          {item.storage && STORAGE_LABELS[item.storage]}
        </Typography.Text>
      </div>
      <Flex className={styles.actions}>
        <Tooltip title="먹었어요">
          <Button
            type="text"
            icon={<CheckCircleOutlined />}
            aria-label={`${item.rawName} 먹었어요`}
            onClick={() => onConsume(item)}
            disabled={removing}
            className={styles.iconButton}
          />
        </Tooltip>
        <Popconfirm
          title={`${withObjectParticle(item.rawName ?? '')} 버릴까요?`}
          description="냉장고에서 빠지고 기록에 남아요."
          okText="버렸어요"
          cancelText="취소"
          okButtonProps={{ loading: removing, disabled: removing }}
          cancelButtonProps={{ disabled: removing }}
          onConfirm={() => onDiscard(fridgeId)}
        >
          <Tooltip title="버렸어요">
            <Button
              type="text"
              icon={<DeleteOutlined />}
              aria-label={`${item.rawName} 버렸어요`}
              loading={removing}
              disabled={removing}
              className={styles.iconButton}
            />
          </Tooltip>
        </Popconfirm>
        {/* 메뉴의 삭제를 누르면 "⋯" 버튼 위에 확인창을 연다. 버튼을 눌러서는 열리지 않는다. */}
        <Popconfirm
          open={deleteConfirmOpen}
          onOpenChange={(open) => {
            if (!open) setDeleteConfirmOpen(false);
          }}
          title={`${withObjectParticle(item.rawName ?? '')} 삭제할까요?`}
          description="잘못 등록한 재료일 때 사용해요. 기록에 남지 않아요."
          okText="삭제"
          okButtonProps={{ danger: true, loading: removing, disabled: removing }}
          cancelButtonProps={{ disabled: removing }}
          onCancel={() => setDeleteConfirmOpen(false)}
          onConfirm={async () => {
            // 삭제가 실패해 예외가 나도 확인창이 열린 채 남지 않게 닫는다.
            try {
              await onDelete(fridgeId);
            } finally {
              setDeleteConfirmOpen(false);
            }
          }}
        >
          <Dropdown
            trigger={['click']}
            disabled={removing}
            menu={{
              items: [
                { key: 'edit', label: '수정' },
                { key: 'delete', label: '삭제', danger: true },
              ],
              onClick: ({ key }) => {
                if (key === 'edit') onEdit(item);
                if (key === 'delete') setDeleteConfirmOpen(true);
              },
            }}
          >
            <Button
              type="text"
              icon={<MoreOutlined />}
              aria-label={`${item.rawName} 더보기`}
              disabled={removing}
              className={styles.iconButton}
            />
          </Dropdown>
        </Popconfirm>
      </Flex>
    </div>
  );
});
