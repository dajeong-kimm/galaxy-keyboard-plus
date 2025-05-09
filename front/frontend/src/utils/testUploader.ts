import {fetchScreenshotImageUris} from '../utils/fetchScreenshotImages';
import {uploadImagesToServer} from '../utils/uploadHelper';

export const uploadTop50Screenshots = async () => {
  const images = await fetchScreenshotImageUris();
  if (images.length === 0) {
    console.warn('⚠️ 스크린샷 이미지 없음');
    return;
  }

  const top50 = images
    .filter(img => img.timestamp > 0)
    .sort((a, b) => b.timestamp - a.timestamp)
    .slice(0, 50);

  await uploadImagesToServer(top50);
};

/**
 * 테스트용으로 스크린샷 이미지 50장 올리는 메소드
 * HomeScreen 버튼에서 동작
 */
