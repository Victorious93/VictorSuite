package com.termux.victor.fragment

import android.view.View
import com.termux.victor.util.UUtils
import com.termux.R

class SettingFragment : BaseFragment {
    constructor() : super()

    override fun getFragmentView(): View {

        return UUtils.getViewLay(R.layout.dialog_switch)
    }

    override fun initFragmentView(mView: View?) {

    }
}
