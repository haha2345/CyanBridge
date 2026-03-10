package com.glasssutdio.wear.home.download;

import android.content.Context;
import androidx.exifinterface.media.ExifInterface;
import androidx.media3.common.MimeTypes;
import com.glasssutdio.wear.GlassApplication;
import com.glasssutdio.wear.home.download.bean.VideoInformation;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.FilenameUtils;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.cookie.ClientCookie;
import com.liulishuo.okdownload.DownloadContext;
import com.liulishuo.okdownload.DownloadContextListener;
import com.liulishuo.okdownload.DownloadTask;
import com.liulishuo.okdownload.core.cause.EndCause;
import com.liulishuo.okdownload.core.cause.ResumeFailedCause;
import com.liulishuo.okdownload.core.listener.DownloadListener1;
import com.liulishuo.okdownload.core.listener.assist.Listener1Assist;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Ref;
import kotlin.text.StringsKt;

/* compiled from: DownloadUtils.kt */
@Metadata(d1 = {"\u0000L\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J0\u0010\u0003\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u0002H\u00050\u00040\u0004\"\u0004\b\u0000\u0010\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u0002H\u00050\u00042\u0006\u0010\u0007\u001a\u00020\bH\u0002J \u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\fH\u0002J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\fH\u0002J\u000e\u0010\u0012\u001a\u00020\u00132\u0006\u0010\u0014\u001a\u00020\u0015J\u0014\u0010\u0016\u001a\u00020\u00132\f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00150\u0004J\u000e\u0010\u0018\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\fJ\u000e\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\u001b\u001a\u00020\u001cJ\u0006\u0010\u001d\u001a\u00020\u001a¨\u0006\u001e"}, d2 = {"Lcom/glasssutdio/wear/home/download/DownloadUtils;", "", "()V", "averageAssign", "", ExifInterface.GPS_DIRECTION_TRUE, "source", "n", "", "createTask", "Lcom/liulishuo/okdownload/DownloadTask;", "url", "", "fileName", "tag", "deleteFile", "", ClientCookie.PATH_ATTR, "downloadFileNotExists", "", "item", "Lcom/glasssutdio/wear/home/download/bean/VideoInformation;", "downloadWatchFaceImageFile", "list", "fileExists", "getAppRootFile", "Ljava/io/File;", "context", "Landroid/content/Context;", "getVideoDirFile", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class DownloadUtils {
    public final void downloadWatchFaceImageFile(List<VideoInformation> list) {
        Intrinsics.checkNotNullParameter(list, "list");
        int size = list.size() / 10;
        if (size == 0) {
            size = 1;
        }
        List<List> listAverageAssign = averageAssign(list, size);
        final int size2 = listAverageAssign.size();
        final Ref.IntRef intRef = new Ref.IntRef();
        for (List<VideoInformation> list2 : listAverageAssign) {
            DownloadContext.QueueSet queueSet = new DownloadContext.QueueSet();
            File videoDirFile = getVideoDirFile();
            queueSet.setParentPathFile(videoDirFile);
            queueSet.setMinIntervalMillisCallbackProcess(1000);
            DownloadContext.Builder builderCommit = queueSet.commit();
            for (VideoInformation videoInformation : list2) {
                String name = videoInformation.getName();
                List listSplit$default = StringsKt.split$default((CharSequence) videoInformation.getPreImageUrl(), new char[]{FilenameUtils.EXTENSION_SEPARATOR}, false, 0, 6, (Object) null);
                String str = ((String) StringsKt.split$default((CharSequence) name, new char[]{FilenameUtils.EXTENSION_SEPARATOR}, false, 0, 6, (Object) null).get(0)) + FilenameUtils.EXTENSION_SEPARATOR + ((String) listSplit$default.get(listSplit$default.size() - 1));
                if (!fileExists(videoDirFile.getAbsolutePath() + '/' + str)) {
                    builderCommit.bindSetTask(createTask(videoInformation.getPreImageUrl(), str, name));
                }
            }
            builderCommit.setListener(new DownloadContextListener() { // from class: com.glasssutdio.wear.home.download.DownloadUtils.downloadWatchFaceImageFile.1
                @Override // com.liulishuo.okdownload.DownloadContextListener
                public void taskEnd(DownloadContext context, DownloadTask task, EndCause cause, Exception realCause, int remainCount) {
                    Intrinsics.checkNotNullParameter(context, "context");
                    Intrinsics.checkNotNullParameter(task, "task");
                    Intrinsics.checkNotNullParameter(cause, "cause");
                }

                @Override // com.liulishuo.okdownload.DownloadContextListener
                public void queueEnd(DownloadContext context) {
                    Intrinsics.checkNotNullParameter(context, "context");
                    intRef.element++;
                    int i = intRef.element;
                }
            });
            builderCommit.build().startOnParallel(new QueueImageListener());
        }
    }

    private final DownloadTask createTask(String url, String fileName, String tag) {
        DownloadTask downloadTaskBuild = new DownloadTask.Builder(url, getVideoDirFile()).setFilename(fileName).setMinIntervalMillisCallbackProcess(64).setPassIfAlreadyCompleted(false).setPriority(10).setConnectionCount(1).setReadBufferSize(8192).build();
        downloadTaskBuild.setTag(tag);
        Intrinsics.checkNotNull(downloadTaskBuild);
        return downloadTaskBuild;
    }

    public final File getVideoDirFile() {
        return new File(getAppRootFile(GlassApplication.INSTANCE.getCONTEXT()), MimeTypes.BASE_TYPE_VIDEO);
    }

    public final boolean fileExists(String path) {
        Intrinsics.checkNotNullParameter(path, "path");
        return new File(path).exists();
    }

    public final File getAppRootFile(Context context) {
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

    private final <T> List<List<T>> averageAssign(List<? extends T> source, int n) {
        List<? extends T> listSubList;
        ArrayList arrayList = new ArrayList();
        int size = source.size() % n;
        int size2 = source.size() / n;
        int i = 0;
        for (int i2 = 0; i2 < n; i2++) {
            if (size > 0) {
                listSubList = source.subList((i2 * size2) + i, ((i2 + 1) * size2) + i + 1);
                size--;
                i++;
            } else {
                listSubList = source.subList((i2 * size2) + i, ((i2 + 1) * size2) + i);
            }
            arrayList.add(listSubList);
        }
        return arrayList;
    }

    public final void downloadFileNotExists(VideoInformation item) {
        Intrinsics.checkNotNullParameter(item, "item");
        File videoDirFile = getVideoDirFile();
        String name = item.getName();
        String str = name;
        List listSplit$default = StringsKt.split$default((CharSequence) str, new char[]{FilenameUtils.EXTENSION_SEPARATOR}, false, 0, 6, (Object) null);
        String str2 = ((String) StringsKt.split$default((CharSequence) str, new char[]{FilenameUtils.EXTENSION_SEPARATOR}, false, 0, 6, (Object) null).get(0)) + FilenameUtils.EXTENSION_SEPARATOR + ((String) listSplit$default.get(listSplit$default.size() - 1));
        if (fileExists(videoDirFile.getAbsolutePath() + '/' + str2)) {
            deleteFile(videoDirFile.getAbsolutePath() + '/' + str2);
        }
        createTask(item.getVideoUrl(), str2, name).enqueue(new DownloadListener1() { // from class: com.glasssutdio.wear.home.download.DownloadUtils.downloadFileNotExists.1
            @Override // com.liulishuo.okdownload.core.listener.assist.Listener1Assist.Listener1Callback
            public void connected(DownloadTask task, int blockCount, long currentOffset, long totalLength) {
                Intrinsics.checkNotNullParameter(task, "task");
            }

            @Override // com.liulishuo.okdownload.core.listener.assist.Listener1Assist.Listener1Callback
            public void progress(DownloadTask task, long currentOffset, long totalLength) {
                Intrinsics.checkNotNullParameter(task, "task");
            }

            @Override // com.liulishuo.okdownload.core.listener.assist.Listener1Assist.Listener1Callback
            public void retry(DownloadTask task, ResumeFailedCause cause) {
                Intrinsics.checkNotNullParameter(task, "task");
                Intrinsics.checkNotNullParameter(cause, "cause");
            }

            @Override // com.liulishuo.okdownload.core.listener.assist.Listener1Assist.Listener1Callback
            public void taskEnd(DownloadTask task, EndCause cause, Exception realCause, Listener1Assist.Listener1Model model) {
                Intrinsics.checkNotNullParameter(task, "task");
                Intrinsics.checkNotNullParameter(cause, "cause");
                Intrinsics.checkNotNullParameter(model, "model");
            }

            @Override // com.liulishuo.okdownload.core.listener.assist.Listener1Assist.Listener1Callback
            public void taskStart(DownloadTask task, Listener1Assist.Listener1Model model) {
                Intrinsics.checkNotNullParameter(task, "task");
                Intrinsics.checkNotNullParameter(model, "model");
            }
        });
    }

    private final boolean deleteFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        file.delete();
        return true;
    }
}
