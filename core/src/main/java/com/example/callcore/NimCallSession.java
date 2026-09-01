package com.example.callcore;

public final class NimCallSession {
    private final String channelId;
    private final String requestId;
    private final String roomId;
    private final String callerId;
    private final String calleeId;
    private final TrtcCallType callType;

    NimCallSession(String channelId, String requestId, String roomId, String callerId,
                   String calleeId, TrtcCallType callType) {
        this.channelId = channelId;
        this.requestId = requestId;
        this.roomId = roomId;
        this.callerId = callerId;
        this.calleeId = calleeId;
        this.callType = callType;
    }

    NimCallSession withChannelId(String value) {
        return new NimCallSession(value, requestId, roomId, callerId, calleeId, callType);
    }

    public String getChannelId() {
        return channelId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getCallerId() {
        return callerId;
    }

    public String getCalleeId() {
        return calleeId;
    }

    public TrtcCallType getCallType() {
        return callType;
    }
}
