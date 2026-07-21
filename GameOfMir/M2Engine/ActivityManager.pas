unit ActivityManager;

interface

uses
  Windows, SysUtils, Classes, IniFiles, Math, Grobal2, M2Share, Envir, ObjBase, ObjPlay;

type
  TActivityState = (asWaiting, asAnnounced, asRunning, asEnded, asDisabled);

  TActivity = packed record
    Info: TActivityInfo;
    State: TActivityState;
    dwStartTick: LongWord;
    dwEndTick: LongWord;
    dwAnnounceTick: LongWord;
    nParticipantCount: Integer;
    ParticipantList: TList;
    RankList: TStringList;
    boStarted: Boolean;
    boEnded: Boolean;
  end;
  pTActivity = ^TActivity;

  TActivityManager = class
  private
    m_ActivityList: TList;
    m_RunningActivities: TList;
    m_boInitialized: Boolean;
    m_dwLastCheckTick: LongWord;
    function GetActivity(nActivityID: Word): pTActivity;
    function IsActivityRunning(nActivityID: Word): Boolean;
    procedure CheckActivityStart;
    procedure CheckActivityEnd;
    procedure AnnounceActivity(Activity: pTActivity; sMsg: string);
    procedure AddBuiltInActivity(nID: Word; sName, sDesc: string;
      AType: TActivityType; sStartTime, sEndTime: string;
      nWeekDay: Byte; nMinLevel, nMaxLevel: Integer;
      boAutoStart: Boolean; nRewardGold, nRewardGameGold, nRewardExp: Integer);
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;
    procedure Run;
    function StartActivity(nActivityID: Word): Boolean;
    function EndActivity(nActivityID: Word): Boolean;
    function GetActivityStatus(nActivityID: Word): string;
    function GetActivityListForClient: string;
    function JoinActivity(PlayObject: TPlayObject; nActivityID: Word): Boolean;
    procedure RegisterParticipation(PlayObject: TPlayObject; nActivityID: Word);
    procedure GetActivityRanking(nActivityID: Word; var RankList: TStringList);
    procedure DistributeRewards(Activity: pTActivity);
    procedure LoadConfig(sConfigFile: string);
    function IsInTimeRange(Activity: pTActivity): Boolean;
    function GetCurrentWeekDay: Integer;
    procedure SendActivityNotice(Activity: pTActivity);
    procedure SendActivityStart(Activity: pTActivity);
    procedure SendActivityEnd(Activity: pTActivity);
  end;

var
  g_ActivityManager: TActivityManager;

implementation

{ TActivityManager }

constructor TActivityManager.Create();
begin
  m_ActivityList := TList.Create;
  m_RunningActivities := TList.Create;
  m_boInitialized := False;
  m_dwLastCheckTick := 0;
end;

destructor TActivityManager.Destroy;
var
  I: Integer;
  Activity: pTActivity;
begin
  for I := m_ActivityList.Count - 1 downto 0 do begin
    Activity := pTActivity(m_ActivityList.Items[I]);
    if Activity <> nil then begin
      if Activity.ParticipantList <> nil then begin
        Activity.ParticipantList.Free;
        Activity.ParticipantList := nil;
      end;
      if Activity.RankList <> nil then begin
        Activity.RankList.Free;
        Activity.RankList := nil;
      end;
      if Activity.Info.RewardItemList <> nil then begin
        Activity.Info.RewardItemList.Free;
        Activity.Info.RewardItemList := nil;
      end;
      Dispose(Activity);
    end;
  end;
  m_ActivityList.Free;
  m_RunningActivities.Free;
  inherited;
end;

procedure TActivityManager.AddBuiltInActivity(nID: Word; sName, sDesc: string;
  AType: TActivityType; sStartTime, sEndTime: string;
  nWeekDay: Byte; nMinLevel, nMaxLevel: Integer;
  boAutoStart: Boolean; nRewardGold, nRewardGameGold, nRewardExp: Integer);
