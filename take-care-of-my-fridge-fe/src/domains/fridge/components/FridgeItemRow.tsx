import { CheckCircleOutlined, DeleteOutlined, MoreOutlined } from '@ant-design/icons';
import { Button, Checkbox, Dropdown, Flex, Popconfirm, Tooltip, Typography } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';

import type { FridgeItemResponse } from '../api';
import { formatQuantity, STORAGE_LABELS, withObjectParticle } from '../format';
import { DdayTag } from './DdayTag';
import { IngredientTypeTag } from './IngredientTypeTag';
import styles from './FridgeItemRow.module.scss';

interface FridgeItemRowProps {
  item: FridgeItemResponse;
  checked: boolean;
  removing: boolean;
  onCheck: (checked: boolean) => void;
  onEdit: () => void;
  onConsume: () => void;
  onDiscard: () => Promise<void>;
  onDelete: () => Promise<void>;
}

export function FridgeItemRow({
  item,
  checked,
  removing,
  onCheck,
  onEdit,
  onConsume,
  onDiscard,
  onDelete,
}: FridgeItemRowProps) {
  const isImminent = item.status !== 'NORMAL';
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  return (
    <div className={styles.row}>
      <Checkbox
        checked={checked}
        onChange={(event) => onCheck(event.target.checked)}
        aria-label={`${item.rawName} 선택`}
        className={styles.check}
      />
      <div className={styles.nameWrapper}>
        <Typography.Text
          strong={checked || isImminent}
          title={item.rawName}
          className={styles.name}
        >
          {item.rawName}
        </Typography.Text>
        <IngredientTypeTag type={item.ingredientType} />
      </div>
      <div className={styles.meta}>
        <Typography.Text className={styles.metaText}>{formatQuantity(item)}</Typography.Text>
        <Typography.Text className={styles.date}>
          {dayjs(item.expirationDate).format('YYYY-MM-DD')}
        </Typography.Text>
        <Typography.Text type="secondary" className={styles.metaText}>
          {item.storage && STORAGE_LABELS[item.storage]}
        </Typography.Text>
      </div>
      <div className={styles.badge}>{item.dDay !== undefined && <DdayTag dDay={item.dDay} />}</div>
      <Flex className={styles.actions}>
        <Tooltip title="먹었어요">
          <Button
            type="text"
            icon={<CheckCircleOutlined />}
            aria-label={`${item.rawName} 먹었어요`}
            onClick={onConsume}
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
          onConfirm={onDiscard}
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
              await onDelete();
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
                if (key === 'edit') onEdit();
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
}
