const { chromium } = require('playwright');

async function blurYoutubeThumbnails() {
  // 브라우저 시작 - 실제 Chrome 경로 사용
  const browser = await chromium.launch({ 
    headless: false,
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe' // 실제 Chrome 경로로 변경
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // 유튜브 검색 결과 페이지로 직접 이동
    await page.goto('https://www.youtube.com/results?search_query=블랙핑크');
    console.log('블랙핑크 검색 결과 페이지로 이동했습니다.');

    // 쿠키 동의 창이 있으면 수락
    try {
      const acceptButton = await page.getByRole('button', { name: /accept|동의|agree/i });
      if (await acceptButton.isVisible({ timeout: 5000 })) {
        await acceptButton.click();
        console.log('쿠키 동의 창을 수락했습니다.');
      }
    } catch (e) {
      console.log('쿠키 동의 창이 없거나 이미 처리되었습니다.');
    }

    // 검색 결과 페이지 콘텐츠가 로드될 때까지 대기
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(3000); // 충분한 대기 시간

    console.log('썸네일 블러 처리를 시작합니다...');
    
    // 더 포괄적인 썸네일 선택자 - Shorts와 일반 비디오 모두 포함
    const allThumbnailsSelector = 'img.yt-core-image';
    
    // 썸네일에 블러 효과 적용
    const thumbnailCount = await page.evaluate((selector) => {
      // 모든 대상 썸네일에 블러 효과 적용
      const thumbnails = document.querySelectorAll(selector);
      console.log(`${thumbnails.length}개의 썸네일을 찾았습니다.`);
      
      if (thumbnails.length === 0) {
        console.log('썸네일을 찾을 수 없습니다. 선택자를 확인해주세요.');
        return 0;
      }
      
      thumbnails.forEach((thumbnail, index) => {
        // 썸네일이 화면에 표시되는지 확인
        const rect = thumbnail.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) {
          // 원래 스타일 백업
          const originalStyle = thumbnail.getAttribute('style') || '';
          
          // 블러 효과 적용
          thumbnail.style.filter = 'blur(10px)';
          
          // 스타일 정보 저장 (나중에 복원할 수 있도록)
          thumbnail.setAttribute('data-original-style', originalStyle);
          
          console.log(`썸네일 #${index + 1}에 블러 효과를 적용했습니다.`);
        }
      });
      
      return thumbnails.length;
    }, allThumbnailsSelector);
    
    console.log(`${thumbnailCount}개의 썸네일을 찾았습니다.`);
    
    // CSS 스타일을 페이지에 주입하여 모든 썸네일에 블러 적용
    await page.evaluate(() => {
      const style = document.createElement('style');
      style.textContent = `
        img.yt-core-image {
          filter: blur(10px) !important;
        }
      `;
      document.head.appendChild(style);
      console.log('전역 CSS 스타일로 모든 썸네일에 블러 효과를 적용했습니다.');
    });
    
    console.log('CSS를 통해 블러 효과를 적용했습니다.');
    
    // 스크롤 이벤트로 더 많은 썸네일 로드 (무한 스크롤)
    for (let i = 0; i < 3; i++) {
      await page.evaluate(() => {
        window.scrollBy(0, window.innerHeight * 0.8);
      });
      
      // 새로 로드된 콘텐츠가 렌더링될 때까지 대기
      await page.waitForTimeout(2000);
      
      // 스크롤 후 상태 확인
      const visibleCount = await page.evaluate((selector) => {
        const thumbnails = document.querySelectorAll(selector);
        return thumbnails.length;
      }, allThumbnailsSelector);
      
      console.log(`스크롤 후 총 ${visibleCount}개의 썸네일이 있습니다.`);
    }
    
    // 스크린샷 저장
    await page.screenshot({ path: 'blackpink-blurred-thumbnails.png', fullPage: true });
    console.log('스크린샷을 저장했습니다: blackpink-blurred-thumbnails.png');
    
    // 사용자가 결과를 볼 수 있도록 잠시 대기
    console.log('30초 후에 브라우저가 종료됩니다...');
    await page.waitForTimeout(30000);
  } catch (error) {
    console.error('오류가 발생했습니다:', error);
  } finally {
    // 브라우저 종료
    await browser.close();
    console.log('브라우저가 종료되었습니다.');
  }
}

// 실행
async function main() {
  await blurYoutubeThumbnails();
}

main().catch(console.error);