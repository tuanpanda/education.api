#Requires -Version 5.1
<#
.SYNOPSIS
  Cai GitHub Actions self-hosted runner tren may Windows (Docker Desktop).

.DESCRIPTION
  Runner nhan label: self-hosted, Windows, docker.
  Workflow .github/workflows/docker-production.yml se build image production
  moi khi co Pull Request hoac git tag v*.

  Token: GitHub repo -> Settings -> Actions -> Runners -> New self-hosted runner.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Token,

    [string]$RepoUrl = "https://github.com/tuanpanda/education.api",
    [string]$RunnerDir = "C:\actions-runner\education-api",
    [string]$RunnerName = "$env:COMPUTERNAME-docker"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Khong thay docker. Mo Docker Desktop truoc."
}
docker version | Out-Null

New-Item -ItemType Directory -Force -Path $RunnerDir | Out-Null
Set-Location $RunnerDir

if (-not (Test-Path ".\config.cmd")) {
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/actions/runner/releases/latest"
    $asset = $release.assets | Where-Object { $_.name -match "actions-runner-win-x64-.*\.zip$" } | Select-Object -First 1
    if (-not $asset) { throw "Khong tai duoc GitHub Actions runner Windows x64." }
    $zip = Join-Path $RunnerDir $asset.name
    Write-Host "Tai $($asset.name) ..."
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $RunnerDir -Force
    Remove-Item $zip -Force
}

& .\config.cmd --unattended --replace `
    --url $RepoUrl `
    --token $Token `
    --name $RunnerName `
    --labels "self-hosted,Windows,docker" `
    --work "_work"

Write-Host ""
Write-Host "Runner da cau hinh. Chay mot trong hai cach:"
Write-Host "  1) Tam thoi:  $RunnerDir\run.cmd"
Write-Host "  2) Service :  $RunnerDir\svc.cmd install  roi  $RunnerDir\svc.cmd start"
Write-Host ""
Write-Host "Giu Docker Desktop dang mo de job build image."
