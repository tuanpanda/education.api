#Requires -Version 5.1
<#
.SYNOPSIS
    Sao luu EDUCATION: schema Oracle (Data Pump expdp) + volume file dinh kem (Docker), kem SHA-256 va xoa ban cu.

.DESCRIPTION
    Xem docs/deploy-internet.md (muc "Sao luu va khoi phuc").
    * Oracle: expdp schemas=<schema> vao DATA_PUMP_DIR, FLASHBACK_TIME=SYSTIMESTAMP (ban chup nhat quan).
      Mat khau KHONG nam tren dong lenh: doc tu bien moi truong EDU_DB_PASSWORD (hoac nhap an), ghi vao parfile
      tam (chi user hien tai doc duoc) va xoa ngay sau khi expdp chay xong.
    * File dinh kem: docker run --rm -v <volume>:/data:ro alpine tar czf ... (chi DOC volume, khong dung container
      dang chay).
    * Ket qua: <BackupRoot>\<yyyyMMdd-HHmmss>\{education-*.dmp, expdp-*.log, uploads-*.tar.gz, SHA256SUMS.txt}.
    * Xoa thu muc sao luu cu hon -RetentionDays ngay (chi trong BackupRoot, chi thu muc dang yyyyMMdd-HHmmss).

    Quyen can co (DBA cap mot lan): GRANT READ, WRITE ON DIRECTORY DATA_PUMP_DIR TO EDUCATION;

.EXAMPLE
    $env:EDU_DB_PASSWORD = "<mat khau>"
    .\scripts\ops\backup.ps1 -BackupRoot D:\Backup\education -RetentionDays 14

.EXAMPLE
    # Chi sao luu Oracle (bo qua volume Docker)
    .\scripts\ops\backup.ps1 -SkipUploads
