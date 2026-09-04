#Requires -Version 7.2
param([Parameter(Mandatory = $true)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$jwtDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$projectDirectory = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
if ($jwtDirectory.Equals($projectDirectory, [StringComparison]::OrdinalIgnoreCase) -or
    $jwtDirectory.StartsWith($projectDirectory + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Choose a private directory outside the repository.'
}
if (Test-Path -LiteralPath $jwtDirectory) { throw 'Directory already exists; no keys were replaced.' }
New-Item -ItemType Directory -Path $jwtDirectory | Out-Null
if ($IsWindows) {
    $jwtOwner = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $projectOwner = (Get-Acl -LiteralPath $projectDirectory).Owner
    & icacls $jwtDirectory /inheritance:r /grant:r "${jwtOwner}:(OI)(CI)F" "${projectOwner}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot protect the new directory; no keys generated.' }
}
$jwtRsa = [Security.Cryptography.RSA]::Create(3072)
try {
    [IO.File]::WriteAllText((Join-Path $jwtDirectory 'private.pem'), $jwtRsa.ExportPkcs8PrivateKeyPem())
    [IO.File]::WriteAllText((Join-Path $jwtDirectory 'public.pem'), $jwtRsa.ExportSubjectPublicKeyInfoPem())
} finally { $jwtRsa.Dispose() }
Write-Output 'Created a new RSA key pair. Private key contents are not displayed.'
