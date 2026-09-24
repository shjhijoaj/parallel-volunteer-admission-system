<#
.SYNOPSIS
    平行志愿录取系统本地一键启动脚本。

.DESCRIPTION
    1. 自动定位 JDK 与 Maven（优先使用环境变量，其次查找 IntelliJ 自带 Maven 与常见 JDK 目录）。
    2. 缺少可执行 jar 时自动执行 mvn -DskipTests package。
    3. 使用环境变量中的数据库配置启动应用。

.EXAMPLE
    $env:MYSQL_PASSWORD = "你的数据库密码"
    pwsh -NoProfile -ExecutionPolicy Bypass -File tools/run-dev.ps1
#>
[CmdletBinding()]
param(
    [string] $JdkHome,
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $projectRoot 'target\parallel-volunteer-admission-system-1.0.0.jar'

function Resolve-Jdk {
    param([string] $Explicit)
    $candidates = @()
    if ($Explicit) { $candidates += $Explicit }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        'D:\eage\开发环境与SDK\jdk-17_windows-x64_bin\jdk-17.0.3.1',
        'C:\Program Files\Java\jdk-17',
        'C:\Program Files\Java\jdk1.8.0_241',
        'C:\Program Files\Eclipse Adoptium\jdk-17*'
    ) | ForEach-Object { $_ }

    foreach ($candidate in $candidates) {
        foreach ($resolved in (Get-Item $candidate -ErrorAction SilentlyContinue)) {
            $javaExe = Join-Path $resolved.FullName 'bin\java.exe'
            if (Test-Path $javaExe) { return $resolved.FullName }
        }
    }
    throw '未找到 JDK。请设置 JAVA_HOME 指向 JDK 安装目录后重试。'
}

function Resolve-Maven {
    $onPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }

    $bundled = @(
        'D:\IDEA\IntelliJ IDEA 2025.1.2\plugins\maven\lib\maven3\bin\mvn.cmd',
        'C:\Program Files\JetBrains\*\plugins\maven\lib\maven3\bin\mvn.cmd'
    )
    foreach ($pattern in $bundled) {
        $found = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }

    $wrapper = Join-Path $projectRoot 'mvnw.cmd'
    if (Test-Path $wrapper) { return $wrapper }
    throw '未找到 Maven。请安装 Maven，或使用项目自带的 mvnw.cmd。'
}

$jdk = Resolve-Jdk -Explicit $JdkHome
$env:JAVA_HOME = $jdk
$java = Join-Path $jdk 'bin\java.exe'

# 数据库与账号配置：未设置的项使用默认值，密码必须由使用者提供。
if (-not $env:MYSQL_HOST) { $env:MYSQL_HOST = '127.0.0.1' }
if (-not $env:MYSQL_PORT) { $env:MYSQL_PORT = '3306' }
if (-not $env:MYSQL_DATABASE) { $env:MYSQL_DATABASE = 'db_enroll' }
if (-not $env:MYSQL_USER) { $env:MYSQL_USER = 'root' }
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }
if (-not $env:ADMIN_NAME) { $env:ADMIN_NAME = 'admin' }
if (-not $env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD = 'admin123' }
if (-not $env:SQL_INIT_MODE) { $env:SQL_INIT_MODE = 'never' }

if (-not $env:MYSQL_PASSWORD) {
    Write-Host '未设置 MYSQL_PASSWORD，将使用空密码连接数据库。' -ForegroundColor Yellow
    Write-Host '如需指定，请先执行： $env:MYSQL_PASSWORD = "你的数据库密码"' -ForegroundColor Yellow
}

Write-Host '=== 平行志愿录取系统 ===' -ForegroundColor Cyan
Write-Host ("JDK      : {0}" -f $jdk)
Write-Host ("数据库   : {0}:{1}/{2} (用户 {3})" -f $env:MYSQL_HOST, $env:MYSQL_PORT, $env:MYSQL_DATABASE, $env:MYSQL_USER)
Write-Host ("建表模式 : {0}" -f $env:SQL_INIT_MODE)
Write-Host ("服务端口 : {0}" -f $env:SERVER_PORT)
Write-Host ''
Write-Host '首次使用请确保数据库已创建，例如：' -ForegroundColor Yellow
Write-Host ("  CREATE DATABASE {0} CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" -f $env:MYSQL_DATABASE)
Write-Host '  首次建表可设置 $env:SQL_INIT_MODE = "always"（会清空同名表）'
Write-Host ''

if (-not $SkipBuild -and -not (Test-Path $jarPath)) {
    $maven = Resolve-Maven
    Write-Host ("未找到可执行 jar，开始构建：{0}" -f $maven) -ForegroundColor Cyan
    & $maven -B -f (Join-Path $projectRoot 'pom.xml') -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' }
}

Write-Host '启动中，按 Ctrl+C 停止服务。' -ForegroundColor Green
Write-Host ("管理台地址：http://127.0.0.1:{0}/admission/index.html" -f $env:SERVER_PORT) -ForegroundColor Green
& $java -jar $jarPath
