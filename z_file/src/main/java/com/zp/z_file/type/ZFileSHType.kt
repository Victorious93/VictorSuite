package com.zp.z_file.type

import android.view.View
import android.widget.ImageView
import com.zp.z_file.R
import com.zp.z_file.common.ZFileType
import com.zp.z_file.content.getZFileConfig
import com.zp.z_file.content.getZFileHelp

/**
 * Shell script (.sh) file. Restored from the shipped z_file.aar; not present in new_ZeroTermux f68fd87.
 */
open class ZFileSHType : ZFileType() {

    override fun openFile(filePath: String, view: View) {
        getZFileHelp().getFileOpenListener().openTXT(filePath, view)
    }

    override fun loadingFile(filePath: String, pic: ImageView) {
        pic.setImageResource(getRes(getZFileConfig().resources.txtRes, R.drawable.ic_zfile_sh))
    }

}