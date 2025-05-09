// src/utils/getScreenshotUri.ts
import {requestStoragePermission} from './permissions'; // 수정된 권한 요청 함수 임포트
import RNFS from 'react-native-fs';

// 스크린샷이 저장될 수 있는 일반적인 디렉토리 목록
// fetchScreenshotImages.ts와 유사하게 정의합니다.
const screenshotDirs = [
  RNFS.PicturesDirectoryPath + '/Screenshots', // 예: /storage/emulated/0/Pictures/Screenshots
  RNFS.ExternalStorageDirectoryPath + '/DCIM/Screenshots', // DCIM 경로 수정
  '/storage/emulated/0/Screenshots', // 일부 기기에서는 이 경로를 사용하기도 합니다.
];

export async function getScreenshotUriById(
  id: string,
  batchSize = 200, // 한 번에 몇 장씩 가져올지 (적당히 조절)
): Promise<string | null> {
  // 1. 저장소 접근 권한 요청
  const hasPermission = await requestStoragePermission();
  if (!hasPermission) {
    // 권한이 거부되면 requestStoragePermission 내부에서 이미 경고 로그를 출력합니다.
    return null;
  }

  // id는 "idScreenshot-20250507_221320_One UI HOME"과 같은 형식이므로,
  // 여기에 확장자를 붙여서 파일명을 완성합니다.
  const possibleExtensions = ['.jpg', '.jpeg', '.png'];

  for (const dir of screenshotDirs) {
    const dirExists = await RNFS.exists(dir);
    if (!dirExists) {
      // console.log(`ℹ️ 디렉토리 없음: ${dir}`);
      continue;
    }

    try {
      const filesInDir = await RNFS.readDir(dir);
      for (const file of filesInDir) {
        if (file.isFile()) {
          for (const ext of possibleExtensions) {
            // 파일명이 "id + 확장자" 형태와 일치하는지 확인
            if (file.name === `${id}${ext}`) {
              console.log(`📸 파일 찾음: ${file.path} (디렉토리: ${dir})`);
              return `file://${file.path}`; // 파일 URI 반환
            }
          }
        }
      }
    } catch (error) {
      console.error(`🚫 ${dir} 디렉토리 읽기 실패:`, error);
      // 특정 디렉토리 읽기 실패 시 다음 디렉토리로 계속 진행
    }
  }

  // 모든 디렉토리에서 파일을 찾지 못한 경우
  console.log(
    `ℹ️ ID "${id}"에 해당하는 스크린샷 파일을 다음 디렉토리들에서 찾지 못했습니다: ${screenshotDirs.join(', ')}`,
  );
  return null;
}
