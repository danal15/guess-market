@echo off
setlocal
set ROOT=%~dp0
set FX=%ROOT%lib\javafx\lib

if exist "%ROOT%out" rmdir /s /q "%ROOT%out"
mkdir "%ROOT%out\engine"
mkdir "%ROOT%out\ui"

echo Compiling the engine...
dir /s /b "%ROOT%engine\src\*.java" > "%ROOT%out\engine-sources.txt"
javac -encoding UTF-8 -d "%ROOT%out\engine" @"%ROOT%out\engine-sources.txt"
if errorlevel 1 exit /b 1

echo Compiling the user interface...
dir /s /b "%ROOT%desktop\src\*.java" > "%ROOT%out\ui-sources.txt"
javac --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 -cp "%ROOT%out\engine" -d "%ROOT%out\ui" @"%ROOT%out\ui-sources.txt"
if errorlevel 1 exit /b 1
xcopy /s /e /q /y "%ROOT%desktop\resources\*" "%ROOT%out\ui\" >nul

echo Adding the JavaFX runtime to the interface jar...
pushd "%ROOT%out\ui"
for %%m in (base controls fxml graphics) do jar --extract --file "%FX%\javafx.%%m.jar"
if exist module-info.class del /q module-info.class
if exist META-INF rmdir /s /q META-INF
popd
for %%f in ("%ROOT%lib\javafx\bin\*.dll") do echo %%~nxf | findstr /i /c:"jfxwebkit" /c:"jfxmedia" /c:"gstreamer-lite" /c:"glib-lite" /c:"fxplugins" >nul || copy /y "%%f" "%ROOT%out\ui\" >nul

echo Building the jars...
(echo Manifest-Version: 1.0) > "%ROOT%out\engine-manifest.txt"
jar --create --file "%ROOT%engine.jar" --manifest "%ROOT%out\engine-manifest.txt" -C "%ROOT%out\engine" .
if errorlevel 1 exit /b 1
(
  echo Manifest-Version: 1.0
  echo Main-Class: app.Launcher
  echo Class-Path: engine.jar
  echo Enable-Native-Access: ALL-UNNAMED
) > "%ROOT%out\ui-manifest.txt"
jar --create --file "%ROOT%guess-market.jar" --manifest "%ROOT%out\ui-manifest.txt" -C "%ROOT%out\ui" .
if errorlevel 1 exit /b 1

echo Done: engine.jar and guess-market.jar are ready in "%ROOT%".
