// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
using System.ComponentModel;
using System.Diagnostics;
using System.Text;
using System.Text.Json;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis.Operations;
using Microsoft.CodeAnalysis.Text;

if (args is not (["extract", _] or ["rewrite", _, _]))
    return Reject("Usage: RoslynUlr extract <file> | rewrite <file> <regelsuche-sdk-all.jar>");

try
{
    var input = File.ReadAllBytes(args[1]);
    var source = SourceText.From(input, input.Length).ToString();
    var match = FindMatch(source, args[1]);
    var request = JsonSerializer.SerializeToElement(new
    {
        version = 1, kind = "counted-sum", bits = 32, overflow = match.Checked ? "checked" : "wrap",
        start = 0, stride = 1, initial = 0, term = new { op = "index" }
    });
    if (args[0] == "extract")
    {
        Console.WriteLine(request.GetRawText());
        return 0;
    }

    using var proof = await Prove(request, args[2]);
    var supported = JsonSerializer.SerializeToElement(new
    {
        version = 1, request, initial = 0, coefficients = new[] { 0, 1 }
    });
    if (!JsonElement.DeepEquals(proof.RootElement, supported))
        return Reject("Proof does not match the extracted request and supported coefficients.");

    var rewritten = Rewrite(source, match);
    if (!File.ReadAllBytes(args[1]).AsSpan().SequenceEqual(input))
        return Reject("Source changed while obtaining the verified proof.");
    Console.Write(rewritten);
    return 0;
}
catch (Exception exception) when (exception is IOException or UnauthorizedAccessException
    or InvalidDataException or JsonException or Win32Exception)
{
    return Reject(exception.Message);
}

static int Reject(string message)
{
    Console.Error.WriteLine(message);
    return 1;
}

static async Task<JsonDocument> Prove(JsonElement request, string sdkJar)
{
    if (!File.Exists(sdkJar))
        throw new InvalidDataException("The standalone Regelsuche SDK JAR could not be opened.");
    var jar = Path.GetFullPath(sdkJar);
    if (jar.Contains(Path.PathSeparator) || !Path.GetExtension(jar).Equals(".jar", StringComparison.OrdinalIgnoreCase))
        throw new InvalidDataException("Supply a single Regelsuche SDK JAR path.");

    var java = OperatingSystem.IsWindows() ? "java.exe" : "java";
    var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
    var start = new ProcessStartInfo(string.IsNullOrEmpty(javaHome) ? java : Path.Combine(javaHome, "bin", java))
    {
        ArgumentList = { "-cp", jar, "de.regelsuche.sdk.optimization.iteration.PolynomialSumCli" },
        StandardInputEncoding = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false),
        UseShellExecute = false, RedirectStandardInput = true, RedirectStandardOutput = true, RedirectStandardError = true
    };
    using var process = Process.Start(start) ?? throw new InvalidDataException("Could not start the Regelsuche verifier.");
    using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(30));
    try
    {
        var stdout = process.StandardOutput.ReadToEndAsync();
        var stderr = process.StandardError.ReadToEndAsync();
        await process.StandardInput.WriteLineAsync(request.GetRawText()).WaitAsync(deadline.Token);
        process.StandardInput.Close();
        await Task.WhenAll(stdout, stderr, process.WaitForExitAsync()).WaitAsync(deadline.Token);
        if (process.ExitCode != 0)
            throw new InvalidDataException($"Regelsuche verifier exited with code {process.ExitCode}. {(await stderr).Trim()}");
        return JsonDocument.Parse(await stdout);
    }
    catch (OperationCanceledException)
    {
        throw new InvalidDataException("Regelsuche verifier timed out after 30 seconds.");
    }
    finally
    {
        try
        {
            if (!process.HasExited)
                process.Kill(entireProcessTree: true);
        }
        catch (Exception exception) when (exception is InvalidOperationException or Win32Exception)
        {
            // Preserve the original failure if the process exits during cleanup.
        }
    }
}

