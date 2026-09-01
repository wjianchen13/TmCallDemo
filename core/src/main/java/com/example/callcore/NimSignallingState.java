package com.example.callcore;

public enum NimSignallingState {
    UNINITIALIZED,
    LOGGED_OUT,
    LOGGING_IN,
    LOGGED_IN,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    CONNECTING,
    IN_CALL
}
