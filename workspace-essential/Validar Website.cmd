@echo off
setlocal
set "PATH=C:\Users\MerelyMe\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;%~dp0.tools\website-bin;%PATH%"
cd /d "%~dp0website"
call npm.cmd run build
if errorlevel 1 (
  echo A validacao falhou.
) else (
  echo Validacao concluida. O site nao foi publicado.
)
pause
