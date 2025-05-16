package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.PermissionChecker;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;
import org.dslul.openboard.inputmethod.latin.auth.AuthManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * 자동 백업의 전체 흐름을 관리하는 매니저 클래스
 */
public class BackupManager {
    private static final String TAG = "Backup - BackupManager";
    private static final int REQUEST_INTERVAL_MS = 1;

    // ★ 백그라운드 스케줄러: CPU 코어 수 기반 스레드풀
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static volatile boolean isBackupRunning = false;

    /**
     * 전체 백업 흐름 실행 함수
     */
    public static void startBackup(
            Context context,
            IntConsumer onUploadStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete) {

        // 0.실행 중이면 중복 방지
        if (isBackupRunning) {
            Log.d(TAG, "백업이 이미 실행 중입니다. 중복 실행 방지됨.");
            return;
        }
        isBackupRunning = true;

        // 1. 권한 확인 (API 33 이상은 READ_MEDIA_IMAGES, 그 이하는 READ_EXTERNAL_STORAGE)
        if (!hasReadPermission(context)) {
            Log.w(TAG, "저장소 권한이 없습니다. 백업을 건너뜁니다.");
            isBackupRunning = false;
            return;
        }

        // 2. 사용자 인증 정보 가져오기
        AuthManager auth = AuthManager.getInstance(context);
        if (!auth.isLoggedIn()) {
            Log.w(TAG, "로그인 필요 → 백업 취소");
            isBackupRunning = false;
            return;
        }
        String userId = auth.getUserId();

        // 3. 이미지 불러오기
        List<GalleryImage> allImages = MediaStoreImageFetcher.getAllImages(context);
        Log.d(TAG, "전체 불러온 이미지 수: " + allImages.size());

        // 4. 필터링 : 서버 통신
        filterImages(
                context,
                allImages,
                userId,
                onUploadStart,
                progressListener,
                onComplete
        );
    }

    private static void filterImages(
            Context context,
            List<GalleryImage> allImages,
            String userId,
            IntConsumer onUploadStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete
    ) {

        if (allImages.isEmpty()) {
            Log.i(TAG, "업로드할 이미지 없음");
            isBackupRunning = false;
            return;
        }

        // 0) 이미 백업된 ID 집합 불러오기
        Set<String> cachedIds = UploadStateTracker.getBackedUpContentIds(context);
        if (cachedIds == null) {
            cachedIds = new HashSet<>();
        }

        // 1) 서버에 체크할 대상만 분리
        List<GalleryImage> toCheck = new ArrayList<>();
        for (GalleryImage img : allImages) {
            if (!cachedIds.contains(img.getContentId())) {
                toCheck.add(img);
            }
        }

        // 2) 검증 대상이 없으면 곧바로 완료
        if (toCheck.isEmpty()) {
            Log.i(TAG, "✅ 모든 이미지가 이미 백업됨, 검증 생략");
            onFilteringDone(context,
                    Collections.emptyList(),
                    onUploadStart, progressListener, onComplete);
            isBackupRunning = false;
            return;
        }

        // 3) toCheck 리스트를 그대로 업로드 단계로 넘기기
        onFilteringDone(context,
                toCheck,
                onUploadStart, progressListener, onComplete);
    }

    /**
     * 필터링된 이미지 리스트를 업로드하고, 진행 콜백을 호출
     */
    private static void onFilteringDone(
            Context context,
            List<GalleryImage> newImages,
            IntConsumer onUploadStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete) {

        Collections.sort(newImages, Comparator.comparingLong(GalleryImage::getTimestamp).reversed());
        int total = newImages.size();
        Log.i(TAG, "✅ 필터링 완료, 업로드 대상=" + total);

        if (total == 0) {
            // 업로드 대상 없으면 토스트만 띄우고 종료
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(context, "업로드할 이미지가 없습니다.", android.widget.Toast.LENGTH_LONG).show()
            );
            isBackupRunning = false;
            return;
        }

        // 업로드 시작 전 총 개수 알림
        onUploadStart.accept(total);

        // 2) 업로드 시작 토스트
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, total + "개 이미지 업로드 시작", Toast.LENGTH_SHORT).show()
        );

        // 3) 실제 업로드 단계로 분리
        uploadImages(context, newImages, progressListener, onComplete);
    }

    /**
     * 실제 업로드만 담당
     */
    private static void uploadImages(
            Context context,
            List<GalleryImage> imagesToUpload,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete) {

        AtomicInteger doneCnt = new AtomicInteger(0);
        long startMs = System.currentTimeMillis();
        Set<String> doneIds = Collections.synchronizedSet(new HashSet<>());

        int total = imagesToUpload.size();
        for (int i = 0; i < total; i++) {
            GalleryImage img = imagesToUpload.get(i);
            scheduler.schedule(() -> {
                ImageUploader.uploadImages(
                        context,
                        Collections.singletonList(img),
                        AuthManager.getInstance(context).getUserId(),
                        doneIds::add,
                        (fn, err) -> Log.e(TAG, "업로드 실패: " + fn, err),
                        () -> {
                            long d = doneCnt.incrementAndGet();
                            progressListener.onProgress(d);
                            if (d == total) {
                                Log.i(TAG, "🏁 전체 업로드 완료 (" + (System.currentTimeMillis() - startMs) + "ms)");
                                UploadStateTracker.setBackedUpContentIds(context, doneIds);
                                onComplete.run();
                                isBackupRunning = false;
                                scheduler.shutdown();
                            }
                        }
                );
            }, i * REQUEST_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static boolean hasReadPermission(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        return PermissionChecker.checkSelfPermission(context, permission)
                == PermissionChecker.PERMISSION_GRANTED;
    }
}
