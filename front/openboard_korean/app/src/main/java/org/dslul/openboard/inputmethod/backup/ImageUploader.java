package org.dslul.openboard.inputmethod.backup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;
import org.dslul.openboard.inputmethod.backup.model.UploadImageKeywordResponse;
import org.dslul.openboard.inputmethod.latin.network.ApiClient;
import org.dslul.openboard.inputmethod.latin.network.ImageUploadApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 서버로 이미지 업로드를 수행하는 유틸 클래스
 */
public class ImageUploader {
    private static final String TAG = "Backup - ImageUploader";

    public static void uploadImages(
            Context context,
            List<GalleryImage> images,
            String userId,
            SuccessCallback onSuccess,
            FailureCallback onFailure,
            CompletionCallback onComplete
    ) {
        /* ▒▒ 1) 공통 Retrofit 초기화 & 서비스 획득 ▒▒ */
        ApiClient.init(context);                       // 싱글턴 보증
        ImageUploadApi api = ApiClient.getDedicatedImageUploadApi(context);

        int total = images.size();
        final int[] completedCount = {0};

        for (GalleryImage image : images) {
            try {
                // ✅ 이미지 압축 및 바이트 배열 획득
                File imageFile = compressAndSaveToFile(context, image.getUri());
                MediaType mediaType = MediaType.parse(image.getMimeType());
                RequestBody imageBody = RequestBody.create(mediaType, imageFile);

                MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                        "file",
                        image.getFilename(),
                        imageBody
                );

                String formattedTime = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                        .format(new Date(image.getTimestamp()));

                RequestBody userIdBody = RequestBody.create(MediaType.parse("text/plain"), userId);
                RequestBody accessIdBody = RequestBody.create(MediaType.parse("text/plain"), image.getContentId());
                RequestBody imageTimeBody = RequestBody.create(MediaType.parse("text/plain"), formattedTime);

                // GalleryImage에 담긴 위도/경도 사용
                RequestBody latBody = RequestBody.create(
                        MediaType.parse("text/plain"),
                        String.valueOf(image.getLatitude())
                );
                RequestBody lonBody = RequestBody.create(
                        MediaType.parse("text/plain"),
                        String.valueOf(image.getLongitude())
                );

                Call<UploadImageKeywordResponse> call = api.uploadImageWithKeywords(
                        userIdBody,
                        accessIdBody,
                        imageTimeBody,
                        latBody,
                        lonBody,
                        filePart
                );

                call.enqueue(new Callback<UploadImageKeywordResponse>() {
                    @Override
                    public void onResponse(Call<UploadImageKeywordResponse> call,
                                           Response<UploadImageKeywordResponse> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            UploadImageKeywordResponse body = resp.body();
                            String msg = body.getMessage();

                            // ① 메시지 없으면 정상 업로드
                            if (msg == null) {
                                onSuccess.onSuccess(image.getContentId());
                                Log.i(TAG, "업로드 성공: " + image.getFilename());

                                // ② '이미 등록된 이미지입니다.' 스킵
                            } else if ("이미 등록된 이미지입니다.".equals(msg)) {
                                onSuccess.onSuccess(image.getContentId());
                                Log.i(TAG, "스킵(이미 등록): " + image.getFilename());

                                // ③ 그 외 오류는 실패 처리(세트에 추가하지 않음)
                            } else {
                                onFailure.onFailure(image.getFilename(), new Exception(msg));
                                Log.w(TAG, "처리 오류: " + msg);
                            }

                        } else {
                            // HTTP 에러
                            onFailure.onFailure(image.getFilename(),
                                    new Exception("HTTP " + resp.code()));
                            Log.w(TAG, "⚠ HTTP 오류: " + resp.code());
                        }

                        imageFile.delete();
                        if (++completedCount[0] == total && onComplete != null) {
                            onComplete.onComplete();
                        }
                    }

                    @Override
                    public void onFailure(Call<UploadImageKeywordResponse> call, Throwable t) {
                        onFailure.onFailure(image.getFilename(), t);
                        imageFile.delete();
                        if (++completedCount[0] == total && onComplete != null) {
                            onComplete.onComplete();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "업로드 중 예외 발생: " + image.getFilename(), e);
                if (onFailure != null) {
                    onFailure.onFailure(image.getFilename(), e);
                }

                // 전처리 에러에 대해서도 수행
                if (++completedCount[0] == total && onComplete != null) {
                    onComplete.onComplete();
                }
            }
        }
    }

    /**
     * ✅ Bitmap을 압축하여 임시 파일로 저장하는 메서드 (메모리 효율적)
     */
    private static File compressAndSaveToFile(Context context, Uri uri) throws IOException {
        // 1) 이미지 크기 확인을 위한 Options을 미리 선언
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        // 1) 원본 크기 확인
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("Failed to open InputStream");
            BitmapFactory.decodeStream(is, null, bounds);
        }

        // 2) 샘플링 크기 계산
        int originalWidth = bounds.outWidth;
        int targetWidth = 720;
        int scale = 1;
        while ((originalWidth / scale) > targetWidth) {
            scale *= 2;
        }
        final int sampleSize = scale;

        // 2) 하드웨어 가속 디코딩(API 28+), 그 외는 BitmapFactory
        final Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source src = ImageDecoder.createSource(
                    context.getContentResolver(), uri
            );
            bitmap = ImageDecoder.decodeBitmap(src, (decoder, info, out) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE);
                decoder.setTargetSampleSize(sampleSize);
            });
        } else {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = scale;
            // 속도/메모리 절약을 위해 RGB_565로도 설정 가능
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(is2, null, opts);
            }
        }

        // 3) JPEG 압축
        File out = new File(context.getCacheDir(),
                "upload_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        }
        bitmap.recycle();
        return out;
    }

    // 성공 콜백 인터페이스
    public interface SuccessCallback {
        void onSuccess(String contentId);
    }

    // 실패 콜백 인터페이스
    public interface FailureCallback {
        void onFailure(String filename, Throwable throwable);
    }

    // Completion 콜백
    public interface CompletionCallback {
        void onComplete();
    }
}
