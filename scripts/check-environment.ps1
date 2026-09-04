param([Parameter(Mandatory = $true)][string]$Config)
$ErrorActionPreference = 'Stop'
$environmentConfig = (Resolve-Path -LiteralPath $Config).Path
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & ./mvnw.cmd -q compile dependency:build-classpath '-Dmdep.outputFile=target/runtime-classpath.txt' '-DincludeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    $runtimeDependencies = (Get-Content -LiteralPath target/runtime-classpath.txt -Raw).Trim()
    & java '-Dfile.encoding=UTF-8' "-Dbookstore.config=$environmentConfig" -cp "target/classes;$runtimeDependencies" vn.edu.utex.bookstore.config.EnvironmentCheck
    if ($LASTEXITCODE -ne 0) { throw 'Environment is not ready; see safe diagnostic results above.' }
} finally { Pop-Location }
