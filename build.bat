@echo off
setlocal
set ROOT=%~dp0
set FX=%ROOT%lib\javafx\lib

if exist "%ROOT%out" rmdir /s /q "%ROOT%out"
mkdir "%ROOT%out\classes"

echo Compiling...
for /f "delims=" %%f in ('dir /s /b "%ROOT%engine\src\*.java" "%ROOT%desktop\src\*.java"') do echo %%f>>"%ROOT%out\sources.txt"
javac --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -d "%ROOT%out\classes" @"%ROOT%out\sources.txt"
if errorlevel 1 exit /b 1

echo Copying resources...
xcopy /s /e /q /y "%ROOT%desktop\resources\*" "%ROOT%out\classes\" >nul

echo Building guess-market.jar...
(
  echo Manifest-Version: 1.0
  echo Main-Class: app.Launcher
) > "%ROOT%out\manifest.txt"
jar --create --file "%ROOT%guess-market.jar" --manifest "%ROOT%out\manifest.txt" -C "%ROOT%out\classes" .
if errorlevel 1 exit /b 1

echo Build done: guess-market.jar is ready in "%ROOT%".
