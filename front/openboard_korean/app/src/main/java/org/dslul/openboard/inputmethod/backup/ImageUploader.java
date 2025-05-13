package org.dslul.openboard.inputmethod.backup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
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
            String accessToken,
            SuccessCallback onSuccess,
            FailureCallback onFailure,
            CompletionCallback onComplete
    ) {
        ImageUploadApi api = RetrofitInstance.getApi();
        int total = images.size();
        final int[] completedCount = {0};

        for (GalleryImage image : images) {
            try {
                // ✅ 이미지 압축 및 바이트 배열 획득
                byte[] imageBytes = compressAndReadBytes(context, image.getUri());
                MediaType mediaType = MediaType.parse(image.getMimeType());
                RequestBody imageBody = RequestBody.create(mediaType, imageBytes);

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

                Call<Void> call = api.uploadImage(
                        "Bearer " + accessToken,
                        userIdBody,
                        accessIdBody,
                        imageTimeBody,
                        filePart
                );

                call.enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.i(TAG, "✅ Uploaded: " + image.getFilename());
                            if (onSuccess != null) {
                                onSuccess.onSuccess(image.getContentId());
                            }
                        } else {
                            Log.w(TAG, "⚠️ Upload failed: " + response.code() + " - " + image.getFilename());
                        }
                        if (++completedCount[0] == total && onComplete != null) {
                            onComplete.onComplete();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "Upload failed: " + image.getFilename(), t);
                        if (onFailure != null) {
                            onFailure.onFailure(image.getFilename(), t);
                        }
                        if (++completedCount[0] == total && onComplete != null) {
                            onComplete.onComplete();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Exception during upload: " + image.getFilename(), e);
                if (onFailure != null) {
                    onFailure.onFailure(image.getFilename(), e);
                }
            }
        }
    }

    /**
     * ✅ Bitmap을 압축해서 byte[]로 변환하는 메서드
     * 최대 해상도 제한 + JPEG 압축 품질 낮춰서 OutOfMemory 방지
     */
    private static byte[] compressAndReadBytes(Context context, Uri uri) throws IOException {
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

        // 3. JPEG 압축 (최대 압축: 품질 50)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream);
        bitmap.recycle(); // 메모리 해제
        return outputStream.toByteArray();
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
