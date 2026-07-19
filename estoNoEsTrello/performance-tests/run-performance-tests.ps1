<#
.SYNOPSIS
  Ejecuta los pasos de siembra de datos (PerformanceDataSeeder) y las
  corridas de JMeter para CP-S-01 a CP-S-04, sin tener que pegar comandos
  largos a mano en la terminal (evita el error de PowerShell que corta
  una linea larga pegada y rompe "-Dexec.mainClass=...").

.USO
  Parado en la carpeta estoNoEsTrello (donde esta el pom.xml), correr:

    .\performance-tests\run-performance-tests.ps1 seed-nominal
    .\performance-tests\run-performance-tests.ps1 seed-max
    .\performance-tests\run-performance-tests.ps1 cp-s01
    .\performance-tests\run-performance-tests.ps1 cp-s02-10
    .\performance-tests\run-performance-tests.ps1 cp-s02-50
    .\performance-tests\run-performance-tests.ps1 cp-s02-100
    .\performance-tests\run-performance-tests.ps1 cp-s03
    .\performance-tests\run-performance-tests.ps1 cp-s04-p95
    .\performance-tests\run-performance-tests.ps1 cp-s04-100

  La primera vez, si Windows bloquea la ejecucion de scripts, correr una
  sola vez (en esa misma terminal):

    Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        "seed-nominal", "seed-max",
        "cp-s01",
        "cp-s02-10", "cp-s02-50", "cp-s02-100",
        "cp-s03",
        "cp-s04-p95", "cp-s04-100"
    )]
    [string]$Action
)

$ErrorActionPreference = "Stop"
$jmx = "performance-tests\jmeter\kanban-crud-plan.jmx"
$resultsDir = "performance-tests\results"

function Run-Seeder {
    param([string]$Scenario)
    Write-Host "Sembrando escenario '$Scenario'..." -ForegroundColor Cyan
    & mvn -q test-compile exec:java `
        "-Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.testutils.PerformanceDataSeeder" `
        "-Dexec.classpathScope=test" `
        "-Dexec.args=$Scenario"
}

function Run-Jmeter {
    param(
        [string]$Name,
        [int]$Threads,
        [int]$Rampup,
        [int]$Duration
    )
    $jtl = Join-Path $resultsDir "$Name.jtl"
    $report = Join-Path $resultsDir "$Name-report"

    if (Test-Path $report) {
        Write-Host "Borrando reporte anterior $report ..." -ForegroundColor Yellow
        Remove-Item -Recurse -Force $report
    }
    if (-not (Test-Path $resultsDir)) {
        New-Item -ItemType Directory -Path $resultsDir | Out-Null
    }

    Write-Host "Ejecutando JMeter: $Name (threads=$Threads, rampup=$Rampup, duration=$Duration s)..." -ForegroundColor Cyan
    & jmeter -n -t $jmx `
        "-Jthreads=$Threads" "-Jrampup=$Rampup" "-Jduration=$Duration" `
        -l $jtl -e -o $report

    Write-Host "Listo. Abri el reporte en: $report\index.html" -ForegroundColor Green
}

switch ($Action) {
    "seed-nominal" { Run-Seeder -Scenario "nominal" }
    "seed-max"     { Run-Seeder -Scenario "max" }

    "cp-s01"       { Run-Jmeter -Name "cp-s01"      -Threads 10  -Rampup 10 -Duration 120 }

    "cp-s02-10"    { Run-Jmeter -Name "cp-s02-10u"  -Threads 10  -Rampup 10 -Duration 300 }
    "cp-s02-50"    { Run-Jmeter -Name "cp-s02-50u"  -Threads 50  -Rampup 30 -Duration 300 }
    "cp-s02-100"   { Run-Jmeter -Name "cp-s02-100u" -Threads 100 -Rampup 60 -Duration 300 }

    "cp-s03"       { Run-Jmeter -Name "cp-s03"      -Threads 50  -Rampup 30 -Duration 900 }

    "cp-s04-p95"   { Run-Jmeter -Name "cp-s04-p95"   -Threads 10  -Rampup 10 -Duration 120 }
    "cp-s04-100"   { Run-Jmeter -Name "cp-s04-100u"  -Threads 100 -Rampup 60 -Duration 300 }
}
