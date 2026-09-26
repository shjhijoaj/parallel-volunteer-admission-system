[CmdletBinding()]
param([string]$JdkHome,[switch]$SkipBuild)
$ErrorActionPreference='Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if($JdkHome){$env:JAVA_HOME=$JdkHome}
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
& $java -version
if($LASTEXITCODE -ne 0){throw 'Java 17 is required. Set JAVA_HOME.'}
$jar='dist/course-selection-platform.jar'
if(-not (Test-Path $jar)){
 $jar='target/parallel-volunteer-admission-system-2.0.0.jar'
 if(-not $SkipBuild){& ./mvnw.cmd -B package; if($LASTEXITCODE -ne 0){throw 'Build failed'}}
}
if(-not (Test-Path $jar)){throw 'JAR missing. Build with mvnw.cmd package.'}
Write-Host 'Open http://127.0.0.1:8088 (unless PORT is set). Initial admin credential: data/admin-credentials.txt'
& $java '-Duser.timezone=Asia/Shanghai' -jar $jar
exit $LASTEXITCODE
