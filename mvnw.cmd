@echo off
setlocal

set "MVNW_DIR=%~dp0"
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%MVNW_DIR%.mvn\apache-maven-%MAVEN_VERSION%"
set "MAVEN_ZIP=%MVNW_DIR%.mvn\apache-maven-%MAVEN_VERSION%-bin.zip"
set "MAVEN_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%MAVEN_ZIP%" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -LiteralPath '%MAVEN_ZIP%' -DestinationPath '%MVNW_DIR%.mvn'"
  if errorlevel 1 exit /b 1
)

for /f "delims=" %%J in ('where java 2^>nul') do (
  set "JAVA_EXE=%%J"
  goto :found_java
)
goto :run_maven

:found_java
for %%J in ("%JAVA_EXE%\..\..") do set "JAVA_HOME=%%~fJ"

:run_maven
call "%MAVEN_HOME%\bin\mvn.cmd" %*
