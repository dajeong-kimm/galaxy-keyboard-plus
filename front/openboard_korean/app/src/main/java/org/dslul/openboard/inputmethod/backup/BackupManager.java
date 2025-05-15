package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;

/**
 * 자동 백업의 전체 흐름을 관리하는 매니저 클래스
 */
public class BackupManager {
    private static final String TAG = "Backup - BackupManager";
    //    private static final String CHANNEL_ID = "backup_upload_channel";
//    private static final int NOTIF_ID = 1001;
    //    private static final int MAX_IMAGES = 100;
//    private static final int MAX_REQUESTS_PER_MINUTE = 1000;
    private static final int REQUEST_INTERVAL_MS = 200;

    // ★ 백그라운드 스케줄러: CPU 코어 수 기반 스레드풀
    private static final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static volatile boolean isBackupRunning = false;

    /**
     * 전체 백업 흐름 실행 함수
     */
    public static void startBackup(
            Context context,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete) {

        // 0.실행 중이면 중복 방지
        if (isBackupRunning) {
            Log.d(TAG, "⏳ 백업이 이미 실행 중입니다. 중복 실행 방지됨.");
            return;
        }

        isBackupRunning = true;

        // 1. 권한 확인 (API 33 이상은 READ_MEDIA_IMAGES, 그 이하는 READ_EXTERNAL_STORAGE)
        if (!hasReadPermission(context)) {
            Log.w(TAG, "⛔ 저장소 권한이 없습니다. 백업을 건너뜁니다.");
            isBackupRunning = false;
            return;
        }

        // 2. 사용자 인증 정보 가져오기
        AuthManager auth = AuthManager.getInstance(context);
        if (!auth.isLoggedIn()) {
            Log.w(TAG, "⛔ 로그인 필요 → 백업 취소");
            isBackupRunning = false;
            scheduler.shutdown();
            return;
        }
        String userId = auth.getUserId();

        // 3. 이미지 불러오기
        List<GalleryImage> allImages = MediaStoreImageFetcher.getAllImages(context);
        Log.d(TAG, "📸 전체 불러온 이미지 수: " + allImages.size());

        // ✅ 필터링 시간 측정 시작
        long filteringStart = System.currentTimeMillis();

        // 4. 필터링 : 서버 통신
        filterNewImages(
                context, allImages, userId, filteringStart,
                progressListener, onComplete
        );

    }

