# Stops only the test copy of the game (never the user's own Celaria)
Get-Process Celaria -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*Celaria_modtest*" } | ForEach-Object { Stop-Process -Id $_.Id -Force; "stopped test game $($_.Id)" }
