package com.tencent.qcloud.tuikit.tuicallkit.call

import android.app.Activity
import android.os.Build
import java.lang.ref.WeakReference

object CallBusinessBridge {
    private var foregroundActivityRef: WeakReference<Activity>? = null
    private var incomingCallInterceptor: IncomingCallInterceptor? = null

    @JvmStatic
    fun setIncomingCallInterceptor(interceptor: IncomingCallInterceptor?) {
        incomingCallInterceptor = interceptor
    }

    @JvmStatic
    fun onActivityResumed(activity: Activity) {
        foregroundActivityRef = WeakReference(activity)
    }

    @JvmStatic
    fun onActivityPaused(activity: Activity) {
        if (foregroundActivityRef?.get() === activity) {
            foregroundActivityRef = null
        }
    }

    fun interceptIncomingCall(info: IncomingCallInfo, action: IncomingCallAction): Boolean {
        val activity = foregroundActivityRef?.get() ?: return false
        if (activity.isFinishing || isDestroyed(activity) || CallActivityRouter.isCallActivity(activity)) {
            return false
        }
        return incomingCallInterceptor?.onIncomingCall(activity, info, action) == true
    }

    private fun isDestroyed(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed
    }
}
