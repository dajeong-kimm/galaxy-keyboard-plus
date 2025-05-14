package org.dslul.openboard.inputmethod.backup.model;

import android.net.Uri;

public class GalleryImage {
    private final Uri uri;             // content:// URI
    private final String filename;     // DISPLAY_NAME
    private final String contentId;    // MediaStore._ID (고유 ID)
    private final long timestamp;      // DATE_TAKEN 또는 DATE_ADDED
    private final String mimeType;     // MIME_TYPE

    public GalleryImage(Uri uri, String filename, String contentId, long timestamp, String mimeType) {
        this.uri = uri;
        this.filename = filename;
        this.contentId = contentId;
        this.timestamp = timestamp;
        this.mimeType = mimeType;
    }

    public Uri getUri() {
        return uri;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentId() {
        return contentId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMimeType() {
        return mimeType;
    }

    @Override
    public String toString() {
        return "GalleryImage{" +
                "uri=" + uri +
                ", filename='" + filename + '\'' +
                ", contentId='" + contentId + '\'' +
                ", timestamp=" + timestamp +
                ", mimeType='" + mimeType + '\'' +
                '}';
    }
}
