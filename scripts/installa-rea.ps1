# Installa REA (morluto/rea, licenza MIT) su Windows e la registra come server MCP in
# Claude Code e Claude Desktop, con quello che serve per analizzare le app Android:
# Node.js, adb, JDK e JADX. Tutto gratuito, non serve amministratore.
#
# Da PowerShell:
#   powershell -ExecutionPolicy Bypass -File scripts\installa-rea.ps1
# Da Claude Code (senza domande; Claude Desktop viene registrato solo se è chiuso):
#   powershell -ExecutionPolicy Bypass -File scripts\installa-rea.ps1 -SenzaDomande
#
# Poi riavvia Claude Code / Claude Desktop: trovi gli strumenti di REA
# (pull_adb_package, inspect_android_package, inspect_apk_signature, ...).

param([switch]$SenzaDomande)

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

Step 'Registrazione in Claude Code e Claude Desktop'
$clients = @('claude_code', 'claude_desktop')
# Claude Desktop aperto può riscrivere il suo file e perdere la registrazione.
if (Get-Process -Name 'Claude' -ErrorAction SilentlyContinue) {
    if ($SenzaDomande) {
        Write-Host '  Claude Desktop è aperto: registro solo Claude Code. Per Claude Desktop chiudilo e rilancia lo script.' -ForegroundColor Yellow
        $clients = @('claude_code')
    } else {
        Write-Host '  Claude Desktop è aperto: chiudilo del tutto (anche dall''icona vicino all''orologio).' -ForegroundColor Yellow
        while (Get-Process -Name 'Claude' -ErrorAction SilentlyContinue) {
            Read-Host '  Premi Invio quando è chiuso'
        }
    }
}
$setupArgs = @('setup', '--tool-families', 'android') + ($clients | ForEach-Object { '--client', $_ })
# Senza -SenzaDomande, setup mostra le modifiche ai file di configurazione e chiede conferma.
if ($SenzaDomande) { $setupArgs += '--yes' }
& rea @setupArgs

Step 'Controllo'
$doctorArgs = @('doctor', '--provider', 'jadx') + ($clients | ForEach-Object { '--client', $_ })
& rea @doctorArgs

Write-Host "`nFatto. Riavvia Claude Code / Claude Desktop, collega il telefono con il debug USB attivo" -ForegroundColor Green
Write-Host "e accetta la richiesta di autorizzazione sul telefono." -ForegroundColor Green
