package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.PermissionChecker;

import org.dslul.openboard.inputmethod.backup.model.FilterImageResponse;
import org.dslul.openboard.inputmethod.backup.model.FilterImageResult;
import org.dslul.openboard.inputmethod.backup.model.GalleryImage;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.ImageFilterApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;

/**
 * 자동 백업의 전체 흐름을 관리하는 매니저 클래스
 */
public class BackupManager {
    private static final String TAG = "Backup - BackupManager";

    private static final int MAX_IMAGES = 100;
    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    private static final int REQUEST_INTERVAL_MS = 60; // 60ms 간격 = 1000개/분

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
        AuthManager auth = AuthManager.getInstance(context);
        if (!auth.isLoggedIn()) {
            Log.w(TAG, "⛔ 로그인 필요 → 백업 취소");
            return;
        }
        String userId      = auth.getUserId();

        // 3. 이미지 불러오기
        List<GalleryImage> allImages = MediaStoreImageFetcher.getAllImages(context);
        Log.d(TAG, "📸 전체 불러온 이미지 수: " + allImages.size());

        // ✅ 필터링 시간 측정 시작
        long filteringStart = System.currentTimeMillis();

        // 4. 필터링 : 서버 통신
        filterNewImages(context, allImages, userId, filteringStart);

    }

    private static void filterNewImages(Context context, List<GalleryImage> allImages, String userId, long filteringStart) {
        /* ApiClient 초기화 보증 후 서비스 사용 */
        ApiClient.init(context);
        ImageFilterApi filterApi = ApiClient.getImageFilterApi();

        List<GalleryImage> newImages = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger pending = new AtomicInteger(allImages.size());

        for (GalleryImage image : allImages) {
            String accessId = image.getContentId();

            filterApi.checkImage(userId, accessId).enqueue(new Callback<FilterImageResponse>() {
                @Override
                public void onResponse(Call<FilterImageResponse> call, retrofit2.Response<FilterImageResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        FilterImageResult result = response.body().getResult();
                        if (result != null && !result.isExist()) {
                            newImages.add(image);
                        }
                    } else {
                        Log.e(TAG, "⚠️ 응답 오류 (contentId=" + accessId + "): " + response.code());
                    }
                    checkComplete();
                }

                @Override
                public void onFailure(Call<FilterImageResponse> call, Throwable t) {
                    Log.e(TAG, "❗ 요청 실패 (contentId=" + accessId + "): " + t.getMessage());
                    checkComplete();
                }

                private void checkComplete() {
                    if (pending.decrementAndGet() == 0) {
                        onAllChecksCompleted(context, newImages, userId, filteringStart);
                    }
                }
            });


        }
    }

    private static void onAllChecksCompleted(Context context, List<GalleryImage> newImages, String userId, long filteringStart) {
        Log.i(TAG, "\u2714\uFE0F 서버 필터링 완료, 존재하지 않는 이미지 수: " + newImages.size());

        Collections.sort(newImages, Comparator.comparingLong(GalleryImage::getTimestamp).reversed());
        if (newImages.size() > MAX_IMAGES) {
            newImages = newImages.subList(0, MAX_IMAGES);
        }

        if (newImages.isEmpty()) {
            Log.i(TAG, "\uD83D\uDFB6 업로드할 이미지 없음");
            isBackupRunning = false;
            return;
        }

        long filteringDuration = System.currentTimeMillis() - filteringStart;
        Log.i(TAG, "\u23F1 필터링 시간: " + filteringDuration + "ms");

        uploadImages(context, newImages, userId, filteringStart);
    }

    private static void uploadImages(Context context, List<GalleryImage> newImages, String userId, long filteringStart) {

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
        Log.i(TAG, "✅ 필터링 완료 (" + newImages.size() + "개), 소요 시간: " + filteringDuration + "ms");


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
                        userId,
                        contentId -> uploadedIds.add(contentId),
                        (filename, throwable) -> {
                            // 실패 로그
                        },
                        // 비동기 실행이 끝나는 모든 순간에 실행
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
    }

    private static boolean hasReadPermission(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES // 정확한 권한 이름
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        return PermissionChecker.checkSelfPermission(context, permission)
                == PermissionChecker.PERMISSION_GRANTED;
    }
}
