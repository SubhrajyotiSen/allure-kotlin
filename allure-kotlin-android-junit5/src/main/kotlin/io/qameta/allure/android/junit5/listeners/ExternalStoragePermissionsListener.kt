package io.qameta.allure.android.junit5.listeners

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.qameta.allure.android.junit5.internal.isDeviceTest
import io.qameta.allure.android.junit5.internal.useTestStorage
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan

/**
 * Grants the external storage permissions required by androidx.test.services
 * (used by [androidx.test.services.storage.TestStorage]) to write test results,
 * the JUnit 5 counterpart of the JUnit 4 module's RunListener with the same name.
 *
 * Registered through the Service Loader together with [io.qameta.allure.android.junit5.AllureJunit5];
 * it does nothing unless the run happens on a device with **allure.results.useTestStorage** enabled,
 * which is the same condition under which the JUnit 4 runners attached it.
 */
class ExternalStoragePermissionsListener : TestExecutionListener {

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        if (!isDeviceTest() || !useTestStorage) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.apply {
            val testServicesPackage = "androidx.test.services"
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    executeShellCommand("appops set $testServicesPackage MANAGE_EXTERNAL_STORAGE allow")
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                    executeShellCommand("pm grant $testServicesPackage android.permission.READ_EXTERNAL_STORAGE")
                    executeShellCommand("pm grant $testServicesPackage android.permission.WRITE_EXTERNAL_STORAGE")
                }
            }
        }
    }
}
