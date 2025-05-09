import {create} from 'zustand';
import {DetailedImageItem} from '../types/imageTypes';

interface ImageDetailStore {
  image: DetailedImageItem | null;
  setImage: (image: DetailedImageItem) => void;
  clearImage: () => void;
}

export const useImageDetailStore = create<ImageDetailStore>(set => ({
  image: null,
  setImage: image => set({image}),
  clearImage: () => set({image: null}),
}));
