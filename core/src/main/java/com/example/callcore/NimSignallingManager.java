package com.example.callcore;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.SDKOptions;
import com.netease.nimlib.sdk.v2.V2NIMError;
import com.netease.nimlib.sdk.v2.V2NIMFailureCallback;
import com.netease.nimlib.sdk.v2.auth.V2NIMLoginListener;
import com.netease.nimlib.sdk.v2.auth.V2NIMLoginService;
import com.netease.nimlib.sdk.v2.auth.enums.V2NIMLoginClientChange;
import com.netease.nimlib.sdk.v2.auth.enums.V2NIMLoginStatus;
import com.netease.nimlib.sdk.v2.auth.model.V2NIMKickedOfflineDetail;
import com.netease.nimlib.sdk.v2.auth.model.V2NIMLoginClient;
import com.netease.nimlib.sdk.v2.auth.option.V2NIMLoginOption;
import com.netease.nimlib.sdk.v2.avsignalling.V2NIMSignallingListener;
import com.netease.nimlib.sdk.v2.avsignalling.V2NIMSignallingService;
import com.netease.nimlib.sdk.v2.avsignalling.config.V2NIMSignallingConfig;
import com.netease.nimlib.sdk.v2.avsignalling.enums.V2NIMSignallingChannelType;
import com.netease.nimlib.sdk.v2.avsignalling.enums.V2NIMSignallingEventType;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingChannelInfo;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingEvent;
import com.netease.nimlib.sdk.v2.avsignalling.model.V2NIMSignallingRoomInfo;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCallParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCallSetupParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingCancelInviteParams;
import com.netease.nimlib.sdk.v2.avsignalling.params.V2NIMSignallingRejectInviteParams;
import com.netease.nimlib.sdk.v2.avsignalling.result.V2NIMSignallingCallResult;
import com.netease.nimlib.sdk.v2.avsignalling.result.V2NIMSignallingCallSetupResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * Maps Yunxin signalling events to a minimal one-to-one call lifecycle.
 * Tencent TRTC remains responsible for audio and video media.
 */
public final class NimSignallingManager {
    private static final int LOCAL_ERROR = -20000;
    private static final int SUCCESS = 200;
    private static final long RING_TIMEOUT_MS = 30_000L;
    private static final String ACTION_HANGUP = "hangup";
    private static final String REASON_BUSY = "busy";
    private static final String REASON_REJECTED = "rejected";
    private static final String REASON_TIMEOUT = "timeout";

    private static final NimSignallingListener EMPTY_LISTENER = new NimSignallingListener() {
    };
    private static final NimSignallingManager INSTANCE = new NimSignallingManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final V2NIMSignallingListener signallingListener = new InternalSignallingListener();
    private final V2NIMLoginListener loginListener = new InternalLoginListener();

    private NimSignallingListener listener = EMPTY_LISTENER;
    private NimSignallingState state = NimSignallingState.UNINITIALIZED;
    private V2NIMLoginService loginService;
    private V2NIMSignallingService signallingService;
    private NimCallSession currentSession;
    private String currentAccountId;
    private boolean initialized;
    private boolean cancelAfterCallCreated;

    private final Runnable ringTimeout = () -> {
        if (state == NimSignallingState.OUTGOING_RINGING) {
            NimCallSession session = currentSession;
            cancelOutgoingCall(REASON_TIMEOUT);
            listener.onCallEnded(session, REASON_TIMEOUT);
        }
    };

    private NimSignallingManager() {
    }

    public static NimSignallingManager getInstance() {
        return INSTANCE;
    }

    public void setListener(NimSignallingListener value) {
        listener = value == null ? EMPTY_LISTENER : value;
        listener.onStateChanged(state);
        if (state == NimSignallingState.INCOMING_RINGING && currentSession != null) {
            listener.onIncomingCall(currentSession);
        }
    }

    public NimSignallingState getState() {
        return state;
    }

    public NimCallSession getCurrentSession() {
        return currentSession;
    }

    public String getCurrentAccountId() {
        return currentAccountId;
    }

