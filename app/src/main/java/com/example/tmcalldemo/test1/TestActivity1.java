package com.example.tmcalldemo.test1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.callcore.NimCallSession;
import com.example.callcore.NimSignallingListener;
import com.example.callcore.NimSignallingManager;
import com.example.callcore.NimSignallingState;
import com.example.callcore.TestUserSigGenerator;
import com.example.callcore.TrtcCallListener;
import com.example.callcore.TrtcCallManager;
import com.example.callcore.TrtcCallParams;
import com.example.callcore.TrtcCallState;
import com.example.callcore.TrtcCallType;
import com.example.tmcalldemo.R;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.util.Map;

/** Simple custom-UI test page using Yunxin signalling and Tencent TRTC media. */
public class TestActivity1 extends AppCompatActivity {
    private EditText userIdInput;
    private EditText tokenInput;
    private EditText peerIdInput;
    private TextView loginStatusView;
    private TextView callStatusView;
    private TextView mediaPlaceholder;
    private TXCloudVideoView localVideoView;
    private TXCloudVideoView remoteVideoView;
    private Button loginButton;
    private Button logoutButton;
    private Button videoCallButton;
    private Button audioCallButton;
    private Button muteMicrophoneButton;
    private Button toggleCameraButton;
    private Button switchCameraButton;
    private Button toggleSpeakerButton;
    private Button hangupButton;

