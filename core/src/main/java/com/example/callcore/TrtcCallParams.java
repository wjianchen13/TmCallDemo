package com.example.callcore;

public final class TrtcCallParams {
    private final int sdkAppId;
    private final String userId;
    private final String userSig;
    private final String roomId;
    private final TrtcCallType callType;

    public TrtcCallParams(int sdkAppId, String userId, String userSig, String roomId,
                          TrtcCallType callType) {
        this.sdkAppId = sdkAppId;
        this.userId = userId;
        this.userSig = userSig;
        this.roomId = roomId;
        this.callType = callType;
    }

    public int getSdkAppId() {
        return sdkAppId;
    }

    public String getUserId() {
        return userId;
    }

    public String getUserSig() {
        return userSig;
    }

    public String getRoomId() {
        return roomId;
    }

    public TrtcCallType getCallType() {
        return callType;
    }
}
