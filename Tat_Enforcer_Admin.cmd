@echo off
chcp 65001 >nul
cd /d "%~dp0"
fltmc >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
echo Dang tat bao ve...
if exist "%~dp0dist\Kyluat\Kyluat.exe" (
    "%~dp0dist\Kyluat\Kyluat.exe" turn-off
    goto done
)
where python >nul 2>&1
if errorlevel 1 (
    echo Khong tim thay Python tren may nay. Chay file exe trong thu muc dist\Kyluat.
    goto done
)
python run_enforcer.pyw turn-off
:done
echo.
echo Xong. Log: "%~dp0data\turnoff.log"
pause
