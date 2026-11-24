# Rimasto per i link già esistenti: lo script vero è .claude\rea\installa-windows.ps1,
# uguale in tutte le repo (Node, JDK, JADX, Apktool, REA, registrazione in Claude Desktop).
#   powershell -ExecutionPolicy Bypass -File scripts\installa-rea.ps1 [-SenzaDomande]
param([switch]$SenzaDomande)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '..\.claude\rea\installa-windows.ps1') -SenzaDomande:$SenzaDomande
