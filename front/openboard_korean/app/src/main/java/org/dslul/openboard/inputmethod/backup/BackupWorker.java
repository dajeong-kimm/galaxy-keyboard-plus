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
import java.util.concurrent.atomic.AtomicInteger;


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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger totalHolder = new AtomicInteger();
        final NotificationCompat.Builder[] builder = new NotificationCompat.Builder[1];

        // 필터링 완료 후 총 개수 → 알림 띄우기
        BackupManager.startBackup(ctx,
                total -> {
                    totalHolder.set(total);
                    builder[0] = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_upload)
                            .setContentTitle("사진을 안전하게 보관하는 중…")
                            .setContentText("0/" + total)
                            .setOnlyAlertOnce(true)
                            .setOngoing(true)
                            .setProgress(total, 0, false);

                    // ForegroundService 로 등록
                    setForegroundAsync(new ForegroundInfo(
                            NOTIF_ID, builder[0].build()));
                },
                done -> {
                    // 진행률 업데이트
                    if (builder[0] != null) {           // 필터 결과가 0 이면 builder[0]이 없음
                        builder[0]
                                .setContentText(done + "/" + totalHolder.get())
                                .setProgress(totalHolder.get(), (int) done, false);
                        safeNotify(builder[0]);
                    }
                },
                () -> {
                    // 최종 완료
                    if (builder[0] != null) {
                        builder[0]
                                .setProgress(0, 0, false)
                                .setContentText("모든 사진이 안전하게 보관되었어요! 🎉")
                                .setOngoing(false)
                                .setAutoCancel(true)
                                .setSmallIcon(R.drawable.ic_upload_done);
                        safeNotify(builder[0]);
                    }
                    latch.countDown();
                }
        );

        // 필터→업로드 전 과정을 동기 대기
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "인터럽트", e);
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

    private void safeNotify(NotificationCompat.Builder b) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        nm.notify(NOTIF_ID, b.build());
    }
}

