package com.jingliu.chat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public final class CapturePhotoProvider extends ContentProvider {
    private static final String SUFFIX = ".camera-files";

    static Uri create(Context context) throws IOException {
        Uri uri = new Uri.Builder().scheme("content").authority(context.getPackageName() + SUFFIX)
            .appendPath(UUID.randomUUID() + ".jpg").build();
        File image = file(context, uri);
        File directory = image.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create camera cache");
        }
        if (!image.createNewFile()) throw new IOException("Cannot create camera output");
        return uri;
    }

    static File file(Context context, Uri uri) {
        List<String> segments = uri.getPathSegments();
        if (!(context.getPackageName() + SUFFIX).equals(uri.getAuthority()) || segments.size() != 1
                || !segments.get(0).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")) {
            throw new IllegalArgumentException("Invalid camera URI");
        }
        return new File(new File(context.getCacheDir(), "camera"), segments.get(0));
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        file(getContext(), uri);
        return "image/jpeg";
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File image = file(getContext(), uri);
        if (!image.isFile()) throw new FileNotFoundException("Camera output missing");
        return ParcelFileDescriptor.open(image, ParcelFileDescriptor.parseMode(mode));
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File image = file(getContext(), uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        if (image.isFile()) {
            Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "拍摄照片.jpg";
                else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = image.length();
            }
            cursor.addRow(row);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
