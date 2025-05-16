package org.dslul.openboard.inputmethod.backup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
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
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) throw new IOException("Failed to open InputStream");

        // 1. 이미지 크기 확인용 decode
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(inputStream, null, boundsOptions);
        inputStream.close();

        int originalWidth = boundsOptions.outWidth;
        int targetWidth = 720;  // 최대 너비 720px
        int scale = 1;
        while ((originalWidth / scale) > targetWidth) {
            scale *= 2;
        }

        // 2. 샘플링 비율로 다시 decode
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = scale;
        inputStream = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions);
        inputStream.close();

        // 3. JPEG 압축 (최대 압축: 품질 90)
        File file = new File(context.getCacheDir(), "upload_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        }
        bitmap.recycle();
        return file;
    }

    // Exif에서 위도/경도 읽어오는 헬퍼 메서드 추가
    private static String extractLatitude(Context ctx, Uri uri) {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);
            float[] latLong = new float[2];
            if (exif.getLatLong(latLong)) return String.valueOf(latLong[0]);
        } catch (Exception ignored) { }
        return "0.0";
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
