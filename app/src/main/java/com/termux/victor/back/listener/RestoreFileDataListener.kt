package com.termux.victor.back.listener

import com.termux.victor.back.bean.DataBean
import java.io.File

interface RestoreFileDataListener {
    fun file(mDataBean: DataBean)
}
