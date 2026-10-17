package com.example.nozapret

import com.example.nozapret.core.Config
import org.junit.Assert.*
import org.junit.Test

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
        val ytArgs = Config.getStrategyArgs("YouTube/Google Fix")
        val discordArgs = Config.getStrategyArgs("Discord/UDP Fix")
        assertFalse(ytArgs.contentEquals(discordArgs))
        assertTrue(ytArgs.contains("--block-quic"))
        assertFalse(discordArgs.contains("--block-quic"))
    }

    @Test
    fun testFakeSniSupportForCustomStrategies() {
        assertTrue(Config.supportsFakeSni("Auto (Recommended)"))
        assertTrue(Config.supportsFakeSni("My User Custom Strategy"))
    }
}
