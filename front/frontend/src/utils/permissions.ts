import {
  PERMISSIONS,
  Permission,
  RESULTS,
  request,
} from 'react-native-permissions';

import {Platform} from 'react-native';

export const requestStoragePermission = async (): Promise<boolean> => {
  if (Platform.OS !== 'android') {
    // Android가 아닌 경우, 이 권한이 필요 없거나 다른 방식으로 처리해야 할 수 있습니다.
    // 우선 false를 반환하거나, 상황에 맞게 true를 반환할 수 있습니다.
    // console.warn('⚠️ 이 권한 요청은 Android 전용입니다.');
    return true; // 혹은 false, 정책에 따라 결정
  }

  const permissionToRequest: Permission =
    Platform.Version >= 33
      ? PERMISSIONS.ANDROID.READ_MEDIA_IMAGES
      : PERMISSIONS.ANDROID.READ_EXTERNAL_STORAGE;

  const result = await request(permissionToRequest);
  if (result === RESULTS.GRANTED) {
    console.log('✅ 이미지 접근 권한 허용됨');
    return true;
  } else {
    console.warn('❌ 권한 거부됨');
    return false;
  }
};
