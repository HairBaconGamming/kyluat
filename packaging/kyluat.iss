; Kỷ luật — bộ cài Windows (cần Inno Setup 6.7 trở lên cho WizardStyle=modern).
; Biên dịch một lệnh:  python tools/build_windows.py
;
; Vì sao cài vào thư mục của riêng người dùng: app ghi config.toml và data/ ngay
; cạnh Kyluat.exe, nên thư mục Program Files (cần quyền admin để ghi) sẽ làm app
; không chạy được. Cách này cũng không đòi UAC khi cài.

#define MyAppName "Kỷ luật"
#define MyAppPublisher "HairBaconGamming"
#define MyAppURL "https://kyluat.vercel.app"
#define MyAppExeName "Kyluat.exe"
#define TaskName "CaiNghienEnforcer"

#ifndef MyAppVersion
  #define MyAppVersion "1.1"
#endif
#ifndef SourceDir
  #define SourceDir "..\dist\Kyluat"
#endif

[Setup]
AppId={{BD234D87-C360-47E5-B717-5E2F6EF94D13}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={localappdata}\Programs\Kyluat
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
DisableWelcomePage=no
DisableFinishedPage=no
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist
OutputBaseFilename=KYLUAT-Setup-v{#MyAppVersion}
SetupIconFile=..\enforcer\static\app.ico
UninstallDisplayName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallFilesDir={app}
LicenseFile=..\LICENSE
CloseApplications=yes
RestartApplications=no
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern

[Languages]
#ifdef HasViIsl
Name: "vi"; MessagesFile: "compiler:Languages\Vietnamese.isl"
#else
Name: "en"; MessagesFile: "compiler:Default.isl"
#endif

[Messages]
WelcomeLabel2={#MyAppName} chặn app và trang web bạn tự chọn trong giờ tập trung.%n%nMọi thứ chạy trên máy bạn: không tài khoản, không máy chủ, không số liệu nào đi ra ngoài.%n%nBộ cài đặt vào thư mục của riêng bạn, không đòi quyền quản trị, và gỡ được sạch bằng Add or remove programs.
FinishedHeadingLabel=Cài xong {#MyAppName} {#MyAppVersion}
FinishedLabelNoIcons={#MyAppName} đã sẵn sàng. Mở app để chọn app và trang web cần chặn.%nNếu Windows SmartScreen hỏi, xem mục "Vì sao an toàn" trên {#MyAppURL}.
FinishedLabel={#MyAppName} đã sẵn sàng. Mở app để chọn app và trang web cần chặn.%nNếu Windows SmartScreen hỏi, xem mục "Vì sao an toàn" trên {#MyAppURL}.
; Inno không có message "UninstallMessage"; câu hỏi xác nhận khi gỡ tên là ConfirmUninstall.
ConfirmUninstall={#MyAppName} sẽ tắt bảo vệ trước rồi gỡ khỏi máy.%n%nLuật, sổ tay học/gym và log trong data\ vẫn được giữ lại để bạn xem về sau.
UninstalledAll={#MyAppName} đã được gỡ khỏi máy.%n%nNếu bạn cài lại, dữ liệu cũ trong data\ vẫn còn nguyên.

[Tasks]
Name: "desktopicon"; Description: "Thêm lối tắt ngoài màn hình nền"; GroupDescription: "Lối tắt bổ sung:"; Flags: unchecked

[Files]
; data\ là dấu vết lần chạy test máy build, không phải thứ để phân phát.
Source: "{#SourceDir}\*"; DestDir: "{app}"; Excludes: "config.toml,data\*"; Flags: ignoreversion recursesubdirs createallsubdirs
; Luật mặc định chỉ ghi khi chưa có, để cài đè bản mới không xóa luật của người dùng.
Source: "..\config.example.toml"; DestDir: "{app}"; DestName: "config.toml"; Flags: onlyifdoesntexist uninsneveruninstall

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Comment: "Mở {#MyAppName}"
Name: "{group}\Vì sao an toàn"; Filename: "{#MyAppURL}"; Comment: "Giải thích những gì app chạm vào trên máy bạn"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Mở {#MyAppName} ngay"; Flags: nowait postinstall skipifsilent runhidden

[Code]
// Gỡ mà để lại DNS trỏ về 127.0.0.1 thì mất mạng, nên bước tắt bảo vệ chạy trước.
function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
  Exe: String;
begin
  Result := True;
  Exe := ExpandConstant('{app}\{#MyAppExeName}');
  if FileExists(Exe) then
  begin
    if (not Exec(Exe, 'turn-off', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)) or (ResultCode <> 0) then
    begin
      MsgBox('Chưa tắt được bảo vệ tự động. Nếu bạn đang bật chặn web, DNS của máy vẫn trỏ về 127.0.0.1 và vào gỡ sẽ làm mất mạng.'
        + #13#10#13#10 + 'Mở {#MyAppName}, bấm Tắt bảo vệ (có xác nhận), rồi gỡ lại.'
        + #13#10 + 'Hoặc chạy lệnh: Kyluat-cli.exe turn-off trong PowerShell.', mbError, MB_OK);
      Result := False;
    end;
  end;
end;

// Task tự bật lại trỏ vào exe này, để lại sau khi gỡ là rác trong máy.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  ResultCode: Integer;
begin
  if CurUninstallStep = usPostUninstall then
    Exec('schtasks.exe', '/Delete /TN {#TaskName} /F', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;
