import {AppState, AppStateStatus} from 'react-native';
import {useCallback, useEffect, useRef} from 'react';

import {fetchScreenshotImageUris} from '../utils/fetchScreenshotImages';
import {uploadImagesToServer} from '../utils/uploadHelper';
import {useAuthStore} from '../stores/authStore';
import {useBackupStore} from '../stores/useBackupStore';

export const useAutoBackup = () => {
  const {userId} = useAuthStore();
  const {lastUploadedAt} = useBackupStore();
  const backedUp = useRef<Set<string>>(new Set());
  const appState = useRef<AppStateStatus>('active');

  const backupNow = useCallback(async () => {
    if (!userId) {
      console.log('userId 존재 Xx');
      return;
    }

    try {
      console.log('마지막으로 업로드된 이미지 시간 : ', lastUploadedAt);

      const images = await fetchScreenshotImageUris();

      const newImages = images
        .filter(
          img =>
            img.filename &&
            !backedUp.current.has(img.filename) &&
            img.timestamp > (lastUploadedAt ?? 0),
        )
        .sort((a, b) => b.timestamp - a.timestamp);

      if (newImages.length === 0) {
        console.log('🟰 업로드할 신규 스크린샷 없음');
        return;
      } else {
        console.log('🍅 업로드할 신규 스크린샷 수 ', newImages.length);
      }

      await uploadImagesToServer(newImages, filename => {
        backedUp.current.add(filename);
      });
    } catch (error) {
      console.error('❌ 자동 백업 중 오류 발생:', error);
    }
  }, [userId, lastUploadedAt]);

  useEffect(() => {
    const handleAppStateChange = (nextState: AppStateStatus) => {
      if (
        (appState.current === 'inactive' ||
          appState.current === 'background') &&
        nextState === 'active'
      ) {
        console.log('📱 포그라운드 전환됨 → 백업 시도');
        backupNow();
      }
      appState.current = nextState;
    };

    const sub = AppState.addEventListener('change', handleAppStateChange);
    backupNow(); // 앱 시작 시에도 백업 시도

    return () => sub.remove();
  }, [backupNow]);

  return {backupNow};
};
