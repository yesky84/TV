package com.fongmi.android.tv.utils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.fongmi.android.tv.App;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLConnection;

public class FileUtil {

    private static final String TAG = FileUtil.class.getSimpleName();

    public static String getRootPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static File getCacheDir() {
        return App.get().getCacheDir();
    }

    public static String getCachePath() {
        return getCacheDir().getAbsolutePath();
    }

    public static File getCacheFile(String fileName) {
        return new File(getCacheDir(), fileName);
    }

    public static File getJar() {
        return getCacheFile("spider.jar");
    }

    public static File getLocal(String path) {
        return new File(path.replace("file:/", getRootPath()));
    }

    private static Uri getShareUri(File file) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.N ? Uri.fromFile(file) : FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
    }

    private static String getMimeType(String fileName) {
        String mimeType = URLConnection.guessContentTypeFromName(fileName);
        return TextUtils.isEmpty(mimeType) ? "*/*" : mimeType;
    }

    public static File write(File file, byte[] data) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.flush();
        fos.close();
        return file;
    }

    public static String read(String path) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(getLocal(path))));
            StringBuilder sb = new StringBuilder();
            String text;
            while ((text = br.readLine()) != null) sb.append(text).append("\n");
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean equals(String md5) throws IOException {
        return Files.hash(FileUtil.getJar(), Hashing.md5()).toString().equalsIgnoreCase(md5);
    }

    public static void clearDir(File dir) {
        if (dir == null) return;
        if (dir.isDirectory()) for (File file : dir.listFiles()) clearDir(file);
        if (dir.delete()) Log.d(TAG, "Deleted:" + dir.getPath());
    }

    public static void openFile(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(getShareUri(file), FileUtil.getMimeType(file.getName()));
        App.get().startActivity(intent);
    }
}
