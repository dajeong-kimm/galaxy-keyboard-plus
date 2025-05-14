import axios from 'axios';
import {DetailedImageItem} from '../types/imageTypes';

export const getImageDetail = async (
  imageId: string,
): Promise<DetailedImageItem> => {
  const response = await axios.get(`/images/${imageId}`);
  return response.data.result;
};
