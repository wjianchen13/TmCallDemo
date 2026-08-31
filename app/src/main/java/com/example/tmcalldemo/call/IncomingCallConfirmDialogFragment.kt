package com.example.tmcalldemo.call

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.example.tmcalldemo.R
import com.tencent.cloud.tuikit.engine.call.TUICallDefine
import com.tencent.qcloud.tuikit.tuicallkit.call.IncomingCallAction
import com.tencent.qcloud.tuikit.tuicallkit.call.IncomingCallInfo
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager
import com.trtc.tuikit.common.livedata.Observer

class IncomingCallConfirmDialogFragment : DialogFragment() {
    private var callInfo: IncomingCallInfo? = null
    private var callAction: IncomingCallAction? = null

    private val callStatusObserver = Observer<TUICallDefine.Status> {
        if (it == TUICallDefine.Status.None || it == TUICallDefine.Status.Accept) {
            dismissAllowingStateLoss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val info = callInfo
        if (info == null) {
            dismissAllowingStateLoss()
            return super.onCreateDialog(savedInstanceState)
        }

        val callerName = if (info.callerNickname.isNullOrEmpty()) {
            info.callerId
        } else {
            info.callerNickname
        }
        val messageResId = if (info.mediaType == TUICallDefine.MediaType.Video) {
            R.string.app_incoming_video_call_message
        } else {
            R.string.app_incoming_audio_call_message
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.app_incoming_call_title)
            .setMessage(getString(messageResId, callerName))
            .setPositiveButton(R.string.app_incoming_call_accept) { _, _ ->
                callAction?.acceptAndOpenCallPage()
            }
            .setNegativeButton(R.string.app_incoming_call_cancel) { _, _ ->
                callAction?.rejectCall()
            }
            .create()
    }

    override fun onStart() {
        super.onStart()
        CallManager.instance.userState.selfUser.get().callStatus.observe(callStatusObserver)
    }

    override fun onStop() {
        CallManager.instance.userState.selfUser.get().callStatus.removeObserver(callStatusObserver)
        super.onStop()
    }

    companion object {
        private const val TAG = "IncomingCallConfirmDialog"

        @JvmStatic
        fun show(activity: FragmentActivity, info: IncomingCallInfo, action: IncomingCallAction): Boolean {
            val fragmentManager = activity.supportFragmentManager
            if (fragmentManager.findFragmentByTag(TAG) != null) {
                return true
            }
            if (fragmentManager.isStateSaved) {
                return false
            }
            val fragment = IncomingCallConfirmDialogFragment()
            fragment.callInfo = info
            fragment.callAction = action
            fragment.show(fragmentManager, TAG)
            return true
        }
    }
}
