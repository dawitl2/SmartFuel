$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = $PSScriptRoot
$adb = "C:\Users\enkud\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$gradle = Join-Path $androidRoot "gradlew.bat"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Push-Location $androidRoot
try {
    & $gradle :app:assembleDebug --no-daemon
}
finally {
    Pop-Location
}

& $adb reverse tcp:4000 tcp:4000
& $adb install -r (Join-Path $androidRoot "app\build\outputs\apk\debug\app-debug.apk")
& $adb shell am start -n com.smartfuel.mobile/.MainActivity
