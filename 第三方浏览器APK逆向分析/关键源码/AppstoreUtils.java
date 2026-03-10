package com.glasssutdio.wear.all.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.Locale;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: AppstoreUtils.kt */
@Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0016\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u0016\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\bJ\u0016\u0010\n\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b¨\u0006\u000b"}, d2 = {"Lcom/glasssutdio/wear/all/utils/AppstoreUtils;", "", "()V", "openAppStore", "", "context", "Landroid/content/Context;", "packageName", "", "openGooglePlayStore", "openTencentAppStore", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class AppstoreUtils {
    public static final AppstoreUtils INSTANCE = new AppstoreUtils();

    private AppstoreUtils() {
    }

    public final void openGooglePlayStore(Context context, String packageName) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.setPackage("com.android.vending");
            context.startActivity(intent);
        } catch (ActivityNotFoundException unused) {
            Intent intent2 = new Intent("android.intent.action.VIEW");
            intent2.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
            context.startActivity(intent2);
        }
    }

    public final void openTencentAppStore(Context context, String packageName) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse("market://details?id=" + packageName));
            intent.setPackage("com.tencent.android.qqdownloader");
            context.startActivity(intent);
        } catch (ActivityNotFoundException unused) {
            Intent intent2 = new Intent("android.intent.action.VIEW");
            intent2.setData(Uri.parse("https://a.app.qq.com/o/simple.jsp?pkgname=" + packageName));
            context.startActivity(intent2);
        }
    }

    public final void openAppStore(Context context, String packageName) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        if (StringsKt.equals(Locale.getDefault().getCountry(), "CN", true)) {
            openTencentAppStore(context, packageName);
        } else {
            openGooglePlayStore(context, packageName);
        }
    }
}
