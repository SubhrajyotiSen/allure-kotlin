package io.qameta.allure.android.junit5.extensions

import android.util.Log
import androidx.test.uiautomator.UiDevice
import io.qameta.allure.android.junit5.internal.uiDevice
import io.qameta.allure.kotlin.Allure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Dumps window hierarchy when the test fails and adds it as an attachment to allure results.
 *
 * This is the JUnit 5 counterpart of the JUnit 4 module's `WindowHierarchyRule`;
 * declare it with [org.junit.jupiter.api.extension.ExtendWith] or register it programmatically.
 */
class WindowHierarchyExtension(private val fileName: String = "window-hierarchy") : AfterEachCallback {

    override fun afterEach(context: ExtensionContext) {
        if (context.executionException.isPresent) {
            dumpWindowHierarchy()
        }
    }

    private fun dumpWindowHierarchy() {
        val uiDevice = uiDevice ?: return Unit.also {
            Log.e(TAG, "UiAutomation is unavailable. Dumping window hierarchy failed.")
        }

        Allure.attachment(
            name = fileName,
            type = "text/xml",
            fileExtension = ".xml",
            content = uiDevice.dumpWindowHierarchy()
        )
    }

    private fun UiDevice.dumpWindowHierarchy(): ByteArrayInputStream =
        ByteArrayOutputStream()
            .apply {
                waitForIdle(TimeUnit.SECONDS.toMillis(5))
                dumpWindowHierarchy(this)
            }
            .toByteArray()
            .inputStream()

    companion object {
        private val TAG = WindowHierarchyExtension::class.java.simpleName
    }
}
