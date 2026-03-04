package com.christiankiernan.cachingproxy;

import picocli.CommandLine;

/**
 * Entry point for the caching proxy CLI application.
 *
 * <p>Delegates argument parsing and execution to {@link ProxyCliCommand} via Picocli.
 */
public class Main {

    /**
     * Parses CLI arguments and runs the proxy command, then exits with the returned status code.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ProxyCliCommand()).execute(args);
        System.exit(exitCode);
    }
}
