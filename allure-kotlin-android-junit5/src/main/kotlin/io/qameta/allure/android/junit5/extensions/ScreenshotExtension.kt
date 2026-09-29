package io.qameta.allure.android.junit5.extensions

import io.qameta.allure.android.junit5.allureScreenshot
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Makes a screenshot of a device upon an end of a test case based on the specified [mode].
 * It is then added as an attachment of a test case (with name [screenshotName]).
 *
 * By default, it will take a screenshot at the end of failed test case.
 *
 * This is the JUnit 5 counterpart of the JUnit 4 module's `ScreenshotRule`;
 * declare it with [org.junit.jupiter.api.extension.ExtendWith] or register it programmatically.
 */
class ScreenshotExtension(
    private val mode: Mode = Mode.FAILURE,
    private val screenshotName: String = "failure-screenshot"
) : AfterEachCallback {

    override fun afterEach(context: ExtensionContext) {
        val failed = context.executionException.isPresent
        val canTakeScreenshot = when (mode) {
            Mode.END -> true
            Mode.SUCCESS -> !failed
            Mode.FAILURE -> failed
        }
        if (canTakeScreenshot) {
            allureScreenshot(screenshotName)
        }
    }

    enum class Mode {
        END,
        SUCCESS,
        FAILURE
    }
}
