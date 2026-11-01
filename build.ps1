$ErrorActionPreference = "Stop"

$JDK_DIR = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$ANDROID_HOME = "C:\Android"
$BUILD_TOOLS = "$ANDROID_HOME\build-tools\33.0.2"
$PLATFORM = "$ANDROID_HOME\platforms\android-33"

$JAVA = "$JDK_DIR\bin\java.exe"
$JAVAC = "$JDK_DIR\bin\javac.exe"
$KEYTOOL = "$JDK_DIR\bin\keytool.exe"
$JAR = "$JDK_DIR\bin\jar.exe"

$AAPT2 = "$BUILD_TOOLS\aapt2.exe"
$ZIPALIGN = "$BUILD_TOOLS\zipalign.exe"
$D8_JAR = "$BUILD_TOOLS\lib\d8.jar"
$APKSIGNER_JAR = "$BUILD_TOOLS\lib\apksigner.jar"
$ANDROID_JAR = "$PLATFORM\android.jar"

$PROJECT_ROOT = "C:\Users\Irvine\.gemini\antigravity\scratch\ZenithLauncher"
$APP_DIR = "$PROJECT_ROOT\app"
$SRC_DIR = "$APP_DIR\src\main"
$RES_DIR = "$SRC_DIR\res"
$JAVA_DIR = "$SRC_DIR\java"
$MANIFEST = "$SRC_DIR\AndroidManifest.xml"

$BUILD_DIR = "$PROJECT_ROOT\build"
$GEN_DIR = "$BUILD_DIR\gen"
$OBJ_DIR = "$BUILD_DIR\obj"
$DEX_DIR = "$BUILD_DIR\dex"

Write-Host "=== 1. Preparing build folders ===" -ForegroundColor Cyan
if (Test-Path $BUILD_DIR) { Remove-Item -Path $BUILD_DIR -Recurse -Force }
New-Item -ItemType Directory -Path $GEN_DIR, $OBJ_DIR, $DEX_DIR, "$BUILD_DIR\res_compiled" | Out-Null

Write-Host "=== 2. Compiling resources (aapt2 compile) ===" -ForegroundColor Cyan
& $AAPT2 compile --dir $RES_DIR -o "$BUILD_DIR\res_compiled\res.zip"

Write-Host "=== 3. Linking resources and generating R.java (aapt2 link) ===" -ForegroundColor Cyan
& $AAPT2 link -I $ANDROID_JAR "$BUILD_DIR\res_compiled\res.zip" --manifest $MANIFEST --java $GEN_DIR -o "$BUILD_DIR\unaligned.apk" --auto-add-overlay

Write-Host "=== 4. Compiling Java sources (javac) ===" -ForegroundColor Cyan
$javaFiles = Get-ChildItem -Path $JAVA_DIR, $GEN_DIR -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }
& $JAVAC -encoding UTF-8 -source 1.8 -target 1.8 -cp $ANDROID_JAR -d $OBJ_DIR $javaFiles

Write-Host "=== 5. Dexing class files (d8) ===" -ForegroundColor Cyan
$classFiles = Get-ChildItem -Path $OBJ_DIR -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
& $JAVA -cp $D8_JAR com.android.tools.r8.D8 --lib $ANDROID_JAR --output $DEX_DIR $classFiles

Write-Host "=== 6. Packaging classes.dex into APK ===" -ForegroundColor Cyan
Push-Location $DEX_DIR
& $JAR -uf "$BUILD_DIR\unaligned.apk" classes.dex
Pop-Location

Write-Host "=== 7. Zipaligning APK ===" -ForegroundColor Cyan
$ALIGNED_APK = "$BUILD_DIR\ZenithLauncher-aligned.apk"
& $ZIPALIGN -p -f 4 "$BUILD_DIR\unaligned.apk" $ALIGNED_APK

Write-Host "=== 8. Signing APK (apksigner) ===" -ForegroundColor Cyan
$KEYSTORE = "$PROJECT_ROOT\keystore\release.keystore"
$FINAL_APK = "$PROJECT_ROOT\ZenithLauncher.apk"
& $JAVA -jar $APKSIGNER_JAR sign --ks $KEYSTORE --ks-pass pass:android --ks-key-alias zenith --key-pass pass:android --out $FINAL_APK $ALIGNED_APK

Write-Host "=== BUILD SUCCESSFUL! ===" -ForegroundColor Green
Write-Host "Output APK: $FINAL_APK" -ForegroundColor Green
Get-Item $FINAL_APK | Select-Object Name, Length, LastWriteTime
