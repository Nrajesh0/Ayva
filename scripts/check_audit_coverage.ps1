# check_audit_coverage.ps1
# ─────────────────────────────────────────────────────────────────────────────
# Ayva Audit Coverage & Change Detection Tool
#
# Usage:
#   .\scripts\check_audit_coverage.ps1 -Since a752be3
#   .\scripts\check_audit_coverage.ps1 -Since HEAD~10
#   .\scripts\check_audit_coverage.ps1 -Since a752be3 -UpdateTracker
#   .\scripts\check_audit_coverage.ps1 -All
#
# What it does:
#   1. Runs `git diff --name-status <Since>..HEAD` to detect:
#        [ADDED]    New Kotlin source files
#        [MODIFIED] Existing files with code changes
#        [DELETED]  Files that were removed
#   2. Cross-references against AUDIT_TRACKER.md:
#        [AUDITED]  - Covered in previous batches
#        [PENDING]  - In tracker but pending audit (e.g. Batch 9)
#        [MISSING]  - Not tracked in AUDIT_TRACKER.md at all
#   3. If -UpdateTracker is specified:
#        - Automatically appends missing/new files to the Pending Audit Queue
#        - Adds new files to the File Coverage Map as ⚪ Not Audited
#        - Updates deleted files in the File Coverage Map as 🗑️ Deleted
# ─────────────────────────────────────────────────────────────────────────────

