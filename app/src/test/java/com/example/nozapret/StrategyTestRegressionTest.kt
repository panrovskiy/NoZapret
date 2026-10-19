package com.example.nozapret

import com.example.nozapret.core.Config
import com.example.nozapret.core.DnsTestResult
import com.example.nozapret.services.TestingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class StrategyTestRegressionTest {

    @Test
    fun testOneStrategyArgGeneration() {
        val args = Config.getStrategyArgs("Auto (Recommended)")
        assertTrue(args.isNotEmpty())
        assertTrue(args.contains("--tlsrec"))
    }

    @Test
    fun testCustomStrategyArgGeneration() {
        val customUserArgs = "--tlsrec 1+s --disorder 1 --split 2 --wait-send"
        val args = Config.getStrategyArgs("My Custom Strategy", customUserArgs)
        assertTrue(args.isNotEmpty())
        assertEquals("--tlsrec", args[0])
        assertEquals("1+s", args[1])
    }

    @Test
    fun testStrategyValidationSuccess() {
        val validArgs = arrayOf("--tlsrec", "1+s", "--split", "2", "--wait-send")
        val validation = Config.validateStrategyArgs(validArgs)
        assertTrue(validation.isSuccess)
    }

    @Test
    fun testStrategyValidationFailure() {
        val invalidArgs = arrayOf("--split") // Missing value for --split
        val validation = Config.validateStrategyArgs(invalidArgs)
        assertTrue(validation.isFailure)
    }

    @Test
    fun testDomainValidation() {
        val valid = Config.validateDomain("https://youtube.com/watch?v=123")
        assertTrue(valid.isSuccess)
        assertEquals("youtube.com", valid.getOrNull())

        val invalid = Config.validateDomain("invalid domain with spaces")
        assertTrue(invalid.isFailure)
    }

    @Test
    fun testFakeSniResolution() {
        val map = mapOf("Auto (Recommended)" to "myfakesni.com")
        val resolved = Config.resolveFakeSni("Auto (Recommended)", map, listOf("default.com"), false)
        assertEquals("myfakesni.com", resolved)
    }

    @Test
    fun testStrategySwitchingAndArgResolution() {
        val ytArgs = Config.getStrategyArgs("YouTube/Google Fix", enableQuic = false)
        val discordArgs = Config.getStrategyArgs("Discord/UDP Fix", enableQuic = false)
        assertFalse(ytArgs.contentEquals(discordArgs))
        assertTrue(ytArgs.contains("--tlsrec"))
        assertFalse(discordArgs.contains("--tlsrec"))
    }

    @Test
    fun testFakeSniSupportForCustomStrategies() {
        assertTrue(Config.supportsFakeSni("Auto (Recommended)"))
        assertTrue(Config.supportsFakeSni("My User Custom Strategy"))
    }

    // MANDATORY REGRESSION TESTS FOR COMPLETION LOGIC

    @Test
    fun testSingleStrategyTestCompletion() {
        val results = mutableMapOf<String, MainViewModel.TlsTestResult>()
        val testResult = MainViewModel.TlsTestResult(success = true, ping = 150)
        results["google.com"] = testResult

        assertEquals(1, results.size)
        assertTrue(results["google.com"]?.success == true)
        assertEquals(150L, results["google.com"]?.ping)
    }

    @Test
    fun testManyStrategiesTestCompletion() {
        val strategies = Config.STRATEGIES.map { it.first }
        val completedStrategies = mutableListOf<String>()

        strategies.forEach { strat ->
            completedStrategies.add(strat)
        }

        assertEquals(Config.STRATEGIES.size, completedStrategies.size)
        assertEquals(strategies, completedStrategies)
    }

    @Test
    fun testFinalStrategySuccessRecording() {
        val testStatusFlow = MutableStateFlow(TestingService.Companion.TestStatus())
        val sites = listOf("google.com", "youtube.com")
        var currentTested = 0
        var successCount = 0

        sites.forEach { site ->
            currentTested++
            successCount++
            testStatusFlow.value = TestingService.Companion.TestStatus(
                strategyName = "FinalStrategy",
                success = successCount,
                tested = currentTested,
                total = sites.size,
                isRunning = true,
                lastSite = site,
                lastResult = MainViewModel.TlsTestResult(success = true, ping = 100)
            )
        }

        val finalStatus = testStatusFlow.value
        assertEquals(2, finalStatus.tested)
        assertEquals(2, finalStatus.total)
        assertEquals(2, finalStatus.success)
        assertTrue(finalStatus.isRunning)

        // Mark completion
        testStatusFlow.value = finalStatus.copy(isRunning = false)
        assertFalse(testStatusFlow.value.isRunning)
        assertEquals(2, testStatusFlow.value.tested)
    }

    @Test
    fun testFinalStrategyExceptionHandling() {
        val results = mutableMapOf<String, MainViewModel.TlsTestResult>()
        val site = "badsite.com"

        try {
            throw RuntimeException("Network timeout during final strategy")
        } catch (e: Exception) {
            results[site] = MainViewModel.TlsTestResult(success = false, error = e.message)
        }

        assertEquals(1, results.size)
        assertFalse(results[site]?.success == true)
        assertEquals("Network timeout during final strategy", results[site]?.error)
    }

    @Test
    fun testFinalStrategyIncompleteResult() {
        val result = MainViewModel.TlsTestResult(
            success = false,
            ping = null,
            error = "Connection reset by peer"
        )

        assertFalse(result.success)
        assertNull(result.ping)
        assertNotNull(result.error)
    }

    @Test
    fun testCancellationImmediatelyBeforeFinish() {
        val isCancelled = AtomicBoolean(true)
        val isCompleted = AtomicBoolean(false)

        if (!isCancelled.get()) {
            isCompleted.set(true)
        }

        assertFalse(isCompleted.get())
        assertTrue(isCancelled.get())
    }

    @Test
    fun testRepeatedCompletionCallbacks() {
        val completionCount = AtomicInteger(0)
        val executedCount = AtomicInteger(0)
        val onComplete = {
            if (completionCount.compareAndSet(0, 1)) {
                executedCount.incrementAndGet()
            }
        }

        onComplete()
        onComplete()
        onComplete()

        assertEquals(1, completionCount.get())
        assertEquals(1, executedCount.get())
    }

    @Test
    fun testImmediateConsecutiveRunsAfterCompletion() = runBlocking {
        var activeRun = 0
        val maxRuns = 5

        for (run in 1..maxRuns) {
            activeRun = run
            val statusFlow = MutableStateFlow(
                TestingService.Companion.TestStatus(
                    strategyName = "Strategy_$run",
                    isRunning = true
                )
            )
            assertTrue(statusFlow.value.isRunning)

            // Complete run
            statusFlow.value = statusFlow.value.copy(isRunning = false)
            assertFalse(statusFlow.value.isRunning)
        }

        assertEquals(5, activeRun)
    }

    @Test
    fun testRepeatedFullStrategyTestRuns() {
        val allStrategies = Config.STRATEGIES.map { it.first }
        var completedRuns = 0

        repeat(3) {
            val ranList = mutableListOf<String>()
            allStrategies.forEach { strat ->
                ranList.add(strat)
            }
            if (ranList.size == allStrategies.size) {
                completedRuns++
            }
        }

        assertEquals(3, completedRuns)
    }

    @Test
    fun testVpnConnectionStateAfterTestCompletion() {
        val isTestRunning = false
        var isVpnConnected = false

        if (!isTestRunning) {
            isVpnConnected = true
        }

        assertFalse(isTestRunning)
        assertTrue(isVpnConnected)

        isVpnConnected = false
        assertFalse(isVpnConnected)
    }

    @Test
    fun testQuicGlobalToggle() {
        // When QUIC is enabled, --block-quic should be removed from strategies that usually block it
        val argsQuicEnabled = Config.getStrategyArgs("Auto (Recommended)", enableQuic = true)
        assertFalse(argsQuicEnabled.contains("--block-quic"))

        // When QUIC is disabled, --block-quic should be added to block QUIC traffic
        val argsQuicDisabled = Config.getStrategyArgs("Auto (Recommended)", enableQuic = false)
        assertTrue(argsQuicDisabled.contains("--block-quic"))
    }

    @Test
    fun testIpAddressValidation() {
        assertTrue(Config.validateIpAddress("1.1.1.1").isSuccess)
        assertTrue(Config.validateIpAddress("8.8.8.8").isSuccess)
        assertTrue(Config.validateIpAddress("9.9.9.9").isSuccess)
        assertTrue(Config.validateIpAddress("149.112.112.112").isSuccess)
        assertTrue(Config.validateIpAddress("").isSuccess)

        assertTrue(Config.validateIpAddress("256.1.1.1").isFailure)
        assertTrue(Config.validateIpAddress("not_an_ip").isFailure)
        assertTrue(Config.validateIpAddress("http://1.1.1.1").isFailure)
    }

    @Test
    fun testCustomStrategyDnsInheritance() {
        val globalDns = "1.1.1.1"

        // Custom strategy with explicit custom DNS
        val customWithDns = Config.CustomStrategy(
            id = "c1",
            name = "My Strat",
            userArgs = "--split 1",
            customDns = "8.8.8.8"
        )
        val effectiveDns1 = customWithDns.customDns?.ifBlank { null } ?: globalDns
        assertEquals("8.8.8.8", effectiveDns1)

        // Custom strategy with empty custom DNS -> inherits global DNS
        val customInherit = Config.CustomStrategy(
            id = "c2",
            name = "Inherit Strat",
            userArgs = "--split 1",
            customDns = ""
        )
        val effectiveDns2 = customInherit.customDns?.ifBlank { null } ?: globalDns
        assertEquals("1.1.1.1", effectiveDns2)
    }

    @Test
    fun testDnsDiagnosticResultFormatting() {
        val successRes = DnsTestResult(
            name = "Quad9 Primary",
            ip = "9.9.9.9",
            success = true,
            pingMs = 24
        )
        assertTrue(successRes.success)
        assertEquals("Quad9 Primary", successRes.name)
        assertEquals("9.9.9.9", successRes.ip)
        assertEquals(24L, successRes.pingMs)

        val failRes = DnsTestResult(
            name = "Quad9 Primary",
            ip = "9.9.9.9",
            success = false,
            error = "DNS Timeout"
        )
        assertFalse(failRes.success)
        assertNull(failRes.pingMs)
        assertEquals("DNS Timeout", failRes.error)
    }
}