    private final NimSignallingManager signallingManager = NimSignallingManager.getInstance();
    private TrtcCallManager trtcCallManager;
    private AlertDialog incomingCallDialog;
    private TrtcCallType pendingOutgoingType;
    private boolean pendingIncomingAccept;
    private NimCallSession activeSession;
    private boolean microphoneMuted;
    private boolean cameraEnabled;
    private boolean speakerEnabled = true;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_test1);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int padding = Math.round(16 * getResources().getDisplayMetrics().density);
            view.setPadding(bars.left + padding, bars.top + padding,
                    bars.right + padding, bars.bottom + padding);
            return insets;
        });

        bindViews();
        bindActions();
        initTrtcManager();
        initSignallingManager();
        renderState(signallingManager.getState());
    }

    private void bindViews() {
        userIdInput = findViewById(R.id.et_user_id);
        tokenInput = findViewById(R.id.et_nim_token);
        peerIdInput = findViewById(R.id.et_peer_id);
        loginStatusView = findViewById(R.id.tv_login_status);
        callStatusView = findViewById(R.id.tv_call_status);
        mediaPlaceholder = findViewById(R.id.tv_media_placeholder);
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        loginButton = findViewById(R.id.btn_login);
        logoutButton = findViewById(R.id.btn_logout);
        videoCallButton = findViewById(R.id.btn_video_call);
        audioCallButton = findViewById(R.id.btn_audio_call);
        muteMicrophoneButton = findViewById(R.id.btn_mute_microphone);
        toggleCameraButton = findViewById(R.id.btn_toggle_camera);
        switchCameraButton = findViewById(R.id.btn_switch_camera);
        toggleSpeakerButton = findViewById(R.id.btn_toggle_speaker);
        hangupButton = findViewById(R.id.btn_hangup);
    }

    private void bindActions() {
        loginButton.setOnClickListener(view -> login());
        logoutButton.setOnClickListener(view -> signallingManager.logout());
        videoCallButton.setOnClickListener(view -> requestOutgoingCall(TrtcCallType.VIDEO));
        audioCallButton.setOnClickListener(view -> requestOutgoingCall(TrtcCallType.AUDIO));
        hangupButton.setOnClickListener(view -> hangup());
        muteMicrophoneButton.setOnClickListener(view -> toggleMicrophone());
        toggleCameraButton.setOnClickListener(view -> toggleCamera());
        switchCameraButton.setOnClickListener(view -> trtcCallManager.switchCamera());
        toggleSpeakerButton.setOnClickListener(view -> toggleSpeaker());
    }

    private void initSignallingManager() {
        signallingManager.setListener(new NimSignallingListener() {
            @Override
            public void onStateChanged(NimSignallingState state) {
                runOnUiThread(() -> renderState(state));
            }

            @Override
            public void onLoginSuccess(String accountId) {
                runOnUiThread(() -> {
                    loginStatusView.setText(getString(R.string.nim_login_success, accountId));
                    showToast(getString(R.string.nim_login_success, accountId));
                });
            }

            @Override
            public void onLogout() {
                runOnUiThread(() -> {
                    dismissIncomingDialog();
                    stopTrtcMedia();
                    loginStatusView.setText(R.string.nim_logged_out);
                    showToast(getString(R.string.nim_logged_out));
                });
            }

            @Override
            public void onOutgoingCall(NimCallSession session) {
                runOnUiThread(() -> callStatusView.setText(getString(
                        R.string.nim_calling_peer, session.getCalleeId())));
            }

            @Override
            public void onIncomingCall(NimCallSession session) {
                runOnUiThread(() -> showIncomingCallDialog(session));
            }

            @Override
            public void onCallAccepted(NimCallSession session) {
                runOnUiThread(() -> {
                    dismissIncomingDialog();
                    activeSession = session;
                    renderState(signallingManager.getState());
                    startTrtc(session);
                });
            }

            @Override
            public void onCallRejected(NimCallSession session, String reason) {
                runOnUiThread(() -> {
                    dismissIncomingDialog();
                    stopTrtcMedia();
                    boolean localRejected = session != null
                            && signallingManager.getCurrentAccountId() != null
                            && signallingManager.getCurrentAccountId().equals(session.getCalleeId());
                    int message = localRejected ? R.string.nim_call_rejected_by_me
                            : "busy".equals(reason) ? R.string.nim_peer_busy
                            : R.string.nim_call_rejected;
                    callStatusView.setText(message);
                    showToast(getString(message));
                });
            }

            @Override
            public void onCallCancelled(NimCallSession session) {
                runOnUiThread(() -> {
                    dismissIncomingDialog();
                    stopTrtcMedia();
                    callStatusView.setText(R.string.nim_call_cancelled);
                    showToast(getString(R.string.nim_call_cancelled));
                });
            }

            @Override
            public void onCallEnded(NimCallSession session, String reason) {
                runOnUiThread(() -> {
                    dismissIncomingDialog();
                    stopTrtcMedia();
                    int message = "timeout".equals(reason)
                            ? R.string.nim_call_timeout : R.string.nim_call_ended;
                    callStatusView.setText(message);
                    showToast(getString(message));
                });
            }

            @Override
            public void onError(int code, String message) {
                runOnUiThread(() -> {
                    String error = getString(R.string.nim_error, code, message);
                    callStatusView.setText(error);
                    showToast(error);
                });
            }
        });
        signallingManager.initialize(getApplicationContext(), NimDemoConfig.APP_KEY);
    }

    private void initTrtcManager() {
        trtcCallManager = new TrtcCallManager(getApplicationContext());
        trtcCallManager.setListener(new TrtcCallListener() {
            @Override
            public void onStateChanged(TrtcCallState state) {
                if (state == TrtcCallState.ENTERING) {
                    runOnUiThread(() -> callStatusView.setText(R.string.trtc_status_entering));
                }
            }

            @Override
            public void onEnterRoom(long elapsed) {
                runOnUiThread(() -> callStatusView.setText(
                        getString(R.string.trtc_status_in_room, elapsed)));
            }

            @Override
            public void onRemoteUserEnterRoom(String userId) {
                runOnUiThread(() -> callStatusView.setText(
                        getString(R.string.trtc_remote_joined, userId)));
            }

            @Override
            public void onRemoteUserLeaveRoom(String userId, int reason) {
                runOnUiThread(() -> {
                    remoteVideoView.setVisibility(View.GONE);
                    mediaPlaceholder.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onRemoteVideoAvailable(String userId, boolean available) {
                runOnUiThread(() -> {
                    remoteVideoView.setVisibility(available ? View.VISIBLE : View.GONE);
                    mediaPlaceholder.setVisibility(available ? View.GONE : View.VISIBLE);
                });
            }

            @Override
            public void onError(int code, String message) {
                runOnUiThread(() -> {
                    String error = getString(R.string.trtc_error, code, message);
                    callStatusView.setText(error);
                    showToast(error);
                    signallingManager.hangup();
                });
            }
        });
    }

    private void login() {
        hideKeyboard();
        if (!signallingManager.initialize(getApplicationContext(), NimDemoConfig.APP_KEY)) {
            return;
        }
        String accountId = userIdInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (accountId.isEmpty()) {
            userIdInput.setError(getString(R.string.nim_user_id_required));
            return;
        }
        if (token.isEmpty()) {
            tokenInput.setError(getString(R.string.nim_token_required));
            return;
        }
        signallingManager.login(accountId, token);
    }

    private void requestOutgoingCall(TrtcCallType callType) {
        String peerId = peerIdInput.getText().toString().trim();
        if (peerId.isEmpty()) {
            peerIdInput.setError(getString(R.string.nim_peer_id_required));
            return;
        }
        pendingOutgoingType = callType;
        String[] permissions = permissionsFor(callType);
        if (hasPermissions(permissions)) {
            pendingOutgoingType = null;
            hideKeyboard();
            signallingManager.call(peerId, callType);
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void requestAcceptIncoming(NimCallSession session) {
        pendingIncomingAccept = true;
        String[] permissions = permissionsFor(session.getCallType());
        if (hasPermissions(permissions)) {
            pendingIncomingAccept = false;
            signallingManager.acceptIncomingCall();
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (pendingIncomingAccept) {
            NimCallSession session = signallingManager.getCurrentSession();
            pendingIncomingAccept = false;
            if (session != null && hasPermissions(permissionsFor(session.getCallType()))) {
                signallingManager.acceptIncomingCall();
            } else {
                signallingManager.rejectIncomingCall();
                showToast(getString(R.string.trtc_permission_denied));
            }
            return;
        }
        if (pendingOutgoingType != null) {
            TrtcCallType callType = pendingOutgoingType;
            pendingOutgoingType = null;
            if (hasPermissions(permissionsFor(callType))) {
                hideKeyboard();
                signallingManager.call(peerIdInput.getText().toString().trim(), callType);
            } else {
                showToast(getString(R.string.trtc_permission_denied));
            }
        }
    }

    private void showIncomingCallDialog(NimCallSession session) {
        dismissIncomingDialog();
        int message = session.getCallType() == TrtcCallType.VIDEO
                ? R.string.nim_incoming_video_call : R.string.nim_incoming_audio_call;
        incomingCallDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.app_incoming_call_title)
                .setMessage(getString(message, session.getCallerId()))
                .setCancelable(false)
                .setNegativeButton(R.string.app_incoming_call_cancel,
                        (dialog, which) -> signallingManager.rejectIncomingCall())
                .setPositiveButton(R.string.app_incoming_call_accept,
                        (dialog, which) -> requestAcceptIncoming(session))
                .create();
        incomingCallDialog.show();
    }

    private void startTrtc(NimCallSession session) {
        String userId = signallingManager.getCurrentAccountId();
        String userSig = TestUserSigGenerator.generate(userId);
        if (userSig.isEmpty()) {
            showToast(getString(R.string.trtc_usersig_failed));
            signallingManager.hangup();
            return;
        }

        activeSession = session;
        microphoneMuted = false;
        cameraEnabled = session.getCallType() == TrtcCallType.VIDEO;
        speakerEnabled = true;
        renderMediaMode(session.getCallType());
        TrtcCallParams params = new TrtcCallParams(TestUserSigGenerator.SDK_APP_ID,
                userId, userSig, session.getRoomId(), session.getCallType());
        boolean started = trtcCallManager.enterRoom(params,
                cameraEnabled ? localVideoView : null,
                cameraEnabled ? remoteVideoView : null);
        if (!started) {
            signallingManager.hangup();
        }
    }

    private void hangup() {
        signallingManager.hangup();
        stopTrtcMedia();
    }

    private void stopTrtcMedia() {
        activeSession = null;
        if (trtcCallManager != null && trtcCallManager.getState() != TrtcCallState.IDLE) {
            trtcCallManager.exitRoom();
        }
        localVideoView.setVisibility(View.GONE);
        remoteVideoView.setVisibility(View.GONE);
        mediaPlaceholder.setVisibility(View.VISIBLE);
        mediaPlaceholder.setText(R.string.nim_media_idle);
    }

    private void toggleMicrophone() {
        microphoneMuted = !microphoneMuted;
        trtcCallManager.setMicrophoneMuted(microphoneMuted);
        updateControlTexts();
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        trtcCallManager.setCameraEnabled(cameraEnabled);
        localVideoView.setVisibility(cameraEnabled ? View.VISIBLE : View.GONE);
        updateControlTexts();
    }

    private void toggleSpeaker() {
        speakerEnabled = !speakerEnabled;
        trtcCallManager.setSpeakerEnabled(speakerEnabled);
        updateControlTexts();
    }

    private void renderState(NimSignallingState state) {
        boolean loggedOut = state == NimSignallingState.LOGGED_OUT
                || state == NimSignallingState.UNINITIALIZED;
        boolean loggedIn = state == NimSignallingState.LOGGED_IN;
        boolean inCall = state == NimSignallingState.IN_CALL;
        boolean busy = state == NimSignallingState.OUTGOING_RINGING
                || state == NimSignallingState.INCOMING_RINGING
                || state == NimSignallingState.CONNECTING || inCall;

        userIdInput.setEnabled(loggedOut);
        tokenInput.setEnabled(loggedOut);
        loginButton.setEnabled(loggedOut);
        logoutButton.setEnabled(!loggedOut && !busy);
        peerIdInput.setEnabled(loggedIn);
        videoCallButton.setEnabled(loggedIn);
        audioCallButton.setEnabled(loggedIn);
        hangupButton.setEnabled(busy);
        muteMicrophoneButton.setEnabled(inCall);
        toggleSpeakerButton.setEnabled(inCall);
        toggleCameraButton.setEnabled(inCall && activeSession != null
                && activeSession.getCallType() == TrtcCallType.VIDEO);
        switchCameraButton.setEnabled(toggleCameraButton.isEnabled());

        if (state == NimSignallingState.LOGGING_IN) {
            loginStatusView.setText(R.string.nim_logging_in);
        } else if (loggedOut) {
            loginStatusView.setText(R.string.nim_not_logged_in);
        } else if (signallingManager.getCurrentAccountId() != null) {
            loginStatusView.setText(getString(R.string.nim_login_success,
                    signallingManager.getCurrentAccountId()));
        }
        if (state == NimSignallingState.OUTGOING_RINGING) {
            callStatusView.setText(R.string.nim_outgoing_ringing);
        } else if (state == NimSignallingState.INCOMING_RINGING) {
            callStatusView.setText(R.string.nim_incoming_ringing);
        } else if (state == NimSignallingState.CONNECTING) {
            callStatusView.setText(R.string.nim_accepting_call);
        } else if (loggedIn) {
            callStatusView.setText(R.string.nim_ready);
        }
    }

    private void renderMediaMode(TrtcCallType callType) {
        boolean video = callType == TrtcCallType.VIDEO;
        toggleCameraButton.setVisibility(video ? View.VISIBLE : View.GONE);
        switchCameraButton.setVisibility(video ? View.VISIBLE : View.GONE);
        localVideoView.setVisibility(video ? View.VISIBLE : View.GONE);
        remoteVideoView.setVisibility(View.GONE);
        mediaPlaceholder.setVisibility(View.VISIBLE);
        mediaPlaceholder.setText(video
                ? R.string.trtc_waiting_remote_video : R.string.trtc_audio_calling);
        updateControlTexts();
    }

    private void updateControlTexts() {
        muteMicrophoneButton.setText(microphoneMuted
                ? R.string.trtc_unmute_microphone : R.string.trtc_mute_microphone);
        toggleCameraButton.setText(cameraEnabled
                ? R.string.trtc_close_camera : R.string.trtc_open_camera);
        toggleSpeakerButton.setText(speakerEnabled
                ? R.string.trtc_use_receiver : R.string.trtc_use_speaker);
    }

    private String[] permissionsFor(TrtcCallType callType) {
        return callType == TrtcCallType.VIDEO
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                : new String[]{Manifest.permission.RECORD_AUDIO};
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void dismissIncomingDialog() {
        if (incomingCallDialog != null) {
            incomingCallDialog.dismiss();
            incomingCallDialog = null;
        }
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager != null) {
                manager.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
            focused.clearFocus();
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        dismissIncomingDialog();
        if (trtcCallManager != null) {
            if (trtcCallManager.getState() != TrtcCallState.IDLE) {
                signallingManager.hangup();
            }
            trtcCallManager.release();
        }
        signallingManager.releaseListener();
        super.onDestroy();
    }
}
