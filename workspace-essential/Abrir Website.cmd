@echo off
setlocal
set "PATH=C:\Users\MerelyMe\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;%~dp0.tools\website-bin;%PATH%"
cd /d "%~dp0website"
echo Website local: http://localhost:3000
echo Para parar, prima Ctrl+C. Este comando nao publica o site.
call npm.cmd run dev -- --host 127.0.0.1 --port 3000
if errorlevel 1 pause
