package com.glasssutdio.wear.all.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.client.methods.HttpGet;
import com.liulishuo.okdownload.OkDownloadProvider;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: NetWorkUtils.kt */
@Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\u0018\u0000 \u00032\u00020\u0001:\u0001\u0003B\u0005¢\u0006\u0002\u0010\u0002¨\u0006\u0004"}, d2 = {"Lcom/glasssutdio/wear/all/utils/NetWorkUtils;", "", "()V", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class NetWorkUtils {

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);

    /* compiled from: NetWorkUtils.kt */
    @Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004J\u0006\u0010\u0005\u001a\u00020\u0004J\u000e\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0007\u001a\u00020\bJ\u0006\u0010\t\u001a\u00020\u0004J\u0006\u0010\n\u001a\u00020\u000b¨\u0006\f"}, d2 = {"Lcom/glasssutdio/wear/all/utils/NetWorkUtils$Companion;", "", "()V", "isChinaOnline", "", "isHwOnline", "isNetworkAvailable", "context", "Landroid/content/Context;", "isOnline", "ping", "", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        public final boolean isOnline() {
            return true;
        }

        private Companion() {
        }

        public final boolean isNetworkAvailable(Context context) {
            Intrinsics.checkNotNullParameter(context, "context");
            Object systemService = OkDownloadProvider.context.getSystemService("connectivity");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.net.ConnectivityManager");
            NetworkInfo activeNetworkInfo = ((ConnectivityManager) systemService).getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected() && activeNetworkInfo.getState() == NetworkInfo.State.CONNECTED;
        }

        public final int ping() {
            try {
                return Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 10 www.google.com").waitFor();
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            } catch (InterruptedException e2) {
                e2.printStackTrace();
                return -1;
            }
        }

        public final boolean isHwOnline() throws IOException {
            try {
                URLConnection uRLConnectionOpenConnection = new URL("https://api1.qcwxkjvip.com/qcwx/test/test").openConnection();
                Intrinsics.checkNotNull(uRLConnectionOpenConnection, "null cannot be cast to non-null type java.net.HttpURLConnection");
                HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnectionOpenConnection;
                httpURLConnection.setRequestMethod(HttpGet.METHOD_NAME);
                httpURLConnection.connect();
                return httpURLConnection.getResponseCode() == 200;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e2) {
                e2.printStackTrace();
                return false;
            }
        }

        public final boolean isChinaOnline() throws IOException {
            try {
                URLConnection uRLConnectionOpenConnection = new URL("https://china.qcwxwire.com/qcwx/test/test").openConnection();
                Intrinsics.checkNotNull(uRLConnectionOpenConnection, "null cannot be cast to non-null type java.net.HttpURLConnection");
                HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnectionOpenConnection;
                httpURLConnection.setRequestMethod(HttpGet.METHOD_NAME);
                httpURLConnection.connect();
                return httpURLConnection.getResponseCode() == 200;
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return false;
            } catch (IOException e2) {
                e2.printStackTrace();
                return false;
            }
        }
    }
}
