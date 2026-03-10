package com.glasssutdio.wear.all.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DataSchemeDataSource;
import com.elvishew.xlog.XLog;
import com.glasssutdio.wear.GlassApplication;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.cookie.ClientCookie;
import com.liulishuo.okdownload.OkDownloadProvider;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.io.CloseableKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.CharsKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;

/* compiled from: GFileUtil.kt */
@Metadata(d1 = {"\u0000^\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0012\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010%\n\u0002\b\u0005\u001a\u0006\u0010\u0000\u001a\u00020\u0001\u001a&\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00012\u0006\u0010\u0005\u001a\u00020\u00012\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\u0007\u001a\u000e\u0010\t\u001a\u00020\u00032\u0006\u0010\n\u001a\u00020\u0001\u001a\u0016\u0010\u000b\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u0010\u0010\u000e\u001a\u00020\u00032\b\u0010\u000f\u001a\u0004\u0018\u00010\u0010\u001a\u0010\u0010\u000e\u001a\u00020\u00032\b\u0010\f\u001a\u0004\u0018\u00010\u0001\u001a\u000e\u0010\u0011\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\u0001\u001a\u000e\u0010\u0012\u001a\u00020\u00032\u0006\u0010\u0013\u001a\u00020\u0001\u001a\u0006\u0010\u0014\u001a\u00020\u0010\u001a\u000e\u0010\u0015\u001a\u00020\u00102\u0006\u0010\u0016\u001a\u00020\u0017\u001a\u000e\u0010\u0018\u001a\u00020\u00102\u0006\u0010\u0016\u001a\u00020\u0017\u001a\u0006\u0010\u0019\u001a\u00020\u0010\u001a\u0006\u0010\u001a\u001a\u00020\u0010\u001a\u0006\u0010\u001b\u001a\u00020\u0010\u001a\u000e\u0010\u001c\u001a\u00020\u00012\u0006\u0010\u0013\u001a\u00020\u0001\u001a\u0006\u0010\u001d\u001a\u00020\u0010\u001a\u0006\u0010\u001e\u001a\u00020\u0010\u001a\u001a\u0010\u001f\u001a\u0004\u0018\u00010\u00012\u0006\u0010\u0016\u001a\u00020\u00172\b\u0010 \u001a\u0004\u0018\u00010!\u001a\u0006\u0010\"\u001a\u00020\u0010\u001a\u000e\u0010#\u001a\u00020$2\u0006\u0010%\u001a\u00020\u0001\u001a\u000e\u0010&\u001a\u00020'2\u0006\u0010(\u001a\u00020\u0001\u001a\u000e\u0010)\u001a\u00020*2\u0006\u0010\u0013\u001a\u00020\u0001\u001a\u0012\u0010+\u001a\u00020\u00072\b\u0010,\u001a\u0004\u0018\u00010\u0001H\u0002\u001a\u001e\u0010-\u001a\u00020'2\u0006\u0010\u000f\u001a\u00020\u00102\u0006\u0010.\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020*\u001a\u0016\u0010/\u001a\u00020\u00012\u0006\u00100\u001a\u0002012\u0006\u0010\r\u001a\u00020\u0001\u001a\u0006\u00102\u001a\u000203\u001a\u0006\u00104\u001a\u000203\u001a*\u00105\u001a\u0002032\u0006\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u00012\u0012\u00106\u001a\u000e\u0012\u0004\u0012\u00020\u0001\u0012\u0004\u0012\u00020*07\u001a\u001e\u00108\u001a\u0002032\u0006\u00109\u001a\u00020\u00012\u0006\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001\u001a\u001e\u0010:\u001a\u0002032\u0006\u0010;\u001a\u00020'2\u0006\u0010\f\u001a\u00020\u00012\u0006\u0010\r\u001a\u00020\u0001¨\u0006<"}, d2 = {"androidCameraFilePath", "", "copyFileSegmentWithRandomAccess", "", "sourcePath", "destPath", "startPosition", "", "length", "createDirs", "folder", "createFile", ClientCookie.PATH_ATTR, "fileName", "deleteFile", "file", "Ljava/io/File;", "fileExists", "fileSupportGyro", "filePath", "getAlbumDirFile", "getAppCacheRootFile", "context", "Landroid/content/Context;", "getAppRootFile", "getBinDirFile", "getCacheFolder", "getDCIMFile", "getFileNameFromPath", "getGPTDirFile", "getLogDirFile", "getRealPathFromUri", "contentUri", "Landroid/net/Uri;", "getThumbnailFile", "getVideoInfo", "Lcom/glasssutdio/wear/all/utils/VideoInfo;", "videoPath", "hexStringToBytes", "", "hexString", "identifyFileType", "", "parseVideoDate", "dateStr", "readFileBytes", "start", "saveBitmapToFolder", "bitmap", "Landroid/graphics/Bitmap;", "test", "", "testFile", "writeGyroFiles", "config", "", "writeStringToFile", MimeTypes.BASE_TYPE_TEXT, "writeToFile1", DataSchemeDataSource.SCHEME_DATA, "app_release"}, k = 2, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class GFileUtilKt {
    public static final void test() {
    }

    public static final File getAppRootFile(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        if (context.getExternalFilesDir("") != null) {
            File externalFilesDir = context.getExternalFilesDir("");
            Intrinsics.checkNotNull(externalFilesDir);
            Intrinsics.checkNotNull(externalFilesDir);
            return externalFilesDir;
        }
        File externalCacheDir = context.getExternalCacheDir();
        File cacheDir = externalCacheDir == null ? context.getCacheDir() : externalCacheDir;
        Intrinsics.checkNotNull(cacheDir);
        return cacheDir;
    }

    public static final File getAppCacheRootFile(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        if (context.getExternalCacheDir() != null) {
            File externalCacheDir = context.getExternalCacheDir();
            Intrinsics.checkNotNull(externalCacheDir);
            Intrinsics.checkNotNull(externalCacheDir);
            return externalCacheDir;
        }
        File externalCacheDir2 = context.getExternalCacheDir();
        File cacheDir = externalCacheDir2 == null ? context.getCacheDir() : externalCacheDir2;
        Intrinsics.checkNotNull(cacheDir);
        return cacheDir;
    }

    public static final File getAlbumDirFile() {
        File file = new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "DCIM_1");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public static final File getCacheFolder() {
        File file = new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "qc_cache");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public static final File getBinDirFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "dfu");
    }

    public static final File getGPTDirFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "ai");
    }

    public static final File getThumbnailFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "thumbnail");
    }

    public static final File getLogDirFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "logs");
    }

    public static final boolean createFile(String path, String fileName) throws IOException {
        Intrinsics.checkNotNullParameter(path, "path");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        File file = new File(path + '/' + fileName);
        if (file.exists()) {
            return false;
        }
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    public static final boolean deleteFile(File file) {
        return file != null && file.delete();
    }

    public static final void writeToFile1(byte[] data, String path, String fileName) throws Throwable {
        BufferedOutputStream bufferedOutputStream;
        File parentFile;
        Intrinsics.checkNotNullParameter(data, "data");
        Intrinsics.checkNotNullParameter(path, "path");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        File file = new File(path + '/' + fileName);
        File parentFile2 = file.getParentFile();
        BufferedOutputStream bufferedOutputStream2 = null;
        Boolean boolValueOf = parentFile2 != null ? Boolean.valueOf(parentFile2.exists()) : null;
        Intrinsics.checkNotNull(boolValueOf);
        if (!boolValueOf.booleanValue() && (parentFile = file.getParentFile()) != null) {
            parentFile.mkdirs();
        }
        if (!file.exists()) {
            createFile(path, fileName);
        }
        try {
            try {
                try {
                    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file, true));
                } catch (Throwable th) {
                    th = th;
                }
            } catch (Exception e) {
                e = e;
            }
            try {
                bufferedOutputStream.write(data);
                bufferedOutputStream.close();
            } catch (Exception e2) {
                e = e2;
                bufferedOutputStream2 = bufferedOutputStream;
                e.printStackTrace();
                if (bufferedOutputStream2 != null) {
                    bufferedOutputStream2.close();
                }
            } catch (Throwable th2) {
                th = th2;
                bufferedOutputStream2 = bufferedOutputStream;
                if (bufferedOutputStream2 != null) {
                    try {
                        bufferedOutputStream2.close();
                    } catch (IOException e3) {
                        e3.printStackTrace();
                    }
                }
                throw th;
            }
        } catch (IOException e4) {
            e4.printStackTrace();
        }
    }

    public static final void writeStringToFile(String text, String path, String fileName) throws Throwable {
        BufferedWriter bufferedWriter;
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(path, "path");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        File file = new File(path + '/' + fileName);
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter bufferedWriter2 = null;
        try {
            try {
                try {
                    bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), Charsets.UTF_8));
                } catch (IOException unused) {
                    return;
                }
            } catch (Exception e) {
                e = e;
            }
        } catch (Throwable th) {
            th = th;
        }
        try {
            bufferedWriter.append((CharSequence) text);
            bufferedWriter.newLine();
            bufferedWriter.close();
        } catch (Exception e2) {
            e = e2;
            bufferedWriter2 = bufferedWriter;
            e.printStackTrace();
            if (bufferedWriter2 != null) {
                bufferedWriter2.close();
            }
        } catch (Throwable th2) {
            th = th2;
            bufferedWriter2 = bufferedWriter;
            if (bufferedWriter2 != null) {
                try {
                    bufferedWriter2.close();
                } catch (IOException unused2) {
                }
            }
            throw th;
        }
    }

    public static final String saveBitmapToFolder(Bitmap bitmap, String fileName) {
        Intrinsics.checkNotNullParameter(bitmap, "bitmap");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        File albumDirFile = getAlbumDirFile();
        if (!albumDirFile.exists() && !albumDirFile.mkdirs()) {
            return "";
        }
        File file = new File(albumDirFile, fileName);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            try {
                FileOutputStream fileOutputStream2 = fileOutputStream;
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream2);
                fileOutputStream2.flush();
                String absolutePath = file.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath, "getAbsolutePath(...)");
                CloseableKt.closeFinally(fileOutputStream, null);
                return absolutePath;
            } finally {
            }
        } catch (IOException e) {
            XLog.e("Error saving bitmap: " + e.getMessage());
            return "";
        }
    }

    public static final boolean fileExists(String path) {
        Intrinsics.checkNotNullParameter(path, "path");
        return new File(path).exists();
    }

    public static final boolean deleteFile(String str) {
        File file = new File(str);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        file.delete();
        return true;
    }

    public static final boolean createDirs(String folder) {
        Intrinsics.checkNotNullParameter(folder, "folder");
        File file = new File(folder);
        if (file.exists()) {
            return false;
        }
        file.mkdirs();
        return true;
    }

    public static final void testFile() throws IOException {
        OkDownloadProvider.context.getExternalFilesDir(null);
        try {
            new File(OkDownloadProvider.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "1.txt").createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:15:0x0046  */
    /* JADX WARN: Removed duplicated region for block: B:23:0x0058  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static final com.glasssutdio.wear.all.utils.VideoInfo getVideoInfo(java.lang.String r17) throws java.io.IOException {
        /*
            Method dump skipped, instructions count: 232
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: com.glasssutdio.wear.all.utils.GFileUtilKt.getVideoInfo(java.lang.String):com.glasssutdio.wear.all.utils.VideoInfo");
    }

    private static final long parseVideoDate(String str) {
        String str2 = str;
        if (str2 == null || StringsKt.isBlank(str2)) {
            return 0L;
        }
        try {
            Date date = new SimpleDateFormat("yyyyMMdd'T'HHmmss.SSSZ", Locale.US).parse(str);
            if (date != null) {
                return date.getTime();
            }
            return 0L;
        } catch (Exception unused) {
            return 0L;
        }
    }

    public static final String getFileNameFromPath(String filePath) {
        Intrinsics.checkNotNullParameter(filePath, "filePath");
        String str = filePath;
        if (StringsKt.contains$default((CharSequence) str, (CharSequence) "/", false, 2, (Object) null)) {
            return StringsKt.substringAfterLast$default(filePath, "/", (String) null, 2, (Object) null);
        }
        return StringsKt.contains$default((CharSequence) str, (CharSequence) "\\", false, 2, (Object) null) ? StringsKt.substringAfterLast$default(filePath, "\\", (String) null, 2, (Object) null) : filePath;
    }

    public static final int identifyFileType(String filePath) {
        Intrinsics.checkNotNullParameter(filePath, "filePath");
        if (StringsKt.contains$default((CharSequence) getFileNameFromPath(filePath), (CharSequence) ".", false, 2, (Object) null)) {
            if (!StringsKt.endsWith$default(filePath, "jpg", false, 2, (Object) null) && !StringsKt.endsWith$default(filePath, "jpeg", false, 2, (Object) null)) {
                if (StringsKt.endsWith$default(filePath, "mp4", false, 2, (Object) null) || StringsKt.endsWith$default(filePath, "avi", false, 2, (Object) null)) {
                    return 2;
                }
                if (StringsKt.endsWith$default(filePath, "opus", false, 2, (Object) null)) {
                    return 3;
                }
            }
            return 1;
        }
        if (fileSupportGyro(filePath)) {
            return 2;
        }
        XLog.i("是否支持陀螺仪：" + fileSupportGyro(filePath));
        return 1;
    }

    public static final boolean fileSupportGyro(String filePath) {
        Intrinsics.checkNotNullParameter(filePath, "filePath");
        try {
            File file = new File(filePath);
            return Intrinsics.areEqual(new String(readFileBytes(file, file.length() - 8, 8), Charsets.UTF_8), "Thisadir");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static final byte[] readFileBytes(File file, long j, int i) {
        Intrinsics.checkNotNullParameter(file, "file");
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
        try {
            RandomAccessFile randomAccessFile2 = randomAccessFile;
            randomAccessFile2.seek(j);
            byte[] bArrCopyOf = new byte[i];
            int i2 = randomAccessFile2.read(bArrCopyOf);
            if (i2 < i) {
                bArrCopyOf = Arrays.copyOf(bArrCopyOf, i2);
                Intrinsics.checkNotNullExpressionValue(bArrCopyOf, "copyOf(...)");
            }
            CloseableKt.closeFinally(randomAccessFile, null);
            return bArrCopyOf;
        } finally {
        }
    }

    public static final boolean copyFileSegmentWithRandomAccess(String sourcePath, String destPath, long j, long j2) {
        int i;
        Intrinsics.checkNotNullParameter(sourcePath, "sourcePath");
        Intrinsics.checkNotNullParameter(destPath, "destPath");
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(sourcePath, "r");
            try {
                RandomAccessFile randomAccessFile2 = randomAccessFile;
                randomAccessFile = new RandomAccessFile(destPath, "rw");
                try {
                    RandomAccessFile randomAccessFile3 = randomAccessFile;
                    randomAccessFile2.seek(j);
                    byte[] bArr = new byte[8192];
                    while (j2 > 0 && (i = randomAccessFile2.read(bArr, 0, (int) Math.min(8192, j2))) != -1) {
                        randomAccessFile3.write(bArr, 0, i);
                        j2 -= i;
                    }
                    Unit unit = Unit.INSTANCE;
                    CloseableKt.closeFinally(randomAccessFile, null);
                    Unit unit2 = Unit.INSTANCE;
                    CloseableKt.closeFinally(randomAccessFile, null);
                    return true;
                } finally {
                }
            } finally {
            }
        } catch (Exception e) {
            e.printStackTrace();
            StringBuilder sb = new StringBuilder("文件复制失败：");
            e.printStackTrace();
            XLog.i(sb.append(Unit.INSTANCE).toString());
            return false;
        }
    }

    public static final byte[] hexStringToBytes(String hexString) {
        Intrinsics.checkNotNullParameter(hexString, "hexString");
        if (hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须是偶数".toString());
        }
        int length = hexString.length() / 2;
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            String strSubstring = hexString.substring(i2, i2 + 2);
            Intrinsics.checkNotNullExpressionValue(strSubstring, "substring(...)");
            bArr[i] = (byte) Integer.parseInt(strSubstring, CharsKt.checkRadix(16));
        }
        return bArr;
    }

    public static final File getDCIMFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), "DCIM_1");
    }

    public static final void writeGyroFiles(String path, String fileName, Map<String, Integer> config) throws IOException {
        File parentFile;
        Intrinsics.checkNotNullParameter(path, "path");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        Intrinsics.checkNotNullParameter(config, "config");
        File file = new File(path + '/' + fileName);
        File parentFile2 = file.getParentFile();
        Boolean boolValueOf = parentFile2 != null ? Boolean.valueOf(parentFile2.exists()) : null;
        Intrinsics.checkNotNull(boolValueOf);
        if (!boolValueOf.booleanValue() && (parentFile = file.getParentFile()) != null) {
            parentFile.mkdirs();
        }
        if (!file.exists()) {
            createFile(path, fileName);
        }
        try {
            Writer outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8);
            BufferedWriter bufferedWriter = outputStreamWriter instanceof BufferedWriter ? (BufferedWriter) outputStreamWriter : new BufferedWriter(outputStreamWriter, 8192);
            try {
                BufferedWriter bufferedWriter2 = bufferedWriter;
                for (Map.Entry<String, Integer> entry : config.entrySet()) {
                    bufferedWriter2.write(entry.getKey() + '=' + entry.getValue().intValue());
                    bufferedWriter2.newLine();
                }
                Unit unit = Unit.INSTANCE;
                CloseableKt.closeFinally(bufferedWriter, null);
            } finally {
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final String androidCameraFilePath() {
        String absolutePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").getAbsolutePath();
        Intrinsics.checkNotNullExpressionValue(absolutePath, "getAbsolutePath(...)");
        return absolutePath;
    }

    /* JADX WARN: Removed duplicated region for block: B:19:0x0040  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public static final java.lang.String getRealPathFromUri(android.content.Context r9, android.net.Uri r10) throws java.lang.Throwable {
        /*
            java.lang.String r0 = "_data"
            java.lang.String r1 = "context"
            kotlin.jvm.internal.Intrinsics.checkNotNullParameter(r9, r1)
            r1 = 1
            r2 = 0
            java.lang.String[] r5 = new java.lang.String[r1]     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L33
            r1 = 0
            r5[r1] = r0     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L33
            android.content.ContentResolver r3 = r9.getContentResolver()     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L33
            kotlin.jvm.internal.Intrinsics.checkNotNull(r10)     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L33
            r7 = 0
            r8 = 0
            r6 = 0
            r4 = r10
            android.database.Cursor r9 = r3.query(r4, r5, r6, r7, r8)     // Catch: java.lang.Throwable -> L31 java.lang.Exception -> L33
            kotlin.jvm.internal.Intrinsics.checkNotNull(r9)     // Catch: java.lang.Exception -> L2f java.lang.Throwable -> L3c
            int r10 = r9.getColumnIndexOrThrow(r0)     // Catch: java.lang.Exception -> L2f java.lang.Throwable -> L3c
            r9.moveToFirst()     // Catch: java.lang.Exception -> L2f java.lang.Throwable -> L3c
            java.lang.String r2 = r9.getString(r10)     // Catch: java.lang.Exception -> L2f java.lang.Throwable -> L3c
        L2b:
            r9.close()
            goto L3b
        L2f:
            r10 = move-exception
            goto L35
        L31:
            r10 = move-exception
            goto L3e
        L33:
            r10 = move-exception
            r9 = r2
        L35:
            r10.printStackTrace()     // Catch: java.lang.Throwable -> L3c
            if (r9 == 0) goto L3b
            goto L2b
        L3b:
            return r2
        L3c:
            r10 = move-exception
            r2 = r9
        L3e:
            if (r2 == 0) goto L43
            r2.close()
        L43:
            throw r10
        */
        throw new UnsupportedOperationException("Method not decompiled: com.glasssutdio.wear.all.utils.GFileUtilKt.getRealPathFromUri(android.content.Context, android.net.Uri):java.lang.String");
    }
}
