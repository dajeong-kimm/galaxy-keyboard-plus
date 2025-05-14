import {StorageValue, persist} from 'zustand/middleware';

import AsyncStorage from '@react-native-async-storage/async-storage';
import {create} from 'zustand';

interface BackupState {
  lastUploadedAt: number | null;
  setLastUploadedAt: (timestamp: number) => void;
}

// ✅ AsyncStorage 래핑 (Zustand가 요구하는 형식에 맞춤)
const zustandStorage = {
  getItem: async (name: string): Promise<StorageValue<BackupState> | null> => {
    const value = await AsyncStorage.getItem(name);
    return value ? JSON.parse(value) : null;
  },
  setItem: async (name: string, value: StorageValue<BackupState>) => {
    await AsyncStorage.setItem(name, JSON.stringify(value));
  },
  removeItem: async (name: string) => {
    await AsyncStorage.removeItem(name);
  },
};

export const useBackupStore = create<BackupState>()(
  persist(
    set => ({
      lastUploadedAt: null,
      setLastUploadedAt: timestamp => set({lastUploadedAt: timestamp}),
    }),
    {
      name: 'backup-storage',
      storage: zustandStorage,
    },
  ),
);
