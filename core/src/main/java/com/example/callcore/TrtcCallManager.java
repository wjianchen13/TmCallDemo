package com.example.callcore;

import android.content.Context;
import android.os.Bundle;

import com.tencent.liteav.device.TXDeviceManager;
import com.tencent.rtmp.ui.TXCloudVideoView;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

/**
 * Minimal TRTC media controller for a single audio or video room session.
 * Call invitation signaling is intentionally outside this class.
 */
public final class TrtcCallManager {
    private static final int ERROR_INVALID_PARAMS = -10001;
    private static final int ERROR_ALREADY_STARTED = -10002;

    private static final TrtcCallListener EMPTY_LISTENER = new TrtcCallListener() {
    };

    private final Context appContext;
    private final TRTCCloudListener cloudListener = new InternalCloudListener();

    private TrtcCallListener listener = EMPTY_LISTENER;
    private TRTCCloud trtcCloud;
    private TXDeviceManager deviceManager;
    private TXCloudVideoView localVideoView;
    private TXCloudVideoView remoteVideoView;
    private TrtcCallState state = TrtcCallState.IDLE;
    private TrtcCallType callType;
    private String remoteUserId;
    private boolean frontCamera = true;
    private boolean cameraEnabled;

    public TrtcCallManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public void setListener(TrtcCallListener listener) {
        this.listener = listener == null ? EMPTY_LISTENER : listener;
    }

    public TrtcCallState getState() {
        return state;
    }

    public boolean enterRoom(TrtcCallParams params, TXCloudVideoView localView,
                             TXCloudVideoView remoteView) {
        if (!isValid(params)) {
            listener.onError(ERROR_INVALID_PARAMS, "TRTC进房参数不完整");
            return false;
        }
        if (state != TrtcCallState.IDLE) {
            listener.onError(ERROR_ALREADY_STARTED, "当前已经在通话中");
            return false;
        }

        callType = params.getCallType();
        localVideoView = localView;
        remoteVideoView = remoteView;
        remoteUserId = null;
        frontCamera = true;
        cameraEnabled = callType == TrtcCallType.VIDEO;

        trtcCloud = TRTCCloud.sharedInstance(appContext);
        trtcCloud.setListener(cloudListener);
        deviceManager = trtcCloud.getDeviceManager();

        TRTCCloudDef.TRTCParams trtcParams = new TRTCCloudDef.TRTCParams();
        trtcParams.sdkAppId = params.getSdkAppId();
        trtcParams.userId = params.getUserId();
        trtcParams.userSig = params.getUserSig();
        trtcParams.strRoomId = params.getRoomId();

        if (callType == TrtcCallType.VIDEO && localVideoView != null) {
            trtcCloud.startLocalPreview(frontCamera, localVideoView);
        }
        trtcCloud.startLocalAudio(TRTCCloudDef.TRTC_AUDIO_QUALITY_SPEECH);
        trtcCloud.setAudioRoute(TRTCCloudDef.TRTC_AUDIO_ROUTE_SPEAKER);

        setState(TrtcCallState.ENTERING);
        int scene = callType == TrtcCallType.VIDEO
                ? TRTCCloudDef.TRTC_APP_SCENE_VIDEOCALL
                : TRTCCloudDef.TRTC_APP_SCENE_AUDIOCALL;
        trtcCloud.enterRoom(trtcParams, scene);
        return true;
    }

    public void exitRoom() {
        if (trtcCloud == null || state == TrtcCallState.IDLE || state == TrtcCallState.EXITING) {
            return;
        }
        setState(TrtcCallState.EXITING);
        stopLocalMedia();
        trtcCloud.stopAllRemoteView();
        trtcCloud.exitRoom();
    }

    public void setMicrophoneMuted(boolean muted) {
        if (trtcCloud != null) {
            trtcCloud.muteLocalAudio(muted);
        }
    }

