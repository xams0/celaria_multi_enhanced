param([string]$action, [int]$x = 0, [int]$y = 0, [string]$key = "", [int]$ms = 0, [string]$out = "")
# Small UI helper for testing the game: click / key hold / screenshot
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class U {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
}
"@
function Shot($path) {
  $b = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
  $bmp = New-Object System.Drawing.Bitmap $b.Width, $b.Height
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($b.Location, [System.Drawing.Point]::Empty, $b.Size)
  $small = New-Object System.Drawing.Bitmap $bmp, ([int]($b.Width / 2)), ([int]($b.Height / 2))
  $small.Save($path)
}
$p = Get-Process Celaria -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Celaria_modtest*" } | Select-Object -First 1
if ($p) { [U]::SetForegroundWindow($p.MainWindowHandle) | Out-Null }
switch ($action) {
  "click" { [U]::SetCursorPos($x, $y); Start-Sleep -Milliseconds 100; [U]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 60; [U]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero) }
  "scankey" {
    $vk = [byte][int]$key; $sc = [byte][U]::MapVirtualKey($vk, 0)
    [U]::keybd_event(0, $sc, 8, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms; [U]::keybd_event(0, $sc, 10, [IntPtr]::Zero)
  }
  "key" {
    $vk = [byte][int]$key; $sc = [byte][U]::MapVirtualKey($vk, 0)
    [U]::keybd_event($vk, $sc, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms; [U]::keybd_event($vk, $sc, 2, [IntPtr]::Zero)
  }
  "shot" { }
}
Start-Sleep -Milliseconds 300
if ($out) { Shot $out; "shot $out" }
