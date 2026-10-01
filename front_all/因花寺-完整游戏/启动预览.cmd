@echo off
cd /d "%~dp0"
if not exist "assets\arrival.webp" goto incomplete
if not exist "original\chapters\wish\index.html" goto incomplete
where python >nul 2>nul
if errorlevel 1 goto trylauncher
python serve.py
if errorlevel 1 pause
exit /b
:trylauncher
where py >nul 2>nul
if errorlevel 1 goto missing
py -3 serve.py
if errorlevel 1 pause
exit /b
:incomplete
echo Please extract the ENTIRE ZIP before launching this game.
pause
exit /b 1
:missing
echo Python 3 is required. Install Python 3 and run this launcher again.
pause
