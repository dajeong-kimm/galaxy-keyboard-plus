import axios from 'axios';

// DOM 요소
const youtubeUrlInput = document.getElementById('youtube-url');
const extractButton = document.getElementById('extract-button');
const languageSelect = document.getElementById('language');
const loadingElement = document.getElementById('loading');
const errorElement = document.getElementById('error');
const resultElement = document.getElementById('result');
const transcriptElement = document.getElementById('transcript');
const videoThumbnailElement = document.getElementById('video-thumbnail');
const videoTitleElement = document.getElementById('video-title');
const videoChannelElement = document.getElementById('video-channel');

// 이벤트 리스너 등록
extractButton.addEventListener('click', extractTranscript);
youtubeUrlInput.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    extractTranscript();
  }
});

// YouTube ID 추출 함수
function extractYoutubeId(url) {
  if (!url) return null;
  
  // URL 형식 처리
  try {
    const parsedUrl = new URL(url);
    
    if (parsedUrl.hostname === 'youtu.be') {
      return parsedUrl.pathname.slice(1);
    } else if (parsedUrl.hostname.includes('youtube.com')) {
      return parsedUrl.searchParams.get('v');
    }
  } catch (error) {
    // URL이 아닌 경우, 직접 비디오 ID인지 확인
    if (/^[a-zA-Z0-9_-]{11}$/.test(url)) {
      return url;
    }
  }
  
  return null;
}

// 비디오 정보 가져오기 (썸네일 및 제목)
async function fetchVideoInfo(videoId) {
  try {
    // 여기에서는 간단하게 썸네일만 표시합니다.
    videoThumbnailElement.innerHTML = `<img src="https://img.youtube.com/vi/${videoId}/0.jpg" alt="Video thumbnail">`;
    videoTitleElement.textContent = '비디오 제목은 API 키 없이 가져올 수 없습니다';
    videoChannelElement.textContent = '채널 정보는 API 키 없이 가져올 수 없습니다';
  } catch (error) {
    console.error('비디오 정보를 가져오는 중 오류 발생:', error);
  }
}

// 타임아웃 변수
let timeoutId = null;

// 트랜스크립트 추출 함수
async function extractTranscript() {
  // 기존 타임아웃 제거
  if (timeoutId) {
    clearTimeout(timeoutId);
  }
  
  // UI 상태 초기화
  showLoading(true);
  hideError();
  hideResult();
  
  const youtubeUrl = youtubeUrlInput.value.trim();
  const language = languageSelect.value;
  
  // 입력 검증
  if (!youtubeUrl) {
    showError('YouTube URL을 입력해주세요.');
    showLoading(false);
    return;
  }
  
  const videoId = extractYoutubeId(youtubeUrl);
  
  if (!videoId) {
    showError('올바른 YouTube URL이 아닙니다.');
    showLoading(false);
    return;
  }
  
  // 30초 후 요청 타임아웃 처리
  timeoutId = setTimeout(() => {
    showLoading(false);
    showError('요청이 시간 초과되었습니다. 다시 시도해주세요.');
  }, 30000);
  
  try {
    // 비디오 정보 가져오기
    await fetchVideoInfo(videoId);
    
    // 서버 API로 자막 요청
    const response = await axios.get(`/api/transcript`, {
      params: {
        url: videoId,
        lang: language
      }
    });
    
    // 타임아웃 제거
    clearTimeout(timeoutId);
    
    // 결과 표시
    if (response.data && response.data.transcript) {
      transcriptElement.textContent = response.data.transcript;
      
      // 영어 자막이 출력되었는지 확인
      if (response.data.note) {
        showError(response.data.note); // 알림 표시
      }
      
      showResult();
    } else {
      showError('트랜스크립트를 찾을 수 없습니다.');
    }
  } catch (error) {
    // 타임아웃 제거
    clearTimeout(timeoutId);
    
    console.error('트랜스크립트 추출 중 오류 발생:', error);
    
    let errorMessage = '트랜스크립트를 가져오는 중 오류가 발생했습니다.';
    
    if (error.response) {
      if (error.response.status === 404) {
        errorMessage = '해당 언어의 트랜스크립트를 찾을 수 없습니다.';
      } else if (error.response.data && error.response.data.error) {
        errorMessage = error.response.data.error;
      }
    }
    
    showError(errorMessage);
  } finally {
    // 로딩 상태 종료
    showLoading(false);
  }
}

// UI 헬퍼 함수들
function showLoading(show) {
  loadingElement.style.display = show ? 'block' : 'none';
}

function showError(message) {
  errorElement.textContent = message;
  errorElement.style.display = 'block';
}

function hideError() {
  errorElement.style.display = 'none';
}

function showResult() {
  resultElement.style.display = 'block';
}

function hideResult() {
  resultElement.style.display = 'none';
}
