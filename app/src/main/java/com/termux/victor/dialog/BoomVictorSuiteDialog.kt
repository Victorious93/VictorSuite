package com.termux.victor.dialog

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blockchain.ub.util.custom.dialog.BaseDialogDown
import com.termux.R
import com.termux.victor.bean.ZeroRunCommandBean
import com.termux.victor.data.LinuxCommandData
import com.termux.victor.dialog.adapter.BoomVictorSuiteAdapter
import com.termux.victor.popuwindow.NginxPopuWindow
import com.termux.victor.popuwindow.WebStartPopuWindow

class BoomVictorSuiteDialog : BaseDialogDown {

    private var recycler_view:RecyclerView? = null


    constructor(context: Context) : super(context)
    constructor(context: Context, themeResId: Int) : super(context, themeResId)

    override fun initViewDialog(mView: View) {

        recycler_view = mView.findViewById(R.id.recycler_view)



        recycler_view!!.layoutManager = GridLayoutManager(mContext,4)

        var mList:ArrayList<ZeroRunCommandBean> = LinuxCommandData.getZeroRunCommandBeanData()


        val boomVictorSuiteAdapter = BoomVictorSuiteAdapter(mList, mContext as Activity)

        boomVictorSuiteAdapter.setDissListener(object : BoomVictorSuiteAdapter.DissListener{
            override fun close() {
                dismiss()
            }
        })

        recycler_view!!.adapter = boomVictorSuiteAdapter
    }

    override fun getContentView(): Int {

        return R.layout.dialog_victor_suite
    }


}
