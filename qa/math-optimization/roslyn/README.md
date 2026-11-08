# Roslyn adapter for counted sums

Use .NET 10 and Java 25. Build the Regelsuche SDK from a revision containing
`PolynomialSumCli` and set `SDK_JAR` to the qualified standalone `-all.jar`.
[Standalone SDK build](https://github.com/carstenartur/Regelsuche/blob/main/regelsuche-optimization-sdk/README.md#reproducible-local-distribution)

```sh
SDK_JAR=/absolute/path/to/regelsuche-optimization-sdk-all.jar
dotnet build qa/math-optimization/roslyn/RoslynUlr.csproj
dotnet qa/math-optimization/roslyn/bin/Debug/net10.0/RoslynUlr.dll rewrite Sum.cs \
  "$SDK_JAR" > Sum.optimized.cs
```

Example `Sum.cs`

```csharp
using System.Linq;

public static class Sum
{
    public static int Run(int n) =>
        Enumerable.Range(0, n).Aggregate(0, (s, i) => unchecked(s + i));
}
```

The standalone adapter accepts one method of this shape with explicit
`checked` or `unchecked` addition and resolved standard LINQ calls. It
runs the Regelsuche verifier with the standalone JAR as its classpath before
emitting C#. The original call
handles negative counts and checked counts above 65,536.

Run the shared Java and .NET checks.

```sh
mvn -f qa/math-optimization/pom.xml test -Dtest=UlrSumProjectionTest,RoslynUlrIntegrationTest \
  -Dsandbox.math.roslyn=true -Dsandbox.math.roslyn.regelsucheJar="$SDK_JAR"
```
