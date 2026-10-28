package com.termux.victor.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import com.termux.R

/**
 * Centred dialog. The layout from [getContentView] is inflated in the constructor,
 * handed to [initViewDialog], then set as the content view. Not cancelable; 50% dim.
 * Width is two thirds of the activity's display width, so [mContext] must be an Activity.
 */
abstract class BaseDialogCentre : Dialog {

    lateinit var mContext: Context
    var mWidth: Int = 0

    private val dimAmount = 0.5f

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
        params.dimAmount = dimAmount
        @Suppress("DEPRECATION")
        mWidth = (mContext as Activity).windowManager.defaultDisplay.width
        params.width = mWidth - (mWidth / 6) * 2
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
