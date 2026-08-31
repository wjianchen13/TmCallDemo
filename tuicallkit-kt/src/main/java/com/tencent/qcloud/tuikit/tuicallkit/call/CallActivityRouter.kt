package com.tencent.qcloud.tuikit.tuicallkit.call

import android.app.Activity
import android.content.Context
import android.content.Intent

object CallActivityRouter {

    private const val DEFAULT_CALL_ACTIVITY_CLASS_NAME =
        "com.tencent.qcloud.tuikit.tuicallkit.call.CallMainActivity"

    private var callActivityClassName: String = DEFAULT_CALL_ACTIVITY_CLASS_NAME

    @JvmStatic
    fun setCallActivityClassName(className: String?) {
        if (!className.isNullOrEmpty()) {
            callActivityClassName = className
        }
    }

    @JvmStatic
    fun startCallActivity(context: Context, action: String? = null) {
        context.startActivity(createCallActivityIntent(context, action))
    }

    @JvmStatic
    fun createCallActivityIntent(context: Context, action: String? = null): Intent {
        val intent = Intent()
        intent.setClassName(context.packageName, callActivityClassName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (!action.isNullOrEmpty()) {
            intent.action = action
        }
        return intent
    }

    @JvmStatic
    fun isCallActivity(activity: Activity): Boolean {
        return activity.componentName.className == callActivityClassName
    }
}
