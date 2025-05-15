package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;
import org.dslul.openboard.inputmethod.latin.R;

import java.util.List;
import java.util.concurrent.CountDownLatch;


public class BackupWorker extends Worker {

    public interface ProgressListener {
        /**
         * 이미지 한 건 업로드가 끝날 때마다 호출
         */
        void onProgress(long done);
    }

    private static final String TAG = "BackupWorker";
    private static final String CHANNEL_ID = "backup_upload_channel";
    private static final int NOTIF_ID = 1001;

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        // 0) 채널 생성
        createChannel(ctx);

        // 1) 전체 업로드 대상 수 계산
        List<GalleryImage> all = MediaStoreImageFetcher.getAllImages(ctx);
        int total = all.size();

        // 2) 초기 알림(Foreground) 띄우기
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_upload)
                .setContentTitle("추억을 안전하게 보관하는 중…")
                .setContentText("0/" + total)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(total, 0, false);

        // WorkManager 에 ForegroundService 로 등록
        safeSetForeground(new ForegroundInfo(NOTIF_ID, builder.build()));

        final CountDownLatch latch = new CountDownLatch(1);

        // 3) 실제 백업 로직 (알림 업데이트는 콜백에서)
        BackupManager.startBackup(ctx, (done) -> {
            builder
                    .setContentTitle("사진을 정리하고 있어요…")
                    .setContentText(done + "/" + total)
                    .setProgress(total, (int) done, false);
            safeNotify(NOTIF_ID, builder);
        }, () -> {
            // 모든 완료시
            builder.setProgress(0, 0, false)
                    .setContentText("모든 사진이 안전하게 저장되었어요! 🎉")
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setSmallIcon(R.drawable.ic_upload_done);
            safeNotify(NOTIF_ID, builder);

            // latch 해제 → doWork()가 다음으로 넘어감
            latch.countDown();
        });

        // 백업 완료 신호까지 대기
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "백업 작업 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
            return Result.failure();
        }

        return Result.success();
    }

    private void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "백업 업로드",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setDescription("사진 백업 진행 상황");
            nm.createNotificationChannel(ch);
        }
    }

    private void safeSetForeground(ForegroundInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // 권한 없으면 그냥 넘어감
        }
        setForegroundAsync(info);
    }

    private void safeNotify(int id, NotificationCompat.Builder builder) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        nm.notify(id, builder.build());
    }
}

