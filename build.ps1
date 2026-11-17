param(
    [Parameter(Mandatory=$true)][string]$Sdk,
    [string]$BuildDir = (Join-Path $PSScriptRoot 'build'),
    [string]$OutputDir = (Join-Path $PSScriptRoot 'apk')
)
$ErrorActionPreference='Stop'
function Run($tool, [string[]]$arguments) {
    & $tool @arguments
    if ($LASTEXITCODE -ne 0) { throw "$tool returned $LASTEXITCODE" }
}
$platform = Join-Path $Sdk 'platforms/android-35/android.jar'
$tools = (Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory | Where-Object Name -Match '^\d+\.\d+\.\d+$' | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1).FullName
New-Item -Type Directory -Force $BuildDir,$OutputDir | Out-Null
$key=Join-Path $BuildDir 'development.p12'
if (!(Test-Path $key)) {
    Run keytool @('-genkeypair','-keystore',$key,'-storepass','android','-keypass','android','-alias','mirror','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=Screen Mirror Development')
}
foreach ($role in 'phone','glasses') {
    $out=Join-Path $BuildDir $role
    $classes=Join-Path $out 'classes'
    New-Item -Type Directory -Force $classes | Out-Null
    $sources=Get-ChildItem "$PSScriptRoot/shared/*.java","$PSScriptRoot/$role/*.java" | ForEach-Object FullName
    Run javac (@('-encoding','UTF-8','--release','8','-classpath',$platform,'-d',$classes)+$sources)
    Run jar @('cf',"$out/classes.jar",'-C',$classes,'.')
    Run "$tools/d8.bat" @('--lib',$platform,'--min-api','28','--output',$out,"$out/classes.jar")
    Run "$tools/aapt2.exe" @('link','-I',$platform,'--manifest',"$PSScriptRoot/$role/AndroidManifest.xml",'-o',"$out/base.apk")
    Run jar @('uf',"$out/base.apk",'-C',$out,'classes.dex')
    Run "$tools/zipalign.exe" @('-f','4',"$out/base.apk","$out/aligned.apk")
    $apk=Join-Path $OutputDir "mirror-$role.apk"
    Run "$tools/apksigner.bat" @('sign','--ks',$key,'--ks-pass','pass:android','--v4-signing-enabled','false','--out',$apk,"$out/aligned.apk")
    Run "$tools/apksigner.bat" @('verify','--verbose',$apk)
}

