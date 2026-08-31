package com.tencent.qcloud.tuikit.tuicallkit.call

import android.app.Activity
import com.tencent.cloud.tuikit.engine.call.TUICallDefine

data class IncomingCallInfo(
    val callId: String,
    val callerId: String,
    val callerNickname: String?,
    val callerAvatar: String?,
    val mediaType: TUICallDefine.MediaType,
    val scene: TUICallDefine.Scene,
    val groupId: String?,
    val calleeIdList: List<String>
)

interface IncomingCallAction {
    fun acceptAndOpenCallPage()
    fun rejectCall()
}

interface IncomingCallInterceptor {
    fun onIncomingCall(activity: Activity, info: IncomingCallInfo, action: IncomingCallAction): Boolean
}
