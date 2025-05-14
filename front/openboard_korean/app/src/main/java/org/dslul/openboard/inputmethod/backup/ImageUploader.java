package org.dslul.openboard.inputmethod.backup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;

import java.io.ByteArrayOutputStream;
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
        ImageUploadApi api = RetrofitInstance.getUploadApi();
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

                Call<Void> call = api.uploadImage(
                        "Bearer " + accessToken,
                        userIdBody,
                        accessIdBody,
                        imageTimeBody,
                        filePart
                );

                call.enqueue(new Callback<Void>() {
                    // 업로드 성공
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Log.i(TAG, "✅ 업로드한 이미지: " + image.getFilename());
                            if (onSuccess != null) {
                                onSuccess.onSuccess(image.getContentId());
                            }
                        } else {
                            Log.w(TAG, "⚠️ 업로드 실패한 이미지: " + response.code() + " - " + image.getFilename());
                        }
                        imageFile.delete();
                        if (++completedCount[0] == total && onComplete != null) {
                            onComplete.onComplete();
                        }
                    }

                    // 업로드 실행 중 에러
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "Upload failed: " + image.getFilename(), t);
                        if (onFailure != null) {
                            onFailure.onFailure(image.getFilename(), t);
                        }
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

        // 3. JPEG 압축 (최대 압축: 품질 50)
        File file = new File(context.getCacheDir(), "upload_" + System.currentTimeMillis() + ".jpg");
        FileOutputStream fos = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);
        bitmap.recycle();
        fos.close();
        return file;
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
