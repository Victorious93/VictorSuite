package com.termux.victor.util

import android.os.Message
import com.lzy.okgo.model.Response

/** Callback for [BaseHttpUtils]; both methods run on the thread that created the BaseHttpUtils. */
interface HttpResponseListenerBase {
    /** HTTP 200: [msg].obj is the response body as a String. */
    fun onSuccessful(msg: Message, mWhat: Int)

    /** Transport error reported by OkGo. [msg] is always "". */
    fun onFailure(response: Response<String>?, msg: String, mWhat: Int)
}
