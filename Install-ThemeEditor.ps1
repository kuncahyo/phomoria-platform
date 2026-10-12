param([Parameter(Mandatory=$true)][string]$ProjectRoot)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path $ProjectRoot).Path
$settings = Join-Path $root 'src\main\java\com\phomoria\ui\SettingsScreen.java'
$settingsBackup = "$settings.before-theme-editor.bak"
$editorSource = Join-Path $PSScriptRoot 'src\main\java\com\phomoria\theme\ThemeEditorPanel.java'
$startSource = Join-Path $PSScriptRoot 'src\main\java\com\phomoria\ui\StartScreen.java'
$themeDir = Join-Path $root 'src\main\java\com\phomoria\theme'
if (!(Test-Path $settings)) { throw "SettingsScreen.java tidak ditemukan: $settings" }
if (!(Test-Path $editorSource) -or !(Test-Path $startSource)) { throw 'File Java dalam ZIP tidak lengkap.' }
$src = [System.IO.File]::ReadAllText($settings)
if ($src.Contains('ThemeEditorPanel themeEditorPanel')) { throw 'Theme Editor tampaknya sudah terpasang. Script dihentikan agar tidak menggandakan integrasi.' }
if (!(Test-Path $settingsBackup)) { Copy-Item $settings $settingsBackup }
$old1 = @'
        setLayout(
                new BorderLayout(15, 15)
        );
'@
$new1 = @'
        setLayout(new CardLayout());
        JPanel settingsView = new JPanel(new BorderLayout(15, 15));
'@
$old2 = @'
        add(
                title,
                BorderLayout.NORTH
        );
'@
$new2 = @'
        settingsView.add(
                title,
                BorderLayout.NORTH
        );
'@
$old3 = @'
        add(
                scroll,
                BorderLayout.CENTER
        );
'@
$new3 = @'
        settingsView.add(
                scroll,
                BorderLayout.CENTER
        );
'@
$old4 = @'
        JButton save =
                new JButton(
                        "SAVE SETTINGS"
                );
'@
$new4 = @'
        JButton save =
                new JButton(
                        "SAVE SETTINGS"
                );
        JButton themeEditorButton = new JButton("THEME EDITOR");
'@
$old5 = @'
        bottom.add(cancel);
        bottom.add(testPrint);
        bottom.add(save);

        add(
                bottom,
                BorderLayout.SOUTH
        );
'@
$new5 = @'
        bottom.add(cancel);
        bottom.add(themeEditorButton);
        bottom.add(testPrint);
        bottom.add(save);

        settingsView.add(bottom, BorderLayout.SOUTH);
        add(settingsView, "SETTINGS");
        ThemeEditorPanel themeEditorPanel = new ThemeEditorPanel(() -> {
            ((CardLayout) getLayout()).show(this, "SETTINGS");
            if (!cameraStatusTimer.isRunning()) cameraStatusTimer.start();
        });
        add(themeEditorPanel, "THEME_EDITOR");
        ((CardLayout) getLayout()).show(this, "SETTINGS");
        themeEditorButton.addActionListener(e -> {
            cameraStatusTimer.stop();
            ((CardLayout) getLayout()).show(this, "THEME_EDITOR");
        });
'@
$replacements = @(
    @{ Old = $old1; New = $new1 },
    @{ Old = $old2; New = $new2 },
    @{ Old = $old3; New = $new3 },
    @{ Old = $old4; New = $new4 },
    @{ Old = $old5; New = $new5 }
)
foreach ($pair in $replacements) {
    if (!$src.Contains($pair.Old)) { throw 'Pola source SettingsScreen tidak cocok. Tidak ada perubahan yang diterapkan.' }
    $src = $src.Replace($pair.Old, $pair.New)
}
New-Item -ItemType Directory -Force -Path $themeDir | Out-Null
Copy-Item $editorSource (Join-Path $themeDir 'ThemeEditorPanel.java') -Force
Copy-Item $startSource (Join-Path $root 'src\main\java\com\phomoria\ui\StartScreen.java') -Force
[System.IO.File]::WriteAllText($settings, $src, [System.Text.UTF8Encoding]::new($false))
Write-Host 'Theme Editor berhasil dipasang. Backup SettingsScreen tersedia.' -ForegroundColor Green
