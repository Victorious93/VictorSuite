package com.termux.victor.dialog.adapter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.termux.victor.util.UUtils
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.victor.bean.VsRunCommandBean
import com.termux.victor.dialog.LoadingDialog
import com.termux.victor.dialog.view_holder.BoomVictorSuiteViewHolder
import com.termux.victor.url.FileUrl.mainHomeUrl
import java.io.File

class BoomVictorSuiteAdapter : RecyclerView.Adapter<BoomVictorSuiteViewHolder> {

    private var mList:ArrayList<VsRunCommandBean>? = null

    private var mActivity:Activity? = null


    constructor(mList:ArrayList<VsRunCommandBean>?,mActivity:Activity) : super(){

        this.mList = mList
        this.mActivity = mActivity

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BoomVictorSuiteViewHolder {
       return BoomVictorSuiteViewHolder(UUtils.getViewLayViewGroup(R.layout.item_boom_victor_suite,parent))
    }

    override fun onBindViewHolder(holder: BoomVictorSuiteViewHolder, position: Int) {

        val vsRunCommandBean = mList!![position]

        if(vsRunCommandBean.type == 0){

            if(vsRunCommandBean.isShow){
                //标题
                holder.msg_card!!.visibility = View.GONE
                holder.title!!.visibility = View.VISIBLE
                holder.title!!.text = vsRunCommandBean.title
            }else{

                holder.msg_card!!.visibility = View.GONE
                holder.title!!.visibility = View.GONE
            }


        }else{
            //内容

            holder.msg_card!!.visibility = View.VISIBLE
            holder.title!!.visibility = View.GONE
            holder.msg!!.text = vsRunCommandBean.name

            holder.msg_card!!.setOnClickListener {

                mDissListener?.close()

                if(vsRunCommandBean.isHttpCommand){

                    com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(vsRunCommandBean.runCommand)

                }else{

                    val loadingDialog = LoadingDialog(mActivity!!)
                    loadingDialog.show()

                    UUtils.runOnThread {


                        UUtils.writerFile(vsRunCommandBean.assetsName, File(mainHomeUrl, "/${vsRunCommandBean.fileName}"))

                        mActivity!!.runOnUiThread {

                            loadingDialog.dismiss()

                            com.termux.victor.utils.SingletonCommunicationUtils.getInstance().getmSingletonCommunicationListener().sendTextToTerminal(vsRunCommandBean.runCommand)

                            if(vsRunCommandBean.runCommit != null){
                                vsRunCommandBean.runCommit.run()
                            }



                        }

                    }

                }







            }

            holder.msg_card!!.setOnLongClickListener {


                val intent = Intent()
                intent.data = Uri.parse(vsRunCommandBean.address) //Url 就是你要打开的网址
                intent.action = Intent.ACTION_VIEW
                mActivity!!.startActivity(intent) //启动浏览器


                true
            }


        }


    }

    override fun getItemCount(): Int {
        return mList!!.size
    }

    private var mDissListener:DissListener? = null

    public fun setDissListener(mDissListener:DissListener){

        this.mDissListener = mDissListener

    }

    public interface DissListener{


        fun close()


    }
}
