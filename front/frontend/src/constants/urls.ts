import {BASE_URL} from '@env';

const URLS = {
  BASE: BASE_URL,
  LOGIN: `${BASE_URL}/auth/login`,
  UPLOAD_IMAGE: `${BASE_URL}/images/upload`,
  GET_IMAGES: `${BASE_URL}/images/list`,
};

export default URLS;
