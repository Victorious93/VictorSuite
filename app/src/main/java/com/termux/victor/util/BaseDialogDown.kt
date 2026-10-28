package com.termux.victor.util

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.termux.R

/**
 * Bottom-anchored dialog. The layout from [getContentView] is inflated in the constructor,
 * handed to [initViewDialog], then set as the content view. Not cancelable; 50% dim.
 * Width is the full screen width unless [mWidth] is set to a non-zero value.
 */
abstract class BaseDialogDown : Dialog {

    lateinit var mContext: Context
    var mWidth: Int = 0

    private val dimAmount = 0.5f
    private val sideMarginDp = 0

    constructor(context: Context) : super(context, R.style.VictorBaseDialog) { setUp(context) }
    constructor(context: Context, themeResId: Int) : super(context, themeResId) { setUp(context) }

    private fun setUp(context: Context) {
        mContext = context
        val view = UUtils.getViewLay(getContentView())
        initViewDialog(view)
        setContentView(view)
    }

    override fun show() {
        super.show()
        val params = window!!.attributes
        params.gravity = Gravity.BOTTOM
        params.dimAmount = dimAmount
        params.width = if (mWidth == 0) {
            getScreenWidth(context) - 2 * dp2px(context, sideMarginDp.toFloat())
        } else {
            mWidth
        }
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.attributes = params
        setCancelable(false)
    }

    open fun getScreenWidth(context: Context): Int = context.resources.displayMetrics.widthPixels

    open fun dp2px(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density + 0.5f).toInt()

    abstract fun initViewDialog(mView: View)

    abstract fun getContentView(): Int
}
