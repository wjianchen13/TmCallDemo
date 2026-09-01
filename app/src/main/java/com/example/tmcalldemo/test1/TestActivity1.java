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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.callcore.TestUserSigGenerator;
import com.example.callcore.TrtcCallListener;
import com.example.callcore.TrtcCallManager;
import com.example.callcore.TrtcCallParams;
import com.example.callcore.TrtcCallState;
import com.example.callcore.TrtcCallType;
import com.example.tmcalldemo.R;
import com.tencent.rtmp.ui.TXCloudVideoView;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal TRTC 1-to-1 test page. Both phones must use different user IDs and the same room ID.
 */
public class TestActivity1 extends AppCompatActivity {
    private EditText userIdInput;
    private EditText roomIdInput;
    private TextView statusView;
    private TextView mediaPlaceholder;
    private TXCloudVideoView localVideoView;
    private TXCloudVideoView remoteVideoView;
    private Button videoCallButton;
    private Button audioCallButton;
    private Button muteMicrophoneButton;
    private Button toggleCameraButton;
    private Button switchCameraButton;
    private Button toggleSpeakerButton;
    private Button hangupButton;

    private TrtcCallManager callManager;
    private TrtcCallType pendingCallType;
    private TrtcCallType activeCallType;
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
        initCallManager();
        renderIdleState();
    }

    private void bindViews() {
        userIdInput = findViewById(R.id.et_user_id);
        roomIdInput = findViewById(R.id.et_room_id);
        statusView = findViewById(R.id.tv_call_status);
        mediaPlaceholder = findViewById(R.id.tv_media_placeholder);
        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        videoCallButton = findViewById(R.id.btn_video_call);
        audioCallButton = findViewById(R.id.btn_audio_call);
        muteMicrophoneButton = findViewById(R.id.btn_mute_microphone);
        toggleCameraButton = findViewById(R.id.btn_toggle_camera);
        switchCameraButton = findViewById(R.id.btn_switch_camera);
        toggleSpeakerButton = findViewById(R.id.btn_toggle_speaker);
        hangupButton = findViewById(R.id.btn_hangup);
    }

    private void bindActions() {
        videoCallButton.setOnClickListener(view -> requestStartCall(TrtcCallType.VIDEO));
        audioCallButton.setOnClickListener(view -> requestStartCall(TrtcCallType.AUDIO));
        hangupButton.setOnClickListener(view -> callManager.exitRoom());
        muteMicrophoneButton.setOnClickListener(view -> toggleMicrophone());
        toggleCameraButton.setOnClickListener(view -> toggleCamera());
        switchCameraButton.setOnClickListener(view -> callManager.switchCamera());
        toggleSpeakerButton.setOnClickListener(view -> toggleSpeaker());
    }

    private void initCallManager() {
        callManager = new TrtcCallManager(getApplicationContext());
        callManager.setListener(new TrtcCallListener() {
            @Override
            public void onStateChanged(TrtcCallState state) {
                runOnUiThread(() -> renderState(state));
            }

            @Override
            public void onEnterRoom(long elapsed) {
                runOnUiThread(() -> statusView.setText(getString(R.string.trtc_status_in_room, elapsed)));
            }

            @Override
            public void onExitRoom(int reason) {
                runOnUiThread(() -> {
                    renderIdleState();
                    showToast(getString(R.string.trtc_exited_room));
                });
            }

            @Override
            public void onRemoteUserEnterRoom(String userId) {
                runOnUiThread(() -> statusView.setText(getString(R.string.trtc_remote_joined, userId)));
            }

            @Override
            public void onRemoteUserLeaveRoom(String userId, int reason) {
                runOnUiThread(() -> {
                    statusView.setText(getString(R.string.trtc_remote_left, userId));
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
                    statusView.setText(error);
                    showToast(error);
                    if (callManager.getState() == TrtcCallState.IDLE) {
                        setCallControlsEnabled(false);
                        videoCallButton.setEnabled(true);
                        audioCallButton.setEnabled(true);
                    }
                });
            }
        });
    }

    private void requestStartCall(TrtcCallType callType) {
        if (callManager.getState() != TrtcCallState.IDLE) {
            showToast(getString(R.string.trtc_already_in_room));
            return;
        }
        if (!validateInputs()) {
            return;
        }

        pendingCallType = callType;
        String[] permissions = callType == TrtcCallType.VIDEO
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        if (hasPermissions(permissions)) {
            pendingCallType = null;
            startCall(callType);
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void onPermissionResult(Map<String, Boolean> result) {
        if (pendingCallType == null) {
            return;
        }
        String[] required = pendingCallType == TrtcCallType.VIDEO
                ? new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        if (hasPermissions(required)) {
            TrtcCallType callType = pendingCallType;
            pendingCallType = null;
            startCall(callType);
        } else {
            pendingCallType = null;
            showToast(getString(R.string.trtc_permission_denied));
        }
    }

    private void startCall(TrtcCallType callType) {
        hideKeyboard();
        String userId = userIdInput.getText().toString().trim();
        String roomId = roomIdInput.getText().toString().trim();
        String userSig = TestUserSigGenerator.generate(userId);
        if (userSig.isEmpty()) {
            showToast(getString(R.string.trtc_usersig_failed));
            return;
        }

        activeCallType = callType;
        microphoneMuted = false;
        cameraEnabled = callType == TrtcCallType.VIDEO;
        speakerEnabled = true;
        renderCallingMode(callType);

        TrtcCallParams params = new TrtcCallParams(
                TestUserSigGenerator.SDK_APP_ID, userId, userSig, roomId, callType);
        boolean started = callManager.enterRoom(
                params,
                callType == TrtcCallType.VIDEO ? localVideoView : null,
                callType == TrtcCallType.VIDEO ? remoteVideoView : null);
        if (!started) {
            renderIdleState();
        }
    }

    private boolean validateInputs() {
        String userId = userIdInput.getText().toString().trim();
        String roomId = roomIdInput.getText().toString().trim();
        if (userId.isEmpty()) {
            userIdInput.setError(getString(R.string.trtc_user_id_required));
            return false;
        }
        if (roomId.isEmpty()) {
            roomIdInput.setError(getString(R.string.trtc_room_id_required));
            return false;
        }
        if (roomId.getBytes(StandardCharsets.UTF_8).length > 64) {
            roomIdInput.setError(getString(R.string.trtc_room_id_too_long));
            return false;
        }
        return true;
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void toggleMicrophone() {
        microphoneMuted = !microphoneMuted;
        callManager.setMicrophoneMuted(microphoneMuted);
        muteMicrophoneButton.setText(microphoneMuted
                ? R.string.trtc_unmute_microphone
                : R.string.trtc_mute_microphone);
    }

    private void toggleCamera() {
        cameraEnabled = !cameraEnabled;
        callManager.setCameraEnabled(cameraEnabled);
        toggleCameraButton.setText(cameraEnabled ? R.string.trtc_close_camera : R.string.trtc_open_camera);
        localVideoView.setVisibility(cameraEnabled ? View.VISIBLE : View.GONE);
    }

    private void toggleSpeaker() {
        speakerEnabled = !speakerEnabled;
        callManager.setSpeakerEnabled(speakerEnabled);
        toggleSpeakerButton.setText(speakerEnabled ? R.string.trtc_use_receiver : R.string.trtc_use_speaker);
    }

    private void renderState(TrtcCallState state) {
        if (state == TrtcCallState.ENTERING) {
            statusView.setText(R.string.trtc_status_entering);
        } else if (state == TrtcCallState.EXITING) {
            statusView.setText(R.string.trtc_status_exiting);
        } else if (state == TrtcCallState.IDLE) {
            renderIdleState();
        }
    }

    private void renderCallingMode(TrtcCallType callType) {
        boolean video = callType == TrtcCallType.VIDEO;
        userIdInput.setEnabled(false);
        roomIdInput.setEnabled(false);
        videoCallButton.setEnabled(false);
        audioCallButton.setEnabled(false);
        setCallControlsEnabled(true);
        toggleCameraButton.setVisibility(video ? View.VISIBLE : View.GONE);
        switchCameraButton.setVisibility(video ? View.VISIBLE : View.GONE);
        localVideoView.setVisibility(video ? View.VISIBLE : View.GONE);
        remoteVideoView.setVisibility(View.GONE);
        mediaPlaceholder.setVisibility(View.VISIBLE);
        mediaPlaceholder.setText(video
                ? R.string.trtc_waiting_remote_video
                : R.string.trtc_audio_calling);
        updateControlTexts();
    }

    private void renderIdleState() {
        activeCallType = null;
        pendingCallType = null;
        microphoneMuted = false;
        cameraEnabled = false;
        speakerEnabled = true;
        userIdInput.setEnabled(true);
        roomIdInput.setEnabled(true);
        videoCallButton.setEnabled(true);
        audioCallButton.setEnabled(true);
        setCallControlsEnabled(false);
        toggleCameraButton.setVisibility(View.VISIBLE);
        switchCameraButton.setVisibility(View.VISIBLE);
        localVideoView.setVisibility(View.GONE);
        remoteVideoView.setVisibility(View.GONE);
        mediaPlaceholder.setVisibility(View.VISIBLE);
        mediaPlaceholder.setText(R.string.trtc_test_hint);
        statusView.setText(R.string.trtc_status_idle);
        updateControlTexts();
    }

    private void setCallControlsEnabled(boolean enabled) {
        muteMicrophoneButton.setEnabled(enabled);
        toggleCameraButton.setEnabled(enabled && activeCallType == TrtcCallType.VIDEO);
        switchCameraButton.setEnabled(enabled && activeCallType == TrtcCallType.VIDEO);
        toggleSpeakerButton.setEnabled(enabled);
        hangupButton.setEnabled(enabled);
    }

    private void updateControlTexts() {
        muteMicrophoneButton.setText(microphoneMuted
                ? R.string.trtc_unmute_microphone
                : R.string.trtc_mute_microphone);
        toggleCameraButton.setText(cameraEnabled ? R.string.trtc_close_camera : R.string.trtc_open_camera);
        toggleSpeakerButton.setText(speakerEnabled ? R.string.trtc_use_receiver : R.string.trtc_use_speaker);
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
        if (callManager != null) {
            callManager.release();
        }
        super.onDestroy();
    }
}
