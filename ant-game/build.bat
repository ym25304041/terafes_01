@echo off
rem ============================================================
rem Build script: compiles Java and creates a WAR for Tomcat 10.
rem   build\classes        : compiled classes
rem   build\war            : exploded web app
rem   build\ant-game.war   : WAR file
rem If Tomcat is not in C:\tomcat10, set TOMCAT_HOME first.
rem (Only servlet-api.jar from Tomcat is used, for compiling.)
rem ============================================================
setlocal
cd /d "%~dp0"

if "%TOMCAT_HOME%"=="" set TOMCAT_HOME=C:\tomcat10
set SERVLET_API=%TOMCAT_HOME%\lib\servlet-api.jar
if not exist "%SERVLET_API%" (
  echo [ERROR] %SERVLET_API% not found. Check TOMCAT_HOME.
  exit /b 1
)

if exist build rmdir /s /q build
mkdir build\classes

rem List all source files, then compile them
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 --release 21 -Xlint:all -cp "%SERVLET_API%" -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1

rem Assemble the web app
xcopy /s /e /i /q src\main\webapp build\war >nul
xcopy /s /e /i /q build\classes build\war\WEB-INF\classes >nul
jar --create --file build\ant-game.war -C build\war .
if errorlevel 1 exit /b 1

echo Build OK: build\ant-game.war
exit /b 0
