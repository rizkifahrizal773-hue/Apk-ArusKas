package com.aruskas.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ImageUtil {

    private static final String TAG = "ImageUtil";

    /**
     * Compress an image from Uri to a temporary cached File under 1MB target.
     * Uses cache directory to store the temporary file.
     */
    public static File compressImage(Context context, Uri imageUri, String targetFileName) {
        try {
            // Open InputStream and decode to bitmap
            InputStream input = context.getContentResolver().openInputStream(imageUri);
            if (input == null) return null;
            
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            input.close();
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI");
                return null;
            }

            // Create target file in cache directory
            File cacheDir = context.getCacheDir();
            File compressedFile = new File(cacheDir, targetFileName);

            int quality = 90;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);

            // Progressively compress until size is less than 1 MB (1024 * 1024 bytes) or quality reaches min limit
            while (bos.toByteArray().length > 1024 * 1024 && quality > 20) {
                bos.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos);
                Log.d(TAG, "Compressing bitmap. Current quality: " + quality + ", size: " + bos.toByteArray().length + " bytes");
            }

            // Write to file
            FileOutputStream fos = new FileOutputStream(compressedFile);
            fos.write(bos.toByteArray());
            fos.flush();
            fos.close();
            bos.close();

            Log.d(TAG, "Compressed image stored successfully at: " + compressedFile.getAbsolutePath() + " with size: " + compressedFile.length() + " bytes");
            return compressedFile;

        } catch (Exception e) {
            Log.e(TAG, "Error compressing image: " + e.getMessage(), e);
            return null;
        }
    }
}
