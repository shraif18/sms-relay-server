@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title SMSForwarder - Push to GitHub
cd /d "%~dp0"

echo ============================================================
echo   SMS Forwarder - Git push helper
echo   Folder: %CD%
echo ============================================================
echo.

REM --- 1. Check that git is installed -------------------------------------
where git >nul 2>nul
if errorlevel 1 (
    echo [X] Git is not installed.
    echo.
    echo     Download Git for Windows from:
    echo         https://git-scm.com/download/win
    echo     Install with default options, then run this script again.
    echo.
    pause
    exit /b 1
)
echo [OK] git found:
git --version
echo.

REM --- 2. Clean any leftover .git directory from a failed init -----------
if exist ".git" (
    echo [..] Found existing .git folder - cleaning it up so we start fresh.
    attrib -h -r -s ".git" /s /d >nul 2>&1
    rmdir /s /q ".git"
    if exist ".git" (
        echo [X] Could not delete .git folder. Please delete it manually
        echo     in File Explorer ^(turn on "Hidden items" in the View tab^),
        echo     then run this script again.
        pause
        exit /b 1
    )
)

REM --- 3. Configure identity (used only for the commit) ------------------
git config --global user.email "shimon@brown-ins.co.il" >nul 2>nul
git config --global user.name  "shimon"               >nul 2>nul

REM --- 4. Initialize, add everything, commit -----------------------------
echo [..] git init...
git init -b main
if errorlevel 1 (
    echo [X] git init failed. See message above.
    pause
    exit /b 1
)

echo [..] Writing .gitignore...
(
    echo # Android build artifacts
    echo /build/
    echo /app/build/
    echo /captures/
    echo .gradle/
    echo local.properties
    echo *.iml
    echo .idea/
    echo /.externalNativeBuild
    echo /.cxx
) > .gitignore

echo [..] git add ...
git add -A

echo [..] git commit ...
git commit -m "Switch SMS forwarding to ntfy.sh" || (
    echo [..] Nothing to commit ^(maybe already committed^).
)
echo.

REM --- 5. Ask for the GitHub repo URL and push ---------------------------
echo ============================================================
echo  Paste the URL of your EXISTING GitHub repo.
echo  It looks like one of these:
echo      https://github.com/YOUR-NAME/YOUR-REPO.git
echo      git@github.com:YOUR-NAME/YOUR-REPO.git
echo.
echo  Tip: on the repo page on GitHub, click the green "Code" button,
echo       and copy the HTTPS URL it shows.
echo ============================================================
echo.
set "REPO_URL="
set /p REPO_URL=Paste the GitHub repo URL and press Enter:

if "!REPO_URL!"=="" (
    echo [X] No URL given. Aborting.
    pause
    exit /b 1
)

echo.
echo [..] Setting remote origin to !REPO_URL!
git remote remove origin >nul 2>nul
git remote add origin "!REPO_URL!"
if errorlevel 1 (
    echo [X] Failed to set remote. Check the URL.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  WARNING: about to FORCE push.
echo  This will REPLACE the contents of the GitHub repo with the
echo  files in this folder. Any commits there will be overwritten.
echo  This is what you want if you just need to update an existing
echo  repo with the latest local files.
echo ============================================================
echo.
set "CONFIRM="
set /p CONFIRM=Type YES to continue, anything else to cancel:
if /I not "!CONFIRM!"=="YES" (
    echo Cancelled. Nothing pushed.
    pause
    exit /b 0
)

echo.
echo [..] Force-pushing to GitHub...
echo      If a browser window opens for GitHub authentication, sign in there.
echo      Or paste a Personal Access Token when prompted for a password.
echo.
git push -u origin main --force
if errorlevel 1 (
    echo.
    echo [X] Push failed. Common reasons:
    echo     - Wrong URL.
    echo     - Wrong credentials or expired token.
    echo     - No internet.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  Done!
echo  Open your repo on GitHub and click the "Actions" tab.
echo  A workflow named "Build Android APK" will run automatically.
echo  When the green check appears, click the run and download the
echo  artifact "sms-forwarder-apk" - inside is app-debug.apk.
echo  Install that on the phone, set the topic, and you are done.
echo ============================================================
echo.
pause
endlocal
