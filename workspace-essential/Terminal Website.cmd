@echo off
set "PATH=C:\Users\MerelyMe\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;%~dp0.tools\website-bin;C:\Users\MerelyMe\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\git\cmd;%PATH%"
cd /d "%~dp0website"
cmd /k
