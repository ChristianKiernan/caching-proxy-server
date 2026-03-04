package com.christiankiernan.cachingproxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProxyCliCommandTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void clearCacheWithoutCacheFilePrintsError() {
        new CommandLine(new ProxyCliCommand()).execute("--clear-cache");
        assertTrue(errContent.toString().contains("--cache-file is required"));
    }

    @Test
    void clearCacheFileDoesNotExistPrintsAlreadyEmpty() {
        Path missing = tempDir.resolve("missing.dat");
        new CommandLine(new ProxyCliCommand()).execute("--clear-cache", "--cache-file", missing.toString());
        assertTrue(outContent.toString().contains("Cache already empty"));
    }

    @Test
    void clearCacheDeletesFileAndPrintsSuccess() throws Exception {
        Path cacheFile = tempDir.resolve("cache.dat");
        Files.writeString(cacheFile, "dummy");
        new CommandLine(new ProxyCliCommand()).execute("--clear-cache", "--cache-file", cacheFile.toString());
        assertFalse(Files.exists(cacheFile));
        assertTrue(outContent.toString().contains("Cache cleared"));
    }

    @Test
    void invalidTimeoutPrintsError() {
        new CommandLine(new ProxyCliCommand()).execute("--port", "3000", "--origin", "http://example.com", "--timeout", "0");
        assertTrue(errContent.toString().contains("--timeout must be a positive integer"));
    }
}
