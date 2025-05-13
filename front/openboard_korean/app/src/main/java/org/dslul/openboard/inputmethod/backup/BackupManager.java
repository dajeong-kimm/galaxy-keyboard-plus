package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 자동 백업의 전체 흐름을 관리하는 매니저 클래스
 */
public class BackupManager {
    private static final String TAG = "Backup - BackupManager";

    private static final int MAX_IMAGES = 30;
    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    private static final int REQUEST_INTERVAL_MS = 120; // 60ms 간격 = 1000개/분

    private static volatile boolean isBackupRunning = false;

    /**
     * 전체 백업 흐름 실행 함수
     */
    public static void startBackup(Context context) {

        // 0.실행 중이면 중복 방지
        if (isBackupRunning) {
            Log.d(TAG, "⏳ 백업이 이미 실행 중입니다. 중복 실행 방지됨.");
            return;
        }

        isBackupRunning = true;

        // 1. 권한 확인 (API 33 이상은 READ_MEDIA_IMAGES, 그 이하는 READ_EXTERNAL_STORAGE)
        if (!hasReadPermission(context)) {
            Log.w(TAG, "⛔ 저장소 권한이 없습니다. 백업을 건너뜁니다.");
            return;
        }

        // 2. 사용자 인증 정보 가져오기
//        String userId = TokenStore.getUserId(context);
//        String accessToken = TokenStore.getAccessToken(context);
//        if (userId.isEmpty() || accessToken.isEmpty()) {
//            Log.w(TAG, "⛔ 사용자 인증 정보 없음. 백업 중단");
//            return;
//        }

        // 3. 이미지 불러오기
        List<GalleryImage> allImages = MediaStoreImageFetcher.getAllImages(context);
        Log.d(TAG, "📸 전체 불러온 이미지 수: " + allImages.size());

        // ✅ 필터링 시간 측정 시작
        long filteringStart = System.currentTimeMillis();

        // 4. 마지막 업로드 시간 이후의 이미지만 필터링
//        List<GalleryImage> newImages = new ArrayList<>();
//        for (GalleryImage image : allImages) {
//            if (image.getTimestamp() >= lastUploadedAt) {
//                newImages.add(image);
//            }
//        }

        Set<String> backedUpIds = UploadStateTracker.getBackedUpContentIds(context);
        List<GalleryImage> newImages = new ArrayList<>();
        for (GalleryImage image : allImages) {
            if (!backedUpIds.contains(image.getContentId())) {
                newImages.add(image);
            }
        }

        // 최신순 정렬
        Collections.sort(newImages, new Comparator<GalleryImage>() {
            @Override
            public int compare(GalleryImage o1, GalleryImage o2) {
                return Long.compare(o2.getTimestamp(), o1.getTimestamp()); // 내림차순
            }
        });

        // 최대 업로드 사진 수 제한
        if (newImages.size() > MAX_IMAGES) {
            newImages = newImages.subList(0, MAX_IMAGES);
        }

        if (newImages.isEmpty()) {
            Log.i(TAG, "🟰 업로드할 새로운 이미지가 없습니다.");
            isBackupRunning = false;
            return;
        }

        Log.i(TAG, "새 이미지 " + newImages.size() + "개 업로드 시작");

        // 필터링 시간 측정 종료
        long filteringEnd = System.currentTimeMillis();
        long filteringDuration = filteringEnd - filteringStart;
        Log.i(TAG, "⏱✅ 필터링 완료 (" + newImages.size() + "개), 소요 시간: " + filteringDuration + "ms");


        // ✅ 시간 측정 시작 (필터링 완료 직후)
        final long startTimeMillis = System.currentTimeMillis();

        // 5. 이미지 업로드
        Set<String> uploadedIds = new HashSet<>();
        Handler handler = new Handler(Looper.getMainLooper());
        int uploadCount = Math.min(newImages.size(), MAX_REQUESTS_PER_MINUTE);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < uploadCount; i++) {
            final GalleryImage image = newImages.get(i);
            int delay = i * REQUEST_INTERVAL_MS;

            handler.postDelayed(() -> {
                ImageUploader.uploadImages(
                        context,
                        Collections.singletonList(image),
                        "36648ad3-ed4b-4eb0-bcf1-1dc66fa5d258", // userId
                        "", // accessToken
                        contentId -> uploadedIds.add(contentId),
                        (filename, throwable) -> {
                            // 실패 로그
                        },
                        () -> {
                            if (completedCount.incrementAndGet() == uploadCount) {
                                UploadStateTracker.addBackedUpContentIds(context, uploadedIds);

                                // ✅ 전체 백업 완료 시점
                                long endTimeMillis = System.currentTimeMillis();
                                long durationMillis = endTimeMillis - startTimeMillis;
                                Log.i(TAG, "✅ 전체 백업 완료 - 걸린 시간: " + durationMillis + "ms");

                                isBackupRunning = false;

                            }
                        }
                );
            }, delay);
        }

        // 6. UploadStateTracker 업데이트
//        long latestTimestamp = lastUploadedAt;
//        for (GalleryImage image : newImages) {
//            if (image.getTimestamp() > latestTimestamp) {
//                latestTimestamp = image.getTimestamp();
//            }
//        }

//        UploadStateTracker.setLastUploadedAt(context, latestTimestamp);
    }

    private static boolean hasReadPermission(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES // 정확한 권한 이름
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        return PermissionChecker.checkSelfPermission(context, permission)
                == PermissionChecker.PERMISSION_GRANTED;
    }
}
