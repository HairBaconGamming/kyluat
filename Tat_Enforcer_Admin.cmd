@echo off
chcp 65001 >nul
cd /d "%~dp0"
fltmc >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)
echo Dang tat bao ve...
rem Ban cai dat nam trong thu mục cua rieng user, khong phai ben canh file nay.
if exist "%LOCALAPPDATA%\Programs\Kyluat\Kyluat-cli.exe" (
    "%LOCALAPPDATA%\Programs\Kyluat\Kyluat-cli.exe" turn-off
    goto done
)
if exist "%~dp0dist\Kyluat\Kyluat-cli.exe" (
    "%~dp0dist\Kyluat\Kyluat-cli.exe" turn-off
    goto done
)
where python >nul 2>&1
if errorlevel 1 (
    echo Khong tim thay Ky Luuat tren may nay. Mo app roi bam Tat bao ve, hoac chay Kyluat-cli.exe turn-off trong thu mục cài.
    goto done
)
python run_enforcer.pyw turn-off
:done
echo.
echo Xong. Log nam trong thu muc data\ canh file exe.
pause
