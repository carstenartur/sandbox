@echo off
setlocal
set "INSTALL_DIR=%~dp0.."
set "JAVA="
if defined JAVA_HOME (
  set "JAVA=%JAVA_HOME%\bin\java.exe"
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME must name a Java 25 installation. 1>&2
    exit /b 1
  )
) else (
  for /d %%D in ("%INSTALL_DIR%\plugins\org.eclipse.justj.openjdk.hotspot.jre.*") do if exist "%%~fD\jre\bin\java.exe" set "JAVA=%%~fD\jre\bin\java.exe"
)
if not defined JAVA set "JAVA=java"
set "JAVA_MAJOR="
for /f "tokens=3" %%V in ('call "%JAVA%" -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version ="') do set "JAVA_MAJOR=%%V"
if not "%JAVA_MAJOR%"=="25" (
  echo Sandbox cleanup requires the supported Java 25 runtime; found %JAVA_MAJOR%. 1>&2
  exit /b 1
)
set "LAUNCHER="
for %%F in ("%INSTALL_DIR%\plugins\org.eclipse.equinox.launcher_*.jar") do if exist "%%~fF" set "LAUNCHER=%%~fF"
if not defined LAUNCHER (
  echo Missing Equinox launcher in %INSTALL_DIR%\plugins. 1>&2
  exit /b 1
)
if defined SANDBOX_WORKSPACE (
  set "WORKSPACE=%SANDBOX_WORKSPACE%"
) else if defined SANDBOX_CLEANUP_WORKSPACE (
  set "WORKSPACE=%SANDBOX_CLEANUP_WORKSPACE%"
) else (
  set "WORKSPACE=%TEMP%\sandbox-cleanup-%RANDOM%-%RANDOM%"
)
if not exist "%WORKSPACE%" mkdir "%WORKSPACE%"
"%JAVA%" -jar "%LAUNCHER%" -nosplash -consoleLog -application org.sandbox.jdt.core.JavaCleanup -configuration "%INSTALL_DIR%\configuration" -data "%WORKSPACE%" %*
exit /b %ERRORLEVEL%
