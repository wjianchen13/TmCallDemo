package com.tencent.qcloud.tuikit.tuicallkit.view.component.videolayout

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import com.tencent.cloud.tuikit.engine.call.TUICallDefine
import com.tencent.qcloud.tuikit.tuicallkit.manager.CallManager

class CallVideoLayout : ConstraintLayout {
    private var smallVideoLayer: ViewGroup? = null
    private var isInitialized = false

    constructor(context: Context) : super(context) {
        initView()
    }

    constructor(context: Context, smallVideoLayer: ViewGroup?) : super(context) {
        this.smallVideoLayer = smallVideoLayer
        initView()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun initWithSmallVideoLayer(smallVideoLayer: ViewGroup?) {
        if (isInitialized) {
            return
        }
        this.smallVideoLayer = smallVideoLayer
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isInitialized) {
            initView()
        }
    }

    private fun initView() {
        if (isInitialized) {
            return
        }
        isInitialized = true
        if (layoutParams == null) {
            this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        val scene = CallManager.instance.callState.scene.get()
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        if (scene == TUICallDefine.Scene.GROUP_CALL || scene == TUICallDefine.Scene.MULTI_CALL) {
            addView(MultiCallVideoLayout(context), params)
        } else {
            addView(SingleCallVideoLayout(context, smallVideoLayer), params)
        }
    }
}
