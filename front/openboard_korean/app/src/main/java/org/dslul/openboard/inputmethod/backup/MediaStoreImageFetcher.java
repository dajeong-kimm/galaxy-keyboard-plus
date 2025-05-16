package org.dslul.openboard.inputmethod.backup;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.exifinterface.media.ExifInterface;
import org.dslul.openboard.inputmethod.backup.model.GalleryImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MediaStoreImageFetcher {

    public static List<GalleryImage> getAllImages(Context context) {
        List<GalleryImage> images = new ArrayList<>();

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.MIME_TYPE
        };

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null, null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
        )) {
            if (cursor == null) return images;

            int idCol       = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int nameCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int dateCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
            int mimeCol     = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);

            while (cursor.moveToNext()) {
                String id      = cursor.getString(idCol);
                String name    = cursor.getString(nameCol);
                long dateTaken = cursor.getLong(dateCol);
                String mime    = cursor.getString(mimeCol);

                Uri contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                );

                // Exif에서 위도/경도 추출
                double[] latLong = extractLatLong(context, contentUri);

                images.add(new GalleryImage(
                        contentUri,
                        name,
                        id,
                        dateTaken,
                        mime,
                        latLong[0],
                        latLong[1]
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return images;
    }

    private static double[] extractLatLong(Context ctx, Uri uri) {
        double lat = 0.0, lon = 0.0;
        try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);
            float[] result = new float[2];
            if (exif.getLatLong(result)) {
                lat = result[0];
                lon = result[1];
            }
        } catch (Exception ignored) { }
        return new double[]{lat, lon};
    }
}
