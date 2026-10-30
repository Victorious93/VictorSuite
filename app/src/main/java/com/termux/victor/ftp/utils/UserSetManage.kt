package com.termux.victor.ftp.utils

import com.termux.victor.util.OtherPrefs
import android.text.TextUtils
import com.termux.victor.util.LogUtils
import com.google.gson.Gson
import com.termux.victor.bean.VsUserBean
import com.termux.victor.config.VsLinks
import com.zp.z_file.content.TAG

class UserSetManage private constructor() {
    companion object {
        private var instance: UserSetManage? = null
            get() {
                if (field == null) {
                    field = UserSetManage()
                }
                return field
            }
        fun get(): UserSetManage{
            return instance!!
        }
        public val ITEM_VISIBLE = "1"
        public val ITEM_GEON = "0"
    }

    public fun getVsUserBean(): VsUserBean {
        val zTUserBeanJson = OtherPrefs.getStringOther(VsConstant.VS_USER_BEAN_KEY)
        if (zTUserBeanJson.isNullOrEmpty() || zTUserBeanJson == "def") {
            return VsUserBean()
        }
        return try {
            Gson().fromJson(zTUserBeanJson, VsUserBean::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            VsUserBean()
        }
    }

    public fun setVsUserBean(mVsUserBean: VsUserBean) {
        val toJson = Gson().toJson(mVsUserBean)
        OtherPrefs.saveStringOther(VsConstant.VS_USER_BEAN_KEY, toJson)
    }

    public fun defServerIPName(): DataServers {
        val arrayList = ArrayList<DataServer>()
        val dataServer = DataServer("def", VsLinks.FEED_BASE)
        arrayList.add(dataServer)
        val dataServers = DataServers(arrayList)
        val vsUserBean = getVsUserBean()
        vsUserBean.serverJsonString = Gson().toJson(dataServers)
        setVsUserBean(vsUserBean)
        return dataServers
    }

    public fun setMainMenuItemShow(id: String, isShow: String) {
        LogUtils.i(TAG, "setMainMenuItemShow id：$id, isShow: $isShow")
        OtherPrefs.saveStringOther(id, isShow)
    }

    public fun getMainMenuItemShow(id: String): Boolean {
        val stringOther = OtherPrefs.getStringOther(id)
        LogUtils.i(TAG, "getMainMenuItemShow id：$id, stringOther: $stringOther")
        if (TextUtils.isEmpty(stringOther) || TextUtils.equals(stringOther, "def")) {
            LogUtils.i(TAG, "getMainMenuItemShow return false(isEmpty|def).")
            return false
        }
        if (ITEM_VISIBLE == stringOther?.trim()) {
            return true
        }
        LogUtils.i(TAG, "getMainMenuItemShow return false(def).")
        return false
    }

    data class DataServer(val serverName: String, val serverUrl: String) {
        override fun toString(): String {
            return "DataServer(serverName='$serverName', serverUrl='$serverUrl')"
        }
    }
    data class DataServers(val dataServers: ArrayList<DataServer>) {
        override fun toString(): String {
            return "DataServers(dataServers=$dataServers)"
        }
    }

}

