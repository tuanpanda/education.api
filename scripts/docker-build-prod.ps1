#Requires -Version 5.1
param(
    [string]$ImageName = "education-api",
    [string]$Tag = ""
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$sha = "unknown"
try { $sha = (git rev-parse --short HEAD).Trim() } catch { }

$pom = Select-Xml -Path pom.xml -XPath "/*[local-name()='project']/*[local-name()='version']"
$pomVersion = if ($pom) { $pom.Node.InnerText.Trim() } else { "0.0.0" }
$date = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$buildVersion = if ($Tag) { $Tag.TrimStart("v") } else { "$pomVersion+local.$sha" }

$tags = @(
    "${ImageName}:prod",
    "${ImageName}:sha-$sha",
    "${ImageName}:prod-local-$sha"
)
if ($Tag) {
    $tags += "${ImageName}:$Tag"
    $tags += "${ImageName}:prod-$Tag"
}

$tagArgs = @()
foreach ($t in $tags) {
    $tagArgs += "-t"
    $tagArgs += $t
}

Write-Host "Building PRODUCTION image $buildVersion"
docker build `
    --target production `
    --build-arg "BUILD_VERSION=$buildVersion" `
    --build-arg "GIT_SHA=$sha" `
    --build-arg "BUILD_DATE=$date" `
    @tagArgs `
    .

Write-Host "Tagged:"
$tags | ForEach-Object { Write-Host "  $_" }
