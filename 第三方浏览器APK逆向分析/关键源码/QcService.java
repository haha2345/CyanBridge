package com.glasssutdio.wear.api;

import com.azure.core.implementation.logging.LoggingKeys;
import com.glasssutdio.wear.all.bean.Req.AIAgentInfoReq;
import com.glasssutdio.wear.all.bean.Req.CheckVersionReq;
import com.glasssutdio.wear.all.bean.Req.FeedbackReq;
import com.glasssutdio.wear.all.bean.Req.LoginReq;
import com.glasssutdio.wear.all.bean.Req.ResetPwdReq;
import com.glasssutdio.wear.all.bean.Req.UpdateUserLocationReq;
import com.glasssutdio.wear.all.bean.Req.UpdateUserReq;
import com.glasssutdio.wear.all.bean.ResetPwdModel;
import com.glasssutdio.wear.api.request.LastOtaRequest;
import com.glasssutdio.wear.api.request.collection.CollectionRequest;
import com.glasssutdio.wear.api.request.collection.KeyRequest;
import com.glasssutdio.wear.api.response.APPKeyData;
import com.glasssutdio.wear.api.response.DevicePictureResp;
import com.glasssutdio.wear.api.response.FirmwareOtaResp;
import com.glasssutdio.wear.home.bean.LoginResModel;
import com.glasssutdio.wear.home.bean.UserModel;
import com.glasssutdio.wear.meeting.model.MeetingRecognizeReq;
import com.google.firebase.crashlytics.buildtools.reloc.org.apache.http.cookie.ClientCookie;
import io.agora.rtm.RtmConstants;
import java.util.List;
import kotlin.Metadata;
import kotlin.coroutines.Continuation;
import okhttp3.RequestBody;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/* compiled from: QcService.kt */
@Metadata(d1 = {"\u0000¨\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\bf\u0018\u0000 [2\u00020\u0001:\u0001[Jg\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u00042\b\b\u0001\u0010\u0006\u001a\u00020\u00042\b\b\u0001\u0010\u0007\u001a\u00020\b2\b\b\u0001\u0010\t\u001a\u00020\b2\b\b\u0001\u0010\n\u001a\u00020\u00042\b\b\u0001\u0010\u000b\u001a\u00020\u00042\b\b\u0001\u0010\f\u001a\u00020\u00042\b\b\u0001\u0010\r\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u0010\u000eJ5\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u00042\b\b\u0001\u0010\u0010\u001a\u00020\u00042\b\b\u0001\u0010\f\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u0010\u0011JI\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u00042\b\b\u0001\u0010\u000b\u001a\u00020\u00042\b\b\u0001\u0010\u0013\u001a\u00020\b2\b\b\u0001\u0010\u0014\u001a\u00020\b2\b\b\u0001\u0010\f\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u0010\u0015J\u001b\u0010\u0016\u001a\u00020\u00172\b\b\u0001\u0010\u0018\u001a\u00020\u0019H§@ø\u0001\u0000¢\u0006\u0002\u0010\u001aJ'\u0010\u001b\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u001d0\u001c0\u00032\b\b\u0001\u0010\u0018\u001a\u00020\u001eH§@ø\u0001\u0000¢\u0006\u0002\u0010\u001fJ!\u0010 \u001a\b\u0012\u0004\u0012\u00020\b0\u00032\b\b\u0001\u0010\u0018\u001a\u00020!H§@ø\u0001\u0000¢\u0006\u0002\u0010\"J\u001b\u0010#\u001a\u00020\u00172\b\b\u0001\u0010$\u001a\u00020%H§@ø\u0001\u0000¢\u0006\u0002\u0010&J!\u0010'\u001a\b\u0012\u0004\u0012\u00020(0\u00032\b\b\u0001\u0010\u0018\u001a\u00020)H§@ø\u0001\u0000¢\u0006\u0002\u0010*J!\u0010+\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0018\u001a\u00020,H§@ø\u0001\u0000¢\u0006\u0002\u0010-J!\u0010.\u001a\b\u0012\u0004\u0012\u00020/0\u00032\b\b\u0001\u00100\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u00101J!\u00102\u001a\b\u0012\u0004\u0012\u00020/0\u00032\b\b\u0001\u00103\u001a\u00020\bH§@ø\u0001\u0000¢\u0006\u0002\u00104J!\u00105\u001a\b\u0012\u0004\u0012\u0002060\u00032\b\b\u0001\u00107\u001a\u000208H§@ø\u0001\u0000¢\u0006\u0002\u00109J!\u0010:\u001a\b\u0012\u0004\u0012\u0002060\u00032\b\b\u0001\u00107\u001a\u000208H§@ø\u0001\u0000¢\u0006\u0002\u00109J!\u0010;\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010<\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u00101J!\u0010=\u001a\b\u0012\u0004\u0012\u00020>0\u00032\b\b\u0001\u0010\u0007\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u00101J!\u0010?\u001a\b\u0012\u0004\u0012\u00020@0\u00032\b\b\u0001\u0010\u0018\u001a\u00020AH§@ø\u0001\u0000¢\u0006\u0002\u0010BJ!\u0010C\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0007\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u00101J!\u0010D\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0018\u001a\u00020EH§@ø\u0001\u0000¢\u0006\u0002\u0010FJ!\u0010G\u001a\b\u0012\u0004\u0012\u00020@0\u00032\b\b\u0001\u0010\u0018\u001a\u00020AH§@ø\u0001\u0000¢\u0006\u0002\u0010BJ+\u0010H\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010I\u001a\u00020\u00042\b\b\u0001\u0010J\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u0010KJ!\u0010L\u001a\b\u0012\u0004\u0012\u00020(0\u00032\b\b\u0001\u0010\u0018\u001a\u00020)H§@ø\u0001\u0000¢\u0006\u0002\u0010*J!\u0010M\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u0005\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u00101J5\u0010N\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u000b\u001a\u00020\u00042\b\b\u0001\u00103\u001a\u00020\u00042\b\b\u0001\u0010\u0005\u001a\u00020\u0004H§@ø\u0001\u0000¢\u0006\u0002\u0010\u0011J+\u0010O\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010\u000b\u001a\u00020\u00042\b\b\u0001\u0010\u0014\u001a\u00020\bH§@ø\u0001\u0000¢\u0006\u0002\u0010PJ\u001b\u0010Q\u001a\u00020\u00172\b\b\u0001\u0010\u0018\u001a\u00020RH§@ø\u0001\u0000¢\u0006\u0002\u0010SJ!\u0010T\u001a\b\u0012\u0004\u0012\u00020>0\u00032\b\b\u0001\u0010\u0018\u001a\u00020UH§@ø\u0001\u0000¢\u0006\u0002\u0010VJ!\u0010W\u001a\b\u0012\u0004\u0012\u00020\u00040\u00032\b\b\u0001\u0010X\u001a\u00020YH§@ø\u0001\u0000¢\u0006\u0002\u0010Z\u0082\u0002\u0004\n\u0002\b\u0019¨\u0006\\"}, d2 = {"Lcom/glasssutdio/wear/api/QcService;", "", "agoraCreateAgent", "Lcom/glasssutdio/wear/api/QcResponse;", "", "app", "agentName", "uid", "", "agentUid", "rtcToken", "mac", "asrName", "ttsName", "(Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "agoraStopAgent", "agentId", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "agoraToken", "rtcUid", "type", "(Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "aiAgentInfo", "Lcom/glasssutdio/wear/api/QcNoDataResponse;", "req", "Lcom/glasssutdio/wear/all/bean/Req/AIAgentInfoReq;", "(Lcom/glasssutdio/wear/all/bean/Req/AIAgentInfoReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "aiAppKeys", "", "Lcom/glasssutdio/wear/api/response/APPKeyData;", "Lcom/glasssutdio/wear/api/request/collection/KeyRequest;", "(Lcom/glasssutdio/wear/api/request/collection/KeyRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "appLastVersion", "Lcom/glasssutdio/wear/all/bean/Req/CheckVersionReq;", "(Lcom/glasssutdio/wear/all/bean/Req/CheckVersionReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "collectionData", "request", "Lcom/glasssutdio/wear/api/request/collection/CollectionRequest;", "(Lcom/glasssutdio/wear/api/request/collection/CollectionRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "emailSendCode", "Lcom/glasssutdio/wear/all/bean/ResetPwdModel;", "Lcom/glasssutdio/wear/all/bean/Req/ResetPwdReq;", "(Lcom/glasssutdio/wear/all/bean/Req/ResetPwdReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "feedback", "Lcom/glasssutdio/wear/all/bean/Req/FeedbackReq;", "(Lcom/glasssutdio/wear/all/bean/Req/FeedbackReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getDevicePicture", "Lcom/glasssutdio/wear/api/response/DevicePictureResp;", ClientCookie.VERSION_ATTR, "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getDevicePictureWithDeviceId", "deviceId", "(ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLastOta", "Lcom/glasssutdio/wear/api/response/FirmwareOtaResp;", "otaRequest", "Lcom/glasssutdio/wear/api/request/LastOtaRequest;", "(Lcom/glasssutdio/wear/api/request/LastOtaRequest;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getLastOtaChina", "getToken", "key", "getUserInfo", "Lcom/glasssutdio/wear/home/bean/UserModel;", RtmConstants.LOGIN_API_STR, "Lcom/glasssutdio/wear/home/bean/LoginResModel;", "Lcom/glasssutdio/wear/all/bean/Req/LoginReq;", "(Lcom/glasssutdio/wear/all/bean/Req/LoginReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "logoff", "meetingRecognize", "Lcom/glasssutdio/wear/meeting/model/MeetingRecognizeReq;", "(Lcom/glasssutdio/wear/meeting/model/MeetingRecognizeReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "registerByEmail", "registerGetCode", "email", "appName", "(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "resetPassword", "scanConfig", "uniqueAllWinner", "uniqueMac", "(Ljava/lang/String;ILkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateLocation", "Lcom/glasssutdio/wear/all/bean/Req/UpdateUserLocationReq;", "(Lcom/glasssutdio/wear/all/bean/Req/UpdateUserLocationReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateUserInfo", "Lcom/glasssutdio/wear/all/bean/Req/UpdateUserReq;", "(Lcom/glasssutdio/wear/all/bean/Req/UpdateUserReq;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "uploadImg", LoggingKeys.BODY_KEY, "Lokhttp3/RequestBody;", "(Lokhttp3/RequestBody;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "Companion", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
/* loaded from: classes2.dex */
public interface QcService {
    public static final String BASE = "https://www.qlifesnap.com";
    public static final String BASE_URL = "https://www.qlifesnap.com/glasses/";
    public static final String BASE_URL_CHINA = "https://www.qlifesnap.com/glasses/";
    public static final String CHINA_PPM_AGREEMENT = "https://www.qlifesnap.com/ppm/heycyan_agreement.html";
    public static final String CHINA_PPM_POLICY = "https://www.qlifesnap.com/ppm/heycyan_cn.html";

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = Companion.$$INSTANCE;
    public static final String GUIDE_ASSISTANT_VOICE_1 = "https://www.qlifesnap.com/101guide/voice-assistant-mobile.html?hw=";
    public static final String GUIDE_ASSISTANT_VOICE_2 = "https://www.qlifesnap.com/101guide/voice-assistant-settings.html?hw=";
    public static final String GUIDE_GLASSES = "https://www.qlifesnap.com/101guide/glasses.html?hw=";
    public static final String GUIDE_TOUCH = "https://www.qlifesnap.com/101guide/touch.html?hw=";
    public static final String GUIDE_URL = "https://www.qlifesnap.com/101guide/onboarding.html?hw=";
    public static final String HW_PPM_AGREEMENT = "https://www.qlifesnap.com/ppm/heycyan_agreement.html";
    public static final String HW_PPM_POLICY = "https://www.qlifesnap.com/heycyan.html";

    @RequiresSignature
    @GET("ai/agora/createAiAgent")
    Object agoraCreateAgent(@Query("app") String str, @Query("agentName") String str2, @Query("uid") int i, @Query("agentUid") int i2, @Query("rtcToken") String str3, @Query("mac") String str4, @Query("asrName") String str5, @Query("ttsName") String str6, Continuation<? super QcResponse<String>> continuation);

    @RequiresSignature
    @GET("ai/agora/stopAiAgent")
    Object agoraStopAgent(@Query("app") String str, @Query("agentId") String str2, @Query("asrName") String str3, Continuation<? super QcResponse<String>> continuation);

    @RequiresSignature
    @GET("ai/agora/token")
    Object agoraToken(@Query("app") String str, @Query("mac") String str2, @Query("rtcUid") int i, @Query("type") int i2, @Query("asrName") String str3, Continuation<? super QcResponse<String>> continuation);

    @POST("ai/agent/info")
    Object aiAgentInfo(@Body AIAgentInfoReq aIAgentInfoReq, Continuation<? super QcNoDataResponse> continuation);

    @RequiresSignature
    @POST("encryption/getKeys")
    Object aiAppKeys(@Body KeyRequest keyRequest, Continuation<? super QcResponse<? extends List<APPKeyData>>> continuation);

    @POST("app-update/appLastVersion")
    Object appLastVersion(@Body CheckVersionReq checkVersionReq, Continuation<? super QcResponse<Integer>> continuation);

    @POST("collection/system/info")
    Object collectionData(@Body CollectionRequest collectionRequest, Continuation<? super QcNoDataResponse> continuation);

    @POST("users/reset-password-email")
    Object emailSendCode(@Body ResetPwdReq resetPwdReq, Continuation<? super QcResponse<ResetPwdModel>> continuation);

    @POST("customer/submit/v2")
    Object feedback(@Body FeedbackReq feedbackReq, Continuation<? super QcResponse<String>> continuation);

    @GET("device/effectPicture")
    Object getDevicePicture(@Query("hardwareVersion") String str, Continuation<? super QcResponse<DevicePictureResp>> continuation);

    @GET("device/effectPicture/deviceId")
    Object getDevicePictureWithDeviceId(@Query("deviceId") int i, Continuation<? super QcResponse<DevicePictureResp>> continuation);

    @POST("app-update/last-ota")
    Object getLastOta(@Body LastOtaRequest lastOtaRequest, Continuation<? super QcResponse<FirmwareOtaResp>> continuation);

    @POST("app-update/last-ota/china")
    Object getLastOtaChina(@Body LastOtaRequest lastOtaRequest, Continuation<? super QcResponse<FirmwareOtaResp>> continuation);

    @GET("token/getToken")
    Object getToken(@Query("key") String str, Continuation<? super QcResponse<String>> continuation);

    @GET("users/info")
    Object getUserInfo(@Query("uid") String str, Continuation<? super QcResponse<UserModel>> continuation);

    @POST("users/login/v1")
    Object login(@Body LoginReq loginReq, Continuation<? super QcResponse<LoginResModel>> continuation);

    @GET("users/login/logoff")
    Object logoff(@Query("uid") String str, Continuation<? super QcResponse<String>> continuation);

    @RequiresSignature
    @POST("ai/text/edit")
    Object meetingRecognize(@Body MeetingRecognizeReq meetingRecognizeReq, Continuation<? super QcResponse<String>> continuation);

    @POST("users/register/v2/verification")
    Object registerByEmail(@Body LoginReq loginReq, Continuation<? super QcResponse<LoginResModel>> continuation);

    @GET("users/register/verification/code")
    Object registerGetCode(@Query("email") String str, @Query("appName") String str2, Continuation<? super QcResponse<String>> continuation);

    @POST("users/reset-password")
    Object resetPassword(@Body ResetPwdReq resetPwdReq, Continuation<? super QcResponse<ResetPwdModel>> continuation);

    @GET("device/scanConfig")
    Object scanConfig(@Query("app") String str, Continuation<? super QcResponse<String>> continuation);

    @RequiresSignature
    @GET("ai/unique/mac/allwinner")
    Object uniqueAllWinner(@Query("mac") String str, @Query("deviceId") String str2, @Query("app") String str3, Continuation<? super QcResponse<String>> continuation);

    @RequiresSignature
    @GET("ai/unique/mac/type")
    Object uniqueMac(@Query("mac") String str, @Query("type") int i, Continuation<? super QcResponse<String>> continuation);

    @POST("users/userLocation")
    Object updateLocation(@Body UpdateUserLocationReq updateUserLocationReq, Continuation<? super QcNoDataResponse> continuation);

    @POST("users/update")
    Object updateUserInfo(@Body UpdateUserReq updateUserReq, Continuation<? super QcResponse<UserModel>> continuation);

    @POST("users/image/upload")
    Object uploadImg(@Body RequestBody requestBody, Continuation<? super QcResponse<String>> continuation);

    /* compiled from: QcService.kt */
    @Metadata(d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\f\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000¨\u0006\u0010"}, d2 = {"Lcom/glasssutdio/wear/api/QcService$Companion;", "", "()V", "BASE", "", "BASE_URL", "BASE_URL_CHINA", "CHINA_PPM_AGREEMENT", "CHINA_PPM_POLICY", "GUIDE_ASSISTANT_VOICE_1", "GUIDE_ASSISTANT_VOICE_2", "GUIDE_GLASSES", "GUIDE_TOUCH", "GUIDE_URL", "HW_PPM_AGREEMENT", "HW_PPM_POLICY", "app_release"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final class Companion {
        static final /* synthetic */ Companion $$INSTANCE = new Companion();
        public static final String BASE = "https://www.qlifesnap.com";
        public static final String BASE_URL = "https://www.qlifesnap.com/glasses/";
        public static final String BASE_URL_CHINA = "https://www.qlifesnap.com/glasses/";
        public static final String CHINA_PPM_AGREEMENT = "https://www.qlifesnap.com/ppm/heycyan_agreement.html";
        public static final String CHINA_PPM_POLICY = "https://www.qlifesnap.com/ppm/heycyan_cn.html";
        public static final String GUIDE_ASSISTANT_VOICE_1 = "https://www.qlifesnap.com/101guide/voice-assistant-mobile.html?hw=";
        public static final String GUIDE_ASSISTANT_VOICE_2 = "https://www.qlifesnap.com/101guide/voice-assistant-settings.html?hw=";
        public static final String GUIDE_GLASSES = "https://www.qlifesnap.com/101guide/glasses.html?hw=";
        public static final String GUIDE_TOUCH = "https://www.qlifesnap.com/101guide/touch.html?hw=";
        public static final String GUIDE_URL = "https://www.qlifesnap.com/101guide/onboarding.html?hw=";
        public static final String HW_PPM_AGREEMENT = "https://www.qlifesnap.com/ppm/heycyan_agreement.html";
        public static final String HW_PPM_POLICY = "https://www.qlifesnap.com/heycyan.html";

        private Companion() {
        }
    }
}