    public boolean initialize(Context context, String appKey) {
        if (initialized) {
            return true;
        }
        if (isEmpty(appKey) || appKey.startsWith("YOUR_")) {
            notifyError(LOCAL_ERROR, "请先在 NimDemoConfig 中填写云信 AppKey");
            return false;
        }

        SDKOptions options = new SDKOptions();
        options.appKey = appKey.trim();
        options.reducedIM = true;
        options.checkManifestConfig = true;
        NIMClient.init(context.getApplicationContext(), null, options);

        loginService = NIMClient.getService(V2NIMLoginService.class);
        signallingService = NIMClient.getService(V2NIMSignallingService.class);
        loginService.addLoginListener(loginListener);
        signallingService.addSignallingListener(signallingListener);
        initialized = true;

        if (loginService.getLoginStatus() == V2NIMLoginStatus.V2NIM_LOGIN_STATUS_LOGINED) {
            currentAccountId = loginService.getLoginUser();
            setState(NimSignallingState.LOGGED_IN);
        } else {
            setState(NimSignallingState.LOGGED_OUT);
        }
        return true;
    }

    public void login(String accountId, String token) {
        if (!initialized) {
            notifyError(LOCAL_ERROR, "云信 SDK 尚未初始化");
            return;
        }
        if (isEmpty(accountId) || isEmpty(token)) {
            notifyError(LOCAL_ERROR, "云信账号和 Token 不能为空");
            return;
        }
        if (state != NimSignallingState.LOGGED_OUT) {
            notifyError(LOCAL_ERROR, "当前状态不能重复登录");
            return;
        }

        setState(NimSignallingState.LOGGING_IN);
        loginService.login(accountId.trim(), token.trim(), new V2NIMLoginOption(), unused -> {
            currentAccountId = accountId.trim();
            setState(NimSignallingState.LOGGED_IN);
            listener.onLoginSuccess(currentAccountId);
        }, error -> {
            setState(NimSignallingState.LOGGED_OUT);
            notifyError(error);
        });
    }

    public void logout() {
        if (!initialized || state == NimSignallingState.LOGGED_OUT) {
            return;
        }
        if (isCallState(state)) {
            hangup();
        }
        loginService.logout(unused -> {
            currentAccountId = null;
            clearSession();
            setState(NimSignallingState.LOGGED_OUT);
            listener.onLogout();
        }, this::notifyError);
    }

    public void call(String calleeAccountId, TrtcCallType callType) {
        if (state != NimSignallingState.LOGGED_IN) {
            notifyError(LOCAL_ERROR, "请先登录云信账号");
            return;
        }
        if (isEmpty(calleeAccountId) || callType == null) {
            notifyError(LOCAL_ERROR, "对方账号或通话类型不能为空");
            return;
        }
        String callee = calleeAccountId.trim();
        if (callee.equals(currentAccountId)) {
            notifyError(LOCAL_ERROR, "不能呼叫自己");
            return;
        }

        String requestId = UUID.randomUUID().toString();
        String roomId = "call_" + requestId.replace("-", "");
        currentSession = new NimCallSession(null, requestId, roomId, currentAccountId,
                callee, callType);
        cancelAfterCallCreated = false;

        V2NIMSignallingChannelType channelType = callType == TrtcCallType.VIDEO
                ? V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_VIDEO
                : V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_AUDIO;
        V2NIMSignallingCallParams params = new V2NIMSignallingCallParams(
                callee, requestId, channelType);
        params.setChannelName("tm_" + requestId.replace("-", ""));
        params.setChannelExtension(toSessionJson(currentSession));
        params.setServerExtension(toSessionJson(currentSession));
        params.setSignallingConfig(createSignallingConfig());

        setState(NimSignallingState.OUTGOING_RINGING);
        signallingService.call(params, this::onCallCreated, error -> {
            NimCallSession failedSession = currentSession;
            clearSession();
            setState(NimSignallingState.LOGGED_IN);
            notifyError(error);
            listener.onCallEnded(failedSession, "call_failed");
        });
    }

    public void acceptIncomingCall() {
        if (state != NimSignallingState.INCOMING_RINGING || currentSession == null) {
            notifyError(LOCAL_ERROR, "当前没有可以接听的来电");
            return;
        }
        NimCallSession session = currentSession;
        V2NIMSignallingCallSetupParams params = new V2NIMSignallingCallSetupParams(
                session.getChannelId(), session.getCallerId(), session.getRequestId());
        params.setServerExtension(actionJson("accept"));
        params.setSignallingConfig(createSignallingConfig());
        setState(NimSignallingState.CONNECTING);
        signallingService.callSetup(params, result -> onCallSetup(session, result), error -> {
            setState(NimSignallingState.INCOMING_RINGING);
            notifyError(error);
        });
    }

    public void rejectIncomingCall() {
        rejectIncomingCall(REASON_REJECTED);
    }

