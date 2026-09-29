package io.qameta.allure.android.junit5.samples

import io.qameta.allure.kotlin.Allure
import io.qameta.allure.kotlin.Description
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Sample test classes, executed only through the Allure JUnit 5 listener under test
 * (see [io.qameta.allure.android.junit5.AllureJunit5Test]). The `allure-sample` tag keeps them
 * out of the module's own test task.
 */
@Tag("allure-sample")
class OneTest {

    @Test
    @DisplayName("Simple test")
    @Description("Description here")
    fun simpleTest() {
    }
}

@Tag("allure-sample")
class FailedTest {

    @Test
    fun failedTest() {
        throw AssertionError("test failed")
    }
}

@Tag("allure-sample")
class BrokenTest {

    @Test
    fun brokenTest() {
        throw RuntimeException("Hello, everybody")
    }
}

@Tag("allure-sample")
class DisabledTest {

    @Test
    @Disabled("test disabled")
    fun disabledTest() = Unit
}

@Tag("allure-sample")
class AbortedTest {

    @Test
    fun abortedTest() {
        assumeTrue(false)
    }
}

@Tag("allure-sample")
class DisplayNameTest {

    @Test
    @DisplayName("custom name")
    fun someTest() {
    }
}

@Tag("allure-sample")
class TaggedTest {

    @Test
    @Tag("smoke")
    fun taggedTest() {
    }
}

@Tag("allure-sample")
class ParameterizedSample {

    @ParameterizedTest
    @ValueSource(strings = ["first", "second"])
    fun parameterized(value: String) {
        Allure.step("step for $value")
    }
}

@Tag("allure-sample")
class TestWithSteps {

    @Test
    fun testWithSteps() {
        Allure.step("step1")
        Allure.step("step2")
        Allure.attachment("text", "content")
    }
}
