package com.zp.z_file.util

import android.content.Context
import android.os.Build
import androidx.collection.ArrayMap
import com.zp.z_file.R
import com.zp.z_file.content.*
import com.zp.z_file.content.QQ_PIC
import com.zp.z_file.content.QQ_PIC_MOVIE
import com.zp.z_file.content.SD_ROOT

internal object ZFileFBHUtil {

    /**
     * 通用的
     */
    fun doingWork(context: Context): ArrayMap<String, ZFileFolderBadgeHintBean> {
        val map = ArrayMap<String, ZFileFolderBadgeHintBean>()
        val dataPath = "${SD_ROOT}Android/data"
        val obbPath = "${SD_ROOT}Android/obb"
        val cameraPath = "${SD_ROOT}DCIM"
        val cameraPath2 = "${SD_ROOT}DCIM/Camera"
        val downloadPath = "${SD_ROOT}Download"
        val musicPath = "${SD_ROOT}Music"
        val picturesPath = "${SD_ROOT}Pictures"
        val moviePath = "${SD_ROOT}Movies"
        val tencentPath = "${SD_ROOT}tencent"
        map[dataPath] = ZFileFolderBadgeHintBean(
            folderPath = "${SD_ROOT}Android/data",
            folderHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Access restricted" else "Application Package",
            folderBadgeIcon = R.drawable.zfile_sys,
            folderBadgeType = 1
        )
        map[obbPath] = ZFileFolderBadgeHintBean(
            folderPath = obbPath,
            folderHint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "Access restricted" else "Data package",
            folderBadgeIcon = R.drawable.zfile_obb,
            folderBadgeType = 1
        )
        map[cameraPath] = ZFileFolderBadgeHintBean(
            folderPath = cameraPath,
            folderHint = "Gallery",
            folderBadgeIcon = R.drawable.zfile_camera,
            folderBadgeType = 1
        )
        map[cameraPath2] = ZFileFolderBadgeHintBean(
            folderPath = cameraPath2,
            folderHint = "Camera",
            folderBadgeIcon = R.drawable.zfile_camera,
            folderBadgeType = 1
        )
        map[downloadPath] = ZFileFolderBadgeHintBean(
            folderPath = downloadPath,
            folderHint = "System download",
            folderBadgeIcon = R.drawable.zfile_download,
            folderBadgeType = 1
        )
        map[musicPath] = ZFileFolderBadgeHintBean(
            folderPath = musicPath,
            folderHint = "Music",
            folderBadgeIcon = R.drawable.zfile_music,
            folderBadgeType = 1
        )
        map[picturesPath] = ZFileFolderBadgeHintBean(
            folderPath = picturesPath,
            folderHint = "Images",
            folderBadgeIcon = R.drawable.zfile_photo,
            folderBadgeType = 1
        )
        map[moviePath] = ZFileFolderBadgeHintBean(
            folderPath = moviePath,
            folderHint = "Videos",
            folderBadgeIcon = R.drawable.zfile_movie,
            folderBadgeType = 1
        )
        map[tencentPath] = ZFileFolderBadgeHintBean(
            folderPath = tencentPath,
            folderHint = "Tencent",
            folderBadgeIcon = R.drawable.zfile_tencent,
            folderBadgeType = 1
        )
        val qqPath = QQ_PIC.substring(0, QQ_PIC.length - 1)
        map[qqPath] = ZFileFolderBadgeHintBean(
            folderPath = qqPath,
            folderHint = "QQ images",
            folderBadgeIcon = R.drawable.zfile_qq,
            folderBadgeType = 1
        )
        val qqPath2 = QQ_PIC_MOVIE.substring(0, QQ_PIC_MOVIE.length - 1)
        map[qqPath2] = ZFileFolderBadgeHintBean(
            folderPath = qqPath2,
            folderHint = "QQ images & videos",
            folderBadgeIcon = R.drawable.zfile_qq,
            folderBadgeType = 1
        )
        val wechatPath = WECHAT_FILE_PATH.substring(0, WECHAT_FILE_PATH.length - 1)
        map[wechatPath] = ZFileFolderBadgeHintBean(
            folderPath = WECHAT_FILE_PATH,
            folderHint = "WeChat",
            folderBadgeIcon = R.drawable.zfile_wechat,
            folderBadgeType = 1
        )
        val wechatPath2 = (WECHAT_FILE_PATH + WECHAT_PHOTO_VIDEO).substring(0, (WECHAT_FILE_PATH + WECHAT_PHOTO_VIDEO).length - 1)
        map[wechatPath2] = ZFileFolderBadgeHintBean(
            folderPath = wechatPath2,
            folderHint = "WeChat images & videos",
            folderBadgeIcon = R.drawable.zfile_wechat,
            folderBadgeType = 1
        )
        val wechatPath3 = (WECHAT_FILE_PATH + WECHAT_DOWLOAD).substring(0, (WECHAT_FILE_PATH + WECHAT_DOWLOAD).length - 1)
        map[wechatPath3] = ZFileFolderBadgeHintBean(
            folderPath = wechatPath3,
            folderHint = "WeChat downloads",
            folderBadgeIcon = R.drawable.zfile_wechat,
            folderBadgeType = 1
        )
        return map
    }

}