    public void hangup() {
        NimCallSession session = currentSession;
        if (session == null) {
            return;
        }
        if (state == NimSignallingState.OUTGOING_RINGING) {
            if (isEmpty(session.getChannelId())) {
                cancelAfterCallCreated = true;
                return;
            }
            cancelOutgoingCall("cancelled");
            listener.onCallCancelled(session);
            return;
        }
        if (state == NimSignallingState.INCOMING_RINGING) {
            rejectIncomingCall(REASON_REJECTED);
            return;
        }

        if (!isEmpty(session.getChannelId())) {
            signallingService.sendControl(session.getChannelId(), peerId(session),
                    actionJson(ACTION_HANGUP), unused -> {
                    }, error -> {
                    });
            signallingService.leaveRoom(session.getChannelId(), false,
                    actionJson(ACTION_HANGUP), unused -> {
                    }, error -> {
                    });
        }
        clearSession();
        setState(NimSignallingState.LOGGED_IN);
        listener.onCallEnded(session, ACTION_HANGUP);
    }

    public void releaseListener() {
        listener = EMPTY_LISTENER;
    }

    private void onCallCreated(V2NIMSignallingCallResult result) {
        if (currentSession == null || state != NimSignallingState.OUTGOING_RINGING) {
            return;
        }
        if (result.getCallStatus() != SUCCESS) {
            NimCallSession failedSession = currentSession;
            clearSession();
            setState(NimSignallingState.LOGGED_IN);
            notifyError(result.getCallStatus(), "云信邀请发送失败");
            listener.onCallEnded(failedSession, "call_failed");
            return;
        }
        V2NIMSignallingRoomInfo roomInfo = result.getRoomInfo();
        if (roomInfo == null || roomInfo.getChannelInfo() == null) {
            notifyError(LOCAL_ERROR, "云信没有返回信令房间信息");
            return;
        }
        currentSession = currentSession.withChannelId(roomInfo.getChannelInfo().getChannelId());
        if (cancelAfterCallCreated) {
            NimCallSession cancelledSession = currentSession;
            cancelOutgoingCall("cancelled");
            listener.onCallCancelled(cancelledSession);
            return;
        }
        listener.onOutgoingCall(currentSession);
        scheduleRingTimeout();
    }

    private void onCallSetup(NimCallSession session, V2NIMSignallingCallSetupResult result) {
        if (currentSession == null || !sameRequest(session, currentSession)) {
            return;
        }
        if (result.getCallStatus() != SUCCESS) {
            setState(NimSignallingState.INCOMING_RINGING);
            notifyError(result.getCallStatus(), "云信接听失败");
            return;
        }
        setState(NimSignallingState.IN_CALL);
        listener.onCallAccepted(currentSession);
    }

    private void rejectIncomingCall(String reason) {
        NimCallSession session = currentSession;
        if (session == null || isEmpty(session.getChannelId())) {
            return;
        }
        V2NIMSignallingRejectInviteParams params = new V2NIMSignallingRejectInviteParams(
                session.getChannelId(), session.getCallerId(), session.getRequestId());
        params.setServerExtension(reasonJson(reason));
        signallingService.rejectInvite(params, unused -> {
            clearSession();
            setState(NimSignallingState.LOGGED_IN);
            listener.onCallRejected(session, reason);
        }, error -> {
            notifyError(error);
            clearSession();
            setState(NimSignallingState.LOGGED_IN);
        });
    }

    private void cancelOutgoingCall(String reason) {
        NimCallSession session = currentSession;
        if (session == null) {
            return;
        }
        mainHandler.removeCallbacks(ringTimeout);
        if (!isEmpty(session.getChannelId())) {
            V2NIMSignallingCancelInviteParams params = new V2NIMSignallingCancelInviteParams(
                    session.getChannelId(), session.getCalleeId(), session.getRequestId());
            params.setServerExtension(reasonJson(reason));
            signallingService.cancelInvite(params, unused -> {
            }, error -> notifyError(error));
        }
        clearSession();
        setState(NimSignallingState.LOGGED_IN);
    }

