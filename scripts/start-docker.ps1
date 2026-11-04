# Starts Docker Desktop on machines where it fails with
# "remove ...\dockerInference: The file cannot be accessed by the system".
# Sockets left from the previous run cannot be deleted at all, so their folders are moved aside.
# Docker creates new ones. The folders hold nothing but those sockets.

$ErrorActionPreference = 'Continue'
$local = $env:LOCALAPPDATA
$socketFolders = @("$local\Docker\run", "$local\docker-secrets-engine")

function Test-Engine {
    $probe = Start-Process docker -ArgumentList 'info' -NoNewWindow -PassThru `
        -RedirectStandardOutput "$env:TEMP\docker-info.txt" -RedirectStandardError "$env:TEMP\docker-info-err.txt"
    # PowerShell 5.1 leaves ExitCode empty unless the handle is read before the process ends.
    $null = $probe.Handle
    if ($probe.WaitForExit(8000)) { return $probe.ExitCode -eq 0 }
    $probe.Kill()
    return $false
}

if (Test-Engine) {
    Write-Output 'Docker already works.'
    exit 0
}

# Only reached when the engine is down, so an open error dialog is all that gets closed here.
Get-Process 'Docker Desktop', 'com.docker.backend' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 3

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
foreach ($folder in $socketFolders) {
    if (Test-Path $folder) {
        try {
            Rename-Item $folder "$(Split-Path $folder -Leaf).old-$stamp" -ErrorAction Stop
        } catch {
            Write-Output "Could not move $folder aside: $($_.Exception.Message)"
            exit 1
        }
    }
}

Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
$deadline = (Get-Date).AddMinutes(3)
while ((Get-Date) -lt $deadline) {
    if (Test-Engine) {
        Write-Output 'Docker is ready.'
        exit 0
    }
    Start-Sleep -Seconds 5
}
Write-Output 'Docker did not start in 3 minutes. Do not use "Reset to factory defaults".'
exit 1
