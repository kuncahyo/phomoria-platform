#define MyAppName "Phomoria"
#define MyAppVersion "0.3.7"
#define MyAppPublisher "Phomoria"

[Setup]
AppId={{A2C4F1A6-7D2B-4C3A-9E0B-6B9D2E4F8A11}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}

DefaultDirName={autopf}\Phomoria
DefaultGroupName=Phomoria

OutputDir=..\..\build\phomoria-package\installer
OutputBaseFilename=Phomoria-Setup-{#MyAppVersion}

SetupIconFile=..\..\PHOMORIA.ico
UninstallDisplayIcon={app}\Phomoria.exe

ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin

DisableProgramGroupPage=yes
WizardStyle=modern

Compression=lzma2
SolidCompression=yes

[Files]
Source: "..\..\target\Phomoria\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Phomoria"; Filename: "{app}\Phomoria.exe"; IconFilename: "{app}\Phomoria.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\Phomoria"; Filename: "{app}\Phomoria.exe"; IconFilename: "{app}\Phomoria.exe"; WorkingDir: "{app}"

[Run]
Filename: "{app}\Phomoria.exe"; Description: "Jalankan Phomoria"; Flags: postinstall nowait skipifsilent
