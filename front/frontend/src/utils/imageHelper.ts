// import {
//   CameraRoll as CameraRollClass,
//   PhotoIdentifier,
// } from '@react-native-camera-roll/camera-roll';

import {PERMISSIONS, RESULTS, request} from 'react-native-permissions';

import {Platform} from 'react-native';

// import {ScreenshotImage} from '../types/imageTypes';

export const requestImagePermission = async (): Promise<boolean> => {
  if (Platform.OS === 'android') {
    const result = await request(PERMISSIONS.ANDROID.READ_MEDIA_IMAGES);
    return result === RESULTS.GRANTED;
  }
  return false;
};

/**
 *🍧 미디어 스토어로 스크린샷 필터 로직의 한계로 인하여 잠깐 주석 처리합니다
 * fs로 접근하는 용의 fetchScreenshotImageUris는 utils에 따로 ts 파일로 생성되어있어요
 *   */

// export const fetchScreenshotImageUris = async (): Promise<
//   ScreenshotImage[]
// > => {
//   const granted = await requestImagePermission();
//   console.log('📛 권한 획득 여부:', granted);
//   if (!granted) return [];

//   try {
//     const photos = await CameraRollClass.getPhotos({
//       first: 100,
//       assetType: 'Photos',
//     });

//     console.log('🍧 가져온 사진 개수:', photos.edges.length);

//     // photos.edges.forEach((edge, i) => {
//     //   console.log(`📷 전체 이미지 ${i + 1}`, {
//     //     uri: edge.node.image.uri,
//     //     filename: edge.node.image.filename,
//     //     // group_name은 Android 13 이상에서는 기대하지 않음
//     //   });-
//     // });

//     const filtered = photos.edges.filter(edge => {
//       const uri = edge.node.image.uri?.toLowerCase() ?? '';
//       return uri.includes('screenshot');
//     });

//     console.log('✅ 스크린샷만 필터링 결과:', filtered.length);

//     return filtered.map(edge => ({
//       uri: edge.node.image.uri,
//       contentId: extractContentIdFromUri(edge.node.image.uri),
//       filename: edge.node.image.filename ?? '',
//       timestamp: edge.node.timestamp,
//     }));
//   } catch (err) {
//     console.error('❌ CameraRoll.getPhotos 에러:', err);
//     return [];
//   }
// };

export const extractContentIdFromUri = (uri: string): string | null => {
  const match = uri.match(/\/media\/(\d+)$/);
  return match ? match[1] : null;
};
