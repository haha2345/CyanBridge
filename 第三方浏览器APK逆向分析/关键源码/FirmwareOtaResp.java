package com.glasssutdio.wear.api.response;

import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.cookie.ClientCookie;
import com.squareup.moshi.JsonClass;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: FirmwareOtaResp.kt */
@JsonClass(generateAdapter = true)
@Metadata(d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0010\n\u0002\u0010\b\n\u0002\b\u0012\b\u0007\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\b\u0010&\u001a\u00020\u0004H\u0016R\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u0006\"\u0004\b\u000b\u0010\bR\u001a\u0010\f\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u0006\"\u0004\b\u000e\u0010\bR\u001a\u0010\u000f\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0006\"\u0004\b\u0011\u0010\bR\u001a\u0010\u0012\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0012\u0010\u0006\"\u0004\b\u0013\u0010\bR\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0016\u0010\u0017\"\u0004\b\u0018\u0010\u0019R\u001a\u0010\u001a\u001a\u00020\u0015X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001b\u0010\u0017\"\u0004\b\u001c\u0010\u0019R\u001a\u0010\u001d\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001e\u0010\u0006\"\u0004\b\u001f\u0010\bR\u001a\u0010 \u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\u0006\"\u0004\b\"\u0010\bR\u001a\u0010#\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b$\u0010\u0006\"\u0004\b%\u0010\b¨\u0006'"}, d2 = {"Lcom/glasssutdio/wear/api/response/FirmwareOtaResp;", "", "()V", "downloadUrl", "", "getDownloadUrl", "()Ljava/lang/String;", "setDownloadUrl", "(Ljava/lang/String;)V", "enforceUpdateFrom", "getEnforceUpdateFrom", "setEnforceUpdateFrom", "enforceUpdateTo", "getEnforceUpdateTo", "setEnforceUpdateTo", "hardwareVersion", "getHardwareVersion", "setHardwareVersion", "isEnforceUpdate", "setEnforceUpdate", "openOrNot", "", "getOpenOrNot", "()I", "setOpenOrNot", "(I)V", "os", "getOs", "setOs", "updateDesc", "getUpdateDesc", "setUpdateDesc", "uploadDate", "getUploadDate", "setUploadDate", ClientCookie.VERSION_ATTR, "getVersion", "setVersion", "toString", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public final class FirmwareOtaResp {
    private int openOrNot;
    private String hardwareVersion = "";
    private String version = "";
    private String enforceUpdateFrom = "";
    private String enforceUpdateTo = "";
    private String isEnforceUpdate = "";
    private String downloadUrl = "";
    private String uploadDate = "";
    private int os = 1;
    private String updateDesc = "";

    public final String getHardwareVersion() {
        return this.hardwareVersion;
    }

    public final void setHardwareVersion(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.hardwareVersion = str;
    }

    public final String getVersion() {
        return this.version;
    }

    public final void setVersion(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.version = str;
    }

    public final String getEnforceUpdateFrom() {
        return this.enforceUpdateFrom;
    }

    public final void setEnforceUpdateFrom(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.enforceUpdateFrom = str;
    }

    public final String getEnforceUpdateTo() {
        return this.enforceUpdateTo;
    }

    public final void setEnforceUpdateTo(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.enforceUpdateTo = str;
    }

    /* renamed from: isEnforceUpdate, reason: from getter */
    public final String getIsEnforceUpdate() {
        return this.isEnforceUpdate;
    }

    public final void setEnforceUpdate(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.isEnforceUpdate = str;
    }

    public final String getDownloadUrl() {
        return this.downloadUrl;
    }

    public final void setDownloadUrl(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.downloadUrl = str;
    }

    public final int getOpenOrNot() {
        return this.openOrNot;
    }

    public final void setOpenOrNot(int i) {
        this.openOrNot = i;
    }

    public final String getUploadDate() {
        return this.uploadDate;
    }

    public final void setUploadDate(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.uploadDate = str;
    }

    public final int getOs() {
        return this.os;
    }

    public final void setOs(int i) {
        this.os = i;
    }

    public final String getUpdateDesc() {
        return this.updateDesc;
    }

    public final void setUpdateDesc(String str) {
        Intrinsics.checkNotNullParameter(str, "<set-?>");
        this.updateDesc = str;
    }

    public String toString() {
        return "FirmwareOtaResp(hardwareVersion='" + this.hardwareVersion + "', version='" + this.version + "', enforceUpdateFrom='" + this.enforceUpdateFrom + "', enforceUpdateTo='" + this.enforceUpdateTo + "', isEnforceUpdate='" + this.isEnforceUpdate + "', downloadUrl='" + this.downloadUrl + "', openOrNot=" + this.openOrNot + ", uploadDate='" + this.uploadDate + "', os=" + this.os + ", updateDesc='" + this.updateDesc + "')";
    }
}