#>
[CmdletBinding()]
param(
    [string]$BackupRoot = "D:\Backup\education",
    [string]$OracleHome = "F:\Database",
    [string]$ConnectString = "localhost:1521/ORCL",
    [string]$DbUser = "EDUCATION",
    [string]$Schema = "EDUCATION",
    [string]$DataPumpDirectory = "DATA_PUMP_DIR",
    # Duong dan vat ly cua DATA_PUMP_DIR tren may chu Oracle. Bo trong: hoi Oracle (ALL_DIRECTORIES).
    [string]$DataPumpPath = "",
    [string]$UploadsVolume = "education-prod-outputs",
    [string]$HelperImage = "alpine:3.20",
    [int]$RetentionDays = 14,
    [switch]$SkipOracle,
    [switch]$SkipUploads
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $BackupRoot $stamp
New-Item -ItemType Directory -Force -Path $target | Out-Null
Write-Host "Sao luu vao $target"

function Get-DbPassword {
    if ($env:EDU_DB_PASSWORD) { return $env:EDU_DB_PASSWORD }
    $secure = Read-Host -AsSecureString "Mat khau Oracle cua $DbUser"
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function New-PrivateTempFile([string]$content) {
    $path = Join-Path ([IO.Path]::GetTempPath()) ("edu-" + [Guid]::NewGuid().ToString("N") + ".par")
    [IO.File]::WriteAllText($path, $content, (New-Object Text.UTF8Encoding($false)))
    # Chi user hien tai doc duoc parfile (chua mat khau).
    $acl = Get-Acl $path
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule(
        [Security.Principal.WindowsIdentity]::GetCurrent().Name, "FullControl", "Allow")
    $acl.SetAccessRule($rule)
    Set-Acl -Path $path -AclObject $acl
    return $path
}

function Invoke-SqlScalar([string]$password, [string]$sql) {
    $sqlplus = Join-Path $OracleHome "bin\sqlplus.exe"
    $script = "SET HEADING OFF FEEDBACK OFF PAGESIZE 0 VERIFY OFF ECHO OFF`nCONNECT $DbUser/`"$password`"@$ConnectString`n$sql`nEXIT`n"
    $file = New-PrivateTempFile $script
    try {
        $output = & $sqlplus -S -L /nolog "@$file"
        if ($LASTEXITCODE -ne 0) { throw "sqlplus loi ($LASTEXITCODE)" }
        $first = @($output | Where-Object { $_ -and $_.Trim() }) | Select-Object -First 1
        if ($null -eq $first) { return "" }
        return ([string]$first).Trim()
    } finally {
        Remove-Item -Force $file -ErrorAction SilentlyContinue
    }
}

if (-not $SkipOracle) {
    $password = Get-DbPassword
    $expdp = Join-Path $OracleHome "bin\expdp.exe"
    if (-not (Test-Path $expdp)) { throw "Khong tim thay $expdp (dat -OracleHome)" }
    if (-not $DataPumpPath) {
        $DataPumpPath = Invoke-SqlScalar $password ("SELECT DIRECTORY_PATH FROM ALL_DIRECTORIES WHERE DIRECTORY_NAME = '" + $DataPumpDirectory + "';")
        if (-not $DataPumpPath) { throw "Khong doc duoc duong dan $DataPumpDirectory (thieu quyen? dat -DataPumpPath)" }
    }
    $dumpName = "education-$stamp.dmp"
    $logName = "expdp-$stamp.log"
    $parfile = New-PrivateTempFile (@(
        "userid=$DbUser/`"$password`"@$ConnectString",
        "schemas=$Schema",
        "directory=$DataPumpDirectory",
        "dumpfile=$dumpName",
        "logfile=$logName",
        "flashback_time=SYSTIMESTAMP",
        "exclude=STATISTICS"
    ) -join "`r`n")
    try {
        Write-Host "expdp schemas=$Schema -> $DataPumpDirectory\$dumpName"
        & $expdp "parfile=$parfile"
        $code = $LASTEXITCODE
    } finally {
        Remove-Item -Force $parfile -ErrorAction SilentlyContinue
        $password = $null
    }
    # expdp tra 5 khi hoan tat co canh bao (EX_SUCC_ERR) - van giu file, kiem tra log.
    if ($code -ne 0 -and $code -ne 5) { throw "expdp loi ($code) - xem $DataPumpPath\$logName" }
    Move-Item -Force (Join-Path $DataPumpPath $dumpName) $target
    Copy-Item -Force (Join-Path $DataPumpPath $logName) $target
}

if (-not $SkipUploads) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"   # PS 5.1: stderr cua lenh ngoai bi chuyen huong khong duoc thanh loi dung
    docker volume inspect $UploadsVolume *> $null
    $inspect = $LASTEXITCODE
    $ErrorActionPreference = $previous
    if ($inspect -ne 0) { throw "Khong thay Docker volume '$UploadsVolume' (dat -UploadsVolume)" }
    $archive = "uploads-$stamp.tar.gz"
    Write-Host "tar volume $UploadsVolume -> $archive"
    docker run --rm -v "${UploadsVolume}:/data:ro" -v "${target}:/backup" $HelperImage `
        tar czf "/backup/$archive" -C /data .
    if ($LASTEXITCODE -ne 0) { throw "Sao luu volume loi ($LASTEXITCODE)" }
}

$sums = Get-ChildItem -File $target | Where-Object { $_.Name -ne "SHA256SUMS.txt" } | ForEach-Object {
    "{0}  {1}" -f (Get-FileHash -Algorithm SHA256 $_.FullName).Hash.ToLowerInvariant(), $_.Name
}
[IO.File]::WriteAllLines((Join-Path $target "SHA256SUMS.txt"), [string[]]@($sums))
Write-Host "Da ghi SHA256SUMS.txt ($(@($sums).Count) file)"

if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -Directory $BackupRoot |
        Where-Object { $_.Name -match '^\d{8}-\d{6}$' -and $_.FullName -ne $target -and $_.LastWriteTime -lt $cutoff } |
        ForEach-Object {
            Write-Host "Xoa ban sao luu cu: $($_.FullName)"
            Remove-Item -Recurse -Force $_.FullName
        }
}

Write-Host "Hoan tat: $target"
