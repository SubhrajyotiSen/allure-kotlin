package io.qameta.allure.android.junit5

import io.qameta.allure.android.junit5.samples.AbortedTest
import io.qameta.allure.android.junit5.samples.BrokenTest
import io.qameta.allure.android.junit5.samples.DisabledTest
import io.qameta.allure.android.junit5.samples.DisplayNameTest
import io.qameta.allure.android.junit5.samples.FailedTest
import io.qameta.allure.android.junit5.samples.OneTest
import io.qameta.allure.android.junit5.samples.ParameterizedSample
import io.qameta.allure.android.junit5.samples.TaggedTest
import io.qameta.allure.android.junit5.samples.TestWithSteps
import io.qameta.allure.kotlin.Allure
import io.qameta.allure.kotlin.AllureLifecycle
import io.qameta.allure.kotlin.model.Stage
import io.qameta.allure.kotlin.model.Status
import io.qameta.allure.kotlin.model.TestResult
import io.qameta.allure.kotlin.test.AllureResultsWriterStub
import io.qameta.allure.kotlin.util.ResultsUtils.FRAMEWORK_LABEL_NAME
import io.qameta.allure.kotlin.util.ResultsUtils.HOST_LABEL_NAME
import io.qameta.allure.kotlin.util.ResultsUtils.LANGUAGE_LABEL_NAME
import io.qameta.allure.kotlin.util.ResultsUtils.TAG_LABEL_NAME
import io.qameta.allure.kotlin.util.ResultsUtils.THREAD_LABEL_NAME
import java.util.ServiceLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory

class AllureJunit5Test {

    private val cachedLifecycle = Allure.lifecycle

    @AfterEach
    fun restoreLifecycle() {
        Allure.lifecycle = cachedLifecycle
    }

    @Test
    fun shouldCreateTestResultForPassedTest() {
        val results = runClasses(OneTest::class.java)

        assertThat(results.testResults).hasSize(1)
        val result = results.testResults[0]
        assertThat(result.fullName)
            .isEqualTo("io.qameta.allure.android.junit5.samples.OneTest.simpleTest")
        assertThat(result.name).isEqualTo("Simple test")
        assertThat(result.description).isEqualTo("Description here")
        assertThat(result.historyId).isNotBlank()
        assertThat(result.status).isEqualTo(Status.PASSED)
        assertThat(result.stage).isEqualTo(Stage.FINISHED)
        assertThat(result.start).isNotNull()
        assertThat(result.stop).isNotNull()
    }

    @Test
    fun shouldSetExecutionLabels() {
        val results = runClasses(OneTest::class.java)

        val labelNames = results.testResults.flatMap { result -> result.labels.map { it.name } }
        assertThat(labelNames).contains(HOST_LABEL_NAME, THREAD_LABEL_NAME)
    }

    @Test
    fun shouldSetFrameworkAndLanguageLabels() {
        val results = runClasses(OneTest::class.java)

        val labels = results.testResults.flatMap { result -> result.labels }
        assertThat(labels).anySatisfy { label: io.qameta.allure.kotlin.model.Label ->
            assertThat(label.name).isEqualTo(FRAMEWORK_LABEL_NAME)
            assertThat(label.value).isEqualTo("junit5")
        }
        assertThat(labels).anySatisfy { label: io.qameta.allure.kotlin.model.Label ->
            assertThat(label.name).isEqualTo(LANGUAGE_LABEL_NAME)
            assertThat(label.value).isEqualTo("kotlin")
        }
    }

    @Test
    fun shouldSetFailedStatusForAssertions() {
        val results = runClasses(FailedTest::class.java)

        assertThat(results.testResults.map { it.status }).containsExactly(Status.FAILED)
    }

    @Test
    fun shouldSetBrokenStatus() {
        val results = runClasses(BrokenTest::class.java)

        assertThat(results.testResults.map { it.status }).containsExactly(Status.BROKEN)
        assertThat(results.testResults[0].statusDetails?.message).isEqualTo("Hello, everybody")
        assertThat(results.testResults[0].statusDetails?.trace).isNotBlank()
    }

    @Test
    fun shouldSetSkippedStatusForDisabledTest() {
        val results = runClasses(DisabledTest::class.java)

        assertThat(results.testResults.map { it.status }).containsExactly(Status.SKIPPED)
        assertThat(results.testResults[0].statusDetails?.message).isEqualTo("test disabled")
    }

    @Test
    fun shouldSetSkippedStatusForAbortedTest() {
        val results = runClasses(AbortedTest::class.java)

        assertThat(results.testResults.map { it.status }).containsExactly(Status.SKIPPED)
        assertThat(results.testResults[0].statusDetails?.message).isNotBlank()
    }

    @Test
    fun shouldSetNameFromDisplayName() {
        val results = runClasses(DisplayNameTest::class.java)

        assertThat(results.testResults.map { it.name }).containsExactly("custom name")
    }

    @Test
    fun shouldSetTagLabelForTaggedTest() {
        val results = runClasses(TaggedTest::class.java)

        val tagLabels = results.testResults
            .flatMap { result -> result.labels }
            .filter { it.name == TAG_LABEL_NAME }
        assertThat(tagLabels.map { it.value }).contains("smoke")
    }

    @Test
    fun shouldCreateTestResultForEveryParameterizedInvocation() {
        val results = runClasses(ParameterizedSample::class.java)

        assertThat(results.testResults).hasSize(2)
        assertThat(results.testResults.map { it.status }).containsOnly(Status.PASSED)
        assertThat(results.testResults.map { it.historyId }).doesNotHaveDuplicates()
        val steps = results.testResults.flatMap { result -> result.steps.map { it.name } }
        assertThat(steps).containsExactlyInAnyOrder("step for first", "step for second")
    }

    @Test
    fun shouldProcessStepsAndAttachments() {
        val results = runClasses(TestWithSteps::class.java)

        assertThat(results.testResults).hasSize(1)
        assertThat(results.testResults[0].steps.map { it.name })
            .containsExactly("step1", "step2")
        assertThat(results.attachments).hasSize(1)
    }

    @Test
    fun shouldBeRegisteredThroughServiceLoader() {
        val discovered =
            ServiceLoader.load(TestExecutionListener::class.java).filterIsInstance<AllureJunit5>()

        assertThat(discovered).isNotEmpty
    }

    private fun runClasses(vararg classes: Class<*>): AllureResultsWriterStub {
        val writer = AllureResultsWriterStub()
        Allure.lifecycle = AllureLifecycle(writer)
        val request = LauncherDiscoveryRequestBuilder.request()
            .selectors(classes.map { DiscoverySelectors.selectClass(it) })
            .build()
        // AllureJunit5 is picked up through its ServiceLoader registration, exactly as in a
        // consumer's JUnit Platform run; on the JVM it keeps the global lifecycle set above.
        LauncherFactory.create().execute(request)
        return writer
    }
}
