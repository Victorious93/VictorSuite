package com.termux.victor.util

import android.app.Application
import android.os.Message
import com.lzy.okgo.model.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BaseHttpUtilsTest {

    private class Recorder : HttpResponseListenerBase {
        var success: Pair<Any?, Int>? = null
        var failure: Pair<Response<String>?, Int>? = null
        override fun onSuccessful(msg: Message, mWhat: Int) { success = msg.obj to mWhat }
        override fun onFailure(response: Response<String>?, msg: String, mWhat: Int) {
            assertEquals("", msg); failure = response to mWhat
        }
    }

    private fun utilsWith(r: Recorder): BaseHttpUtils {
        val u = BaseHttpUtils()
        val f = BaseHttpUtils::class.java.getDeclaredField("listener")
        f.isAccessible = true
        f.set(u, r)
        return u
    }

    private fun msg(arg1: Int, what: Int, obj: Any?) = Message().also { it.arg1 = arg1; it.what = what; it.obj = obj }

    @Test fun successDeliversBody() {
        val r = Recorder()
        utilsWith(r).dispatch(msg(BaseHttpUtils.RESULT_OK, 7, "body"))
        assertEquals("body" to 7, r.success)
    }

    @Test fun errorResponseDeliversFailure() {
        val r = Recorder(); val resp = Response<String>()
        utilsWith(r).dispatch(msg(BaseHttpUtils.RESULT_FAILURE, 3, resp))
        assertEquals(resp to 3, r.failure)
    }

    @Test fun nonOkStatusWithStringBodyIsSilent() {
        val r = Recorder()
        utilsWith(r).dispatch(msg(BaseHttpUtils.RESULT_FAILURE, 3, "oops"))
        utilsWith(r).dispatch(msg(BaseHttpUtils.RESULT_UNAUTHORIZED, 3, null))
        assertNull(r.success); assertNull(r.failure)
    }
}
