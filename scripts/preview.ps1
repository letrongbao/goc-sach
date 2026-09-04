$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & ./mvnw.cmd -q test-compile dependency:build-classpath '-Dmdep.outputFile=target/preview-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Preview build failed' }
    $previewDependencies = (Get-Content -LiteralPath 'target/preview-classpath.txt' -Raw).Trim()
    & java '-Dfile.encoding=UTF-8' -cp "target/test-classes;target/classes;$previewDependencies" vn.edu.utex.bookstore.PreviewServer
    if ($LASTEXITCODE -ne 0) { throw 'Preview failed' }
} finally { Pop-Location }
