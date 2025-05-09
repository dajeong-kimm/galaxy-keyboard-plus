export interface BasicImageItem {
  imageId: string;
  accessId: string;
  star: boolean;
}

export interface DetailedImageItem extends BasicImageItem {
  imageTime: string;
  content: string;
}

export function generateUriFromAccessId(accessId: string): string {
  return `file:///storage/emulated/0/DCIM/Screenshots/${accessId}.jpg`;
}

export type ScreenshotImage = {
  uri: string;
  contentId: string | null;
  filename: string;
  timestamp: number;
};
