/*
 * Copyright (c) 2020-2026 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.jtasklet.provider.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNull;

public class VNMIFERuntimeEnvironment extends AbstractRuntimeEnvironment {
    public static final String BINARY_PROPERTY = "jtasklet.vnmife.binary";
    public static final String BINARY_ENV = "JTASKLET_VNMIFE_BINARY";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static volatile String extractedBinary;

    private final String binary;

    public VNMIFERuntimeEnvironment() {
        this(resolveBinary());
    }

    public VNMIFERuntimeEnvironment(final String binary) {
        this.binary = requireNonNull(binary);
    }

    public String setup(final Path authority,
                        final String clientId,
                        final int vecLen,
                        final int boundX,
                        final int boundY,
                        final int boundN) {
        try {
            Files.createDirectories(authority.toAbsolutePath().getParent());
            final Path clientKeyOut = Files.createTempFile("vnmife-client-key-", ".json");
            try {
                final List<String> command = new ArrayList<>();
                command.add(binary);
                command.add("setup");
                command.add("--authority");
                command.add(authority.toAbsolutePath().toString());
                command.add("--client-id");
                command.add(clientId);
                command.add("--client-key-out");
                command.add(clientKeyOut.toAbsolutePath().toString());
                if (Files.notExists(authority)) {
                    command.add("--vec-len");
                    command.add(String.valueOf(vecLen));
                    command.add("--bound-x");
                    command.add(String.valueOf(boundX));
                    command.add("--bound-y");
                    command.add(String.valueOf(boundY));
                    command.add("--bound-n");
                    command.add(String.valueOf(boundN));
                }
                executeProcess(command);
                return Files.readString(clientKeyOut, UTF_8);
            }
            finally {
                Files.deleteIfExists(clientKeyOut);
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to execute VNMIFE setup.", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing VNMIFE setup.", e);
        }
    }

    public String encrypt(final String clientKey,
                          final List<String> clientIds,
                          final String session,
                          final List<Integer> x,
                          final String existingCiphertexts) {
        try {
            final Path clientKeyPath = Files.createTempFile("vnmife-client-key-", ".json");
            final Path ciphertextsPath;
            final boolean deleteParentDirectory;
            if (existingCiphertexts != null && !existingCiphertexts.isBlank()) {
                ciphertextsPath = Files.createTempFile("vnmife-ciphertexts-", ".json");
                deleteParentDirectory = false;
            }
            else {
                final Path tempDir = Files.createTempDirectory("vnmife-ciphertexts-");
                ciphertextsPath = tempDir.resolve("ciphertexts.json");
                deleteParentDirectory = true;
            }
            try {
                Files.writeString(clientKeyPath, clientKey, UTF_8);
                if (existingCiphertexts != null && !existingCiphertexts.isBlank()) {
                    Files.writeString(ciphertextsPath, existingCiphertexts, UTF_8);
                }

                final List<String> command = new ArrayList<>();
                command.add(binary);
                command.add("encrypt");
                command.add("--client-key");
                command.add(clientKeyPath.toAbsolutePath().toString());
                command.add("--client-ids");
                command.add(OBJECT_MAPPER.writeValueAsString(clientIds));
                command.add("--session");
                command.add(session);
                command.add("--x");
                command.add(OBJECT_MAPPER.writeValueAsString(x));
                command.add("--out");
                command.add(ciphertextsPath.toAbsolutePath().toString());
                executeProcess(command);
                return Files.readString(ciphertextsPath, UTF_8);
            }
            finally {
                Files.deleteIfExists(clientKeyPath);
                Files.deleteIfExists(ciphertextsPath);
                final Path parent = ciphertextsPath.getParent();
                if (deleteParentDirectory && parent != null) {
                    Files.deleteIfExists(parent);
                }
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to execute VNMIFE encrypt.", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing VNMIFE encrypt.", e);
        }
    }

    public String deriveKey(final Path authority,
                            final List<String> clientIds,
                            final String session,
                            final Object weights) {
        try {
            final Path weightsPath = Files.createTempFile("vnmife-weights-", ".json");
            final Path functionalKeyPath = Files.createTempFile("vnmife-functional-key-", ".json");
            try {
                Files.writeString(weightsPath, serializeArgument(weights), UTF_8);
                final List<String> command = new ArrayList<>();
                command.add(binary);
                command.add("derive-key");
                command.add("--authority");
                command.add(authority.toAbsolutePath().toString());
                command.add("--client-ids");
                command.add(OBJECT_MAPPER.writeValueAsString(clientIds));
                command.add("--session");
                command.add(session);
                command.add("--weights-file");
                command.add(weightsPath.toAbsolutePath().toString());
                command.add("--out");
                command.add(functionalKeyPath.toAbsolutePath().toString());
                executeProcess(command);
                return Files.readString(functionalKeyPath, UTF_8);
            }
            finally {
                Files.deleteIfExists(weightsPath);
                Files.deleteIfExists(functionalKeyPath);
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException(describeProcessStartFailure("derive-key", e), e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing VNMIFE derive-key.", e);
        }
    }

    @Override
    public ExecutionResult decrypt(final String ciphertexts,
                                   final String functionalKey,
                                   final Object weights) {
        final Instant start = Instant.now();
        try {
            final Path ciphertextsPath = Files.createTempFile("vnmife-ciphertexts-", ".json");
            final Path functionalKeyPath = Files.createTempFile("vnmife-functional-key-", ".json");
            final Path weightsPath = Files.createTempFile("vnmife-weights-", ".json");
            try {
                Files.writeString(ciphertextsPath, ciphertexts, UTF_8);
                Files.writeString(functionalKeyPath, functionalKey, UTF_8);
                Files.writeString(weightsPath, serializeArgument(weights), UTF_8);
                final List<String> command = new ArrayList<>();
                command.add(binary);
                command.add("decrypt");
                command.add("--ciphertexts");
                command.add(ciphertextsPath.toAbsolutePath().toString());
                command.add("--functional-key");
                command.add(functionalKeyPath.toAbsolutePath().toString());
                command.add("--weights-file");
                command.add(weightsPath.toAbsolutePath().toString());
                final ProcessResult result = executeProcess(command);
                final Instant end = Instant.now();
                return new ExecutionResult(parseOutput(result.stdout), Duration.between(start, end).toMillis());
            }
            finally {
                Files.deleteIfExists(ciphertextsPath);
                Files.deleteIfExists(functionalKeyPath);
                Files.deleteIfExists(weightsPath);
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException(describeProcessStartFailure("decrypt", e), e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while executing VNMIFE decrypt.", e);
        }
    }

    @Override
    public ExecutionResult execute(final CharSequence source, final Object... input) {
        return executeInternal(source == null ? null : source.toString(), input);
    }

    @Override
    public ExecutionResult executeEncrypted(final String key, final String encryptedInput) {
        throw new UnsupportedOperationException("VNMIFE now uses decrypt() for ciphertext bundles.");
    }

    @Override
    public ExecutionResult execute(final Path source, final Object... input) {
        return executeInternal(source == null ? null : source.toAbsolutePath().toString(), input);
    }

    @Override
    public ExecutionResult execute(final InputStream source, final Object... input) {
        if (source == null) {
            return executeInternal(null, input);
        }

        try {
            final Path tempFile = Files.createTempFile("vnmife-input-", ".json");
            Files.copy(source, tempFile, REPLACE_EXISTING);
            try {
                return executeInternal(tempFile.toAbsolutePath().toString(), input);
            }
            finally {
                Files.deleteIfExists(tempFile);
            }
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to materialize VNMIFE input stream.", e);
        }
    }

    private ExecutionResult executeInternal(final String sourceArgument, final Object... input) {
        final Instant start = Instant.now();
        try {
            final ProcessResult process = executeProcess(buildCommand(sourceArgument, input));
            final Instant end = Instant.now();

            return new ExecutionResult(parseOutput(process.stdout), Duration.between(start, end).toMillis());
        }
        catch (final IOException e) {
            throw new IllegalStateException("Unable to execute VNMIFE binary `" + binary + "`.", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for VNMIFE binary `" + binary + "`.", e);
        }
    }

    private List<String> buildCommand(final String sourceArgument, final Object... input) {
        final List<String> command = new ArrayList<>();
        command.add(binary);
        if (sourceArgument != null && !sourceArgument.isBlank()) {
            command.add(sourceArgument);
        }
        for (final Object value : input) {
            command.add(serializeArgument(value));
        }
        return command;
    }

    private ProcessResult executeProcess(final List<String> command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        final String stdout;
        final String stderr;
        try (InputStream stdoutStream = process.getInputStream();
             InputStream stderrStream = process.getErrorStream()) {
            stdout = new String(stdoutStream.readAllBytes(), UTF_8).trim();
            stderr = new String(stderrStream.readAllBytes(), UTF_8).trim();
        }

        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("VNMIFE binary exited with code " + exitCode + (stderr.isEmpty() ? "" : ": " + stderr));
        }

        return new ProcessResult(stdout, stderr);
    }

    private String serializeArgument(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        }
        catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize VNMIFE argument `" + value + "`.", e);
        }
    }

    private String describeProcessStartFailure(final String subcommand,
                                               final IOException e) {
        final String message = e.getMessage();
        if (message != null && message.contains("Argument list too long")) {
            return "Unable to execute VNMIFE " + subcommand + ": the current CLI passes large JSON payloads via command-line arguments, but the operating system rejected the process start with `Argument list too long`. The VNMIFE binary needs file-based arguments (for example `--weights-file` / `--x-file`) for large inputs.";
        }
        return "Unable to execute VNMIFE " + subcommand + ".";
    }

    private Object[] parseOutput(final String stdout) throws JsonProcessingException {
        if (stdout.isEmpty()) {
            return new Object[0];
        }

        try {
            final Object parsed = OBJECT_MAPPER.readValue(stdout, Object.class);
            if (parsed instanceof List) {
                return ((List<?>) parsed).toArray(new Object[0]);
            }
            return new Object[]{ parsed };
        }
        catch (final JsonProcessingException ignored) {
            return new Object[]{ stdout };
        }
    }

    private static String resolveBinary() {
        final String propertyValue = System.getProperty(BINARY_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        final String envValue = System.getenv(BINARY_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return extractBundledBinary();
    }

    private static String extractBundledBinary() {
        if (extractedBinary != null) {
            return extractedBinary;
        }

        synchronized (VNMIFERuntimeEnvironment.class) {
            if (extractedBinary != null) {
                return extractedBinary;
            }

            final String resourceName = "/vnmife/" + detectBinaryName();
            try (InputStream inputStream = VNMIFERuntimeEnvironment.class.getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new IllegalStateException("No bundled VNMIFE binary found for resource `" + resourceName + "`.");
                }

                final String suffix = resourceName.endsWith(".exe") ? ".exe" : "";
                final Path binaryPath = Files.createTempFile("vnmife-", suffix);
                Files.copy(inputStream, binaryPath, REPLACE_EXISTING);
                final boolean executable = binaryPath.toFile().setExecutable(true, true) || binaryPath.toFile().setExecutable(true, false);
                if (!executable && !isWindows()) {
                    throw new IllegalStateException("Bundled VNMIFE binary `" + resourceName + "` could not be marked executable.");
                }
                binaryPath.toFile().deleteOnExit();
                extractedBinary = binaryPath.toAbsolutePath().toString();
                return extractedBinary;
            }
            catch (final IOException e) {
                throw new IllegalStateException("Unable to extract bundled VNMIFE binary.", e);
            }
        }
    }

    private static String detectBinaryName() {
        final String osName = System.getProperty("os.name", "").toLowerCase();
        final String osArch = System.getProperty("os.arch", "").toLowerCase();

        if (osName.contains("mac")) {
            if (isArm64(osArch)) {
                return "vnmife-darwin-arm64";
            }
            if (isAmd64(osArch)) {
                return "vnmife-darwin-amd64";
            }
        }
        else if (osName.contains("linux")) {
            if (isArm64(osArch)) {
                return "vnmife-linux-arm64";
            }
            if (isAmd64(osArch)) {
                return "vnmife-linux-amd64";
            }
        }
        else if (osName.contains("win")) {
            if (isArm64(osArch)) {
                return "vnmife-windows-arm64.exe";
            }
            if (isAmd64(osArch)) {
                return "vnmife-windows-amd64.exe";
            }
        }

        throw new IllegalStateException("Unsupported platform for bundled VNMIFE binary: os.name=`" + osName + "`, os.arch=`" + osArch + "`.");
    }

    private static boolean isArm64(final String osArch) {
        return "aarch64".equals(osArch) || "arm64".equals(osArch);
    }

    private static boolean isAmd64(final String osArch) {
        return "amd64".equals(osArch) || "x86_64".equals(osArch);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static class ProcessResult {
        private final String stdout;
        private final String stderr;

        private ProcessResult(final String stdout, final String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
