package com.example.tmcalldemo.call.view

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.tmcalldemo.R
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuikit.tuicallkit.R
import com.tencent.qcloud.tuikit.tuicallkit.common.data.Constants
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager
import com.tencent.qcloud.tuikit.tuicallkit.state.GlobalState
import com.tencent.qcloud.tuikit.tuicallkit.view.component.videolayout.CallVideoLayout
import com.trtc.tuikit.common.livedata.Observer

class SingleCallView(context: Context) : ConstraintLayout(context) {
    private val activityContext = context
    private var layoutFunction: FrameLayout? = null
    private var layoutTimer: FrameLayout? = null
    private var layoutCallHint: FrameLayout? = null
    private var floatButton: ImageView? = null

    private var isScreenCleanedObserver = Observer<Boolean> {
        layoutFunction?.visibility = if (it) View.GONE else View.VISIBLE
        layoutTimer?.visibility = if (it) View.GONE else View.VISIBLE
        layoutCallHint?.visibility = if (it) View.GONE else View.VISIBLE
        floatButton?.visibility = if (it) View.GONE else View.VISIBLE
    }

    init {
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        registerObserver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unregisterObserver()
    }

    private fun registerObserver() {
        CallManager.instance.viewState.isScreenCleaned.observe(isScreenCleanedObserver)
    }

    private fun unregisterObserver() {
        CallManager.instance.viewState.isScreenCleaned.removeObserver(isScreenCleanedObserver)
    }

    private fun initView() {
        LayoutInflater.from(activityContext).inflate(R.layout.tuicallkit_root_view_single, this)

        layoutFunction = findViewById(R.id.rl_single_function)
        layoutTimer = findViewById(R.id.rl_single_time)
        layoutCallHint = findViewById(R.id.fl_call_hint)
        floatButton = findViewById(R.id.image_float_icon)

        val smallVideoLayer: FrameLayout = findViewById(R.id.fl_video_small_layer)
        val callVideoLayout: CallVideoLayout = findViewById(R.id.call_video_layout)
        val callAdapter = GlobalState.instance.callAdapter
        val view = callAdapter?.onCreateStreamView(CallVideoLayout(activityContext))
        if (view != null) {
            removeDefaultCallContent(smallVideoLayer)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            this.addView(view, lp)
            return
        }

        callVideoLayout.initWithSmallVideoLayer(smallVideoLayer)
        addFloatButton()
    }

    private fun removeDefaultCallContent(smallVideoLayer: FrameLayout) {
        findViewById<FrameLayout>(R.id.fl_video).removeAllViews()
        layoutFunction?.removeAllViews()
        layoutTimer?.removeAllViews()
        layoutCallHint?.removeAllViews()
        smallVideoLayer.removeAllViews()
        layoutFunction?.visibility = View.GONE
        layoutTimer?.visibility = View.GONE
        layoutCallHint?.visibility = View.GONE
        floatButton?.visibility = View.GONE
    }

    private fun addFloatButton() {
        if (!GlobalState.instance.enableFloatWindow) {
            return
        }
        floatButton?.visibility = View.VISIBLE
        floatButton?.setOnClickListener {
            TUICore.notifyEvent(Constants.KEY_TUI_CALLKIT, Constants.SUB_KEY_SHOW_FLOAT_WINDOW, null)
        }
    }
}
