// ---------------------------------------------------------------------------
// Kakao OAuth 관련 서비스 로직
// ---------------------------------------------------------------------------
import { login as kakaoLogin, logout as kakaoLogout, unlink } from '@react-native-seoul/kakao-login';
import { api } from '../api/axios';
import { useAuthStore } from '../stores/authStore';

/** ① 카카오 SDK → 백엔드 로그인 */
export async function signInWithKakao() {
  console.log('[AuthService] signInWithKakao 시작');
  try {
    // 1) Kakao SDK 로그인
    console.log('[AuthService] Kakao SDK 로그인 시도...');
    const kRes = await kakaoLogin(); // {accessToken, idToken, ...}
    console.log('[AuthService] Kakao SDK 로그인 성공:', kRes);
    console.log("[AuthService] Kakao accessToken:", kRes.accessToken);

    // 2) 우리 서버에 전달해 JWT 세트 획득
    console.log('[AuthService] 백엔드 서버에 로그인 요청 시도...');
    const { data } = await api.post('/auth/kakao/login', {
      kakaoAccessToken: kRes.accessToken,
    });
    // data: { accessToken, refreshToken, userId }
    console.log('[AuthService] 백엔드 서버 로그인 성공:', data);

    // 3) 전역 상태 & SecureStore 에 저장
    console.log('[AuthService] 전역 상태에 토큰 저장 시도...');
    useAuthStore.getState().setTokens(data);
    console.log('[AuthService] 전역 상태에 토큰 저장 완료');
    console.log('[AuthService] signInWithKakao 성공');
  } catch (error: any) {
    console.error('✖ [AuthService] signInWithKakao 실패:', error);
    // Kakao SDK 에러 객체에 code 또는 message 속성이 있을 수 있습니다.
    if (error.code) console.error('✖ [AuthService] Kakao SDK 에러 코드:', error.code);
    if (error.message) console.error('✖ [AuthService] 에러 메시지:', error.message);
    // Axios 에러인 경우 추가 정보 로깅
    if (error.isAxiosError) {
      console.error('✖ [AuthService] Axios 에러 응답:', error.response?.data);
    }
    throw error; // 에러를 다시 throw하여 호출부에서 처리할 수 있도록 함
  }
}

/** ② access 토큰 재발급 */
export async function refreshWithServer() {
  const { refreshToken } = useAuthStore.getState();
  if (!refreshToken) throw new Error('NO_REFRESH_TOKEN');

  const { data } = await api.post('/auth/reissue', { refreshToken });
  // data: { accessToken, refreshToken }
  useAuthStore.getState().setTokens(data);
}

/** ③ 로그아웃 */
export async function signOut() {
  try {
    await kakaoLogout();        // 카카오 세션 끊기
  } catch (_) {
    /* ignore */
  }

  // try {
  //   // (선택) 서버에도 RT 블랙리스트 요청
  //   const { refreshToken } = useAuthStore.getState();
  //   if (refreshToken) await api.post('/auth/logout', { refreshToken });
  // } catch (_) {
  //   /* ignore */
  // }

  useAuthStore.getState().clear();
}

/** ④ 회원탈퇴 = 카카오 unlink + 우리 DB 탈퇴 */
export async function unlinkKakao() {
  await unlink();
  await api.post('/auth/withdraw');
  useAuthStore.getState().clear();
}
