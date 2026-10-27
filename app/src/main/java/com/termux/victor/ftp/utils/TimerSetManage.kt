package com.termux.victor.ftp.utils

import com.example.xh_lib.utils.SaveData
import com.google.gson.Gson
import com.termux.victor.bean.VsUserBean
import com.termux.victor.settings.timer.TimerBean

class TimerSetManage private constructor() {
    companion object {
        private var instance: TimerSetManage? = null
            get() {
                if (field == null) {
                    field = TimerSetManage()
                }
                return field
            }
        fun get(): TimerSetManage{
            return instance!!
        }
    }

    public fun getZTTimerBean(): TimerBean {
        val zTUserBeanJson = SaveData.getStringOther(VsConstant.ZT_TIMER_BEAN_KEY)
        if (zTUserBeanJson.isNullOrEmpty() || zTUserBeanJson == "def") {
            return TimerBean()
        }
        return try {
            Gson().fromJson(zTUserBeanJson, TimerBean::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            TimerBean()
        }
    }

    public fun setZTTimerBean(mZTUserBean: TimerBean) {
        val toJson = Gson().toJson(mZTUserBean)
        SaveData.saveStringOther(VsConstant.ZT_TIMER_BEAN_KEY, toJson)
    }

}

