@echo off
if not exist "%~dp0guess-market.jar" call "%~dp0build.bat"
java --module-path "%~dp0lib\javafx\lib" --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -jar "%~dp0guess-market.jar"
pause
