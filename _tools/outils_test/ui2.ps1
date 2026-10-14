param([string]$action, [int]$x = 0, [int]$y = 0, [string]$key = "", [int]$ms = 0, [string]$out = "", [string]$text = "")
# UI helper for testing the game copies (Celaria_modtest / Celaria_injtest): click / key / text / screenshot.
# SAFETY: input is only sent when the foreground window belongs to a test copy of the game (never to the user's windows).
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class U2 {
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
  [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint type);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
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
$p = Get-Process Celaria -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Celaria_modtest*" -or $_.Path -like "*Celaria_injtest*" } | Select-Object -First 1
if ($action -ne "shot") {
  if (-not $p) { "NO TEST GAME RUNNING"; exit 1 }
  [U2]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
  Start-Sleep -Milliseconds 150
  $fpid = 0
  [U2]::GetWindowThreadProcessId([U2]::GetForegroundWindow(), [ref]$fpid) | Out-Null
  if ($fpid -ne $p.Id) { "GAME NOT IN FOREGROUND: nothing sent"; exit 2 }
}
switch ($action) {
  "click" { [U2]::SetCursorPos($x, $y); Start-Sleep -Milliseconds 100; [U2]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 60; [U2]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero) }
  "scankey" {
    $vk = [byte][int]$key; $sc = [byte][U2]::MapVirtualKey($vk, 0)
    [U2]::keybd_event(0, $sc, 8, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms; [U2]::keybd_event(0, $sc, 10, [IntPtr]::Zero)
  }
  "key" {
    $vk = [byte][int]$key; $sc = [byte][U2]::MapVirtualKey($vk, 0)
    [U2]::keybd_event($vk, $sc, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms; [U2]::keybd_event($vk, $sc, 2, [IntPtr]::Zero)
  }
  "holdshot" {
    $vk = [byte][int]$key; $sc = [byte][U2]::MapVirtualKey($vk, 0)
    [U2]::keybd_event($vk, $sc, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds $ms; if ($out) { Shot $out; "shot $out" }; [U2]::keybd_event($vk, $sc, 2, [IntPtr]::Zero); exit 0
  }
  "tabclick" {
    $vk = [byte]9; $sc = [byte][U2]::MapVirtualKey($vk, 0)
    [U2]::keybd_event($vk, $sc, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 700
    [U2]::SetCursorPos($x, $y); Start-Sleep -Milliseconds 200; [U2]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 60; [U2]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero)
    Start-Sleep -Milliseconds $ms; if ($out) { Shot $out; "shot $out" }; [U2]::keybd_event($vk, $sc, 2, [IntPtr]::Zero); exit 0
  }
  "text" { [System.Windows.Forms.SendKeys]::SendWait($text) }
  "shot" { }
}
Start-Sleep -Milliseconds 300
if ($out) { Shot $out; "shot $out" }
