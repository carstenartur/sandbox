# Roslyn adapter for counted sums

Use .NET 10 and Java 25. Run these commands from the repository root.

```sh
dotnet build qa/math-optimization/roslyn/RoslynUlr.csproj
mvn -f qa/math-optimization/pom.xml compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath \
  -DincludeScope=compile -Dmdep.outputFile=target/ulr-classpath.txt
dotnet qa/math-optimization/roslyn/bin/Debug/net10.0/RoslynUlr.dll rewrite Sum.cs \
  "qa/math-optimization/target/classes:$(cat qa/math-optimization/target/ulr-classpath.txt)" > Sum.optimized.cs
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
runs the local Regelsuche verifier before emitting C#. The original call
handles negative counts and checked counts above 65,536.

Run the shared Java and .NET checks.

```sh
mvn -f qa/math-optimization/pom.xml test -Dtest=UlrSumProjectionTest,RoslynUlrIntegrationTest -Dsandbox.math.roslyn=true
```