var
  Activity: pTActivity;
begin
  New(Activity);
  FillChar(Activity^, SizeOf(TActivity), #0);
  Activity.Info.nActivityID := nID;
  Activity.Info.sActivityName := sName;
  Activity.Info.sDescription := sDesc;
  Activity.Info.ActivityType := AType;
  Activity.Info.sStartTime := sStartTime;
  Activity.Info.sEndTime := sEndTime;
  Activity.Info.nWeekDay := nWeekDay;
  Activity.Info.nMonthDay := 0;
  Activity.Info.nMinLevel := nMinLevel;
  Activity.Info.nMaxLevel := nMaxLevel;
  Activity.Info.nMinPlayers := 0;
  Activity.Info.nMaxPlayers := 0;
  Activity.Info.nDuration := 0;
  Activity.Info.nPrepareTime := 0;
  Activity.Info.sMapName := '';
  Activity.Info.nEnterX := 0;
  Activity.Info.nEnterY := 0;
  Activity.Info.sScript := '';
  Activity.Info.boEnabled := True;
  Activity.Info.boAutoStart := boAutoStart;
  Activity.Info.nRewardGold := nRewardGold;
  Activity.Info.nRewardGameGold := nRewardGameGold;
  Activity.Info.nRewardExp := nRewardExp;
  Activity.Info.RewardItemList := TList.Create;
  Activity.State := asWaiting;
  Activity.dwStartTick := 0;
  Activity.dwEndTick := 0;
  Activity.dwAnnounceTick := 0;
  Activity.nParticipantCount := 0;
  Activity.ParticipantList := TList.Create;
  Activity.RankList := TStringList.Create;
  Activity.boStarted := False;
  Activity.boEnded := False;
  m_ActivityList.Add(Activity);
end;

procedure TActivityManager.Initialize;
var
  I: Integer;
  Activity: pTActivity;
begin
  if m_boInitialized then Exit;

  // Clear existing
  for I := m_ActivityList.Count - 1 downto 0 do begin
    Activity := pTActivity(m_ActivityList.Items[I]);
    if Activity <> nil then begin
      if Activity.ParticipantList <> nil then Activity.ParticipantList.Free;
      if Activity.RankList <> nil then Activity.RankList.Free;
      if Activity.Info.RewardItemList <> nil then Activity.Info.RewardItemList.Free;
      Dispose(Activity);
    end;
  end;
  m_ActivityList.Clear;
  m_RunningActivities.Clear;

  // 1. 每日签到 (Daily, auto)
  AddBuiltInActivity(1, '每日签到', '每日签到获得奖励，连续签到可获得额外奖励',
    atDaily, '00:00', '23:59', 0, 1, 65535, True, 10000, 0, 1000);

  // 2. 双倍经验 (Daily, 18:00-20:00)
  AddBuiltInActivity(2, '双倍经验', '活动期间击杀怪物获得双倍经验',
    atDaily, '18:00', '20:00', 0, 1, 65535, True, 0, 0, 0);

  // 3. 世界BOSS (Daily, 20:00-21:00)
  AddBuiltInActivity(3, '世界BOSS', '世界BOSS出现在盟重，击杀可获得丰厚奖励',
    atDaily, '20:00', '21:00', 0, 40, 65535, True, 50000, 100, 50000);

  // 4. 怪物攻城 (Weekly, Saturday 14:00-15:00)
  AddBuiltInActivity(4, '怪物攻城', '大量怪物进攻比奇城，保卫家园！',
    atWeekly, '14:00', '15:00', 7, 30, 65535, True, 30000, 50, 30000);

  // 5. 行会争霸 (Weekly, Sunday 19:00-20:00)
  AddBuiltInActivity(5, '行会争霸', '行会之间的大战，争夺霸主地位',
    atWeekly, '19:00', '20:00', 1, 50, 65535, True, 100000, 200, 100000);

  // 6. 答题活动 (Daily, 12:00-12:30)
  AddBuiltInActivity(6, '答题活动', '参与答题赢取丰厚奖励，考验你的知识储备',
    atDaily, '12:00', '12:30', 0, 1, 65535, True, 5000, 10, 5000);

  // 7. 押镖活动 (Daily, 10:00-22:00)
  AddBuiltInActivity(7, '押镖活动', '押送镖车到达目的地，获得丰厚酬劳，小心劫镖！',
    atDaily, '10:00', '22:00', 0, 30, 65535, True, 20000, 30, 20000);

  // 8. 竞技场 (Daily, 15:00-16:00)
  AddBuiltInActivity(8, '竞技场', '进入竞技场与其他玩家切磋，排名越高奖励越丰厚',
    atDaily, '15:00', '16:00', 0, 35, 65535, True, 30000, 50, 30000);

  // 9. 寻宝活动 (Special, manually triggered)
  AddBuiltInActivity(9, '寻宝活动', '活动期间特定地图刷新大量宝箱，开启可获得珍贵物品',
    atSpecial, '00:00', '23:59', 0, 1, 65535, False, 0, 20, 10000);

  // 10. 限时充值 (Special, manually triggered)
  AddBuiltInActivity(10, '限时充值', '活动期间充值可获得额外元宝奖励，多充多送',
    atSpecial, '00:00', '23:59', 0, 1, 65535, False, 0, 0, 0);

  m_boInitialized := True;
  m_dwLastCheckTick := GetTickCount;
  MainOutMessage('[ActivityManager] 初始化完成，共加载 ' + IntToStr(m_ActivityList.Count) + ' 个活动定义');
end;

function TActivityManager.GetActivity(nActivityID: Word): pTActivity;
var
  I: Integer;
  Activity: pTActivity;
begin
  Result := nil;
  for I := 0 to m_ActivityList.Count - 1 do begin
    Activity := pTActivity(m_ActivityList.Items[I]);
    if (Activity <> nil) and (Activity.Info.nActivityID = nActivityID) then begin
      Result := Activity;
      Exit;
    end;
  end;
end;

function TActivityManager.IsActivityRunning(nActivityID: Word): Boolean;
var
  Activity: pTActivity;
begin
  Result := False;
  Activity := GetActivity(nActivityID);
  if Activity <> nil then
    Result := Activity.State = asRunning;
end;

function TActivityManager.GetCurrentWeekDay: Integer;
var
  wDay: Word;
begin
  wDay := DayOfWeek(Now);
  // Delphi: 1=Sunday, 2=Monday, ..., 7=Saturday
  // Our convention: 0=每天, 1=周一, 2=周二, ..., 7=周日
  case wDay of
    1: Result := 7; // Sunday
    2: Result := 1; // Monday
    3: Result := 2; // Tuesday
    4: Result := 3; // Wednesday
    5: Result := 4; // Thursday
    6: Result := 5; // Friday
    7: Result := 6; // Saturday
  else
    Result := 0;
  end;
end;

function TActivityManager.IsInTimeRange(Activity: pTActivity): Boolean;
var
  sCurTime: string;
  nCurHour, nCurMin: Integer;
  nStartHour, nStartMin: Integer;
  nEndHour, nEndMin: Integer;
  nCurMinutes, nStartMinutes, nEndMinutes: Integer;
begin
  Result := False;
  if Activity = nil then Exit;

  sCurTime := FormatDateTime('HH:NN', Now);
  nCurHour := StrToIntDef(Copy(sCurTime, 1, 2), 0);
  nCurMin := StrToIntDef(Copy(sCurTime, 4, 2), 0);
  nCurMinutes := nCurHour * 60 + nCurMin;

  nStartHour := StrToIntDef(Copy(Activity.Info.sStartTime, 1, 2), 0);
  nStartMin := StrToIntDef(Copy(Activity.Info.sStartTime, 4, 2), 0);
  nStartMinutes := nStartHour * 60 + nStartMin;

  nEndHour := StrToIntDef(Copy(Activity.Info.sEndTime, 1, 2), 0);
  nEndMin := StrToIntDef(Copy(Activity.Info.sEndTime, 4, 2), 0);
  nEndMinutes := nEndHour * 60 + nEndMin;

  if nStartMinutes <= nEndMinutes then begin
    Result := (nCurMinutes >= nStartMinutes) and (nCurMinutes <= nEndMinutes);
  end else begin
    // Crosses midnight
    Result := (nCurMinutes >= nStartMinutes) or (nCurMinutes <= nEndMinutes);
  end;
end;

procedure TActivityManager.CheckActivityStart;
var
  I: Integer;
  Activity: pTActivity;
  nCurWeekDay: Integer;
begin
  nCurWeekDay := GetCurrentWeekDay;
  for I := 0 to m_ActivityList.Count - 1 do begin
    Activity := pTActivity(m_ActivityList.Items[I]);
    if Activity = nil then Continue;
    if not Activity.Info.boEnabled then Continue;
    if not Activity.Info.boAutoStart then Continue;
    if Activity.State <> asWaiting then Continue;

    // Check weekday
    if Activity.Info.nWeekDay > 0 then begin
      if Activity.Info.nWeekDay <> nCurWeekDay then Continue;
    end;

    // Check if it's time to start
    if IsInTimeRange(Activity) then begin
      StartActivity(Activity.Info.nActivityID);
    end;
  end;
end;

procedure TActivityManager.CheckActivityEnd;
var
  I: Integer;
  Activity: pTActivity;
begin
  for I := m_RunningActivities.Count - 1 downto 0 do begin
    Activity := pTActivity(m_RunningActivities.Items[I]);
    if Activity = nil then Continue;
    if Activity.State <> asRunning then Continue;

    // Check if end time has passed
    if not IsInTimeRange(Activity) then begin
      EndActivity(Activity.Info.nActivityID);
    end;
  end;
end;

procedure TActivityManager.AnnounceActivity(Activity: pTActivity; sMsg: string);
begin
  if Activity = nil then Exit;
  if UserEngine <> nil then begin
    UserEngine.SendBroadCastMsg(sMsg, t_System);
  end;
end;

function TActivityManager.StartActivity(nActivityID: Word): Boolean;
var
  Activity: pTActivity;
  sAnnounce: string;
begin
  Result := False;
  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;
  if Activity.State = asRunning then begin
    Result := True;
    Exit;
  end;
  if not Activity.Info.boEnabled then Exit;

  // Reset activity state
  Activity.State := asRunning;
  Activity.boStarted := True;
  Activity.boEnded := False;
  Activity.dwStartTick := GetTickCount;
  Activity.nParticipantCount := 0;
  Activity.ParticipantList.Clear;
  Activity.RankList.Clear;

  // Add to running list
  if m_RunningActivities.IndexOf(Activity) < 0 then
    m_RunningActivities.Add(Activity);

  // Send activity start notice
  SendActivityStart(Activity);

  sAnnounce := Format('[%s] 活动已开始！', [Activity.Info.sActivityName]);
  AnnounceActivity(Activity, sAnnounce);

  MainOutMessage('[ActivityManager] 活动开始: ' + Activity.Info.sActivityName + ' (ID=' + IntToStr(nActivityID) + ')');
  Result := True;
end;

function TActivityManager.EndActivity(nActivityID: Word): Boolean;
var
  Activity: pTActivity;
  nIdx: Integer;
  sAnnounce: string;
begin
  Result := False;
  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;
  if Activity.State <> asRunning then begin
    Result := True;
    Exit;
  end;

  // Distribute rewards before ending
  DistributeRewards(Activity);

  // Mark ended
  Activity.State := asWaiting;
  Activity.boEnded := True;
  Activity.dwEndTick := GetTickCount;

  // Remove from running list
  nIdx := m_RunningActivities.IndexOf(Activity);
  if nIdx >= 0 then
    m_RunningActivities.Delete(nIdx);

  // Send activity end notice
  SendActivityEnd(Activity);

  sAnnounce := Format('[%s] 活动已结束！', [Activity.Info.sActivityName]);
  AnnounceActivity(Activity, sAnnounce);

  MainOutMessage('[ActivityManager] 活动结束: ' + Activity.Info.sActivityName + ' (ID=' + IntToStr(nActivityID) + ')');
  Result := True;
end;

function TActivityManager.GetActivityStatus(nActivityID: Word): string;
var
  Activity: pTActivity;
  sState: string;
begin
  Result := '';
  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;

  case Activity.State of
    asWaiting: sState := '等待中';
    asAnnounced: sState := '已公告';
    asRunning: sState := '进行中';
    asEnded: sState := '已结束';
    asDisabled: sState := '已禁用';
  else
    sState := '未知';
  end;

  Result := Format('活动ID:%d 名称:%s 状态:%s 参与人数:%d',
    [Activity.Info.nActivityID, Activity.Info.sActivityName,
     sState, Activity.nParticipantCount]);
  if Activity.State = asRunning then begin
    Result := Result + Format(' 已运行:%d秒',
      [(GetTickCount - Activity.dwStartTick) div 1000]);
  end;
end;

function TActivityManager.GetActivityListForClient: string;
var
  I: Integer;
  Activity: pTActivity;
  sLine: string;
  sState: string;
begin
  Result := '';
  for I := 0 to m_ActivityList.Count - 1 do begin
    Activity := pTActivity(m_ActivityList.Items[I]);
    if Activity = nil then Continue;
    if not Activity.Info.boEnabled then Continue;

    case Activity.State of
      asWaiting: sState := '0';
      asAnnounced: sState := '1';
      asRunning: sState := '2';
      asEnded: sState := '3';
      asDisabled: sState := '4';
    else
      sState := '0';
    end;

    sLine := Format('%d/%s/%s/%s/%d/%d/%d/%d/%s',
      [Activity.Info.nActivityID,
       Activity.Info.sActivityName,
       sState,
       Activity.Info.sStartTime + '-' + Activity.Info.sEndTime,
       Activity.Info.nMinLevel,
       Activity.Info.nMaxLevel,
       Activity.Info.nRewardGold,
       Activity.Info.nRewardExp,
       Activity.Info.sDescription]);

    if Result = '' then
      Result := sLine
    else
      Result := Result + '|' + sLine;
  end;
end;

function TActivityManager.JoinActivity(PlayObject: TPlayObject; nActivityID: Word): Boolean;
var
  Activity: pTActivity;
begin
  Result := False;
  if PlayObject = nil then Exit;

  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;
  if Activity.State <> asRunning then begin
    PlayObject.SysMsg('该活动当前未在运行中', c_Red, t_Hint);
    Exit;
  end;

  // Check level requirements
  if (Activity.Info.nMinLevel > 0) and (PlayObject.m_Abil.Level < Activity.Info.nMinLevel) then begin
    PlayObject.SysMsg(Format('您的等级不足%d级，无法参加此活动', [Activity.Info.nMinLevel]), c_Red, t_Hint);
    Exit;
  end;
  if (Activity.Info.nMaxLevel > 0) and (PlayObject.m_Abil.Level > Activity.Info.nMaxLevel) then begin
    PlayObject.SysMsg(Format('您的等级超过%d级，无法参加此活动', [Activity.Info.nMaxLevel]), c_Red, t_Hint);
    Exit;
  end;

  // Check max players
  if (Activity.Info.nMaxPlayers > 0) and (Activity.nParticipantCount >= Activity.Info.nMaxPlayers) then begin
    PlayObject.SysMsg('活动参与人数已满', c_Red, t_Hint);
    Exit;
  end;

  // Check if already joined
  if Activity.ParticipantList.IndexOf(PlayObject) >= 0 then begin
    PlayObject.SysMsg('您已经参加了此活动', c_Red, t_Hint);
    Exit;
  end;

  RegisterParticipation(PlayObject, nActivityID);
  PlayObject.SysMsg(Format('您已成功参加[%s]活动', [Activity.Info.sActivityName]), c_Green, t_Hint);
  Result := True;
end;

procedure TActivityManager.RegisterParticipation(PlayObject: TPlayObject; nActivityID: Word);
var
  Activity: pTActivity;
begin
  if PlayObject = nil then Exit;
  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;

  if Activity.ParticipantList.IndexOf(PlayObject) < 0 then begin
    Activity.ParticipantList.Add(PlayObject);
    Inc(Activity.nParticipantCount);
  end;
end;

procedure TActivityManager.GetActivityRanking(nActivityID: Word; var RankList: TStringList);
var
  Activity: pTActivity;
  I: Integer;
  PlayObject: TPlayObject;
begin
  if RankList = nil then Exit;
  RankList.Clear;

  Activity := GetActivity(nActivityID);
  if Activity = nil then Exit;

  // Sort participants by level (simple ranking)
  for I := 0 to Activity.ParticipantList.Count - 1 do begin
    PlayObject := TPlayObject(Activity.ParticipantList.Items[I]);
    if (PlayObject <> nil) and (not PlayObject.m_boGhost) then begin
      RankList.AddObject(Format('%s(%d级)', [PlayObject.m_sCharName, PlayObject.m_Abil.Level]),
        TObject(PlayObject.m_Abil.Level));
    end;
  end;

  // Sort by Level descending
  if RankList.Count > 1 then begin
    // Simple bubble sort
    RankList.Sort;
    for I := 0 to RankList.Count - 1 do begin
      RankList.Objects[I] := TObject(RankList.Count - I);
    end;
  end;

  if RankList.Count = 0 then
    RankList.Add('暂无参与玩家');
end;

procedure TActivityManager.DistributeRewards(Activity: pTActivity);
var
  I: Integer;
  PlayObject: TPlayObject;
  nGoldReward: Integer;
  nExpReward: Integer;
  nGameGoldReward: Integer;
begin
  if Activity = nil then Exit;
  if Activity.ParticipantList.Count = 0 then Exit;

  nGoldReward := Activity.Info.nRewardGold;
  nExpReward := Activity.Info.nRewardExp;
  nGameGoldReward := Activity.Info.nRewardGameGold;

  for I := 0 to Activity.ParticipantList.Count - 1 do begin
    PlayObject := TPlayObject(Activity.ParticipantList.Items[I]);
    if (PlayObject = nil) or PlayObject.m_boGhost then Continue;

    // Give gold reward
    if nGoldReward > 0 then begin
      PlayObject.m_nGold := PlayObject.m_nGold + nGoldReward;
      PlayObject.SendDefMsg(PlayObject, SM_GOLDCHANGED, 0, 0, 0, 0, '');
      PlayObject.SysMsg(Format('您在[%s]活动中获得%d金币奖励', [Activity.Info.sActivityName, nGoldReward]), c_Green, t_Hint);
    end;

    // Give exp reward
    if nExpReward > 0 then begin
      PlayObject.m_Abil.Exp := PlayObject.m_Abil.Exp + nExpReward;
      PlayObject.SendDefMsg(PlayObject, SM_WINEXP, 0, nExpReward, 0, 0, '');
      PlayObject.SysMsg(Format('您在[%s]活动中获得%d经验奖励', [Activity.Info.sActivityName, nExpReward]), c_Green, t_Hint);
    end;

    // Give game gold reward
    if nGameGoldReward > 0 then begin
      PlayObject.m_nGameGold := PlayObject.m_nGameGold + nGameGoldReward;
      PlayObject.SendDefMsg(PlayObject, SM_GAMEGOLDNAME, 0, 0, 0, 0, '');
      PlayObject.SysMsg(Format('您在[%s]活动中获得%d元宝奖励', [Activity.Info.sActivityName, nGameGoldReward]), c_Green, t_Hint);
    end;
  end;

  // Send ranking notice
  if Activity.ParticipantList.Count > 0 then begin
    AnnounceActivity(Activity, Format('[%s] 活动奖励已发放，共%d位玩家获得奖励',
      [Activity.Info.sActivityName, Activity.ParticipantList.Count]));
  end;
end;

procedure TActivityManager.LoadConfig(sConfigFile: string);
var
  Ini: TIniFile;
  nCount: Integer;
  I: Integer;
  sSection: string;
  Activity: pTActivity;
  nID: Word;
  sName: string;
  sDesc: string;
  nType: Integer;
  sStartTime: string;
  sEndTime: string;
  nWeekDay: Byte;
  nMinLevel: Integer;
  nMaxLevel: Integer;
  boEnabled: Boolean;
  boAutoStart: Boolean;
  nRewardGold: Integer;
  nRewardGameGold: Integer;
  nRewardExp: Integer;
begin
  if not FileExists(sConfigFile) then Exit;

  Ini := TIniFile.Create(sConfigFile);
  try
    nCount := Ini.ReadInteger('ActivityConfig', 'ActivityCount', 0);
    if nCount <= 0 then begin
      MainOutMessage('[ActivityManager] 配置文件中无额外活动定义');
      Exit;
    end;

    for I := 0 to nCount - 1 do begin
      sSection := 'Activity' + IntToStr(I);
      nID := Ini.ReadInteger(sSection, 'ActivityID', 0);
      sName := Ini.ReadString(sSection, 'ActivityName', '');
      sDesc := Ini.ReadString(sSection, 'Description', '');
      nType := Ini.ReadInteger(sSection, 'ActivityType', 0);
      sStartTime := Ini.ReadString(sSection, 'StartTime', '00:00');
      sEndTime := Ini.ReadString(sSection, 'EndTime', '23:59');
      nWeekDay := Ini.ReadInteger(sSection, 'WeekDay', 0);
      nMinLevel := Ini.ReadInteger(sSection, 'MinLevel', 1);
      nMaxLevel := Ini.ReadInteger(sSection, 'MaxLevel', 65535);
      boEnabled := Ini.ReadBool(sSection, 'Enabled', True);
      boAutoStart := Ini.ReadBool(sSection, 'AutoStart', True);
      nRewardGold := Ini.ReadInteger(sSection, 'RewardGold', 0);
      nRewardGameGold := Ini.ReadInteger(sSection, 'RewardGameGold', 0);
      nRewardExp := Ini.ReadInteger(sSection, 'RewardExp', 0);

      if (nID = 0) or (sName = '') then Continue;

      // Check if activity already exists
      Activity := GetActivity(nID);
      if Activity <> nil then begin
        // Update existing activity
        Activity.Info.sActivityName := sName;
        Activity.Info.sDescription := sDesc;
        Activity.Info.ActivityType := TActivityType(nType);
        Activity.Info.sStartTime := sStartTime;
        Activity.Info.sEndTime := sEndTime;
        Activity.Info.nWeekDay := nWeekDay;
        Activity.Info.nMinLevel := nMinLevel;
        Activity.Info.nMaxLevel := nMaxLevel;
        Activity.Info.boEnabled := boEnabled;
        Activity.Info.boAutoStart := boAutoStart;
        Activity.Info.nRewardGold := nRewardGold;
        Activity.Info.nRewardGameGold := nRewardGameGold;
        Activity.Info.nRewardExp := nRewardExp;
        if not boEnabled then
          Activity.State := asDisabled;
      end else begin
        AddBuiltInActivity(nID, sName, sDesc, TActivityType(nType),
          sStartTime, sEndTime, nWeekDay, nMinLevel, nMaxLevel,
          boAutoStart, nRewardGold, nRewardGameGold, nRewardExp);
      end;
    end;

    MainOutMessage('[ActivityManager] 配置文件加载完成: ' + sConfigFile);
  finally
    Ini.Free;
  end;
end;

procedure TActivityManager.SendActivityNotice(Activity: pTActivity);
var
  sMsg: string;
  I: Integer;
  PlayObject: TPlayObject;
begin
  if Activity = nil then Exit;
  if UserEngine = nil then Exit;

  sMsg := Format('[%s] 即将开始！开始时间: %s 结束时间: %s',
    [Activity.Info.sActivityName, Activity.Info.sStartTime, Activity.Info.sEndTime]);

  try
    EnterCriticalSection(ProcessHumanCriticalSection);
    for I := 0 to UserEngine.m_PlayObjectList.Count - 1 do begin
      PlayObject := TPlayObject(UserEngine.m_PlayObjectList.Objects[I]);
      if PlayObject <> nil then begin
        if (not PlayObject.m_boGhost) and (not PlayObject.m_boSafeOffLine) then begin
          PlayObject.SendDefMsg(PlayObject, SM_ACTIVITYNOTICE, Activity.Info.nActivityID, 0, 0, 0, sMsg);
        end;
      end;
    end;
  finally
    LeaveCriticalSection(ProcessHumanCriticalSection);
  end;
end;

procedure TActivityManager.SendActivityStart(Activity: pTActivity);
var
  sMsg: string;
  I: Integer;
  PlayObject: TPlayObject;
begin
  if Activity = nil then Exit;
  if UserEngine = nil then Exit;

  sMsg := Format('[%s] 活动已开始！(%s-%s) 快来参加吧！',
    [Activity.Info.sActivityName, Activity.Info.sStartTime, Activity.Info.sEndTime]);

  try
    EnterCriticalSection(ProcessHumanCriticalSection);
    for I := 0 to UserEngine.m_PlayObjectList.Count - 1 do begin
      PlayObject := TPlayObject(UserEngine.m_PlayObjectList.Objects[I]);
      if PlayObject <> nil then begin
        if (not PlayObject.m_boGhost) and (not PlayObject.m_boSafeOffLine) then begin
          PlayObject.SendDefMsg(PlayObject, SM_ACTIVITYSTART, Activity.Info.nActivityID, 0, 0, 0, sMsg);
        end;
      end;
    end;
  finally
    LeaveCriticalSection(ProcessHumanCriticalSection);
  end;
end;

procedure TActivityManager.SendActivityEnd(Activity: pTActivity);
var
  sMsg: string;
  I: Integer;
  PlayObject: TPlayObject;
begin
  if Activity = nil then Exit;
  if UserEngine = nil then Exit;

  sMsg := Format('[%s] 活动已结束！感谢参与！',
    [Activity.Info.sActivityName]);

  try
    EnterCriticalSection(ProcessHumanCriticalSection);
    for I := 0 to UserEngine.m_PlayObjectList.Count - 1 do begin
      PlayObject := TPlayObject(UserEngine.m_PlayObjectList.Objects[I]);
      if PlayObject <> nil then begin
        if (not PlayObject.m_boGhost) and (not PlayObject.m_boSafeOffLine) then begin
          PlayObject.SendDefMsg(PlayObject, SM_ACTIVITYEND, Activity.Info.nActivityID, 0, 0, 0, sMsg);
        end;
      end;
    end;
  finally
    LeaveCriticalSection(ProcessHumanCriticalSection);
  end;
end;

procedure TActivityManager.Run;
var
  dwNow: LongWord;
begin
  if not m_boInitialized then Exit;

  dwNow := GetTickCount;

  // Check every 30 seconds
  if dwNow - m_dwLastCheckTick < 30000 then Exit;
  m_dwLastCheckTick := dwNow;

  try
    CheckActivityStart;
    CheckActivityEnd;
  except
    on E: Exception do begin
      MainOutMessage('[ActivityManager] Run Error: ' + E.Message);
    end;
  end;
end;

initialization
  g_ActivityManager := nil;

finalization
  if g_ActivityManager <> nil then begin
    g_ActivityManager.Free;
    g_ActivityManager := nil;
  end;

end.