    public void setCameraEnabled(boolean enabled) {
        if (trtcCloud == null || callType != TrtcCallType.VIDEO || localVideoView == null) {
            return;
        }
        cameraEnabled = enabled;
        if (enabled) {
            trtcCloud.startLocalPreview(frontCamera, localVideoView);
        } else {
            trtcCloud.stopLocalPreview();
        }
    }

    public boolean isCameraEnabled() {
        return cameraEnabled;
    }

    public void switchCamera() {
        if (deviceManager == null || callType != TrtcCallType.VIDEO) {
            return;
        }
        frontCamera = !frontCamera;
        deviceManager.switchCamera(frontCamera);
    }

    public void setSpeakerEnabled(boolean enabled) {
        if (trtcCloud != null) {
            trtcCloud.setAudioRoute(enabled
                    ? TRTCCloudDef.TRTC_AUDIO_ROUTE_SPEAKER
                    : TRTCCloudDef.TRTC_AUDIO_ROUTE_EARPIECE);
        }
    }

    public void release() {
        if (trtcCloud != null) {
            stopLocalMedia();
            trtcCloud.stopAllRemoteView();
            trtcCloud.exitRoom();
            trtcCloud.setListener(null);
            trtcCloud = null;
            deviceManager = null;
            TRTCCloud.destroySharedInstance();
        }
        clearSession();
        listener = EMPTY_LISTENER;
    }

    private boolean isValid(TrtcCallParams params) {
        return params != null
                && params.getSdkAppId() > 0
                && !isEmpty(params.getUserId())
                && !isEmpty(params.getUserSig())
                && !isEmpty(params.getRoomId())
                && params.getCallType() != null;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void stopLocalMedia() {
        if (trtcCloud == null) {
            return;
        }
        trtcCloud.stopLocalAudio();
        if (callType == TrtcCallType.VIDEO) {
            trtcCloud.stopLocalPreview();
        }
    }

    private void clearSession() {
        state = TrtcCallState.IDLE;
        callType = null;
        remoteUserId = null;
        localVideoView = null;
        remoteVideoView = null;
        cameraEnabled = false;
    }

    private void setState(TrtcCallState newState) {
        state = newState;
        listener.onStateChanged(newState);
    }

    private final class InternalCloudListener extends TRTCCloudListener {
        @Override
        public void onEnterRoom(long result) {
            if (result > 0) {
                setState(TrtcCallState.IN_ROOM);
                listener.onEnterRoom(result);
            } else {
                stopLocalMedia();
                setState(TrtcCallState.IDLE);
                listener.onError((int) result, "进入TRTC房间失败");
            }
        }

        @Override
        public void onExitRoom(int reason) {
            clearSession();
            listener.onStateChanged(TrtcCallState.IDLE);
            listener.onExitRoom(reason);
        }

        @Override
        public void onRemoteUserEnterRoom(String userId) {
            if (remoteUserId == null) {
                remoteUserId = userId;
            }
            listener.onRemoteUserEnterRoom(userId);
        }

        @Override
        public void onRemoteUserLeaveRoom(String userId, int reason) {
            if (trtcCloud != null) {
                trtcCloud.stopRemoteView(userId, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
            }
            if (userId != null && userId.equals(remoteUserId)) {
                remoteUserId = null;
            }
            listener.onRemoteUserLeaveRoom(userId, reason);
        }

        @Override
        public void onUserVideoAvailable(String userId, boolean available) {
            if (trtcCloud != null && remoteVideoView != null) {
                if (available && (remoteUserId == null || userId.equals(remoteUserId))) {
                    remoteUserId = userId;
                    trtcCloud.startRemoteView(userId, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG,
                            remoteVideoView);
                } else if (!available) {
                    trtcCloud.stopRemoteView(userId, TRTCCloudDef.TRTC_VIDEO_STREAM_TYPE_BIG);
                }
            }
            listener.onRemoteVideoAvailable(userId, available);
        }

        @Override
        public void onError(int errCode, String errMsg, Bundle extraInfo) {
            listener.onError(errCode, errMsg == null ? "TRTC发生未知错误" : errMsg);
        }
    }
}
