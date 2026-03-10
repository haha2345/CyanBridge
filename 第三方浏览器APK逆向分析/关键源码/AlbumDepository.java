package com.glasssutdio.wear.depository;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.constraintlayout.core.motion.utils.TypedValues;
import androidx.core.content.ContextCompat;
import com.androidnetworking.AndroidNetworking;
import com.androidnetworking.common.Priority;
import com.androidnetworking.error.ANError;
import com.androidnetworking.interfaces.DownloadListener;
import com.androidnetworking.interfaces.DownloadProgressListener;
import com.elvishew.xlog.XLog;
import com.glasssutdio.wear.GlassApplication;
import com.glasssutdio.wear.ai.spark.AudioTrackManager;
import com.glasssutdio.wear.all.Constant;
import com.glasssutdio.wear.all.GlobalKt;
import com.glasssutdio.wear.all.ThreadExtKt;
import com.glasssutdio.wear.all.pref.UserConfig;
import com.glasssutdio.wear.all.utils.DateUtil;
import com.glasssutdio.wear.all.utils.GFileUtilKt;
import com.glasssutdio.wear.all.utils.GsonInstance;
import com.glasssutdio.wear.bus.RecordingToPcmSuccessfullyEvent;
import com.glasssutdio.wear.database.GlassDatabase;
import com.glasssutdio.wear.database.dao.GlassAlbumDao;
import com.glasssutdio.wear.database.entity.GlassAlbumEntity;
import com.glasssutdio.wear.depository.AlbumDepository;
import com.glasssutdio.wear.depository.bean.DownloadSpeedCalculator;
import com.glasssutdio.wear.depository.bean.PictureDownloadBean;
import com.glasssutdio.wear.home.album.update.PcmToMp3Kt;
import com.glasssutdio.wear.stabilization.ByteUtils;
import com.glasssutdio.wear.stabilization.Mp4Decode;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.commons.io.FilenameUtils;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.cookie.ClientCookie;
import com.hjq.permissions.Permission;
import com.jieli.jl_audio_decode.callback.OnStateCallback;
import com.jieli.jl_audio_decode.opus.OpusManager;
import com.jieli.jl_audio_decode.opus.model.OpusOption;
import com.liulishuo.okdownload.core.breakpoint.BreakpointSQLiteKey;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.Unit;
import kotlin.io.ByteStreamsKt;
import kotlin.io.CloseableKt;
import kotlin.io.FilesKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Lambda;
import kotlin.jvm.internal.Ref;
import kotlin.math.MathKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.greenrobot.eventbus.EventBus;

