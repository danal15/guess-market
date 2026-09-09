@echo off
if not exist "%~dp0guess-market.jar" (
    echo guess-market.jar is missing. Keep it in the same folder as this file.
    pause
    exit /b 1
)
java --module-path "%~dp0lib\javafx\lib" --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -jar "%~dp0guess-market.jar"
pause
