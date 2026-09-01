package com.example.callcore;

public interface NimSignallingListener {
    default void onStateChanged(NimSignallingState state) {
    }

    default void onLoginSuccess(String accountId) {
    }

    default void onLogout() {
    }

    default void onOutgoingCall(NimCallSession session) {
    }

    default void onIncomingCall(NimCallSession session) {
    }

    default void onCallAccepted(NimCallSession session) {
    }

    default void onCallRejected(NimCallSession session, String reason) {
    }

    default void onCallCancelled(NimCallSession session) {
    }

    default void onCallEnded(NimCallSession session, String reason) {
    }

    default void onError(int code, String message) {
    }
}