/* compiled from: AlbumDepository.kt */
@Metadata(d1 = {"\u0000\u0094\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0002\b\f\u0018\u0000 d2\u00020\u0001:\u0002deB\u0005¢\u0006\u0002\u0010\u0002J\u000e\u0010*\u001a\u00020\u00042\u0006\u0010+\u001a\u00020\u0018J\u0006\u0010,\u001a\u00020-J\u0018\u0010.\u001a\u00020-2\u0006\u0010/\u001a\u0002002\u0006\u00101\u001a\u000202H\u0002J\u000e\u00103\u001a\u00020-2\u0006\u00104\u001a\u00020\u001bJ\u000e\u00105\u001a\u00020-2\u0006\u00106\u001a\u00020\u001bJ\b\u00107\u001a\u00020-H\u0002J\u0015\u00108\u001a\u0004\u0018\u00010\u00042\u0006\u00109\u001a\u00020\u0016¢\u0006\u0002\u0010:J\u0010\u0010;\u001a\u00020\u00162\u0006\u0010<\u001a\u00020=H\u0002J\u001e\u0010>\u001a\u00020-2\u0006\u0010?\u001a\u00020\u00162\u0006\u0010@\u001a\u00020\u00162\u0006\u00109\u001a\u00020\u0016J(\u0010A\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001b0\u001a0B2\f\u0010C\u001a\b\u0012\u0004\u0012\u00020\u001b0DH\u0002J\u000e\u0010E\u001a\u00020!2\u0006\u0010F\u001a\u00020\u0016J\u0014\u0010G\u001a\u00020-2\f\u0010H\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001aJ\u0006\u0010I\u001a\u00020!J\u0012\u0010J\u001a\u0004\u0018\u00010K2\u0006\u0010L\u001a\u00020\u0016H\u0002J\b\u0010M\u001a\u00020-H\u0002J\u000e\u0010N\u001a\u00020\u00182\u0006\u0010O\u001a\u00020\u0016J\f\u0010P\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001aJ\u0018\u0010Q\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001b0\u001a0BJ\u0010\u0010R\u001a\u0004\u0018\u00010\u001b2\u0006\u00109\u001a\u00020\u0016J \u0010S\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001b0\u001a0B2\u0006\u0010T\u001a\u00020\u0004J\u0018\u0010U\u001a\u0014\u0012\u0004\u0012\u00020\u0004\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001b0\u001a0BJ\u0010\u0010V\u001a\u00020-2\u0006\u0010\"\u001a\u00020\u0016H\u0002J\u000e\u0010W\u001a\u00020-2\u0006\u00106\u001a\u00020\u001bJ\u0018\u0010X\u001a\u00020-2\u0006\u0010Y\u001a\u00020Z2\u0006\u0010[\u001a\u00020=H\u0002J\u0018\u0010\\\u001a\u00020-2\u0006\u0010Y\u001a\u00020Z2\u0006\u0010]\u001a\u00020=H\u0002J\u000e\u0010^\u001a\u00020-2\u0006\u0010(\u001a\u00020)J\u000e\u0010_\u001a\u00020-2\u0006\u0010`\u001a\u00020\u0016J\u0010\u0010a\u001a\u00020-2\u0006\u0010b\u001a\u00020\u0016H\u0007J\u0010\u0010c\u001a\u00020-2\u0006\u0010b\u001a\u00020\u0016H\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u0011\u0010\u0005\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u000b\"\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u001a\u0010\u0014\u001a\u000e\u0012\u0004\u0012\u00020\u0016\u0012\u0004\u0012\u00020\u00040\u0015X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e¢\u0006\u0002\n\u0000R \u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001aX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u001d\"\u0004\b\u001e\u0010\u001fR\u000e\u0010 \u001a\u00020!X\u0082\u000e¢\u0006\u0002\n\u0000R\u0019\u0010\"\u001a\n #*\u0004\u0018\u00010\u00160\u0016¢\u0006\b\n\u0000\u001a\u0004\b$\u0010%R\u0014\u0010&\u001a\b\u0012\u0004\u0012\u00020\u001b0\u000fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020\u0004X\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010(\u001a\u0004\u0018\u00010)X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006f"}, d2 = {"Lcom/glasssutdio/wear/depository/AlbumDepository;", "", "()V", "downloadErrorCount", "", "eisCallback", "Lcom/glasssutdio/wear/stabilization/Mp4Decode$Mp4DecodeCallback;", "getEisCallback", "()Lcom/glasssutdio/wear/stabilization/Mp4Decode$Mp4DecodeCallback;", "failCount", "getFailCount", "()I", "setFailCount", "(I)V", "fileQueue", "Ljava/util/concurrent/BlockingDeque;", "Lcom/glasssutdio/wear/depository/bean/PictureDownloadBean;", "glassAlbumDao", "Lcom/glasssutdio/wear/database/dao/GlassAlbumDao;", "intervalTime", "keyMap", "", "", "lastUpdateTime", "", "listData", "", "Lcom/glasssutdio/wear/database/entity/GlassAlbumEntity;", "getListData", "()Ljava/util/List;", "setListData", "(Ljava/util/List;)V", "opusToPcmIng", "", ClientCookie.PATH_ATTR, "kotlin.jvm.PlatformType", "getPath", "()Ljava/lang/String;", "recordQueue", "totalFiles", "wifiFilesDownloadListener", "Lcom/glasssutdio/wear/depository/AlbumDepository$WifiFilesDownloadListener;", "calculatePCMPlaybackDuration", "totalBytes", "cleanFileQueue", "", "copyFile", "inputStream", "Ljava/io/InputStream;", "outputStream", "Ljava/io/OutputStream;", "decodeOpusStream", "entity", "deleteFile", "album", "downloadGlassFile", "getIndexByFileName", "fileName", "(Ljava/lang/String;)Ljava/lang/Integer;", "getMimeType", "file", "Ljava/io/File;", "getPhotoTextFile", "url", "dirPath", "groupAlbumsByDate", "Ljava/util/TreeMap;", "albums", "", "hasFileExtension", BreakpointSQLiteKey.FILENAME, "initDetailList", "list", "isFileQueueIng", "loadVideoFirstFrame", "Landroid/graphics/Bitmap;", "videoPath", "opusToPcm", "parseTimeMillisFromName", "name", "queryAllAudio", "queryAllMedia", "queryFileByName", "queryImageMedia", "fileType", "queryLikeMedia", "readPhotoFile", "saveAlbum", "saveFileToAppGalleryFolder", "context", "Landroid/content/Context;", "sourceFile", "savePcmAsWavAndAddToMediaStore", "pcmFile", "setWifiDownloadListener", "testEis2", "finalName", "transformToLandscape", "inputFilePath", "transformToPortrait", "Companion", "WifiFilesDownloadListener", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class AlbumDepository {

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static final Lazy<AlbumDepository> getInstance$delegate = LazyKt.lazy(LazyThreadSafetyMode.SYNCHRONIZED, (Function0) new Function0<AlbumDepository>() { // from class: com.glasssutdio.wear.depository.AlbumDepository$Companion$getInstance$2
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // kotlin.jvm.functions.Function0
        public final AlbumDepository invoke() {
            return new AlbumDepository();
        }
    });
    private int downloadErrorCount;
    private int failCount;
    private long lastUpdateTime;
    private boolean opusToPcmIng;
    private int totalFiles;
    private WifiFilesDownloadListener wifiFilesDownloadListener;
    private final GlassAlbumDao glassAlbumDao = GlassDatabase.INSTANCE.getDatabase(GlassApplication.INSTANCE.getCONTEXT()).glassAlbumDao();
    private List<GlassAlbumEntity> listData = new ArrayList();
    private Map<String, Integer> keyMap = new LinkedHashMap();
    private BlockingDeque<PictureDownloadBean> fileQueue = new LinkedBlockingDeque(100);
    private final BlockingDeque<GlassAlbumEntity> recordQueue = new LinkedBlockingDeque(50);
    private int intervalTime = 500;
    private final String path = GFileUtilKt.getCacheFolder().getAbsolutePath();
    private final Mp4Decode.Mp4DecodeCallback eisCallback = new Mp4Decode.Mp4DecodeCallback() { // from class: com.glasssutdio.wear.depository.AlbumDepository$eisCallback$1
        @Override // com.glasssutdio.wear.stabilization.Mp4Decode.Mp4DecodeCallback
        public void eisStart(String fileName, String filePath) {
            Intrinsics.checkNotNullParameter(fileName, "fileName");
            Intrinsics.checkNotNullParameter(filePath, "filePath");
            XLog.i("eisStart->" + filePath);
            if (this.this$0.wifiFilesDownloadListener != null) {
                AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = this.this$0.wifiFilesDownloadListener;
                Intrinsics.checkNotNull(wifiFilesDownloadListener);
                wifiFilesDownloadListener.eisStart(fileName, filePath);
            }
        }

        @Override // com.glasssutdio.wear.stabilization.Mp4Decode.Mp4DecodeCallback
        public void eisError(final String fileName, final String sourcePath, final String eisFilePath, String errorInfo) throws InterruptedException {
            Intrinsics.checkNotNullParameter(fileName, "fileName");
            Intrinsics.checkNotNullParameter(sourcePath, "sourcePath");
            Intrinsics.checkNotNullParameter(eisFilePath, "eisFilePath");
            Intrinsics.checkNotNullParameter(errorInfo, "errorInfo");
            XLog.i("eisError" + errorInfo + sourcePath + "-----" + eisFilePath);
            ThreadExtKt.removeTaskRecord(fileName);
            final AlbumDepository albumDepository = this.this$0;
            ThreadExtKt.ktxRunOnBgSingleDao(this, new Function1<AlbumDepository$eisCallback$1, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository$eisCallback$1$eisError$1
                /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                {
                    super(1);
                }

                @Override // kotlin.jvm.functions.Function1
                public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository$eisCallback$1 albumDepository$eisCallback$1) {
                    invoke2(albumDepository$eisCallback$1);
                    return Unit.INSTANCE;
                }

                /* renamed from: invoke, reason: avoid collision after fix types in other method */
                public final void invoke2(AlbumDepository$eisCallback$1 ktxRunOnBgSingleDao) {
                    Intrinsics.checkNotNullParameter(ktxRunOnBgSingleDao, "$this$ktxRunOnBgSingleDao");
                    GFileUtilKt.deleteFile(eisFilePath);
                    GlassAlbumEntity glassAlbumEntityQueryFileByName = albumDepository.queryFileByName(fileName);
                    if (glassAlbumEntityQueryFileByName != null) {
                        glassAlbumEntityQueryFileByName.setFilePath(sourcePath);
                        glassAlbumEntityQueryFileByName.setEisInProgress(false);
                        albumDepository.saveAlbum(glassAlbumEntityQueryFileByName);
                    }
                }
            });
            if (this.this$0.wifiFilesDownloadListener != null) {
                AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = this.this$0.wifiFilesDownloadListener;
                Intrinsics.checkNotNull(wifiFilesDownloadListener);
                wifiFilesDownloadListener.eisError(fileName, sourcePath, errorInfo);
            }
        }

        @Override // com.glasssutdio.wear.stabilization.Mp4Decode.Mp4DecodeCallback
        public void eisEnd(final String fileName, final String sourceFilePath, final String eisFilePath) throws InterruptedException {
            Intrinsics.checkNotNullParameter(fileName, "fileName");
            Intrinsics.checkNotNullParameter(sourceFilePath, "sourceFilePath");
            Intrinsics.checkNotNullParameter(eisFilePath, "eisFilePath");
            XLog.i("eisEnd:" + fileName + "-filePath:" + eisFilePath + "-----" + sourceFilePath);
            ThreadExtKt.removeTaskRecord(fileName);
            final AlbumDepository albumDepository = this.this$0;
            ThreadExtKt.ktxRunOnBgSingleDao(this, new Function1<AlbumDepository$eisCallback$1, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository$eisCallback$1$eisEnd$1
                /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                {
                    super(1);
                }

                @Override // kotlin.jvm.functions.Function1
                public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository$eisCallback$1 albumDepository$eisCallback$1) {
                    invoke2(albumDepository$eisCallback$1);
                    return Unit.INSTANCE;
                }

                /* renamed from: invoke, reason: avoid collision after fix types in other method */
                public final void invoke2(AlbumDepository$eisCallback$1 ktxRunOnBgSingleDao) {
                    Intrinsics.checkNotNullParameter(ktxRunOnBgSingleDao, "$this$ktxRunOnBgSingleDao");
                    if (new File(eisFilePath).length() <= 10) {
                        XLog.i("防抖空文件");
                        GlassAlbumEntity glassAlbumEntityQueryFileByName = albumDepository.queryFileByName(fileName);
                        if (glassAlbumEntityQueryFileByName != null) {
                            glassAlbumEntityQueryFileByName.setFilePath(sourceFilePath);
                            if (UserConfig.INSTANCE.getInstance().getUseGyro() || StringsKt.startsWith$default(fileName, "video-", false, 2, (Object) null)) {
                                glassAlbumEntityQueryFileByName.setSupportGyro(true);
                            }
                            glassAlbumEntityQueryFileByName.setEisInProgress(false);
                            albumDepository.saveAlbum(glassAlbumEntityQueryFileByName);
                        }
                        if (albumDepository.wifiFilesDownloadListener != null) {
                            AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = albumDepository.wifiFilesDownloadListener;
                            Intrinsics.checkNotNull(wifiFilesDownloadListener);
                            wifiFilesDownloadListener.eisEnd(fileName, sourceFilePath);
                            return;
                        }
                        return;
                    }
                    GlassAlbumEntity glassAlbumEntityQueryFileByName2 = albumDepository.queryFileByName(fileName);
                    if (glassAlbumEntityQueryFileByName2 != null) {
                        glassAlbumEntityQueryFileByName2.setEisInProgress(false);
                        if (UserConfig.INSTANCE.getInstance().getUseGyro() || StringsKt.startsWith$default(fileName, "video-", false, 2, (Object) null)) {
                            glassAlbumEntityQueryFileByName2.setSupportGyro(true);
                        }
                        XLog.i("防抖处理成功：" + eisFilePath + ",是否支持陀螺仪：" + glassAlbumEntityQueryFileByName2.getSupportGyro());
                        glassAlbumEntityQueryFileByName2.setFilePath(eisFilePath);
                        albumDepository.saveAlbum(glassAlbumEntityQueryFileByName2);
                    }
                    if (albumDepository.wifiFilesDownloadListener != null) {
                        AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener2 = albumDepository.wifiFilesDownloadListener;
                        Intrinsics.checkNotNull(wifiFilesDownloadListener2);
                        wifiFilesDownloadListener2.eisEnd(fileName, eisFilePath);
                    }
                }
            });
            final AlbumDepository albumDepository2 = this.this$0;
            ThreadExtKt.ktxRunOnBgFix(this, new Function1<AlbumDepository$eisCallback$1, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository$eisCallback$1$eisEnd$2
                /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                {
                    super(1);
                }

                @Override // kotlin.jvm.functions.Function1
                public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository$eisCallback$1 albumDepository$eisCallback$1) throws InterruptedException, Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
                    invoke2(albumDepository$eisCallback$1);
                    return Unit.INSTANCE;
                }

                /* renamed from: invoke, reason: avoid collision after fix types in other method */
                public final void invoke2(AlbumDepository$eisCallback$1 ktxRunOnBgFix) throws InterruptedException, Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
                    Intrinsics.checkNotNullParameter(ktxRunOnBgFix, "$this$ktxRunOnBgFix");
                    Thread.sleep(2000L);
                    albumDepository2.saveFileToAppGalleryFolder(GlassApplication.INSTANCE.getCONTEXT(), new File(eisFilePath));
                }
            });
        }
    };

    /* compiled from: AlbumDepository.kt */
    @Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0007\n\u0002\u0010\b\n\u0002\b\n\n\u0002\u0018\u0002\n\u0002\b\u0005\bf\u0018\u00002\u00020\u0001J\u0018\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0005H&J \u0010\u0007\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\b\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\u0005H&J\u0018\u0010\n\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u0005H&J\u0018\u0010\u000b\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\rH&J\b\u0010\u000f\u001a\u00020\u0003H&J\u0018\u0010\u0010\u001a\u00020\u00032\u0006\u0010\u0011\u001a\u00020\r2\u0006\u0010\u0012\u001a\u00020\rH&J\b\u0010\u0013\u001a\u00020\u0003H&J\u0018\u0010\u0014\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0015\u001a\u00020\rH&J\u0010\u0010\u0016\u001a\u00020\u00032\u0006\u0010\u0017\u001a\u00020\u0018H&J \u0010\u0019\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00052\u0006\u0010\u001a\u001a\u00020\rH&J\u0018\u0010\u001b\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u00052\u0006\u0010\t\u001a\u00020\u0005H&J\u0010\u0010\u001c\u001a\u00020\u00032\u0006\u0010\u001c\u001a\u00020\u0005H&¨\u0006\u001d"}, d2 = {"Lcom/glasssutdio/wear/depository/AlbumDepository$WifiFilesDownloadListener;", "", "eisEnd", "", "fileName", "", "filePath", "eisError", "sourcePath", "errorInfo", "eisStart", "fileCount", "index", "", "total", "fileDownloadComplete", "fileDownloadError", "fileType", "errorType", "fileDownloadStart", "fileProgress", "progress", "fileWasDownloadSuccessfully", "entity", "Lcom/glasssutdio/wear/database/entity/GlassAlbumEntity;", "recordingToPcm", TypedValues.TransitionType.S_DURATION, "recordingToPcmError", "wifiSpeed", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public interface WifiFilesDownloadListener {
        void eisEnd(String fileName, String filePath);

        void eisError(String fileName, String sourcePath, String errorInfo);

        void eisStart(String fileName, String filePath);

        void fileCount(int index, int total);

        void fileDownloadComplete();

        void fileDownloadError(int fileType, int errorType);

        void fileDownloadStart();

        void fileProgress(String fileName, int progress);

        void fileWasDownloadSuccessfully(GlassAlbumEntity entity);

        void recordingToPcm(String fileName, String filePath, int duration);

        void recordingToPcmError(String fileName, String errorInfo);

        void wifiSpeed(String wifiSpeed);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void savePcmAsWavAndAddToMediaStore$lambda$7$lambda$6(String str, Uri uri) {
    }

    public final void transformToLandscape(String inputFilePath) {
        Intrinsics.checkNotNullParameter(inputFilePath, "inputFilePath");
    }

    public final void transformToPortrait(String inputFilePath) {
        Intrinsics.checkNotNullParameter(inputFilePath, "inputFilePath");
    }

    public final List<GlassAlbumEntity> getListData() {
        return this.listData;
    }

    public final void setListData(List<GlassAlbumEntity> list) {
        Intrinsics.checkNotNullParameter(list, "<set-?>");
        this.listData = list;
    }

    public final String getPath() {
        return this.path;
    }

    public final void setWifiDownloadListener(WifiFilesDownloadListener wifiFilesDownloadListener) {
        Intrinsics.checkNotNullParameter(wifiFilesDownloadListener, "wifiFilesDownloadListener");
        this.wifiFilesDownloadListener = wifiFilesDownloadListener;
    }

    public final void initDetailList(List<GlassAlbumEntity> list) {
        Intrinsics.checkNotNullParameter(list, "list");
        this.listData = list;
        int i = 0;
        for (GlassAlbumEntity glassAlbumEntity : list) {
            int i2 = i + 1;
            this.keyMap.put(glassAlbumEntity.getFileName(), Integer.valueOf(i));
            i = i2;
        }
    }

    public final Integer getIndexByFileName(String fileName) {
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        return this.keyMap.get(fileName);
    }

    private final TreeMap<Integer, List<GlassAlbumEntity>> groupAlbumsByDate(List<GlassAlbumEntity> albums) {
        TreeMap<Integer, List<GlassAlbumEntity>> treeMap = new TreeMap<>();
        for (GlassAlbumEntity glassAlbumEntity : albums) {
            TreeMap<Integer, List<GlassAlbumEntity>> treeMap2 = treeMap;
            Integer numValueOf = Integer.valueOf(DateUtil.dateY_M_D2StampSecond(glassAlbumEntity.getFileDate()));
            ArrayList arrayList = treeMap2.get(numValueOf);
            if (arrayList == null) {
                arrayList = new ArrayList();
                treeMap2.put(numValueOf, arrayList);
            }
            arrayList.add(glassAlbumEntity);
        }
        return treeMap;
    }

    public final TreeMap<Integer, List<GlassAlbumEntity>> queryAllMedia() {
        return groupAlbumsByDate(this.glassAlbumDao.queryAllFile(UserConfig.INSTANCE.getInstance().getDeviceAddressNoClear()));
    }

    public final TreeMap<Integer, List<GlassAlbumEntity>> queryImageMedia(int fileType) {
        return groupAlbumsByDate(this.glassAlbumDao.queryImageFileByteType(UserConfig.INSTANCE.getInstance().getDeviceAddressNoClear(), fileType));
    }

    public final List<GlassAlbumEntity> queryAllAudio() {
        return this.glassAlbumDao.queryImageFileByteType(UserConfig.INSTANCE.getInstance().getDeviceAddressNoClear(), 3);
    }

    public final TreeMap<Integer, List<GlassAlbumEntity>> queryLikeMedia() {
        return groupAlbumsByDate(this.glassAlbumDao.queryLikeMedia(UserConfig.INSTANCE.getInstance().getDeviceAddressNoClear()));
    }

    public final void saveAlbum(GlassAlbumEntity album) {
        Intrinsics.checkNotNullParameter(album, "album");
        XLog.i(GsonInstance.INSTANCE.getGson().toJson(album));
        this.glassAlbumDao.insert(album);
    }

    public final GlassAlbumEntity queryFileByName(String fileName) {
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        return this.glassAlbumDao.queryAlbumFileByName(fileName, UserConfig.INSTANCE.getInstance().getDeviceAddressNoClear());
    }

    public final void deleteFile(GlassAlbumEntity album) {
        Intrinsics.checkNotNullParameter(album, "album");
        this.glassAlbumDao.delete(album);
    }

    public final boolean isFileQueueIng() {
        XLog.i("fileCount:" + ((this.totalFiles - this.fileQueue.size()) + 1));
        return (this.totalFiles - this.fileQueue.size()) + 1 >= 1;
    }

    /* compiled from: AlbumDepository.kt */
    @Metadata(d1 = {"\u0000\f\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0000\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\n¢\u0006\u0002\b\u0003"}, d2 = {"<anonymous>", "", "Lcom/glasssutdio/wear/depository/AlbumDepository;", "invoke"}, k = 3, mv = {1, 9, 0}, xi = 48)
    /* renamed from: com.glasssutdio.wear.depository.AlbumDepository$getPhotoTextFile$1, reason: invalid class name and case insensitive filesystem */
    static final class C02301 extends Lambda implements Function1<AlbumDepository, Unit> {
        final /* synthetic */ String $dirPath;
        final /* synthetic */ String $fileName;
        final /* synthetic */ String $url;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        C02301(String str, String str2, String str3) {
            super(1);
            this.$url = str;
            this.$dirPath = str2;
            this.$fileName = str3;
        }

        @Override // kotlin.jvm.functions.Function1
        public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository albumDepository) {
            invoke2(albumDepository);
            return Unit.INSTANCE;
        }

        /* renamed from: invoke, reason: avoid collision after fix types in other method */
        public final void invoke2(AlbumDepository ktxRunOnBgSingle) {
            Intrinsics.checkNotNullParameter(ktxRunOnBgSingle, "$this$ktxRunOnBgSingle");
            XLog.i(this.$url);
            if (GFileUtilKt.fileExists(this.$dirPath + '/' + this.$fileName)) {
                GFileUtilKt.deleteFile(this.$dirPath + '/' + this.$fileName);
            }
            ktxRunOnBgSingle.fileQueue.clear();
            ktxRunOnBgSingle.downloadErrorCount = 0;
            invoke$startDownload(this.$url, this.$dirPath, this.$fileName, ktxRunOnBgSingle, new Ref.IntRef(), 1);
        }

        /* JADX INFO: Access modifiers changed from: private */
        public static final void invoke$startDownload(final String str, final String str2, final String str3, final AlbumDepository albumDepository, final Ref.IntRef intRef, final int i) {
            AndroidNetworking.download(str, str2, str3).setTag((Object) "photo.txt").setPriority(Priority.MEDIUM).build().startDownload(new DownloadListener() { // from class: com.glasssutdio.wear.depository.AlbumDepository$getPhotoTextFile$1$startDownload$1
                @Override // com.androidnetworking.interfaces.DownloadListener
                public void onDownloadComplete() throws InterruptedException {
                    XLog.i("download photo text success");
                    albumDepository.fileQueue.clear();
                    albumDepository.readPhotoFile(str2 + '/' + str3);
                    if (albumDepository.wifiFilesDownloadListener != null) {
                        AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = albumDepository.wifiFilesDownloadListener;
                        Intrinsics.checkNotNull(wifiFilesDownloadListener);
                        wifiFilesDownloadListener.fileDownloadStart();
                    }
                }

                @Override // com.androidnetworking.interfaces.DownloadListener
                public void onError(ANError error) {
                    Intrinsics.checkNotNullParameter(error, "error");
                    XLog.i(String.valueOf(error.getErrorCode()));
                    XLog.i(error.getErrorDetail());
                    if (intRef.element >= i) {
                        if (albumDepository.wifiFilesDownloadListener != null) {
                            AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = albumDepository.wifiFilesDownloadListener;
                            Intrinsics.checkNotNull(wifiFilesDownloadListener);
                            wifiFilesDownloadListener.fileDownloadError(1, error.getErrorCode());
                            return;
                        }
                        return;
                    }
                    intRef.element++;
                    XLog.i("Download failed, retrying (" + intRef.element + '/' + i + ')');
                    AlbumDepository.C02301.invoke$startDownload(str, str2, str3, albumDepository, intRef, i);
                }
            });
        }
    }

    public final void getPhotoTextFile(String url, String dirPath, String fileName) throws InterruptedException {
        Intrinsics.checkNotNullParameter(url, "url");
        Intrinsics.checkNotNullParameter(dirPath, "dirPath");
        Intrinsics.checkNotNullParameter(fileName, "fileName");
        ThreadExtKt.ktxRunOnBgSingle(this, new C02301(url, dirPath, fileName));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void readPhotoFile(String path) throws InterruptedException {
        try {
            List<String> lines$default = FilesKt.readLines$default(new File(path), null, 1, null);
            XLog.i("总文件数:" + lines$default.size());
            this.fileQueue = new LinkedBlockingDeque(lines$default.size() + 10);
            for (String str : lines$default) {
                String str2 = "http://" + UserConfig.INSTANCE.getInstance().getGlassDeviceWifiIP() + "/files/" + str;
                if (UserConfig.INSTANCE.getInstance().getGlassesLogs()) {
                    str2 = "http://" + UserConfig.INSTANCE.getInstance().getGlassDeviceWifiIP() + "/files/log/" + str;
                }
                this.fileQueue.putLast(new PictureDownloadBean(str2, str));
            }
            this.totalFiles = lines$default.size();
            ThreadExtKt.ktxRunOnBgSingle(this, new Function1<AlbumDepository, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository.readPhotoFile.1
                @Override // kotlin.jvm.functions.Function1
                public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository albumDepository) {
                    invoke2(albumDepository);
                    return Unit.INSTANCE;
                }

                /* renamed from: invoke, reason: avoid collision after fix types in other method */
                public final void invoke2(AlbumDepository ktxRunOnBgSingle) {
                    Intrinsics.checkNotNullParameter(ktxRunOnBgSingle, "$this$ktxRunOnBgSingle");
                    ktxRunOnBgSingle.downloadGlassFile();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            XLog.i("下载文件失败：" + e);
        }
    }

    public final boolean hasFileExtension(String filename) {
        Intrinsics.checkNotNullParameter(filename, "filename");
        int iLastIndexOf$default = StringsKt.lastIndexOf$default((CharSequence) filename, FilenameUtils.EXTENSION_SEPARATOR, 0, false, 6, (Object) null);
        return iLastIndexOf$default > 0 && iLastIndexOf$default < filename.length() - 1;
    }

    public final void cleanFileQueue() {
        this.totalFiles = 0;
        this.failCount = 0;
        this.fileQueue.clear();
    }

    public final int getFailCount() {
        return this.failCount;
    }

    public final void setFailCount(int i) {
        this.failCount = i;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void downloadGlassFile() {
        XLog.i("还剩下几个文件:" + this.fileQueue.size() + ",file:");
        if (this.fileQueue.isEmpty()) {
            this.failCount = 0;
            this.downloadErrorCount = 0;
            WifiFilesDownloadListener wifiFilesDownloadListener = this.wifiFilesDownloadListener;
            Intrinsics.checkNotNull(wifiFilesDownloadListener);
            wifiFilesDownloadListener.fileDownloadComplete();
            UserConfig.INSTANCE.getInstance().setGlassesLogs(false);
            return;
        }
        WifiFilesDownloadListener wifiFilesDownloadListener2 = this.wifiFilesDownloadListener;
        Intrinsics.checkNotNull(wifiFilesDownloadListener2);
        wifiFilesDownloadListener2.fileCount((this.totalFiles - this.fileQueue.size()) + 1, this.totalFiles);
        if (this.failCount > 1) {
            try {
                this.failCount = 0;
                this.fileQueue.removeFirst();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final PictureDownloadBean pictureDownloadBeanPeekFirst = this.fileQueue.peekFirst();
        final DownloadSpeedCalculator downloadSpeedCalculator = new DownloadSpeedCalculator();
        downloadSpeedCalculator.start();
        this.lastUpdateTime = 0L;
        XLog.i("开始下载：" + pictureDownloadBeanPeekFirst.getPath());
        AndroidNetworking.download(pictureDownloadBeanPeekFirst.getPath(), GFileUtilKt.getAlbumDirFile().getAbsolutePath(), pictureDownloadBeanPeekFirst.getFileName()).setTag((Object) "download_file").setPriority(Priority.MEDIUM).build().setDownloadProgressListener(new DownloadProgressListener() { // from class: com.glasssutdio.wear.depository.AlbumDepository$$ExternalSyntheticLambda1
            @Override // com.androidnetworking.interfaces.DownloadProgressListener
            public final void onProgress(long j, long j2) {
                AlbumDepository.downloadGlassFile$lambda$1(this.f$0, downloadSpeedCalculator, pictureDownloadBeanPeekFirst, j, j2);
            }
        }).startDownload(new DownloadListener() { // from class: com.glasssutdio.wear.depository.AlbumDepository.downloadGlassFile.2
            /* JADX WARN: Multi-variable type inference failed */
            /* JADX WARN: Removed duplicated region for block: B:41:0x019f  */
            /* JADX WARN: Removed duplicated region for block: B:44:0x01aa  */
            /* JADX WARN: Removed duplicated region for block: B:47:0x01d9  */
            /* JADX WARN: Removed duplicated region for block: B:51:0x0225  */
            /* JADX WARN: Removed duplicated region for block: B:85:0x024b A[EXC_TOP_SPLITTER, SYNTHETIC] */
            /* JADX WARN: Type inference failed for: r2v2, types: [java.lang.String] */
            /* JADX WARN: Type inference failed for: r2v23, types: [com.glasssutdio.wear.depository.AlbumDepository$downloadGlassFile$2, java.lang.Object] */
            /* JADX WARN: Type inference failed for: r2v26, types: [com.glasssutdio.wear.depository.AlbumDepository$downloadGlassFile$2] */
            /* JADX WARN: Type inference failed for: r2v27 */
            /* JADX WARN: Type inference failed for: r2v3 */
            /* JADX WARN: Type inference failed for: r2v4 */
            /* JADX WARN: Type inference failed for: r2v5 */
            /* JADX WARN: Type inference failed for: r2v6, types: [com.glasssutdio.wear.depository.AlbumDepository$downloadGlassFile$2, java.lang.Object] */
            /* JADX WARN: Type inference failed for: r2v7 */
            /* JADX WARN: Type inference failed for: r2v8 */
            @Override // com.androidnetworking.interfaces.DownloadListener
            /*
                Code decompiled incorrectly, please refer to instructions dump.
                To view partially-correct add '--show-bad-code' argument
            */
            public void onDownloadComplete() throws java.lang.InterruptedException, java.io.IOException {
                /*
                    Method dump skipped, instructions count: 1314
                    To view this dump add '--comments-level debug' option
                */
                throw new UnsupportedOperationException("Method not decompiled: com.glasssutdio.wear.depository.AlbumDepository.AnonymousClass2.onDownloadComplete():void");
            }

            @Override // com.androidnetworking.interfaces.DownloadListener
            public void onError(ANError error) {
                Intrinsics.checkNotNullParameter(error, "error");
                AlbumDepository albumDepository = this;
                albumDepository.setFailCount(albumDepository.getFailCount() + 1);
                this.downloadErrorCount++;
                XLog.i(String.valueOf(error.getErrorCode()));
                XLog.i(error.getErrorDetail().toString());
                if (this.wifiFilesDownloadListener != null) {
                    WifiFilesDownloadListener wifiFilesDownloadListener3 = this.wifiFilesDownloadListener;
                    Intrinsics.checkNotNull(wifiFilesDownloadListener3);
                    wifiFilesDownloadListener3.fileDownloadError(2, error.getErrorCode());
                }
                if (this.downloadErrorCount <= 2) {
                    this.downloadGlassFile();
                } else {
                    XLog.i("失败了很多次：" + this.downloadErrorCount);
                    this.fileQueue.clear();
                }
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void downloadGlassFile$lambda$1(AlbumDepository this$0, DownloadSpeedCalculator calculator, PictureDownloadBean pictureDownloadBean, long j, long j2) {
        Intrinsics.checkNotNullParameter(this$0, "this$0");
        Intrinsics.checkNotNullParameter(calculator, "$calculator");
        if (j2 > 0) {
            long jCurrentTimeMillis = System.currentTimeMillis();
            boolean z = j == j2 / ((long) 2);
            if (jCurrentTimeMillis - this$0.lastUpdateTime >= this$0.intervalTime || z || j == j2) {
                this$0.lastUpdateTime = jCurrentTimeMillis;
                String strCalculate = calculator.calculate(j);
                if (this$0.wifiFilesDownloadListener != null && !Intrinsics.areEqual(strCalculate, "-1")) {
                    WifiFilesDownloadListener wifiFilesDownloadListener = this$0.wifiFilesDownloadListener;
                    Intrinsics.checkNotNull(wifiFilesDownloadListener);
                    wifiFilesDownloadListener.wifiSpeed(strCalculate);
                }
            }
            int i = (int) ((j * 100) / j2);
            WifiFilesDownloadListener wifiFilesDownloadListener2 = this$0.wifiFilesDownloadListener;
            if (wifiFilesDownloadListener2 != null) {
                Intrinsics.checkNotNull(wifiFilesDownloadListener2);
                wifiFilesDownloadListener2.fileProgress(pictureDownloadBean.getFileName(), i);
            }
        }
    }

    public final void testEis2(final String finalName) {
        Intrinsics.checkNotNullParameter(finalName, "finalName");
        ThreadExtKt.ktxRunOnBgFix(this, new Function1<AlbumDepository, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository.testEis2.1
            /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
            {
                super(1);
            }

            @Override // kotlin.jvm.functions.Function1
            public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository albumDepository) throws IOException {
                invoke2(albumDepository);
                return Unit.INSTANCE;
            }

            /* renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2(AlbumDepository ktxRunOnBgFix) throws IOException {
                boolean z;
                Intrinsics.checkNotNullParameter(ktxRunOnBgFix, "$this$ktxRunOnBgFix");
                String absolutePath = GFileUtilKt.getAlbumDirFile().getAbsolutePath();
                File file = new File(absolutePath + "/video-2025112233");
                long length = file.length();
                if (!file.exists()) {
                    final String str = Constant.COMMON_VIDEO_NAME;
                    ThreadExtKt.ktxRunOnUi(ktxRunOnBgFix, new Function1<AlbumDepository, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository.testEis2.1.1
                        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                        {
                            super(1);
                        }

                        @Override // kotlin.jvm.functions.Function1
                        public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository albumDepository) {
                            invoke2(albumDepository);
                            return Unit.INSTANCE;
                        }

                        /* renamed from: invoke, reason: avoid collision after fix types in other method */
                        public final void invoke2(AlbumDepository ktxRunOnUi) {
                            Intrinsics.checkNotNullParameter(ktxRunOnUi, "$this$ktxRunOnUi");
                            GlobalKt.showToast$default("视频：" + str + "不存在", 0, 1, null);
                        }
                    });
                    return;
                }
                XLog.i("总长度：" + length);
                long j = 8;
                byte[] fileBytes = GFileUtilKt.readFileBytes(file, length - j, 8);
                byte[] fileBytes2 = GFileUtilKt.readFileBytes(file, length - 16, 8);
                XLog.i("fileType:".concat(new String(fileBytes, Charsets.UTF_8)));
                XLog.i("fileCount:".concat(new String(fileBytes2, Charsets.UTF_8)));
                long j2 = length - 24;
                String str2 = new String(GFileUtilKt.readFileBytes(file, j2, 8), Charsets.UTF_8);
                long jBytesToLongBigEndian = ByteUtils.INSTANCE.bytesToLongBigEndian(GFileUtilKt.hexStringToBytes(str2));
                long j3 = 32;
                long j4 = j2 - j3;
                String strTrimEnd = StringsKt.trimEnd(new String(GFileUtilKt.readFileBytes(file, j4, 32), Charsets.UTF_8), '0');
                XLog.i("gyroName length:" + str2 + ",name = " + strTrimEnd + ",length = " + jBytesToLongBigEndian);
                long j5 = j4 - j;
                String str3 = new String(GFileUtilKt.readFileBytes(file, j5, 8), Charsets.UTF_8);
                long jBytesToLongBigEndian2 = ByteUtils.INSTANCE.bytesToLongBigEndian(GFileUtilKt.hexStringToBytes(str3));
                long j6 = j5 - j3;
                String strTrimEnd2 = StringsKt.trimEnd(new String(GFileUtilKt.readFileBytes(file, j6, 32), Charsets.UTF_8), '0');
                XLog.i("frameFileName length:" + str3 + ",name = " + strTrimEnd2 + ",length = " + jBytesToLongBigEndian2);
                String str4 = new String(GFileUtilKt.readFileBytes(file, j6 - j, 8), Charsets.UTF_8);
                long jBytesToLongBigEndian3 = ByteUtils.INSTANCE.bytesToLongBigEndian(GFileUtilKt.hexStringToBytes(str4));
                XLog.i("mp4 length:" + str4 + ",mp4Name = video-2025112233.mp4,mp4Length = " + jBytesToLongBigEndian3);
                File file2 = new File(absolutePath + "/video-2025112233.mp4");
                String absolutePath2 = file.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath2, "getAbsolutePath(...)");
                String absolutePath3 = file2.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath3, "getAbsolutePath(...)");
                boolean zCopyFileSegmentWithRandomAccess = GFileUtilKt.copyFileSegmentWithRandomAccess(absolutePath2, absolutePath3, 0L, jBytesToLongBigEndian3);
                File file3 = new File(absolutePath + '/' + strTrimEnd2);
                String absolutePath4 = file.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath4, "getAbsolutePath(...)");
                String absolutePath5 = file3.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath5, "getAbsolutePath(...)");
                boolean zCopyFileSegmentWithRandomAccess2 = GFileUtilKt.copyFileSegmentWithRandomAccess(absolutePath4, absolutePath5, jBytesToLongBigEndian3, jBytesToLongBigEndian2);
                File file4 = new File(absolutePath + '/' + strTrimEnd);
                String absolutePath6 = file.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath6, "getAbsolutePath(...)");
                String absolutePath7 = file4.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath7, "getAbsolutePath(...)");
                boolean z2 = true;
                XLog.i("文件裁剪结果：视频长度" + zCopyFileSegmentWithRandomAccess + (char) 65306 + file2.length() + "，陀螺仪长度" + zCopyFileSegmentWithRandomAccess2 + (char) 65306 + file3.length() + ",行曝光" + GFileUtilKt.copyFileSegmentWithRandomAccess(absolutePath6, absolutePath7, jBytesToLongBigEndian3 + jBytesToLongBigEndian2, jBytesToLongBigEndian) + (char) 65306 + file4.length());
                String str5 = "eis_video-2025112233-" + finalName + ".mp4";
                Mp4Decode mp4Decode = new Mp4Decode();
                XLog.tag("防抖eis").i("初始化防抖数据：gyroPath=" + file4.getPath() + "，videoFramePtsPath=" + file3.getPath());
                String absolutePath8 = file2.getAbsolutePath();
                Intrinsics.checkNotNullExpressionValue(absolutePath8, "getAbsolutePath(...)");
                Bitmap bitmapLoadVideoFirstFrame = ktxRunOnBgFix.loadVideoFirstFrame(absolutePath8);
                if (bitmapLoadVideoFirstFrame != null) {
                    z = bitmapLoadVideoFirstFrame.getWidth() > bitmapLoadVideoFirstFrame.getHeight();
                    UserConfig companion = UserConfig.INSTANCE.getInstance();
                    if (bitmapLoadVideoFirstFrame.getWidth() <= bitmapLoadVideoFirstFrame.getHeight()) {
                        z2 = false;
                    }
                    companion.setVideoIsLandscape(z2);
                } else {
                    z = true;
                }
                String path = file4.getPath();
                Intrinsics.checkNotNullExpressionValue(path, "getPath(...)");
                String path2 = file3.getPath();
                Intrinsics.checkNotNullExpressionValue(path2, "getPath(...)");
                mp4Decode.eisInit(path, path2, z, UserConfig.INSTANCE.getInstance().getSupportVideoInterpolation());
                File file5 = new File(GFileUtilKt.getAlbumDirFile().getAbsolutePath(), str5);
                XLog.tag("防抖eis").i("mp4OutFile：mp4OutFile=" + file2.getPath() + "，outFile=" + file5.getPath());
                mp4Decode.eisYuv2Mp4(file2, file5, ktxRunOnBgFix.getEisCallback());
                XLog.i("eis fileName:" + str5);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final Bitmap loadVideoFirstFrame(String videoPath) throws IOException {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            try {
                try {
                    mediaMetadataRetriever.setDataSource(videoPath);
                    return mediaMetadataRetriever.getFrameAtTime(1L, 2);
                } catch (Exception e) {
                    e.printStackTrace();
                    mediaMetadataRetriever.release();
                    return null;
                }
            } finally {
                mediaMetadataRetriever.release();
            }
        } catch (Exception e2) {
            e2.printStackTrace();
            return null;
        }
    }

    public final Mp4Decode.Mp4DecodeCallback getEisCallback() {
        return this.eisCallback;
    }

    public final long parseTimeMillisFromName(String name) {
        Intrinsics.checkNotNullParameter(name, "name");
        try {
            String strSubstringBefore$default = StringsKt.substringBefore$default(name, ".", (String) null, 2, (Object) null);
            if (StringsKt.startsWith$default(name, "video-", false, 2, (Object) null)) {
                strSubstringBefore$default = StringsKt.removePrefix(name, (CharSequence) "video-");
            }
            Date date = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).parse(strSubstringBefore$default);
            if (date == null) {
                return System.currentTimeMillis();
            }
            Calendar calendar = Calendar.getInstance();
            calendar.set(2000, 0, 1, 0, 0, 0);
            calendar.set(14, 0);
            if (date.getTime() < calendar.getTimeInMillis()) {
                return System.currentTimeMillis();
            }
            return date.getTime();
        } catch (Exception unused) {
            return System.currentTimeMillis();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void opusToPcm() throws InterruptedException {
        if (this.recordQueue.isEmpty()) {
            return;
        }
        if (!this.opusToPcmIng) {
            XLog.i("opusToPcm");
            this.opusToPcmIng = true;
            GlassAlbumEntity glassAlbumEntityTake = this.recordQueue.take();
            Intrinsics.checkNotNull(glassAlbumEntityTake);
            decodeOpusStream(glassAlbumEntityTake);
            return;
        }
        XLog.i("opusToPcm 正在进行中");
    }

    public final void decodeOpusStream(final GlassAlbumEntity entity) {
        Intrinsics.checkNotNullParameter(entity, "entity");
        final String str = ((String) StringsKt.split$default((CharSequence) entity.getFileName(), new String[]{"."}, false, 0, 6, (Object) null).get(0)) + ".pcm";
        String str2 = GFileUtilKt.getDCIMFile().getAbsolutePath() + '/' + str;
        OpusOption opusOption = new OpusOption();
        opusOption.setHasHead(false);
        opusOption.setSampleRate(AudioTrackManager.mSampleRateIn16KHz);
        opusOption.setPacketSize(40);
        opusOption.setChannel(1);
        final OpusManager opusManager = new OpusManager();
        opusManager.decodeFile(entity.getFilePath(), str2, opusOption, new OnStateCallback() { // from class: com.glasssutdio.wear.depository.AlbumDepository.decodeOpusStream.1
            @Override // com.jieli.jl_audio_decode.callback.OnStateCallback
            public void onStart() {
                XLog.i("开始解码");
            }

            @Override // com.jieli.jl_audio_decode.callback.OnStateCallback
            public void onComplete(String p0) throws InterruptedException {
                Intrinsics.checkNotNullParameter(p0, "p0");
                XLog.i("解码结束 >> " + p0);
                AlbumDepository.this.opusToPcmIng = false;
                entity.setFilePath(GFileUtilKt.getDCIMFile().getAbsolutePath() + '/' + str);
                final int iCalculatePCMPlaybackDuration = AlbumDepository.this.calculatePCMPlaybackDuration(new File(entity.getFilePath()).length());
                XLog.i("recording length:" + iCalculatePCMPlaybackDuration);
                final GlassAlbumEntity glassAlbumEntity = entity;
                final AlbumDepository albumDepository = AlbumDepository.this;
                ThreadExtKt.ktxRunOnBgSingleDao(this, new Function1<AnonymousClass1, Unit>() { // from class: com.glasssutdio.wear.depository.AlbumDepository$decodeOpusStream$1$onComplete$1
                    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                    {
                        super(1);
                    }

                    @Override // kotlin.jvm.functions.Function1
                    public /* bridge */ /* synthetic */ Unit invoke(AlbumDepository.AnonymousClass1 anonymousClass1) throws Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
                        invoke2(anonymousClass1);
                        return Unit.INSTANCE;
                    }

                    /* renamed from: invoke, reason: avoid collision after fix types in other method */
                    public final void invoke2(AlbumDepository.AnonymousClass1 ktxRunOnBgSingleDao) throws Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
                        Intrinsics.checkNotNullParameter(ktxRunOnBgSingleDao, "$this$ktxRunOnBgSingleDao");
                        glassAlbumEntity.setVideoLength(iCalculatePCMPlaybackDuration);
                        albumDepository.saveAlbum(glassAlbumEntity);
                        AlbumDepository.WifiFilesDownloadListener wifiFilesDownloadListener = albumDepository.wifiFilesDownloadListener;
                        if (wifiFilesDownloadListener != null) {
                            wifiFilesDownloadListener.recordingToPcm(glassAlbumEntity.getFileName(), glassAlbumEntity.getFilePath(), iCalculatePCMPlaybackDuration);
                        }
                        EventBus.getDefault().post(new RecordingToPcmSuccessfullyEvent(glassAlbumEntity.getFileName(), glassAlbumEntity.getFilePath(), iCalculatePCMPlaybackDuration));
                        albumDepository.saveFileToAppGalleryFolder(GlassApplication.INSTANCE.getCONTEXT(), new File(glassAlbumEntity.getFilePath()));
                    }
                });
                opusManager.stopEncodeStream();
                AlbumDepository.this.opusToPcm();
            }

            @Override // com.jieli.jl_audio_decode.callback.OnStateCallback
            public void onError(int code, String message) throws InterruptedException {
                Intrinsics.checkNotNullParameter(message, "message");
                try {
                    AlbumDepository.this.opusToPcmIng = false;
                    XLog.i(code + ", " + message);
                    AlbumDepository.this.recordQueue.put(entity);
                    WifiFilesDownloadListener wifiFilesDownloadListener = AlbumDepository.this.wifiFilesDownloadListener;
                    if (wifiFilesDownloadListener != null) {
                        wifiFilesDownloadListener.recordingToPcmError(entity.getFileName(), message);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private final void savePcmAsWavAndAddToMediaStore(Context context, File pcmFile) throws Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
        File fileConvertPcmToWav = PcmToMp3Kt.convertPcmToWav(pcmFile);
        String appName = GlobalKt.getAppName(context);
        if (fileConvertPcmToWav != null) {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues contentValues = new ContentValues();
                contentValues.put("_display_name", fileConvertPcmToWav.getName());
                contentValues.put("mime_type", "audio/x-wav");
                contentValues.put("relative_path", Environment.DIRECTORY_MUSIC + '/' + appName);
                ContentResolver contentResolver = context.getContentResolver();
                Uri uriInsert = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues);
                if (uriInsert != null) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(fileConvertPcmToWav);
                        OutputStream outputStreamOpenOutputStream = contentResolver.openOutputStream(uriInsert);
                        if (outputStreamOpenOutputStream != null) {
                            OutputStream outputStream = outputStreamOpenOutputStream;
                            try {
                                Long.valueOf(ByteStreamsKt.copyTo$default(fileInputStream, outputStream, 0, 2, null));
                                CloseableKt.closeFinally(outputStream, null);
                            } finally {
                            }
                        }
                        fileInputStream.close();
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
                return;
            }
            MediaScannerConnection.scanFile(context, new String[]{fileConvertPcmToWav.getAbsolutePath()}, new String[]{"audio/x-wav"}, new MediaScannerConnection.OnScanCompletedListener() { // from class: com.glasssutdio.wear.depository.AlbumDepository$$ExternalSyntheticLambda0
                @Override // android.media.MediaScannerConnection.OnScanCompletedListener
                public final void onScanCompleted(String str, Uri uri) {
                    AlbumDepository.savePcmAsWavAndAddToMediaStore$lambda$7$lambda$6(str, uri);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void saveFileToAppGalleryFolder(Context context, File sourceFile) throws Resources.NotFoundException, PackageManager.NameNotFoundException, IOException {
        FileOutputStream fileInputStream;
        Pair pair;
        if (!UserConfig.INSTANCE.getInstance().getPictureAutoSave()) {
            return;
        }
        String appName = GlobalKt.getAppName(context);
        String mimeType = getMimeType(sourceFile);
        if (StringsKt.startsWith$default(mimeType, "audio/", false, 2, (Object) null)) {
            savePcmAsWavAndAddToMediaStore(context, sourceFile);
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            if (StringsKt.startsWith$default(mimeType, "image/", false, 2, (Object) null)) {
                ContentValues contentValues = new ContentValues();
                contentValues.put("_display_name", sourceFile.getName());
                contentValues.put("mime_type", mimeType);
                contentValues.put("relative_path", Environment.DIRECTORY_DCIM + '/' + appName);
                pair = TuplesKt.to(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            } else if (StringsKt.startsWith$default(mimeType, "video/", false, 2, (Object) null)) {
                ContentValues contentValues2 = new ContentValues();
                contentValues2.put("_display_name", sourceFile.getName());
                contentValues2.put("mime_type", mimeType);
                contentValues2.put("relative_path", Environment.DIRECTORY_DCIM + '/' + appName);
                pair = TuplesKt.to(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues2);
            } else {
                XLog.i("不支持的文件类型");
                return;
            }
            Uri uri = (Uri) pair.component1();
            ContentValues contentValues3 = (ContentValues) pair.component2();
            ContentResolver contentResolver = context.getContentResolver();
            Uri uriInsert = contentResolver.insert(uri, contentValues3);
            if (uriInsert != null) {
                try {
                    fileInputStream = new FileInputStream(sourceFile);
                    try {
                        FileInputStream fileInputStream2 = fileInputStream;
                        OutputStream outputStreamOpenOutputStream = contentResolver.openOutputStream(uriInsert);
                        if (outputStreamOpenOutputStream != null) {
                            fileInputStream = outputStreamOpenOutputStream;
                            try {
                                OutputStream outputStream = fileInputStream;
                                Intrinsics.checkNotNull(outputStream);
                                copyFile(fileInputStream2, outputStream);
                                Unit unit = Unit.INSTANCE;
                                CloseableKt.closeFinally(fileInputStream, null);
                                Unit unit2 = Unit.INSTANCE;
                            } finally {
                                try {
                                    throw th;
                                } finally {
                                }
                            }
                        }
                        CloseableKt.closeFinally(fileInputStream, null);
                        XLog.i("文件保存成功");
                        return;
                    } finally {
                        try {
                            throw th;
                        } finally {
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    XLog.i("文件保存失败");
                    return;
                }
            }
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Permission.WRITE_EXTERNAL_STORAGE) != 0) {
            XLog.i("缺少写入外部存储权限");
            return;
        }
        if (StringsKt.startsWith$default(mimeType, "image/", false, 2, (Object) null) || StringsKt.startsWith$default(mimeType, "video/", false, 2, (Object) null)) {
            File externalStoragePublicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
            File file = new File(externalStoragePublicDirectory, appName);
            if (!file.exists()) {
                file.mkdirs();
            }
            File file2 = new File(file, sourceFile.getName());
            try {
                fileInputStream = new FileInputStream(sourceFile);
                try {
                    FileInputStream fileInputStream3 = fileInputStream;
                    fileInputStream = new FileOutputStream(file2);
                    try {
                        copyFile(fileInputStream3, fileInputStream);
                        Unit unit3 = Unit.INSTANCE;
                        CloseableKt.closeFinally(fileInputStream, null);
                        Unit unit4 = Unit.INSTANCE;
                        CloseableKt.closeFinally(fileInputStream, null);
                        context.sendBroadcast(new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE", Uri.fromFile(file2)));
                    } finally {
                    }
                } finally {
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                XLog.i("文件保存失败");
            }
        } else {
            XLog.i("不支持的文件类型");
        }
    }

    private final void copyFile(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] bArr = new byte[1024];
        while (true) {
            int i = inputStream.read(bArr);
            if (i <= 0) {
                return;
            } else {
                outputStream.write(bArr, 0, i);
            }
        }
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue
    java.lang.NullPointerException: Cannot invoke "java.util.List.iterator()" because the return value of "jadx.core.dex.visitors.regions.SwitchOverStringVisitor$SwitchData.getNewCases()" is null
    	at jadx.core.dex.visitors.regions.SwitchOverStringVisitor.restoreSwitchOverString(SwitchOverStringVisitor.java:109)
    	at jadx.core.dex.visitors.regions.SwitchOverStringVisitor.visitRegion(SwitchOverStringVisitor.java:66)
    	at jadx.core.dex.visitors.regions.DepthRegionTraversal.traverseIterativeStepInternal(DepthRegionTraversal.java:77)
    	at jadx.core.dex.visitors.regions.DepthRegionTraversal.traverseIterativeStepInternal(DepthRegionTraversal.java:82)
    	at jadx.core.dex.visitors.regions.DepthRegionTraversal.traverseIterative(DepthRegionTraversal.java:31)
    	at jadx.core.dex.visitors.regions.SwitchOverStringVisitor.visit(SwitchOverStringVisitor.java:60)
     */
    /* JADX WARN: Removed duplicated region for block: B:33:0x007c A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:37:0x0088 A[RETURN, SYNTHETIC] */
    /* JADX WARN: Removed duplicated region for block: B:38:0x008b A[ORIG_RETURN, RETURN] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private final java.lang.String getMimeType(java.io.File r3) {
        /*
            r2 = this;
            java.lang.String r3 = r3.getName()
            kotlin.jvm.internal.Intrinsics.checkNotNull(r3)
            r0 = 46
            java.lang.String r1 = ""
            java.lang.String r3 = kotlin.text.StringsKt.substringAfterLast(r3, r0, r1)
            java.util.Locale r0 = java.util.Locale.ROOT
            java.lang.String r1 = "ROOT"
            kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(r0, r1)
            java.lang.String r3 = r3.toLowerCase(r0)
            java.lang.String r0 = "toLowerCase(...)"
            kotlin.jvm.internal.Intrinsics.checkNotNullExpressionValue(r3, r0)
            int r0 = r3.hashCode()
            switch(r0) {
                case 96980: goto L7f;
                case 97669: goto L73;
                case 102340: goto L6a;
                case 105441: goto L61;
                case 108184: goto L58;
                case 108273: goto L4f;
                case 108308: goto L46;
                case 110810: goto L3a;
                case 111145: goto L31;
                case 3268712: goto L28;
                default: goto L26;
            }
        L26:
            goto L8b
        L28:
            java.lang.String r0 = "jpeg"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L7c
            goto L8b
        L31:
            java.lang.String r0 = "png"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L7c
            goto L8b
        L3a:
            java.lang.String r0 = "pcm"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L43
            goto L8b
        L43:
            java.lang.String r3 = "audio/*"
            goto L8d
        L46:
            java.lang.String r0 = "mov"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L88
            goto L8b
        L4f:
            java.lang.String r0 = "mp4"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L88
            goto L8b
        L58:
            java.lang.String r0 = "mkv"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L88
            goto L8b
        L61:
            java.lang.String r0 = "jpg"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L7c
            goto L8b
        L6a:
            java.lang.String r0 = "gif"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L7c
            goto L8b
        L73:
            java.lang.String r0 = "bmp"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L7c
            goto L8b
        L7c:
            java.lang.String r3 = "image/*"
            goto L8d
        L7f:
            java.lang.String r0 = "avi"
            boolean r3 = r3.equals(r0)
            if (r3 != 0) goto L88
            goto L8b
        L88:
            java.lang.String r3 = "video/*"
            goto L8d
        L8b:
        */
        //  java.lang.String r3 = "*/*"
        /*
        L8d:
            return r3
        */
        throw new UnsupportedOperationException("Method not decompiled: com.glasssutdio.wear.depository.AlbumDepository.getMimeType(java.io.File):java.lang.String");
    }

    public final int calculatePCMPlaybackDuration(long totalBytes) {
        return MathKt.roundToInt((totalBytes / 32000) * 1000);
    }

    /* compiled from: AlbumDepository.kt */
    @Metadata(d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u001b\u0010\u0003\u001a\u00020\u00048FX\u0086\u0084\u0002¢\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006¨\u0006\t"}, d2 = {"Lcom/glasssutdio/wear/depository/AlbumDepository$Companion;", "", "()V", "getInstance", "Lcom/glasssutdio/wear/depository/AlbumDepository;", "getGetInstance", "()Lcom/glasssutdio/wear/depository/AlbumDepository;", "getInstance$delegate", "Lkotlin/Lazy;", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final AlbumDepository getGetInstance() {
            return (AlbumDepository) AlbumDepository.getInstance$delegate.getValue();
        }
    }
}
