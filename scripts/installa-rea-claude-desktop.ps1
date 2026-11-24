# Installa REA (morluto/rea, licenza MIT) come server MCP in Claude Desktop su Windows,
# con quello che serve per analizzare le app Android: Node.js, adb, JDK e JADX.
# Tutto gratuito.
#
# Uso, da PowerShell (non serve amministratore):
#   powershell -ExecutionPolicy Bypass -File scripts\installa-rea-claude-desktop.ps1
#
# Alla fine riapri Claude Desktop: in una chat nuova trovi gli strumenti di REA
# (pull_adb_package, inspect_android_package, inspect_apk_signature, ...).

$ErrorActionPreference = 'Stop'

$ReaVersion = '6.10.1'
$JadxUrl    = 'https://github.com/1013503897/jadx-headless-mcp/releases/download/v0.7.1/jadx-headless-mcp-0.7.1-all.jar'
$JadxSha256 = '6E5EACF500B64292BFB73C49797C1958F6EE44646E43E868039AE7FEB573FF75'
$ToolDir    = Join-Path $env:LOCALAPPDATA 'rea'

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

function Refresh-Path {
    $machine = [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $user    = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:Path = "$machine;$user"
}

function Set-UserEnv($name, $value) {
    [Environment]::SetEnvironmentVariable($name, $value, 'User')
    Set-Item -Path "Env:$name" -Value $value
    Write-Host "  $name = $value"
}

function Winget-Install($id) {
    if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
        throw "winget non trovato: installa «Programma di installazione app» dal Microsoft Store e riprova."
    }
    winget install --id $id -e --silent --accept-source-agreements --accept-package-agreements
    Refresh-Path
}

# REA richiede Node ^22.23.4, ^24.22.1 o >=26.12.1.
function Node-Ok {
    $node = Get-Command node -ErrorAction SilentlyContinue
    if (-not $node) { return $false }
    $v = [version]((& node -v).TrimStart('v'))
    return ($v.Major -eq 22 -and $v -ge [version]'22.23.4') -or
           ($v.Major -eq 24 -and $v -ge [version]'24.22.1') -or
           ($v -ge [version]'26.12.1')
}

Step 'Node.js'
if (-not (Node-Ok)) {
    Winget-Install 'OpenJS.NodeJS.LTS'
    if (-not (Node-Ok)) {
        throw "Node.js $(& node -v) è troppo vecchio per REA: installa l'ultima versione 24 da https://nodejs.org e rilancia lo script."
    }
}
Write-Host "  node $(& node -v)"

Step 'adb (Android platform-tools)'
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Winget-Install 'Google.PlatformTools' }
$adb = (Get-Command adb -ErrorAction Stop).Source
Set-UserEnv 'REA_ADB_PATH' $adb

Step 'JDK (serve a JADX)'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Winget-Install 'EclipseAdoptium.Temurin.21.JDK' }
$java = (Get-Command java -ErrorAction Stop).Source
if (-not $env:JAVA_HOME -and $java -notmatch 'javapath') {
    Set-UserEnv 'JAVA_HOME' (Split-Path (Split-Path $java -Parent) -Parent)
}
Write-Host "  java: $java"

Step 'JADX (lettura dei manifest)'
New-Item -ItemType Directory -Force -Path $ToolDir | Out-Null
$jar = Join-Path $ToolDir 'jadx-headless-mcp-0.7.1-all.jar'
if (-not (Test-Path $jar) -or (Get-FileHash $jar -Algorithm SHA256).Hash -ne $JadxSha256) {
    Invoke-WebRequest -Uri $JadxUrl -OutFile $jar -UseBasicParsing
}
$hash = (Get-FileHash $jar -Algorithm SHA256).Hash
if ($hash -ne $JadxSha256) {
    Remove-Item $jar
    throw "Lo SHA-256 del JAR di JADX non corrisponde ($hash): file scartato."
}
Set-UserEnv 'REA_JADX_MCP_JAR' $jar

Step "REA $ReaVersion"
npm install --global "rea-agents@$ReaVersion"
Refresh-Path

Step 'Registrazione in Claude Desktop'
if (Get-Process -Name 'Claude' -ErrorAction SilentlyContinue) {
    Write-Host '  Claude Desktop è aperto: chiudilo del tutto (anche dall''icona vicino all''orologio).' -ForegroundColor Yellow
    while (Get-Process -Name 'Claude' -ErrorAction SilentlyContinue) {
        Read-Host '  Premi Invio quando è chiuso'
    }
}
# Setup mostra le modifiche al file di configurazione e chiede conferma.
rea setup --client claude_desktop --tool-families android

Step 'Controllo'
rea doctor --client claude_desktop --provider jadx

Write-Host "`nFatto. Riapri Claude Desktop, collega il telefono con il debug USB attivo e accetta" -ForegroundColor Green
Write-Host "la richiesta di autorizzazione sul telefono. Cosa chiedere a Claude è in docs\compatibilita.md." -ForegroundColor Green