param(
    [string]$Since          = "HEAD~5",
    [string]$Tracker        = "AUDIT_TRACKER.md",
    [switch]$All,
    [switch]$UpdateTracker
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

if (-not (Test-Path ".git"))   { Write-Error "Must be run from repo root."; exit 1 }
if (-not (Test-Path $Tracker)) { Write-Error "Tracker file '$Tracker' not found."; exit 1 }

$trackerFullPath = Resolve-Path $Tracker
$trackerContent  = [System.IO.File]::ReadAllText($trackerFullPath, [System.Text.Encoding]::UTF8)

# ── Data structures for detected items ───────────────────────────────────────
class ChangeItem {
    [string]$File
    [string]$StatusType # ADDED, MODIFIED, DELETED, RENAMED
    [string]$RawStatus
    [string]$Path
}

$items = [System.Collections.Generic.List[ChangeItem]]::new()

if ($All) {
    Write-Output ""
    Write-Output "Scanning ALL Kotlin source files in repository..."
    $allFiles = Get-ChildItem -Recurse -Filter "*.kt" -Path "app/src/main/java" |
                Select-Object -ExpandProperty Name | Sort-Object -Unique
    foreach ($f in $allFiles) {
        $item = [ChangeItem]::new()
        $item.File       = $f
        $item.StatusType = "EXISTING"
        $item.RawStatus  = "E"
        $item.Path       = $f
        $items.Add($item)
    }
} else {
    Write-Output ""
    Write-Output "Inspecting git changes since '$Since'..."
    $diffLines = & git diff --name-status "$Since..HEAD" -- "*.kt" 2>&1
    
    foreach ($line in ($diffLines -join "`n" -split "`n")) {
        $trimmed = $line.Trim()
        if (-not $trimmed) { continue }

        $parts = $trimmed -split "`t"
        if ($parts.Count -lt 2) { continue }

        $statusCode = $parts[0].Trim()
        # In case of rename (R100 old\path new\path), take destination path
        $filePath   = if ($parts.Count -ge 3) { $parts[2].Trim() } else { $parts[1].Trim() }

        # Filter strictly for production Kotlin sources
        if ($filePath -notmatch "app/src/main/java") { continue }

        $fileName = Split-Path $filePath -Leaf
        $item = [ChangeItem]::new()
        $item.File      = $fileName
        $item.Path      = $filePath
        $item.RawStatus = $statusCode

        if ($statusCode -like "A*") {
            $item.StatusType = "ADDED"
        } elseif ($statusCode -like "M*") {
            $item.StatusType = "MODIFIED"
        } elseif ($statusCode -like "D*") {
            $item.StatusType = "DELETED"
        } elseif ($statusCode -like "R*") {
            $item.StatusType = "RENAMED"
        } else {
            $item.StatusType = "CHANGED ($statusCode)"
        }
        $items.Add($item)
    }
}

if ($items.Count -eq 0) {
    Write-Output "  No Kotlin source file changes found since '$Since'."
    exit 0
}

# ── Classify items against AUDIT_TRACKER.md ───────────────────────────────────
$auditedList = [System.Collections.Generic.List[ChangeItem]]::new()
$pendingList = [System.Collections.Generic.List[ChangeItem]]::new()
$missingList = [System.Collections.Generic.List[ChangeItem]]::new()
$deletedList = [System.Collections.Generic.List[ChangeItem]]::new()

foreach ($it in $items) {
    if ($it.StatusType -eq "DELETED") {
        $deletedList.Add($it)
        continue
    }

    $escapedFile       = [regex]::Escape($it.File)
    $backtickPattern   = '`' + $escapedFile + '`'
    $notAuditedPattern = '`' + $escapedFile + '`[^\n]*Not Audited'

    if ($trackerContent -match $backtickPattern) {
        if ($trackerContent -match $notAuditedPattern) {
            $pendingList.Add($it)
        } else {
            $auditedList.Add($it)
        }
    } else {
        $missingList.Add($it)
    }
}

# ── Display Summary ───────────────────────────────────────────────────────────
Write-Output ""

if ($auditedList.Count -gt 0) {
    Write-Output "=== [AUDITED & PROTECTED] ($($auditedList.Count)) ==="
    foreach ($a in $auditedList) {
        Write-Output ("  [OK]        [{0,-8}] {1}" -f $a.StatusType, $a.File)
    }
    Write-Output ""
}

if ($pendingList.Count -gt 0) {
    Write-Output "=== [PENDING AUDIT] ($($pendingList.Count)) in tracker ==="
    foreach ($p in $pendingList) {
        Write-Output ("  [PENDING]   [{0,-8}] {1}" -f $p.StatusType, $p.File)
    }
    Write-Output ""
}

if ($missingList.Count -gt 0) {
    Write-Output "=== [MISSING FROM TRACKER] ($($missingList.Count)) brand new ==="
    foreach ($m in $missingList) {
        Write-Output ("  [MISSING]   [{0,-8}] {1}" -f $m.StatusType, $m.File)
    }
    Write-Output ""
}

if ($deletedList.Count -gt 0) {
    Write-Output "=== [DELETED IN GIT] ($($deletedList.Count)) ==="
    foreach ($d in $deletedList) {
        Write-Output ("  [DELETED]   [{0,-8}] {1}" -f $d.StatusType, $d.File)
    }
    Write-Output ""
}

# ── Handle -UpdateTracker ─────────────────────────────────────────────────────
if ($UpdateTracker) {
    Write-Output "-------------------------------------------------------------"
    Write-Output "Updating $Tracker..."
    $today = Get-Date -Format "yyyy-MM-dd"
    $modifiedContent = $trackerContent
    $updatedCount = 0

    # 1. Add any MISSING files to File Coverage Map
    if ($missingList.Count -gt 0) {
        foreach ($m in $missingList) {
            $bt = [char]96
            $mapRow = "| " + $bt + $m.File + $bt + " | - | [Not Audited] | - |`n"
            $mapPattern = '(?s)(## .*?File Coverage Map.*?\| `[^\n]+\|[^\n]*\n)(\r?\n---)'
            if ($modifiedContent -match $mapPattern) {
                $replacement = '${1}' + $mapRow + '${2}'
                $modifiedContent = [regex]::Replace($modifiedContent, $mapPattern, $replacement)
                Write-Output "  + Added to File Coverage Map: $($m.File)"
                $updatedCount++
            }
        }
    }

    # 2. Add MISSING or UNQUEUED items into Pending Audit Queue
    $queueCandidates = @($missingList) + @($pendingList)
    foreach ($c in $queueCandidates) {
        # Check if already present in the Pending Audit Queue section
        $queueSectionMatch = [regex]::Match($modifiedContent, '(?s)## .*?Pending Audit Queue.*?(?=\r?\n---|\Z)')
        if ($queueSectionMatch.Success) {
            $queueText = $queueSectionMatch.Value
            $bt = [char]96
            $filePattern = $bt + [regex]::Escape($c.File) + $bt
            if ($queueText -notmatch $filePattern) {
                $desc = "**$($c.StatusType)** via git diff since $Since"
                $newQueueRow = "| " + $bt + $c.File + $bt + " | $desc | $today |`n"

                $tableHeadPattern = '(?s)(## .*?Pending Audit Queue.*?\|:---\|:---\|:---\|\r?\n)'
                $modifiedContent = [regex]::Replace($modifiedContent, $tableHeadPattern, ('${1}' + $newQueueRow))
                Write-Output "  + Queued in Pending Audit Queue: $($c.File) ($($c.StatusType))"
                $updatedCount++
            }
        }
    }

    # 3. Handle DELETED files
    if ($deletedList.Count -gt 0) {
        foreach ($d in $deletedList) {
            $escaped = [regex]::Escape($d.File)
            $delPattern = '(\| `*' + $escaped + '`* \| [^|]+ \| )[^|]+( \| [^|]+ \|)'
            if ($modifiedContent -match $delPattern) {
                $modifiedContent = [regex]::Replace($modifiedContent, $delPattern, ('${1}[Deleted ' + $today + ']${2}'))
                Write-Output "  ~ Marked as Deleted in Coverage Map: $($d.File)"
                $updatedCount++
            }
        }
    }

    if ($updatedCount -gt 0) {
        $utf8Encoding = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($trackerFullPath, $modifiedContent, $utf8Encoding)
        Write-Output "[DONE] Successfully updated $Tracker ($updatedCount change(s) applied)."
    } else {
        Write-Output "[INFO] $Tracker is already up to date with all detected changes."
    }
} else {
    $actionNeeded = $pendingList.Count + $missingList.Count + $deletedList.Count
    if ($actionNeeded -gt 0) {
        Write-Output "-------------------------------------------------------------"
        Write-Output "TIP: Run with -UpdateTracker to automatically sync these"
        Write-Output "     changes into AUDIT_TRACKER.md without manual editing:"
        Write-Output "     .\scripts\check_audit_coverage.ps1 -Since $Since -UpdateTracker"
    } else {
        Write-Output "All changed files are fully audited! Everything is clean."
    }
}
Write-Output ""
