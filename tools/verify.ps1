[CmdletBinding()]
param([string]$JdkHome,[switch]$UnitOnly)
$ErrorActionPreference='Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
if($JdkHome){$env:JAVA_HOME=$JdkHome}
if($UnitOnly){& ./mvnw.cmd -B '-Dtest=EnrollEngineTest' test}
else{& ./mvnw.cmd -B test}
exit $LASTEXITCODE
