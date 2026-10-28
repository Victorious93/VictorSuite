package com.termux.victor.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.lzy.okgo.OkGo
import com.lzy.okgo.callback.StringCallback
import com.lzy.okgo.model.HttpHeaders
import com.lzy.okgo.model.HttpParams
import com.lzy.okgo.model.Response

/**
 * Minimal GET helper on top of OkGo. Results are posted back to the looper of the thread that
 * constructed this object.
 *
 * - HTTP 200  -> listener.onSuccessful(message with obj = body, what)
 * - HTTP 401 or any other non-200 status -> listener is NOT called (only logged)
 * - OkGo error (network failure, exception) -> listener.onFailure(response, "", what)
 */
open class BaseHttpUtils {

    companion object {
        /** Sent as the Authorization header. */
        @JvmField
        var token = ""

        internal const val RESULT_OK = 999
        internal const val RESULT_FAILURE = 998
        internal const val RESULT_UNAUTHORIZED = 997
    }

    private lateinit var listener: HttpResponseListenerBase

    private val handler = Handler(Looper.myLooper()!!) { msg ->
        dispatch(msg)
        false
    }

    internal fun dispatch(msg: Message) {
        when (msg.arg1) {
            RESULT_OK -> listener.onSuccessful(msg, msg.what)
            RESULT_FAILURE -> {
                @Suppress("UNCHECKED_CAST")
                val response = msg.obj as? Response<String>
                if (response != null) listener.onFailure(response, "", msg.what)
            }
            // RESULT_UNAUTHORIZED: intentionally ignored
        }
    }

    @JvmOverloads
    fun getUrl(
        url: String,
        mHttpResponseListener: HttpResponseListenerBase,
        mHashMap: HashMap<String, String> = HashMap(),
        mWhat: Int = 0,
    ) {
        val headers = HttpHeaders().apply {
            put("clientType", "0")
            put("Content-Type", "application/json")
            put("Authorization", token)
        }
        listener = mHttpResponseListener
        val params = HttpParams()
        for ((k, v) in mHashMap) params.put(k, v)

        OkGo.get<String>(url).tag(UUtils.getContext()).headers(headers).params(params)
            .execute(object : StringCallback() {
                override fun onSuccess(response: Response<String>) {
                    val code = response.rawResponse.code
                    val message = Message()
                    message.what = mWhat
                    when (code) {
                        401 -> message.arg1 = RESULT_UNAUTHORIZED
                        200 -> {
                            message.arg1 = RESULT_OK
                            message.obj = response.body()
                        }
                        else -> {
                            message.arg1 = RESULT_FAILURE
                            message.obj = response.body()
                            UUtils.showLog("错误消息2222:" + response.body())
                        }
                    }
                    handler.sendMessage(message)
                }

                override fun onError(response: Response<String>) {
                    super.onError(response)
                    val message = Message()
                    message.arg1 = RESULT_FAILURE
                    message.what = mWhat
                    message.obj = response
                    handler.sendMessage(message)
                }
            })
    }
}