    private static void filterNewImages(
            Context context,
            List<GalleryImage> allImages,
            String userId,
            long filteringStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete
    ) {

        if (allImages.isEmpty()) {
            // 빈 리스트일 때도 스케줄러 정리
            isBackupRunning = false;
            scheduler.shutdown();
            return;
        }

        /* ApiClient 초기화 보증 후 서비스 사용 */
        ApiClient.init(context);
        ImageFilterApi filterApi = ApiClient.getDedicatedImageFilterApi(context);

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
                        onAllChecksCompleted(
                                context, newImages, userId, filteringStart,
                                progressListener, onComplete
                        );
                    }
                }
            });
        }
    }

    private static void onAllChecksCompleted(
            Context context,
            List<GalleryImage> newImages,
            String userId,
            long filteringStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete
    ) {
        Log.i(TAG, "\u2714\uFE0F 서버 필터링 완료, 존재하지 않는 이미지 수: " + newImages.size());

        Collections.sort(newImages, Comparator.comparingLong(GalleryImage::getTimestamp).reversed());

        if (newImages.isEmpty()) {
            Log.i(TAG, "\uD83D\uDFB6 업로드할 이미지 없음");
            isBackupRunning = false;
            return;
        }

        long filteringDuration = System.currentTimeMillis() - filteringStart;
        Log.i(TAG, "\u23F1 필터링 시간: " + filteringDuration + "ms");

        uploadImages(
                context,
                newImages,
                userId,
                filteringStart,
                progressListener,
                onComplete
        );
    }

    private static void uploadImages(
            Context context,
            List<GalleryImage> newImages,
            String userId,
            long filteringStart,
            BackupWorker.ProgressListener progressListener,
            Runnable onComplete
    ) {
        // 0) 채널 보장
//        createNotificationChannel(context);

        // 1) 업로드 대상 개수 확인
        if (newImages.isEmpty()) {
            Log.i(TAG, "🟰 업로드할 새로운 이미지가 없습니다.");
            // ★ 업로드할 이미지가 없을 때도 토스트 알림
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "업로드할 이미지가 없습니다.", Toast.LENGTH_LONG).show()
            );
            isBackupRunning = false;
            return;
        }

        final int total = newImages.size();
        Log.i(TAG, "새 이미지 " + total + "개 업로드 시작");

        // ★ 토스트 알림 추가 (Main 스레드에서 실행)
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, total + "개의 이미지 업로드 시작", Toast.LENGTH_LONG).show()
        );

        // 2) 알림 빌더 초기화 (진행 중)
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
//                .setSmallIcon(R.drawable.ic_upload)      // 업로드 아이콘
//                .setContentTitle("추억을 안전하게 보관하는 중…")
//                .setContentText("0/" + total)
//                .setOnlyAlertOnce(true)
//                .setOngoing(true)
//                .setProgress(total, 0, false);
//
//        safeNotify(context, builder);

        // 3) 실제 업로드 시작
        long startTimeMillis = System.currentTimeMillis();
        Set<String> uploadedIds = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            GalleryImage image = newImages.get(i);
            long delay = i * REQUEST_INTERVAL_MS;

            // ★ 메인 스레드가 아닌 scheduler를 통해 지연 실행
            scheduler.schedule(() -> {
                ImageUploader.uploadImages(
                        context,
                        Collections.singletonList(image),
                        userId,
                        contentId -> uploadedIds.add(contentId),
                        (filename, throwable) -> {
                            Log.e(TAG, "업로드 실패: " + filename, throwable);
                        },
                        () -> {
                            long done = completedCount.incrementAndGet();
                            progressListener.onProgress(done);
                            // 4) 진행률 업데이트
//                            builder
//                                    .setContentTitle("사진을 정리하고 있어요…")
//                                    .setContentText(done + "/" + total)
//                                    .setProgress(total, done, false);
//                            safeNotify(context, builder);

                            // 5) 마지막 이미지 완료 시 알림 최종 업데이트
                            if (done == total) {
                                long duration = System.currentTimeMillis() - startTimeMillis;
                                Log.i(TAG, "✅ 전체 백업 완료 - 걸린 시간: " + duration + "ms");

//                                builder.setContentText("모든 사진이 안전하게 저장되었어요! 🎉")
//                                        .setContentText(total + "개 사진이 안전하게 저장되었어요!")
//                                        .setProgress(0, 0, false)
//                                        .setOngoing(false)
//                                        .setAutoCancel(true)
//                                        .setSmallIcon(R.drawable.ic_upload_done);
//                                safeNotify(context, builder);

                                UploadStateTracker.addBackedUpContentIds(context, uploadedIds);
                                onComplete.run();
                                isBackupRunning = false;

                                // 스케줄러 자원 해제
                                scheduler.shutdown();
                            }
                        }
                );
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private static boolean hasReadPermission(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES // 정확한 권한 이름
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        return PermissionChecker.checkSelfPermission(context, permission)
                == PermissionChecker.PERMISSION_GRANTED;
    }

//    private static void createNotificationChannel(Context context) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
//        NotificationManager nm = context.getSystemService(NotificationManager.class);
//        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
//        NotificationChannel ch = new NotificationChannel(
//                CHANNEL_ID,
//                "백업 업로드",
//                NotificationManager.IMPORTANCE_LOW
//        );
//        ch.setDescription("이미지 백업 업로드 진행 상황");
//        nm.createNotificationChannel(ch);
//    }

//    private static void safeNotify(Context context, NotificationCompat.Builder builder) {
//        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
//                    == PackageManager.PERMISSION_GRANTED) {
//                nm.notify(NOTIF_ID, builder.build());
//            }
//        } else {
//            nm.notify(NOTIF_ID, builder.build());
//        }
//    }
}
