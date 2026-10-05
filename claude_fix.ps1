# ---------------------------------------------------------------------------
#  Removes the duplicate digitsOnly(String) that Claude added to
#  FindCardActivity, then re-exports the sources and rebuilds.
#
#  FindCardActivity already had a private static digitsOnly(String) with an
#  identical body. The new card-number decoder added a second copy of it, and
#  javac rejects two methods with the same signature in one class.
#
#  The match is anchored on the javadoc line that follows the duplicate, which
#  exists nowhere else in the file - so the ORIGINAL digitsOnly, whose body is
#  byte-identical, cannot be the one removed. If the anchor is not found, or
#  the count afterwards is not exactly one, the file is left untouched.
# ---------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$root = 'D:\projectv1.85\projectv1\offlinepaydemo'
$src  = Join-Path $root 'app\src\main\java\com\hjq\demo\ui\activity\FindCardActivity.java'

Write-Host '[1/3] Removing the duplicate digitsOnly...'
$text   = [IO.File]::ReadAllText($src)
$before = ([regex]::Matches($text, 'private static String digitsOnly\(')).Count
Write-Host "      definitions found: $before"

if ($before -le 1) {
    Write-Host '      nothing to remove - already fixed.'
} else {
    # Leading indentation is consumed and put back as four spaces, otherwise
    # the javadoc that follows ends up indented by eight.
    $pattern = '[ \t]*private static String digitsOnly\(String s\) \{\r?\n\s*return s == null \? "" : s\.replaceAll\("\[\^0-9\]", ""\);\r?\n\s*\}\r?\n\r?\n[ \t]*(/\*\* 12 to 19 digits, per ISO/IEC 7812\. \*/)'
    if (-not [regex]::IsMatch($text, $pattern)) {
        Write-Host '      ANCHOR NOT FOUND - file untouched. Tell Claude.'
        exit 1
    }
    Copy-Item $src "$src.bak" -Force
    $fixed = [regex]::Replace($text, $pattern, '    $1', 1)
    $after = ([regex]::Matches($fixed, 'private static String digitsOnly\(')).Count
    if ($after -ne 1) {
        Write-Host "      REFUSED - expected 1 definition afterwards, got $after. File untouched."
        exit 1
    }
    [IO.File]::WriteAllText($src, $fixed)
    Write-Host '      removed. Backup: FindCardActivity.java.bak'
}

Write-Host '[2/3] Exporting sources for Claude...'
$srcDir = Join-Path $root 'app\src\main\java'
$dst    = Join-Path $root '_claude_src'
if (Test-Path $dst) { Remove-Item $dst -Recurse -Force }
New-Item -ItemType Directory -Path $dst | Out-Null
Get-ChildItem -Path $srcDir -Recurse -Filter *.java | ForEach-Object {
    $rel = $_.FullName.Substring($srcDir.Length + 1) -replace '\\', '__'
    Copy-Item $_.FullName (Join-Path $dst ($rel + '.txt'))
}
Write-Host ("      exported " + (Get-ChildItem $dst).Count + ' files')

Write-Host '[3/3] Rebuilding - about a minute...'
Set-Location $root
& cmd /c "gradlew.bat :app:compileDebugJavaWithJavac --console=plain > `"$dst\_build_output.txt`" 2>&1"

Write-Host ''
Write-Host '================= COMPILER ERRORS ================='
$errs = Select-String -Path "$dst\_build_output.txt" -Pattern 'error:' -SimpleMatch
if ($errs) { $errs | ForEach-Object { $_.Line } } else { Write-Host '  none - BUILD OK' }
Write-Host '==================================================='
Write-Host ''
Write-Host "Full log: _claude_src\_build_output.txt"
