/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Compiles both source versions with the real .NET compiler after independent Regelsuche replay. */
class RoslynUlrIntegrationTest {
    @TempDir Path temporary;
    private final String dotnet = System.getProperty("sandbox.math.roslyn.dotnet", "dotnet");

    @Test
    @EnabledIfSystemProperty(named = "sandbox.math.roslyn", matches = "true")
    void verifiedRewritesPreserveRangeAndOverflowContracts() throws Exception {
        Path project = Path.of("qa/math-optimization/roslyn/RoslynUlr.csproj").toAbsolutePath();
        Path artifacts = temporary.resolve("adapter");
        assertSuccess(run("build", project.toString(), "--artifacts-path", artifacts.toString(), "--nologo", "-v:q"));
        String adapter = artifacts.resolve("bin/RoslynUlr/debug/RoslynUlr.dll").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path target = Files.createDirectory(temporary.resolve("runtime"));
        for (String mode : List.of("unchecked", "checked")) {
            String variant = mode.equals("checked") ? "Checked" : "Wrap";
            Path original = target.resolve("Before" + variant + ".cs");
            Files.writeString(original, """
                    using System.Linq;
                    public static class Before%s {
                        public static int Run(int n) =>
                            Enumerable.Range(0, n).Aggregate(0, (s, i) => %s(s + i));
                    }
                    """.formatted(variant, mode));
            String request = assertSuccess(run(adapter, "extract", original.toString())).strip();
            String rewritten = assertSuccess(run(adapter, "rewrite", original.toString(), classpath));
            assertNotEquals(Files.readString(original), rewritten);
            Files.writeString(target.resolve("After" + variant + ".cs"), rewritten.replace("Before" + variant, "After" + variant));

            Path supplied = temporary.resolve(variant + "-supplied.json");
            Files.writeString(supplied, "{\"version\":1,\"request\":" + request + ",\"initial\":0,\"coefficients\":[0,1]}");
            Result rejected = run(adapter, "rewrite", original.toString(), supplied.toString());
            assertNotEquals(0, rejected.exit());
            assertTrue(rejected.output().isEmpty());
        }

        Path runtime = target.resolve("Runtime.csproj");
        Files.writeString(runtime, """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <OutputType>Exe</OutputType>
                    <TargetFramework>net10.0</TargetFramework>
                    <CheckForOverflowUnderflow>true</CheckForOverflowUnderflow>
                  </PropertyGroup>
                </Project>
                """);
        Files.writeString(target.resolve("Program.cs"), """
                using System;
                foreach (int n in new[] { -1, 0, 1, 65536, 65537, 100000 })
                    Console.WriteLine($"{n}:{Result(BeforeWrap.Run, n)}:{Result(AfterWrap.Run, n)}"
                        + $":{Result(BeforeChecked.Run, n)}:{Result(AfterChecked.Run, n)}");
                Console.WriteLine($"max:{Result(AfterWrap.Run, int.MaxValue)}");

                static string Result(Func<int, int> function, int n) {
                    try { return function(n).ToString(System.Globalization.CultureInfo.InvariantCulture); }
                    catch (ArgumentOutOfRangeException error) { return "range-" + error.ParamName; }
                    catch (OverflowException) { return "overflow"; }
                }
                """);
        assertSuccess(run("build", runtime.toString(), "--nologo", "-v:q"));
        var output = assertSuccess(run(target.resolve("bin/Debug/net10.0/Runtime.dll").toString()));
        assertEquals(List.of("-1:range-count:range-count:range-count:range-count",
                "0:0:0:0:0", "1:0:0:0:0",
                "65536:2147450880:2147450880:2147450880:2147450880",
                "65537:-2147450880:-2147450880:overflow:overflow",
                "100000:704982704:704982704:overflow:overflow",
                "max:1073741825"), output.lines().toList());
    }

    private Result run(String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(dotnet);
        command.addAll(List.of(args));
        Path stdout = Files.createTempFile(temporary, "stdout", ".txt");
        Path stderr = Files.createTempFile(temporary, "stderr", ".txt");
        var builder = new ProcessBuilder(command).directory(temporary.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().put("DOTNET_CLI_TELEMETRY_OPTOUT", "1");
        builder.environment().put("DOTNET_NOLOGO", "1");
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        var process = builder.start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), command.toString());
            return new Result(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String assertSuccess(Result result) {
        assertEquals(0, result.exit(), result.output() + result.error());
        return result.output();
    }

    private record Result(int exit, String output, String error) { }
}