    private void handleEvent(V2NIMSignallingEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }
        V2NIMSignallingEventType type = event.getEventType();
        if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_INVITE) {
            handleInvite(event);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_ACCEPT) {
            handleAccept(event);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_REJECT) {
            handleReject(event);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CANCEL_INVITE) {
            handleCancel(event);
        } else if (type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CONTROL
                || type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CLOSE
                || type == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_LEAVE) {
            handleRemoteEnd(event);
        }
    }

    private void handleInvite(V2NIMSignallingEvent event) {
        NimCallSession incoming = sessionFromEvent(event);
        if (incoming == null) {
            notifyError(LOCAL_ERROR, "收到的云信来电参数不完整");
            return;
        }
        if (state != NimSignallingState.LOGGED_IN) {
            rejectBusy(incoming);
            return;
        }
        currentSession = incoming;
        setState(NimSignallingState.INCOMING_RINGING);
        listener.onIncomingCall(incoming);
    }

    private void handleAccept(V2NIMSignallingEvent event) {
        if (state != NimSignallingState.OUTGOING_RINGING || !matchesCurrent(event)) {
            return;
        }
        mainHandler.removeCallbacks(ringTimeout);
        setState(NimSignallingState.IN_CALL);
        listener.onCallAccepted(currentSession);
    }

    private void handleReject(V2NIMSignallingEvent event) {
        if (state != NimSignallingState.OUTGOING_RINGING || !matchesCurrent(event)) {
            return;
        }
        NimCallSession session = currentSession;
        String reason = readReason(event.getServerExtension(), REASON_REJECTED);
        clearSession();
        setState(NimSignallingState.LOGGED_IN);
        listener.onCallRejected(session, reason);
    }

    private void handleCancel(V2NIMSignallingEvent event) {
        if (state != NimSignallingState.INCOMING_RINGING || !matchesCurrent(event)) {
            return;
        }
        NimCallSession session = currentSession;
        clearSession();
        setState(NimSignallingState.LOGGED_IN);
        listener.onCallCancelled(session);
    }

    private void handleRemoteEnd(V2NIMSignallingEvent event) {
        if (!isCallState(state) || !matchesCurrent(event)
                || currentAccountId.equals(event.getOperatorAccountId())) {
            return;
        }
        if (event.getEventType() == V2NIMSignallingEventType.V2NIM_SIGNALLING_EVENT_TYPE_CONTROL
                && !ACTION_HANGUP.equals(readAction(event.getServerExtension()))) {
            return;
        }
        NimCallSession session = currentSession;
        clearSession();
        setState(NimSignallingState.LOGGED_IN);
        listener.onCallEnded(session, ACTION_HANGUP);
    }

    private void rejectBusy(NimCallSession incoming) {
        V2NIMSignallingRejectInviteParams params = new V2NIMSignallingRejectInviteParams(
                incoming.getChannelId(), incoming.getCallerId(), incoming.getRequestId());
        params.setServerExtension(reasonJson(REASON_BUSY));
        signallingService.rejectInvite(params, unused -> {
        }, error -> {
        });
    }

    private NimCallSession sessionFromEvent(V2NIMSignallingEvent event) {
        V2NIMSignallingChannelInfo channel = event.getChannelInfo();
        if (channel == null || isEmpty(channel.getChannelId()) || isEmpty(event.getRequestId())) {
            return null;
        }
        JSONObject payload = parseJson(channel.getChannelExtension());
        String roomId = payload == null ? null : payload.optString("roomId", null);
        String callerId = payload == null ? event.getInviterAccountId()
                : payload.optString("callerId", event.getInviterAccountId());
        String calleeId = payload == null ? event.getInviteeAccountId()
                : payload.optString("calleeId", event.getInviteeAccountId());
        TrtcCallType type = channel.getChannelType()
                == V2NIMSignallingChannelType.V2NIM_SIGNALLING_CHANNEL_TYPE_VIDEO
                ? TrtcCallType.VIDEO : TrtcCallType.AUDIO;
        if (payload != null && "video".equals(payload.optString("callType"))) {
            type = TrtcCallType.VIDEO;
        }
        if (isEmpty(roomId) || isEmpty(callerId) || isEmpty(calleeId)) {
            return null;
        }
        return new NimCallSession(channel.getChannelId(), event.getRequestId(), roomId,
                callerId, calleeId, type);
    }

    private V2NIMSignallingConfig createSignallingConfig() {
        V2NIMSignallingConfig config = new V2NIMSignallingConfig();
        config.setOfflineEnabled(false);
        config.setUnreadEnabled(false);
        return config;
    }

    private void scheduleRingTimeout() {
        mainHandler.removeCallbacks(ringTimeout);
        mainHandler.postDelayed(ringTimeout, RING_TIMEOUT_MS);
    }

    private void clearSession() {
        mainHandler.removeCallbacks(ringTimeout);
        currentSession = null;
        cancelAfterCallCreated = false;
    }

    private void setState(NimSignallingState value) {
        state = value;
        dispatch(() -> listener.onStateChanged(value));
    }

    private void notifyError(V2NIMError error) {
        if (error == null) {
            notifyError(LOCAL_ERROR, "云信发生未知错误");
        } else {
            notifyError(error.getCode(), error.getDesc());
        }
    }

    private void notifyError(int code, String message) {
        dispatch(() -> listener.onError(code,
                isEmpty(message) ? "云信发生未知错误" : message));
    }

    private void dispatch(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private boolean matchesCurrent(V2NIMSignallingEvent event) {
        return currentSession != null
                && currentSession.getRequestId().equals(event.getRequestId())
                && (event.getChannelInfo() == null
                || currentSession.getChannelId() == null
                || currentSession.getChannelId().equals(event.getChannelInfo().getChannelId()));
    }

    private boolean sameRequest(NimCallSession first, NimCallSession second) {
        return first != null && second != null
                && first.getRequestId().equals(second.getRequestId());
    }

    private boolean isCallState(NimSignallingState value) {
        return value == NimSignallingState.OUTGOING_RINGING
                || value == NimSignallingState.INCOMING_RINGING
                || value == NimSignallingState.CONNECTING
                || value == NimSignallingState.IN_CALL;
    }

    private String peerId(NimCallSession session) {
        return currentAccountId.equals(session.getCallerId())
                ? session.getCalleeId() : session.getCallerId();
    }

    private String toSessionJson(NimCallSession session) {
        JSONObject json = new JSONObject();
        try {
            json.put("version", 1);
            json.put("roomId", session.getRoomId());
            json.put("callerId", session.getCallerId());
            json.put("calleeId", session.getCalleeId());
            json.put("callType", session.getCallType() == TrtcCallType.VIDEO ? "video" : "audio");
        } catch (JSONException ignored) {
        }
        return json.toString();
    }

    private String actionJson(String action) {
        JSONObject json = new JSONObject();
        try {
            json.put("action", action);
        } catch (JSONException ignored) {
        }
        return json.toString();
    }

    private String reasonJson(String reason) {
        JSONObject json = new JSONObject();
        try {
            json.put("reason", reason);
        } catch (JSONException ignored) {
        }
        return json.toString();
    }

    private String readAction(String json) {
        JSONObject object = parseJson(json);
        return object == null ? "" : object.optString("action", "");
    }

    private String readReason(String json, String fallback) {
        JSONObject object = parseJson(json);
        return object == null ? fallback : object.optString("reason", fallback);
    }

    private JSONObject parseJson(String value) {
        if (isEmpty(value)) {
            return null;
        }
        try {
            return new JSONObject(value);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private final class InternalSignallingListener implements V2NIMSignallingListener {
        @Override
        public void onOnlineEvent(V2NIMSignallingEvent event) {
            mainHandler.post(() -> handleEvent(event));
        }

        @Override
        public void onOfflineEvent(List<V2NIMSignallingEvent> events) {
            if (events == null) {
                return;
            }
            for (V2NIMSignallingEvent event : events) {
                mainHandler.post(() -> handleEvent(event));
            }
        }

        @Override
        public void onMultiClientEvent(V2NIMSignallingEvent event) {
        }

        @Override
        public void onSyncRoomInfoList(List<V2NIMSignallingRoomInfo> roomInfoList) {
        }
    }

    private final class InternalLoginListener implements V2NIMLoginListener {
        @Override
        public void onLoginStatus(V2NIMLoginStatus loginStatus) {
            if (loginStatus == V2NIMLoginStatus.V2NIM_LOGIN_STATUS_LOGOUT
                    || loginStatus == V2NIMLoginStatus.V2NIM_LOGIN_STATUS_UNLOGIN) {
                currentAccountId = null;
                clearSession();
                setState(NimSignallingState.LOGGED_OUT);
            }
        }

        @Override
        public void onLoginFailed(V2NIMError error) {
            setState(NimSignallingState.LOGGED_OUT);
            notifyError(error);
        }

        @Override
        public void onKickedOffline(V2NIMKickedOfflineDetail detail) {
            NimCallSession session = currentSession;
            currentAccountId = null;
            clearSession();
            setState(NimSignallingState.LOGGED_OUT);
            if (session != null) {
                listener.onCallEnded(session, "kicked_offline");
            }
            notifyError(LOCAL_ERROR, "云信账号已被其他设备踢下线");
        }

        @Override
        public void onLoginClientChanged(V2NIMLoginClientChange change,
                                         List<V2NIMLoginClient> clients) {
        }
    }
}
