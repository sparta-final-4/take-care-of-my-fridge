import { afterEach, describe, expect, it, vi } from 'vitest';

import { adminImageApi } from '@/common/api/clients';

import { uploadProductImage } from './api';

vi.mock('@/common/api/clients', () => ({
  adminImageApi: { createProductImageUploadUrl: vi.fn() },
  adminProductApi: {},
  productApi: {},
}));

describe('uploadProductImage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.mocked(adminImageApi.createProductImageUploadUrl).mockReset();
  });

  function presigned() {
    vi.mocked(adminImageApi.createProductImageUploadUrl).mockResolvedValue({
      data: {
        success: true,
        data: {
          uploadUrl: 'https://bucket.s3.amazonaws.com/dev/images/products/a.png?X-Amz-Signature=x',
          imageUrl: 'https://dev.imhungry.click/images/products/a.png',
        },
      },
    } as Awaited<ReturnType<typeof adminImageApi.createProductImageUploadUrl>>);
  }

  it('파일 형식·크기로 URL을 받고, 같은 Content-Type으로 S3에 PUT 한 뒤 조회 URL을 돌려준다', async () => {
    presigned();
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const file = new File(['png-bytes'], 'a.png', { type: 'image/png' });

    await expect(uploadProductImage(file)).resolves.toBe(
      'https://dev.imhungry.click/images/products/a.png',
    );
    expect(adminImageApi.createProductImageUploadUrl).toHaveBeenCalledWith({
      imageUploadUrlRequest: { contentType: 'image/png', fileSize: file.size },
    });
    expect(fetchMock).toHaveBeenCalledWith(
      'https://bucket.s3.amazonaws.com/dev/images/products/a.png?X-Amz-Signature=x',
      { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: file },
    );
  });

  it('S3가 거부하면 실패한다', async () => {
    presigned();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 403 })));

    await expect(
      uploadProductImage(new File(['x'], 'a.png', { type: 'image/png' })),
    ).rejects.toThrow('image upload failed: 403');
  });
});
