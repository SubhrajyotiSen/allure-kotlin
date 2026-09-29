package io.qameta.allure.android.junit5

import io.qameta.allure.android.junit5.internal.isDeviceTest
import io.qameta.allure.android.junit5.internal.useTestStorage
import io.qameta.allure.android.junit5.writer.TestStorageResultsWriter
import io.qameta.allure.kotlin.Allure
import io.qameta.allure.kotlin.AllureLifecycle
import io.qameta.allure.kotlin.Description
import io.qameta.allure.kotlin.model.Label
import io.qameta.allure.kotlin.model.Link
import io.qameta.allure.kotlin.model.Status
import io.qameta.allure.kotlin.model.StatusDetails
import io.qameta.allure.kotlin.model.TestResult
import io.qameta.allure.kotlin.util.AnnotationUtils.getLabels
import io.qameta.allure.kotlin.util.AnnotationUtils.getLinks
import io.qameta.allure.kotlin.util.ResultsUtils.createFrameworkLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createHostLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createLanguageLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createPackageLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createSuiteLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createTagLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createTestClassLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createTestMethodLabel
import io.qameta.allure.kotlin.util.ResultsUtils.createThreadLabel
import io.qameta.allure.kotlin.util.ResultsUtils.getProvidedLabels
import io.qameta.allure.kotlin.util.ResultsUtils.getStatus
import io.qameta.allure.kotlin.util.ResultsUtils.getStatusDetails
import io.qameta.allure.kotlin.util.ResultsUtils.md5
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.MethodSource
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan

/**
 * Allure JUnit 5 listener, the counterpart of [io.qameta.allure.kotlin.junit4.AllureJunit4]
 * for the JUnit Platform: it bridges JUnit Platform execution events to the Allure lifecycle and
 * works with any engine, including Jupiter, its parameterized and `@TestFactory` tests, and
 * JUnit 4 tests running through the vintage engine (e.g. Robolectric).
 *
 * The listener registers itself through the Java Service Loader
 * (`META-INF/services/org.junit.platform.launcher.TestExecutionListener`), so every JUnit Platform
 * based run reports to Allure without any custom runner: Gradle's `useJUnitPlatform()` on the JVM,
 * or on-device instrumentation tests through de.mannodermaus' JUnit 5 instrumentation support,
 * whose runner creates the launcher with the default Service Loader discovery.
 *
 * On a device the listener owns the global [Allure.lifecycle], replacing it with an
 * [AllureAndroidLifecycle] at the start of the test plan (the JUnit 4 module did the same in its
 * `AllureAndroidJUnitRunner`). Results then go to the instrumentation target's files dir, or to
 * androidx.test.services Test Storage when **allure.results.useTestStorage** is set to `true`.
 * A host that installs its own lifecycle before the plan starts keeps the file-system default
 * writer, so such hosts should either set that property to match their writer or use the
 * [TestStorageResultsWriter]-based lifecycle from this module. On the JVM the global lifecycle is
 * left untouched and results go to the directory configured through **allure.results.directory**.
 *
 * Each test, and each invocation of a parameterized or dynamic test, becomes its own Allure test
 * result with a history id derived from the platform's unique id, so repeated runs and retries
 * deduplicate correctly in the report.
 */
