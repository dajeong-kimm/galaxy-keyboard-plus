import RNFS from 'react-native-fs';

export const deleteImageByContentId = async (contentId: string) => {
  const uri = `content://media/external/images/media/${contentId}`;

  try {
    await RNFS.unlink(uri); // 일부 기기에서는 작동 안 할 수 있음
    return true;
  } catch (err) {
    console.warn('삭제 실패:', err);
    return false;
  }
};

// ⚠️ RNFS.unlink은 일반적으로 file:// 경로에서만 작동합니다.
// content:// 삭제는 Java NativeModule(Android용)에서 MediaStore API로 따로 처리하는 게 정확합니다.
