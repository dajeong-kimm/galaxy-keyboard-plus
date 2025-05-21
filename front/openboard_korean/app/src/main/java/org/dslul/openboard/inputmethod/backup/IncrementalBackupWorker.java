package org.dslul.openboard.inputmethod.backup;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.dslul.openboard.inputmethod.latin.R;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class IncrementalBackupWorker extends Worker {
    private static final String UNIQUE_NAME = "incremental_backup";
    private static final String TAG = "IncrementalBackupWorker";
    private static final String CHANNEL_ID = "backup_upload_channel";
    private static final int NOTIF_ID = 1001;
    private static final long DELAY_SEC = 1;

    public IncrementalBackupWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();

        // ─── ① 워커 시작 직후 “다음 증분” 워크 재스케줄 ───
        Constraints cons = new Constraints.Builder()
                .addContentUriTrigger(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        /* triggerForDescendants= */ true
                )
                .setTriggerContentUpdateDelay(Duration.ofSeconds(DELAY_SEC))
                .build();

        OneTimeWorkRequest next =
                new OneTimeWorkRequest.Builder(IncrementalBackupWorker.class)
                        .setConstraints(cons)
                        .build();

        WorkManager.getInstance(ctx)
                .enqueueUniqueWork(
                        UNIQUE_NAME,
                        ExistingWorkPolicy.REPLACE,
                        next
                );

        // ─── ② 알림용 채널 생성 ───
        createChannel(ctx);

        // ─── ③ 진행률 표시 준비 ───
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger totalHolder = new AtomicInteger();
        final NotificationCompat.Builder[] builder = new NotificationCompat.Builder[1];

        // ─── ④ 실제 증분 백업 수행 & 알림 콜백 ───
        BackupManager.startBackup(
                ctx,
                total -> {
                    // 업로드 시작
                    totalHolder.set(total);
                    builder[0] = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_upload)
                            .setContentTitle("사진을 안전하게 보관할게요.")
                            .setContentText("0/" + totalHolder.get())
                            .setOnlyAlertOnce(true)
                            .setOngoing(true)
                            .setProgress(totalHolder.get(), 0, false);

                    // 포그라운드 서비스 알림 등록
                    setForegroundAsync(new ForegroundInfo(
                            NOTIF_ID,
                            builder[0].build()
                    ));
                },
                done -> {
                    // 진행률 업데이트
                    if (builder[0] != null) {
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
                                .setContentTitle("모든 사진이 안전하게 보관되었어요! 🎉")
                                .setContentText("Galaxy Search Plus에서 사진을 검색해보세요")
                                .setOngoing(false)
                                .setAutoCancel(true)
                                .setSmallIcon(R.drawable.ic_upload_done);
                    }
                    latch.countDown();
                }
        );

        // ─── ⑤ 백업 완료 대기 ───
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for backup", e);
            Thread.currentThread().interrupt();
            return Result.failure();
        }

        // ─── ⑥ 워커 종료 후에도 알림이 남도록 일반 Notification으로 다시 띄우기 ───
        if (builder[0] != null) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {
                nm.notify(NOTIF_ID, builder[0].build());
            }
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
                ContextCompat.checkSelfPermission(
                        getApplicationContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            return;
        }
        nm.notify(NOTIF_ID, b.build());
    }
}
