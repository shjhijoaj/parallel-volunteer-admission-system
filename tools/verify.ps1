<#
.SYNOPSIS
    运行单元测试，并在配置了测试库时执行 MySQL 端到端集成测试。

.EXAMPLE
    $env:MYSQL_PASSWORD = "你的数据库密码"
    pwsh -NoProfile -ExecutionPolicy Bypass -File tools/verify.ps1

.EXAMPLE
    # 只跑不需要数据库的规则单元测试
    pwsh -NoProfile -ExecutionPolicy Bypass -File tools/verify.ps1 -UnitOnly
#>
[CmdletBinding()]
param(
    [string] $JdkHome,
    [switch] $UnitOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

function Resolve-Jdk {
    param([string] $Explicit)
    $candidates = @()
    if ($Explicit) { $candidates += $Explicit }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        'D:\eage\开发环境与SDK\jdk-17_windows-x64_bin\jdk-17.0.3.1',
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Java\jdk1.8.0_241'
    )
    foreach ($candidate in $candidates) {
        foreach ($resolved in (Get-Item $candidate -ErrorAction SilentlyContinue)) {
            $javaExe = Join-Path $resolved.FullName 'bin\java.exe'
            if (Test-Path $javaExe) { return $resolved.FullName }
        }
    }
    throw '未找到 JDK。请设置 JAVA_HOME 后重试。'
}

function Resolve-Maven {
    $onPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    foreach ($pattern in @(
            'D:\IDEA\IntelliJ IDEA 2025.1.2\plugins\maven\lib\maven3\bin\mvn.cmd',
            'C:\Program Files\JetBrains\*\plugins\maven\lib\maven3\bin\mvn.cmd')) {
        $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    $wrapper = Join-Path $projectRoot 'mvnw.cmd'
    if (Test-Path $wrapper) { return $wrapper }
    throw '未找到 Maven。'
}

$env:JAVA_HOME = Resolve-Jdk -Explicit $JdkHome
$maven = Resolve-Maven

if ($UnitOnly) {
    Write-Host '=== 只运行规则单元测试（无需数据库） ===' -ForegroundColor Cyan
    & $maven -B -f (Join-Path $projectRoot 'pom.xml') '-Dtest=EnrollEngineTest' test
    exit $LASTEXITCODE
}

if (-not $env:MYSQL_PASSWORD) {
    Write-Host '未设置 MYSQL_PASSWORD，只运行不需要数据库的规则单元测试。' -ForegroundColor Yellow
    Write-Host '如需执行端到端集成测试，请先设置 MYSQL_PASSWORD 与 MYSQL_DATABASE（建议使用独立测试库）。' -ForegroundColor Yellow
    & $maven -B -f (Join-Path $projectRoot 'pom.xml') '-Dtest=EnrollEngineTest' test
    exit $LASTEXITCODE
}

if (-not $env:MYSQL_DATABASE) { $env:MYSQL_DATABASE = 'enroll_it' }
if ($env:MYSQL_DATABASE -eq 'db_enroll') {
    throw '集成测试会执行 DROP TABLE，请把 MYSQL_DATABASE 设置为独立测试库（例如 enroll_it）。'
}

$env:ENROLL_IT = 'true'
Write-Host ('=== 单元测试 + MySQL 端到端集成测试（库：{0}） ===' -f $env:MYSQL_DATABASE) -ForegroundColor Cyan
& $maven -B -f (Join-Path $projectRoot 'pom.xml') test
exit $LASTEXITCODE
