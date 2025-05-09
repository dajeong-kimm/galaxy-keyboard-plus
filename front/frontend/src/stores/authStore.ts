// stores/authStore.ts

import * as Keychain from 'react-native-keychain';

import {StorageValue, persist} from 'zustand/middleware';

import {SECURE_KEY} from '@env';
import {create} from 'zustand';

interface Tokens {
  accessToken: string;
  refreshToken: string;
}

interface AuthState extends Tokens {
  userId: string | null;

  /** 로그인·리프레시 시 토큰/유저ID 업데이트 */
  setTokens: (payload: Partial<Tokens & {userId: string}>) => void;

  /** 로그아웃 시 초기화 */
  clear: () => void;
}

const keychainStorage = {
  getItem: async (key: string): Promise<StorageValue<AuthState> | null> => {
    const creds = await Keychain.getGenericPassword({service: key});
    if (!creds) return null;
    try {
      return JSON.parse(creds.password);
    } catch {
      return null;
    }
  },
  setItem: async (key: string, value: StorageValue<AuthState>) => {
    const json = JSON.stringify(value);
    await Keychain.setGenericPassword('auth', json, {service: key});
  },
  removeItem: async (key: string) => {
    await Keychain.resetGenericPassword({service: key});
  },
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: '',
      refreshToken: '',
      userId: null,

      setTokens: ({accessToken, refreshToken, userId}) =>
        set(state => ({
          accessToken: accessToken ?? state.accessToken,
          refreshToken: refreshToken ?? state.refreshToken,
          userId: userId ?? state.userId,
        })),

      clear: () =>
        set({
          accessToken: '',
          refreshToken: '',
          userId: null,
        }),
    }),
    {
      name: `${SECURE_KEY}:auth`,
      storage: keychainStorage,
      version: 1,
    },
  ),
);
