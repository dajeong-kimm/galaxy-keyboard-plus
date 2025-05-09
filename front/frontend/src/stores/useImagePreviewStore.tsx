import {create} from 'zustand';
import {BasicImageItem} from '../types/imageTypes';

interface ImagePreviewStore {
  images: BasicImageItem[];
  setImages: (images: BasicImageItem[]) => void;
  appendImages: (images: BasicImageItem[]) => void;
  clearImages: () => void;
}

export const useImagePreviewStore = create<ImagePreviewStore>(set => ({
  images: [],
  setImages: images => set({images}),
  appendImages: images =>
    set(state => ({images: [...state.images, ...images]})),
  clearImages: () => set({images: []}),
}));
