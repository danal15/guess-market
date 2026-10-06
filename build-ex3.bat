@echo off
rem Builds the exercise 3 submission: one war for Tomcat, and a folder holding
rem the client with everything it needs to run from a bare "java -jar".
rem
rem Tomcat 10.1 is assumed, which means the jakarta.servlet packages. The
rem servlet api jar is only needed to compile against - Tomcat supplies it at
rem run time, and shipping a copy inside the war would clash with Tomcat's own.
setlocal
set ROOT=%~dp0
set FX=%ROOT%lib\javafx\lib
set GSON=%ROOT%lib\gson\gson-2.13.2.jar
set SERVLET=%ROOT%lib\tomcat\lib\servlet-api.jar
set WAR_NAME=guess-market
set CLIENT_DIR=%ROOT%ex3-client

if not exist "%GSON%" (
    echo Missing %GSON%
    exit /b 1
)
if not exist "%SERVLET%" (
    echo Missing %SERVLET% - the Tomcat used to compile against is not in lib\tomcat.
    exit /b 1
)

if exist "%ROOT%out3" rmdir /s /q "%ROOT%out3"
mkdir "%ROOT%out3\engine"
mkdir "%ROOT%out3\desktop"
mkdir "%ROOT%out3\server"
mkdir "%ROOT%out3\client"

echo Compiling the engine...
dir /s /b "%ROOT%engine\src\*.java" > "%ROOT%out3\engine-sources.txt"
javac -encoding UTF-8 -d "%ROOT%out3\engine" @"%ROOT%out3\engine-sources.txt"
if errorlevel 1 exit /b 1

echo Compiling the exercise 2 screens the client reuses...
dir /s /b "%ROOT%desktop\src\*.java" > "%ROOT%out3\desktop-sources.txt"
javac --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 ^
      -cp "%ROOT%out3\engine" -d "%ROOT%out3\desktop" @"%ROOT%out3\desktop-sources.txt"
if errorlevel 1 exit /b 1
xcopy /s /e /q /y "%ROOT%desktop\resources\*" "%ROOT%out3\desktop\" >nul

echo Compiling the server...
dir /s /b "%ROOT%server\src\*.java" > "%ROOT%out3\server-sources.txt"
javac -encoding UTF-8 -cp "%ROOT%out3\engine;%GSON%;%SERVLET%" ^
      -d "%ROOT%out3\server" @"%ROOT%out3\server-sources.txt"
if errorlevel 1 exit /b 1

echo Compiling the client...
dir /s /b "%ROOT%client\src\*.java" > "%ROOT%out3\client-sources.txt"
javac --module-path "%FX%" --add-modules javafx.controls,javafx.fxml -encoding UTF-8 ^
      -cp "%ROOT%out3\engine;%ROOT%out3\desktop;%GSON%" ^
      -d "%ROOT%out3\client" @"%ROOT%out3\client-sources.txt"
if errorlevel 1 exit /b 1
xcopy /s /e /q /y "%ROOT%client\resources\*" "%ROOT%out3\client\" >nul

echo Building engine.jar...
(echo Manifest-Version: 1.0) > "%ROOT%out3\engine-manifest.txt"
jar --create --file "%ROOT%out3\engine.jar" --manifest "%ROOT%out3\engine-manifest.txt" -C "%ROOT%out3\engine" .
if errorlevel 1 exit /b 1

echo Assembling the war...
rem The engine travels as a jar in WEB-INF\lib beside gson, which is the shape
rem the exercise asks for. Only the servlets are loose classes.
mkdir "%ROOT%out3\war\WEB-INF\classes"
mkdir "%ROOT%out3\war\WEB-INF\lib"
xcopy /s /e /q /y "%ROOT%out3\server\*" "%ROOT%out3\war\WEB-INF\classes\" >nul
copy /y "%ROOT%out3\engine.jar" "%ROOT%out3\war\WEB-INF\lib\" >nul
copy /y "%GSON%" "%ROOT%out3\war\WEB-INF\lib\" >nul
copy /y "%ROOT%server\web\WEB-INF\web.xml" "%ROOT%out3\war\WEB-INF\" >nul
(echo Manifest-Version: 1.0) > "%ROOT%out3\war-manifest.txt"
jar --create --file "%ROOT%%WAR_NAME%.war" --manifest "%ROOT%out3\war-manifest.txt" -C "%ROOT%out3\war" .
if errorlevel 1 exit /b 1

echo Packing the JavaFX runtime and Gson into the client jar...
pushd "%ROOT%out3\client"
for %%m in (base controls fxml graphics) do jar --extract --file "%FX%\javafx.%%m.jar"
jar --extract --file "%GSON%"
if exist module-info.class del /q module-info.class
if exist META-INF rmdir /s /q META-INF
rem The reused exercise 2 screens and their stylesheets travel with the client.
xcopy /s /e /q /y "%ROOT%out3\desktop\*" "%ROOT%out3\client\" >nul
popd
for %%f in ("%ROOT%lib\javafx\bin\*.dll") do echo %%~nxf | findstr /i /c:"jfxwebkit" /c:"jfxmedia" /c:"gstreamer-lite" /c:"glib-lite" /c:"fxplugins" >nul || copy /y "%%f" "%ROOT%out3\client\" >nul

echo Building the client folder...
if exist "%CLIENT_DIR%" rmdir /s /q "%CLIENT_DIR%"
mkdir "%CLIENT_DIR%"
copy /y "%ROOT%out3\engine.jar" "%CLIENT_DIR%\engine.jar" >nul
(
  echo Manifest-Version: 1.0
  echo Main-Class: app.ClientLauncher
  echo Class-Path: engine.jar
  echo Enable-Native-Access: ALL-UNNAMED
) > "%ROOT%out3\client-manifest.txt"
jar --create --file "%CLIENT_DIR%\guess-market-client.jar" --manifest "%ROOT%out3\client-manifest.txt" -C "%ROOT%out3\client" .
if errorlevel 1 exit /b 1
copy /y "%ROOT%run-client.bat" "%CLIENT_DIR%\run.bat" >nul

echo.
echo Done.
echo   %WAR_NAME%.war          - drop this into tomcat\webapps
echo   ex3-client\             - the client, started by its own run.bat