static SourceMatch FindMatch(string source, string path)
{
    var tree = CSharpSyntaxTree.ParseText(source, path: path);
    var root = tree.GetRoot();
    if (root.DescendantTrivia().Any(trivia => trivia.IsDirective))
        throw new InvalidDataException("Source with preprocessor directives is not supported.");

    var runtimeDirectory = Path.GetDirectoryName(typeof(object).Assembly.Location);
    var references = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")!).Split(Path.PathSeparator)
        .Where(file => Path.GetDirectoryName(file) == runtimeDirectory)
        .Select(file => MetadataReference.CreateFromFile(file)).ToArray();
    var compilation = CSharpCompilation.Create("Source", [tree], references,
        new CSharpCompilationOptions(OutputKind.DynamicallyLinkedLibrary));
    var error = compilation.GetDiagnostics().FirstOrDefault(diagnostic => diagnostic.Severity == DiagnosticSeverity.Error);
    if (error is not null)
        throw new InvalidDataException($"Source does not compile: {error}");

    // Look up Enumerable in the runtime assembly, even if the source declares a type with that name.
    var linqReference = references.Single(reference => reference.FilePath == typeof(Enumerable).Assembly.Location);
    var linqAssembly = (IAssemblySymbol)compilation.GetAssemblyOrModuleSymbol(linqReference)!;
    var enumerable = linqAssembly.GetTypeByMetadataName("System.Linq.Enumerable")!;
    var model = compilation.GetSemanticModel(tree);
    var matches = root.DescendantNodes().OfType<MethodDeclarationSyntax>()
        .Select(method => MatchMethod(model, enumerable, method))
        .OfType<SourceMatch>().Take(2).ToArray();
    return matches is [var match] ? match
        : throw new InvalidDataException("Expected exactly one supported Enumerable.Range(...).Aggregate(...) return.");
}

static SourceMatch? MatchMethod(SemanticModel model, INamedTypeSymbol enumerable, MethodDeclarationSyntax method)
{
    if (model.GetDeclaredSymbol(method) is not
        {
            IsStatic: true, ReturnType.SpecialType: SpecialType.System_Int32,
            Parameters: [{ RefKind: RefKind.None, Type.SpecialType: SpecialType.System_Int32 } count]
        })
        return null;

    var expression = method.ExpressionBody?.Expression;
    if (method.Body is { Statements: [ReturnStatementSyntax { Expression: { } returned }] })
        expression = returned;
    if (expression is null || model.GetOperation(expression) is not IInvocationOperation aggregate
        || !IsEnumerableCall(aggregate, enumerable, "Aggregate", 2, 3)
        || aggregate.TargetMethod.TypeArguments is not
            [{ SpecialType: SpecialType.System_Int32 }, { SpecialType: SpecialType.System_Int32 }]
        || Argument(aggregate, 0) is not IInvocationOperation range
        || !IsEnumerableCall(range, enumerable, "Range", 0, 2)
        || !IsZero(Argument(range, 0)) || !RefersTo(Argument(range, 1), count)
        || !IsZero(Argument(aggregate, 1)))
        return null;

    if (Argument(aggregate, 2) is not IDelegateCreationOperation
        {
            IsImplicit: true,
            Target: IAnonymousFunctionOperation
                { Syntax: LambdaExpressionSyntax { Body: CheckedExpressionSyntax overflow } } reducer
        }
        || reducer.Symbol.Parameters is not [var accumulator, var index]
        || reducer.Body.Operations is not [IReturnOperation { ReturnedValue: IBinaryOperation addition }]
        || addition is not { OperatorKind: BinaryOperatorKind.Add, OperatorMethod: null,
            Type.SpecialType: SpecialType.System_Int32 }
        || !RefersTo(addition.LeftOperand, accumulator) || !RefersTo(addition.RightOperand, index)
        || addition.IsChecked != overflow.IsKind(SyntaxKind.CheckedExpression))
        return null;

    return new SourceMatch(expression.Span, method.ParameterList.Parameters[0].Identifier.Text, addition.IsChecked);
}

static bool IsEnumerableCall(IInvocationOperation call, INamedTypeSymbol enumerable, string name, int arity, int arguments) =>
    call.TargetMethod is { IsStatic: true } method && method.Name == name && method.Arity == arity
    && SymbolEqualityComparer.Default.Equals(method.ContainingType, enumerable)
    && call.Arguments.Length == arguments;

static IOperation? Argument(IInvocationOperation call, int ordinal) =>
    call.Arguments.FirstOrDefault(argument => argument.Parameter?.Ordinal == ordinal)?.Value;

static bool IsZero(IOperation? operation) =>
    operation is ILiteralOperation { Type.SpecialType: SpecialType.System_Int32, ConstantValue.Value: 0 };

static bool RefersTo(IOperation? operation, IParameterSymbol parameter) =>
    operation is IParameterReferenceOperation reference
    && SymbolEqualityComparer.Default.Equals(reference.Parameter, parameter);

static string Rewrite(string source, SourceMatch match)
{
    var n = match.Count;
    var guard = match.Checked ? $"{n} >= 0 && {n} <= 65536" : $"{n} >= 0";
    var mode = match.Checked ? "checked" : "unchecked";
    // The supported Newton coefficients give C(n, 2). The product fits in long
    // for all nonnegative int counts. The fallback preserves Range/overflow exceptions.
    var original = source[match.Span.Start..match.Span.End];
    var replacement = $"{guard} ? {mode}((int)((long){n} * ({n} - 1L) / 2L)) : {original}";
    return source[..match.Span.Start] + replacement + source[match.Span.End..];
}

sealed record SourceMatch(TextSpan Span, string Count, bool Checked);
