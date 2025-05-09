import {PERMISSIONS, RESULTS, request} from 'react-native-permissions';

import {Platform} from 'react-native';
import RNFS from 'react-native-fs';
import {ScreenshotImage} from '../types/imageTypes';

/**
 * 파일시스템 속 screenshot 폴더를 접근하는 방식으로 이미지 불러오는 utils
 * 현재는 전체 uris를 보내는 형식으로 되어있어 업로드 시 사용되고 있음
 */

const screenshotDirs = [
  '/storage/emulated/0/DCIM/Screenshots',
  '/storage/emulated/0/Pictures/Screenshots',
];

export const requestStoragePermission = async (): Promise<boolean> => {
  if (Platform.OS !== 'android') return false;

  const permission =
    Platform.Version >= 33
      ? PERMISSIONS.ANDROID.READ_MEDIA_IMAGES
      : PERMISSIONS.ANDROID.READ_EXTERNAL_STORAGE;

  const result = await request(permission);
  return result === RESULTS.GRANTED;
};

export const fetchScreenshotImageUris = async (): Promise<
  ScreenshotImage[]
> => {
  const granted = await requestStoragePermission();
  if (!granted) {
    console.warn('❌ 권한 거부됨');
    return [];
  }

  for (const dir of screenshotDirs) {
    const exists = await RNFS.exists(dir);
    if (!exists) continue;

    try {
      const files = await RNFS.readDir(dir);
      const images = files
        .filter(file => file.isFile() && /\.(jpg|jpeg|png)$/i.test(file.name))
        .map(file => ({
          uri: `file://${file.path}`,
          contentId: extractContentIdFromUri(file.path),
          filename: file.name,
          timestamp: file.mtime?.getTime() || 0,
        }));

      console.log(`📸 ${dir} 에서 ${images.length}개 이미지 발견`);
      return images;
    } catch (err) {
      console.error(`🚫 ${dir} 읽기 실패`, err);
    }
  }

  console.log('⚠️ 스크린샷 폴더를 찾지 못했습니다.');
  return [];
};

const extractContentIdFromUri = (uri: string): string | null => {
  const match = uri.match(/\/(\d+)\.(jpg|jpeg|png)$/i);
  return match ? match[1] : null;
};
