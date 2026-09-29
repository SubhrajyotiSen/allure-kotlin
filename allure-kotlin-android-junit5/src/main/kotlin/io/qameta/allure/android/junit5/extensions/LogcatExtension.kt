package io.qameta.allure.android.junit5.extensions

import android.os.Build
import android.util.Log
import androidx.test.uiautomator.UiDevice
import io.qameta.allure.android.junit5.internal.uiDevice
import io.qameta.allure.kotlin.Allure
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Clears logcat before each test and dumps the logcat as an attachment after test failure.
 *
 * Available since API 21, no effect for other versions.
 *
 * This is the JUnit 5 counterpart of the JUnit 4 module's `LogcatRule`;
 * declare it with [org.junit.jupiter.api.extension.ExtendWith] or register it programmatically.
 */
class LogcatExtension(private val fileName: String = "logcat-dump") : BeforeEachCallback, AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        clear()
    }

    override fun afterEach(context: ExtensionContext) {
        if (context.executionException.isPresent) {
            dump()
        }
    }

    private fun clear() {
        val uiDevice = uiDevice ?: return Unit.also {
            Log.e(TAG, "UiDevice is unavailable. Clearing logs failed.")
        }
        uiDevice.executeShellCommandSafely("logcat -c")
    }

    private fun dump() {
        val uiDevice = uiDevice ?: return Unit.also {
            Log.e(TAG, "UiDevice is unavailable. Dumping logs failed.")
        }
        val output = uiDevice.executeShellCommandSafely("logcat -d") ?: return
        Allure.attachment(
            name = fileName,
            content = output,
            type = "text/plain",
            fileExtension = ".txt"
        )
    }

    private fun UiDevice.executeShellCommandSafely(cmd: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        return executeShellCommand(cmd)
    }

    companion object {
        private val TAG = LogcatExtension::class.java.simpleName
    }
}
