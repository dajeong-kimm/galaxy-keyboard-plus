package org.dslul.openboard.inputmethod.backup;


import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import org.dslul.openboard.inputmethod.backup.model.GalleryImage;

import java.util.ArrayList;
import java.util.List;

/**
 * 기기의 MediaStore에서 모든 이미지(사진) 정보를 불러오는 유틸 클래스
 */
public class MediaStoreImageFetcher {
    private static final String TAG = "Backup - ImageFetcher";

    /**
     * 기기의 MediaStore에서 모든 이미지(사진) 정보를 불러와 리스트로 반환
     * - 동영상 제외
     * - 최신순 정렬
     */
    public static List<GalleryImage> getAllImages(Context context) {
        List<GalleryImage> images = new ArrayList<>();

        Log.d(TAG, "이미지 로딩 시작");

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.MIME_TYPE
        };

        String sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC";

        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                MediaStore.Images.Media.MIME_TYPE + " LIKE ?",
                new String[]{"image/%"},
                sortOrder
        );

        if (cursor != null) {
            try {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                int timeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                int mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String name = cursor.getString(nameCol);
                    long time = cursor.getLong(timeCol);
                    String mime = cursor.getString(mimeCol);
                    Uri contentUri = ContentUris.withAppendedId(uri, id);

                    images.add(new GalleryImage(
                            contentUri,
                            name != null ? name : "unknown.jpg",
                            String.valueOf(id),
                            time,
                            mime
                    ));
                }

                Log.i(TAG, "✅ 총 " + images.size() + "개의 이미지 로드 완료");

            } catch (Exception e) {
                Log.e(TAG, "❗ 이미지 로드 중 오류 발생", e);
            } finally {
                cursor.close();
            }
        } else {
            Log.w(TAG, "❗ 이미지 커서를 불러오지 못함 (cursor is null)");
        }

        return images;
    }
}
