// api/axios.ts

import axios from 'axios';
import { BASE_URL } from '@env';
import { useAuthStore } from '../stores/authStore';
import { refreshWithServer } from '../services/authService';

export const api = axios.create({
  baseURL: 'https://k12e201.p.ssafy.io',
  // baseURL: BASE_URL,
  timeout: 200000,
});

// ★ 토큰 주입
api.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();

  // baseURL이 없으면 빈 문자열로 대체
  const base = config.baseURL ?? '';
  const url  = config.url        ?? '';

  console.log('▶ 요청 URL:', `${base}${url}`);
  console.log('▶ interceptor 에서 가져온 accessToken:', accessToken);

  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// ★ 401 → RT로 재발급(중복 호출 lock)
let refreshPromise: Promise<void> | null = null;

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config;
    if (
      error.response?.status === 401 &&
      !original._retry &&
      useAuthStore.getState().refreshToken
    ) {
      original._retry = true;

      if (!refreshPromise) {
        refreshPromise = refreshWithServer().finally(
          () => (refreshPromise = null),
        );
      }
      await refreshPromise;
      return api(original);
    }
    return Promise.reject(error);
  },
);
