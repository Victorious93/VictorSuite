package com.zp.z_file.util

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.zp.z_file.async.ZFileListAsync
import com.zp.z_file.common.ZFileTypeManage
import com.zp.z_file.content.*
import com.zp.z_file.ui.dialog.ZFileLoadingDialog
import java.io.File
import kotlin.concurrent.thread

internal object ZFileUtil {

    private final val TAG = "ZFileUtil"
    /**
     * 获取文件
     */
    fun getList(context: Context, block: MutableList<ZFileBean>?.() -> Unit) {
        ZFileListAsync(context, block).start(getZFileConfig().filePath)
    }

    /**
     * 打开文件
     */
    fun openFile(filePath: String, view: View) {
        ZFileTypeManage.getTypeManager().openFile(filePath, view)
    }

    /**
     * 查看文件详情
     */
    fun infoFile(bean: ZFileBean, context: Context) {
        ZFileTypeManage.getTypeManager().infoFile(bean, context)
    }

    /**
     * 重命名文件
     */
    fun renameFile(
        filePath: String,
        newName: String,
        context: Context,
        block: (Boolean, String) -> Unit
    ) {
        ZFileLog.i("Rename file path: $filePath")
        ZFileLog.i("Rename new name: $newName")
        val activity = context as Activity
        val title = "Renaming..."
        val dialog = getZFileHelp().getOtherListener()?.getLoadingDialog(activity, title)
            ?: ZFileLoadingDialog(activity, title).run {
                setCancelable(false)
                this
            }
        dialog.show()
        thread {
            val isSuccess = try {
                val oldFile = filePath.toFile()
                val oldPath = oldFile.path.substring(0, oldFile.path.lastIndexOf("/") + 1)
                Log.e(TAG, "renameFile: oldPath: ${oldPath}")
                val newFile = File("$oldPath$newName")
                Log.e(TAG, "renameFile: newFile:${newFile.absolutePath}")
                oldFile.renameTo(newFile)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "renameFile: " + e.toString())
                false
            }
            activity.runOnUiThread {
                dialog.dismiss()
                activity.toast(if (isSuccess) "Renamed" else "Rename failed")
                block.invoke(isSuccess, newName)
            }
        }
    }

    /**
     * 删除文件
     */
    fun deleteFile(filePath: String, context: Context, block: Boolean.() -> Unit) {
        ZFileLog.i("Delete file path: $filePath")
        ZFileSth.callFileByType(filePath, "", context, DELTE_TYPE, block)
    }

    /**
     * 复制文件
     */
    fun copyFile(filePath: String, outPath: String, context: Context, block: Boolean.() -> Unit) {
        ZFileLog.i("Source path: $filePath")
        ZFileLog.i("Copy target directory: $outPath")
        ZFileSth.callFileByType(filePath, outPath, context, COPY_TYPE, block)
    }

    /**
     * 剪切文件
     */
    fun cutFile(filePath: String, outPath: String, context: Context, block: Boolean.() -> Unit) {
        ZFileLog.i("Source path: $filePath")
        ZFileLog.i("Move target directory: $outPath")
        ZFileSth.callFileByType(filePath, outPath, context, CUT_TYPE, block)
    }

    /**
     * 解压文件
     */
    fun zipFile(filePath: String, outZipPath: String, context: Context, block: Boolean.() -> Unit) {
        ZFileLog.i("Source path: $filePath")
        ZFileLog.i("Extract directory: $outZipPath")
        ZFileSth.callFileByType(filePath, outZipPath, context, ZIP_TYPE, block)
    }

    fun resetAll() {
        getZFileConfig().apply {
            filePath = null
        }
    }

}