class AllureJunit5 internal constructor(
    private val lifecycleProvider: () -> AllureLifecycle
) : TestExecutionListener {

    constructor() : this({ Allure.lifecycle })

    private val testCases = ConcurrentHashMap<String, String>()

    override fun testPlanExecutionStarted(testPlan: TestPlan) {
        installAllureAndroidLifecycleIfRequired()
    }

    override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
        if (!testIdentifier.isTest) return
        val lifecycle = lifecycleProvider()
        val uuid = UUID.randomUUID().toString()
        val result = createTestResult(uuid, testIdentifier).apply {
            status = Status.SKIPPED
            statusDetails = StatusDetails(message = reason.ifBlank { "Test skipped (without reason)!" })
            start = System.currentTimeMillis()
        }
        lifecycle.scheduleTestCase(result)
        lifecycle.stopTestCase(uuid)
        lifecycle.writeTestCase(uuid)
    }

    override fun executionStarted(testIdentifier: TestIdentifier) {
        if (!testIdentifier.isTest) return
        val lifecycle = lifecycleProvider()
        val uuid = UUID.randomUUID().toString()
        testCases[testIdentifier.uniqueId] = uuid
        lifecycle.scheduleTestCase(createTestResult(uuid, testIdentifier))
        lifecycle.startTestCase(uuid)
    }

    override fun executionFinished(
        testIdentifier: TestIdentifier,
        testExecutionResult: TestExecutionResult
    ) {
        if (!testIdentifier.isTest) return
        val uuid = testCases.remove(testIdentifier.uniqueId) ?: return
        val throwable = testExecutionResult.throwable.orElse(null)
        val lifecycle = lifecycleProvider()
        lifecycle.updateTestCase(uuid) { testResult: TestResult ->
            testResult.status = when (testExecutionResult.status) {
                TestExecutionResult.Status.SUCCESSFUL -> Status.PASSED
                TestExecutionResult.Status.ABORTED -> Status.SKIPPED
                TestExecutionResult.Status.FAILED -> getStatus(throwable) ?: Status.BROKEN
            }
            if (throwable != null) {
                getStatusDetails(throwable)?.let { testResult.statusDetails = it }
            }
        }
        lifecycle.stopTestCase(uuid)
        lifecycle.writeTestCase(uuid)
    }

    /**
     * Same ownership rule as the JUnit 4 module's device runners: on a device this module owns
     * the global lifecycle, unless one of its own lifecycles is already installed.
     */
    private fun installAllureAndroidLifecycleIfRequired() {
        if (!isDeviceTest()) return
        if (Allure.lifecycle is AllureAndroidLifecycle) return
        Allure.lifecycle = createAllureAndroidLifecycle()
    }

    private fun createAllureAndroidLifecycle(): AllureAndroidLifecycle =
        if (useTestStorage) {
            AllureAndroidLifecycle(TestStorageResultsWriter())
        } else {
            AllureAndroidLifecycle()
        }

    private fun createTestResult(uuid: String, testIdentifier: TestIdentifier): TestResult {
        val methodSource = testIdentifier.source.orElse(null) as? MethodSource
        val classSource = testIdentifier.source.orElse(null) as? ClassSource
        val className = methodSource?.className
            ?: classSource?.className
            ?: testIdentifier.parentId.orElse(UNKNOWN_NAME)
        val methodName = methodSource?.methodName
        val fullName = if (methodName != null) "$className.$methodName" else className
        val testClass = classSource?.javaClass ?: resolveClass(methodSource?.className)
        val testMethod: Method? = methodName?.let { findTestMethod(testClass, it) }

        val labels = ArrayList<Label>(getProvidedLabels())
        labels.addAll(
            listOf(
                createPackageLabel(className.substringBeforeLast('.', "")),
                createTestClassLabel(className),
                createTestMethodLabel(methodName ?: testIdentifier.displayName),
                createSuiteLabel(className),
                createHostLabel(),
                createThreadLabel(),
                createFrameworkLabel("junit5"),
                createLanguageLabel("kotlin")
            )
        )
        testClass?.let { labels.addAll(getLabels(it)) }
        testMethod?.let { labels.addAll(getLabels(it)) }
        labels.addAll(testIdentifier.tags.map { createTagLabel(it.name) })

        val links = ArrayList<Link>()
        testClass?.let { links.addAll(getLinks(it)) }
        testMethod?.let { links.addAll(getLinks(it)) }

        return TestResult(uuid).apply {
            historyId = md5(testIdentifier.uniqueId)
            this.fullName = fullName
            name = testIdentifier.displayName
            this.labels.addAll(labels)
            this.links.addAll(links)
            testMethod?.getAnnotation(Description::class.java)?.let { description = it.value }
        }
    }

    /**
     * Resolves the test class of a method-based identifier reflectively: unlike the newer
     * `MethodSource.getJavaMethod()`, this works with every JUnit Platform version this module
     * compiles against and runs on. Overloads are matched by name only.
     */
    private fun resolveClass(className: String?): Class<*>? = className?.let {
        runCatching { Class.forName(it) }.getOrNull()
    }

    private fun findTestMethod(testClass: Class<*>?, methodName: String): Method? =
        testClass?.methods?.firstOrNull { it.name == methodName }

    private companion object {
        private const val UNKNOWN_NAME = "unknown"
    }
}
