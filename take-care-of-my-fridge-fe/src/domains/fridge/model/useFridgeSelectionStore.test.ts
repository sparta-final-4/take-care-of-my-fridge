import { afterEach, describe, expect, it } from 'vitest';

import { useFridgeSelectionStore } from './useFridgeSelectionStore';

describe('useFridgeSelectionStore', () => {
  afterEach(() => {
    useFridgeSelectionStore.getState().clear();
  });

  it('체크하면 담고, 다시 체크해도 한 번만 담는다', () => {
    const { setChecked } = useFridgeSelectionStore.getState();
    setChecked(1, true);
    setChecked(2, true);
    setChecked(1, true);
    expect(useFridgeSelectionStore.getState().checkedIds).toEqual([2, 1]);
  });

  it('체크를 풀면 뺀다', () => {
    const { setChecked } = useFridgeSelectionStore.getState();
    setChecked(1, true);
    setChecked(2, true);
    setChecked(1, false);
    expect(useFridgeSelectionStore.getState().checkedIds).toEqual([2]);
  });

  it('냉장고에서 빠진 재료만 체크를 푼다', () => {
    const { setChecked, uncheck } = useFridgeSelectionStore.getState();
    [1, 2, 3].forEach((id) => setChecked(id, true));
    uncheck([1, 3]);
    expect(useFridgeSelectionStore.getState().checkedIds).toEqual([2]);
  });
});
