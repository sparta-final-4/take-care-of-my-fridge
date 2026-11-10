import { MoreOutlined } from '@ant-design/icons';
import { Button, Checkbox, Dropdown, Popconfirm, Tag, Typography } from 'antd';
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
  // 재료 추가에서 방금 등록한 재료
  highlighted: boolean;
  // 마우스를 쓰는 PC: [먹었어요][버리기]를 줄에 마우스를 올렸을 때 보여 준다. 아니면 ⋯ 메뉴에 넣는다.
  hoverActions: boolean;
  // 목록이 길어도 바뀐 줄만 다시 그리도록(memo) 부모는 매번 같은 함수를 넘기고, 줄이 자기 재료 id·재료를 넣어 부른다.
  onCheck: (fridgeId: number, checked: boolean) => void;
  onEdit: (item: FridgeItemResponse) => void;
  onConsume: (item: FridgeItemResponse) => void;
  onDiscard: (fridgeId: number) => Promise<void>;
  onDelete: (fridgeId: number) => Promise<void>;
}

// ⋯ 버튼 위에 여는 확인창 종류
type ConfirmKind = 'discard' | 'delete';

// 바뀐 줄만 다시 그린다: 체크·처리 중 표시가 바뀐 줄과 데이터가 바뀐 줄만. 나머지 줄은 그대로 둔다.
export const FridgeItemRow = memo(function FridgeItemRow({
  item,
  checked,
  removing,
  highlighted,
  hoverActions,
  onCheck,
  onEdit,
  onConsume,
  onDiscard,
  onDelete,
}: FridgeItemRowProps) {
  const [confirmKind, setConfirmKind] = useState<ConfirmKind>();
  const fridgeId = item.id ?? 0;
  const name = item.rawName ?? '';
  const quantity = formatQuantity(item);
  const storage = item.storage ? STORAGE_LABELS[item.storage] : '';

  const discardConfirm = {
    title: `${withObjectParticle(name)} 버릴까요?`,
    description: '냉장고에서 빠지고 기록에 남아요.',
    okText: '버리기',
  };
  const confirm =
    confirmKind === 'delete'
      ? {
          title: `${withObjectParticle(name)} 삭제할까요?`,
          description: '잘못 등록한 재료일 때 사용해요. 기록에 남지 않아요.',
          okText: '삭제',
        }
      : discardConfirm;

  const menuItems = [
    ...(hoverActions
      ? []
      : [
          { key: 'consume', label: '먹었어요' },
          { key: 'discard', label: '버리기' },
        ]),
    { key: 'edit', label: '수정' },
    { key: 'delete', label: '잘못 등록', danger: true },
  ];

  // 줄: [체크] [이름 · 밀키트·NEW / (휴대폰) 수량 · 보관] [수량] [보관] [남은 날] [먹었어요 · 버리기] [⋯]
  return (
    <div className={styles.row}>
      <Checkbox
        checked={checked}
        onChange={(event) => onCheck(fridgeId, event.target.checked)}
        aria-label={`${name} 선택`}
        className={styles.check}
      />
      <div className={styles.main}>
        <div className={styles.nameLine}>
          <Typography.Text title={name} className={styles.name}>
            {name}
          </Typography.Text>
          <IngredientTypeTag type={item.ingredientType} />
          {highlighted && (
            <Tag variant="filled" color="blue" className={styles.newTag}>
              NEW
            </Tag>
          )}
        </div>
        <Typography.Text type="secondary" className={styles.sub}>
          {quantity}
          {storage && ` · ${storage}`}
        </Typography.Text>
      </div>
      <Typography.Text className={styles.quantity}>{quantity}</Typography.Text>
      <Typography.Text type="secondary" className={styles.storage}>
        {storage}
      </Typography.Text>
      <span className={styles.badge}>
        {item.dDay !== undefined && (
          <DdayTag dDay={item.dDay} expirationDate={item.expirationDate} />
        )}
      </span>
      {hoverActions && (
        <div className={styles.hoverActions}>
          <Button
            size="small"
            type="text"
            onClick={() => onConsume(item)}
            disabled={removing}
            aria-label={`${name} 먹었어요`}
          >
            먹었어요
          </Button>
          <Popconfirm
            {...discardConfirm}
            cancelText="취소"
            okButtonProps={{ loading: removing, disabled: removing }}
            cancelButtonProps={{ disabled: removing }}
            onConfirm={() => onDiscard(fridgeId)}
          >
            <Button
              size="small"
              type="text"
              loading={removing}
              disabled={removing}
              aria-label={`${name} 버리기`}
            >
              버리기
            </Button>
          </Popconfirm>
        </div>
      )}
      {/* 메뉴의 버리기·잘못 등록을 누르면 "⋯" 버튼 위에 확인창을 연다. 버튼을 눌러서는 열리지 않는다. */}
      <Popconfirm
        open={confirmKind !== undefined}
        onOpenChange={(open) => {
          if (!open) setConfirmKind(undefined);
        }}
        title={confirm.title}
        description={confirm.description}
        okText={confirm.okText}
        cancelText="취소"
        okButtonProps={{ danger: confirmKind === 'delete', loading: removing, disabled: removing }}
        cancelButtonProps={{ disabled: removing }}
        onCancel={() => setConfirmKind(undefined)}
        onConfirm={async () => {
          // 실패해 예외가 나도 확인창이 열린 채 남지 않게 닫는다.
          try {
            await (confirmKind === 'delete' ? onDelete(fridgeId) : onDiscard(fridgeId));
          } finally {
            setConfirmKind(undefined);
          }
        }}
      >
        <Dropdown
          trigger={['click']}
          disabled={removing}
          menu={{
            items: menuItems,
            onClick: ({ key }) => {
              if (key === 'consume') onConsume(item);
              if (key === 'edit') onEdit(item);
              if (key === 'discard' || key === 'delete') setConfirmKind(key);
            },
          }}
        >
          <Button
            type="text"
            icon={<MoreOutlined />}
            aria-label={`${name} 더보기`}
            disabled={removing}
            className={styles.moreButton}
          />
        </Dropdown>
      </Popconfirm>
    </div>
  );
});
