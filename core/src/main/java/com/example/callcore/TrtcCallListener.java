package com.example.callcore;

public interface TrtcCallListener {
    default void onStateChanged(TrtcCallState state) {
    }

    default void onEnterRoom(long elapsed) {
    }

    default void onExitRoom(int reason) {
    }

    default void onRemoteUserEnterRoom(String userId) {
    }

    default void onRemoteUserLeaveRoom(String userId, int reason) {
    }

    default void onRemoteVideoAvailable(String userId, boolean available) {
    }

    default void onError(int code, String message) {
    }
}
