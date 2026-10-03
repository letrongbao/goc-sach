$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & ./mvnw.cmd -q test-compile dependency:build-classpath '-Dmdep.outputFile=target/payment-preview-classpath.txt' '-DincludeScope=test'
    if ($LASTEXITCODE -ne 0) { throw 'Payment preview build failed' }
    $paymentPreviewDependencies = (Get-Content -LiteralPath 'target/payment-preview-classpath.txt' -Raw).Trim()
    & java '-Dfile.encoding=UTF-8' -cp "target/test-classes;target/classes;$paymentPreviewDependencies" vn.edu.utex.bookstore.PaymentPreviewServer
    if ($LASTEXITCODE -ne 0) { throw 'Payment preview failed' }
} finally { Pop-Location }
