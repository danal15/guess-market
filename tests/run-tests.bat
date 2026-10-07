@echo off
rem Runs every suite against a freshly started server.
rem
rem The engine ones need nothing. The rest talk to a deployed war, so start it
rem first with run-server.bat in the project root and leave it running. Start it
rem FRESH: the market keeps its users and event names for the server's lifetime,
rem and api_test will say so rather than fail confusingly if it is not.
setlocal
set ROOT=%~dp0..
set HERE=%~dp0
set OUT=%HERE%out
set CLIENT=%ROOT%\ex3-client

if not exist "%CLIENT%\guess-market-client.jar" (
    echo Build first: run build-ex3.bat in the project root.
    exit /b 1
)

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

set CP=%CLIENT%\guess-market-client.jar;%CLIENT%\engine.jar;%OUT%
set FXSWING=%ROOT%\lib\javafx\lib\javafx.swing.jar
if exist "%FXSWING%" set CP=%CP%;%FXSWING%

echo Compiling the suites...
javac -encoding UTF-8 -nowarn -cp "%CP%" -d "%OUT%" "%HERE%*.java"
if errorlevel 1 exit /b 1

echo.
echo ============ engine, no server needed ============
java -cp "%CP%" CloseBug
java -cp "%ROOT%\guess-market.jar;%ROOT%\engine.jar;%OUT%" Ex2Regress "%ROOT%" "%OUT%"

echo.
echo ============ against the running server ============
java --enable-native-access=ALL-UNNAMED -cp "%CP%" WalkLauncher MultiClient
java --enable-native-access=ALL-UNNAMED -cp "%CP%" WalkLauncher ClientWalkthrough "%ROOT%"
java -cp "%OUT%" Hammer

echo.
echo ============ the servlets on their own ============
echo These two are shell scripts; run them from Git Bash:
echo   bash tests/api_test.sh
echo   bash tests/edge_test.sh
