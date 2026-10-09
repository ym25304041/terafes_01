@echo off
rem ============================================================
rem Test script: builds, then runs all tests.
rem Exit code is 1 if any test fails.
rem ============================================================
setlocal
cd /d "%~dp0"

call "%~dp0build.bat"
if errorlevel 1 exit /b 1

if "%TOMCAT_HOME%"=="" set TOMCAT_HOME=C:\tomcat10
set SERVLET_API=%TOMCAT_HOME%\lib\servlet-api.jar

mkdir build\test-classes
dir /s /b src\test\java\*.java > build\test-sources.txt
javac -encoding UTF-8 --release 21 -cp "build\classes;%SERVLET_API%" -d build\test-classes @build\test-sources.txt
if errorlevel 1 exit /b 1

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "build\classes;build\test-classes;%SERVLET_API%" com.terafes.ant.TestMain
exit /b %errorlevel%
