param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SigningStore = (Join-Path $env:LOCALAPPDATA 'JingliuAndroidTools/jingliu-debug.jks')
)
$ErrorActionPreference = 'Stop'
if (!$SdkRoot) { $SdkRoot = $env:ANDROID_SDK_ROOT }
if (!$SdkRoot -or !$JavaHome) { throw 'Set ANDROID_HOME and JAVA_HOME, or pass -SdkRoot and -JavaHome.' }
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome/bin;$env:Path"
$tool = Join-Path $SdkRoot 'build-tools/35.0.0'
$android = Join-Path $SdkRoot 'platforms/android-35/android.jar'
if (!(Test-Path $android)) { throw 'Install Android SDK platform 35.' }
if (!(Test-Path (Join-Path $tool 'aapt2.exe'))) { throw 'Install Android SDK build-tools 35.0.0.' }

function Invoke-Tool {
    param([string]$File, [string[]]$Arguments)
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$File failed (exit code $LASTEXITCODE)." }
}

$main = Join-Path $PSScriptRoot 'src/main'
$build = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $build 'classes'
New-Item -Type Directory -Force $build | Out-Null
if (Test-Path -LiteralPath $classes) {
    $resolved = (Resolve-Path -LiteralPath $classes).ProviderPath
    $expected = Join-Path (Resolve-Path -LiteralPath $build).ProviderPath 'classes'
    $entry = Get-Item -LiteralPath $resolved
    if ($resolved -ne $expected -or ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Unexpected compilation directory: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
New-Item -Type Directory $classes | Out-Null
$version = '0.32.0'
$dependencies = [ordered]@{
    'commonmark' = 'A7075291A1FF852DF6E185963A0016D24913ABF689F4F913D017D9FF19A14AC2'
    'commonmark-ext-gfm-tables' = '511B9A282C991560ACAB6AB087A7F25E4CE94747AA3BFA6E08BE32D9ADC77BE0'
    'commonmark-ext-gfm-strikethrough' = '77AAFC2C0B4D11DB6F8A38D01D20F78EC9095B39379E768FE2007708A71F8303'
    'commonmark-ext-task-list-items' = '1C6C949B8F1995BE5D3CE6B46E3974DD47E04380928EE4CFFAEFCC3E3B6409C6'
    'commonmark-ext-autolink' = '9DB3051BBF64012BC353B0B7B221B6558E17A56FC47241C9250AA0FAD663C4AF'
    'autolink' = '04647EF989BD8096CFC253A8B5B8C36760918E3F163C1D918DF6DFBFB79AA8C6'
}
$libraries = @(foreach ($name in $dependencies.Keys) {
    $artifactVersion = if ($name -eq 'autolink') { '0.12.0' } else { $version }
    $groupPath = if ($name -eq 'autolink') { 'org/nibor/autolink' } else { 'org/commonmark' }
    $jar = Join-Path $build "$name-$artifactVersion.jar"
    if (!(Test-Path $jar)) {
        $url = "https://repo.maven.apache.org/maven2/$groupPath/$name/$artifactVersion/$name-$artifactVersion.jar"
        Invoke-Tool 'curl.exe' @('-fLsS','--retry','2',$url,'-o',$jar)
    }
    if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $dependencies[$name]) {
        throw "Unexpected SHA-256 for $jar."
    }
    $jar
})
$res = Join-Path $build 'resources.zip'
$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'
$apk = Join-Path $build 'Asteria.apk'

Invoke-Tool "$tool/aapt2.exe" @('compile','--dir',"$main/res",'-o',$res)
Invoke-Tool "$tool/aapt2.exe" @('link','-o',$unsigned,'-I',$android,'--java',$build,'--manifest',"$main/AndroidManifest.xml",'-A',"$main/assets",$res)
$sources = @(Get-ChildItem "$main/java" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Tool "$JavaHome/bin/javac.exe" (@('-encoding','UTF-8','--release','8','-classpath',((@($android) + $libraries) -join ';'),'-d',$classes) + $sources + "$build/com/jingliu/chat/R.java")
$bytecode = @(Get-ChildItem $classes -Recurse -Filter '*.class' | ForEach-Object FullName)
Invoke-Tool "$tool/d8.bat" (@('--lib',$android,'--min-api','26','--output',$build) + $bytecode + $libraries)
$entities = 'org/commonmark/internal/util/entities.txt'
Push-Location $classes
try {
    Invoke-Tool "$JavaHome/bin/jar.exe" @('xf',(Join-Path $build "commonmark-$version.jar"),$entities)
} finally {
    Pop-Location
}
Invoke-Tool "$JavaHome/bin/jar.exe" @('uf',$unsigned,'-C',$build,'classes.dex','-C',$classes,$entities,'-C',$PSScriptRoot,'LICENSE')
Invoke-Tool "$tool/zipalign.exe" @('-P','16','-f','4',$unsigned,$aligned)

if (!(Test-Path $SigningStore)) {
    New-Item -Type Directory -Force (Split-Path -Parent $SigningStore) | Out-Null
    Invoke-Tool "$JavaHome/bin/keytool.exe" @('-genkeypair','-keystore',$SigningStore,'-alias','jingliu-debug','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Jingliu Local Build')
}
Invoke-Tool "$tool/apksigner.bat" @('sign','--ks',$SigningStore,'--ks-key-alias','jingliu-debug','--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Invoke-Tool "$tool/apksigner.bat" @('verify','--verbose',$apk)
Write-Output $apk
