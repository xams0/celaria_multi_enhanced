param([int]$x, [int]$y, [string]$title = "Celaria Lanppart3", [string]$text = "")
# Click at (x, y) relative to the launcher window (only if it is in the foreground); optional text typed after the click
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class LC {
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern IntPtr FindWindow(string c, string t);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
}
"@
$h = [LC]::FindWindow([NullString]::Value, $title)
if ($h -eq [IntPtr]::Zero) { "window not found"; exit 1 }
$cls = if ("LC" -eq "LC") { [LC] } else { [W] }
if ($cls::IsIconic($h)) { $cls::ShowWindow($h, 9) | Out-Null; Start-Sleep -Milliseconds 400 }
if ([LC]::GetForegroundWindow() -ne $h) { [LC]::keybd_event(0x12, 0, 0, [IntPtr]::Zero); [LC]::keybd_event(0x12, 0, 2, [IntPtr]::Zero) }
[LC]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 300
if ([LC]::GetForegroundWindow() -ne $h) { "launcher not in foreground: nothing sent"; exit 2 }
$r = New-Object LC+RECT
[LC]::GetWindowRect($h, [ref]$r) | Out-Null
[LC]::SetCursorPos($r.L + $x, $r.T + $y) | Out-Null
Start-Sleep -Milliseconds 100
[LC]::mouse_event(2, 0, 0, 0, [IntPtr]::Zero); Start-Sleep -Milliseconds 50; [LC]::mouse_event(4, 0, 0, 0, [IntPtr]::Zero)
if ($text) {
  Start-Sleep -Milliseconds 200
  if ([LC]::GetForegroundWindow() -eq $h) { [System.Windows.Forms.SendKeys]::SendWait($text) }
}
"clicked"
