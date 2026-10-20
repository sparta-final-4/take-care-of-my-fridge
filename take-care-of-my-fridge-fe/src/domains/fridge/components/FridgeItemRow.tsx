import { Button, Checkbox, Flex, Popconfirm, Tag, Typography } from 'antd';
import dayjs from 'dayjs';

import type { FridgeItemResponse } from '../api';
import { formatDDay, formatQuantity, STORAGE_LABELS, withObjectParticle } from '../format';
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

  return (
    <div className={styles.row}>
      <Checkbox
        checked={checked}
        onChange={(event) => onCheck(event.target.checked)}
        aria-label={`${item.rawName} 선택`}
        className={styles.check}
      />
      <Typography.Text strong={checked || isImminent} title={item.rawName} className={styles.name}>
        {item.rawName}
      </Typography.Text>
      <div className={styles.meta}>
        <Typography.Text className={styles.metaText}>{formatQuantity(item)}</Typography.Text>
        <Typography.Text className={styles.date}>
          {dayjs(item.expirationDate).format('YYYY-MM-DD')}
        </Typography.Text>
        <Typography.Text type="secondary" className={styles.metaText}>
          {STORAGE_LABELS[item.storage]}
        </Typography.Text>
      </div>
      <Tag className={isImminent ? styles.imminentBadge : styles.normalBadge}>
        {formatDDay(item.dDay)}
      </Tag>
      <Flex wrap className={styles.actions}>
        <Button size="small" onClick={onEdit} disabled={removing}>
          수정
        </Button>
        <Button size="small" onClick={onConsume} disabled={removing}>
          먹었어요
        </Button>
        <Popconfirm
          title={`${withObjectParticle(item.rawName)} 버릴까요?`}
          description="냉장고에서 빠지고 기록에 남아요."
          okText="버렸어요"
          cancelText="취소"
          okButtonProps={{ loading: removing, disabled: removing }}
          cancelButtonProps={{ disabled: removing }}
          onConfirm={onDiscard}
        >
          <Button size="small" loading={removing} disabled={removing}>
            버렸어요
          </Button>
        </Popconfirm>
        <Popconfirm
          title={`${withObjectParticle(item.rawName)} 삭제할까요?`}
          description="잘못 등록한 재료일 때 사용해요. 기록에 남지 않아요."
          okText="삭제"
          okButtonProps={{ danger: true, loading: removing, disabled: removing }}
          cancelButtonProps={{ disabled: removing }}
          onConfirm={onDelete}
        >
          <Button size="small" danger loading={removing} disabled={removing}>
            삭제
          </Button>
        </Popconfirm>
      </Flex>
    </div>
  );
}
