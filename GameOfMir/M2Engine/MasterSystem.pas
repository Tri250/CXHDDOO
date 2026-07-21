unit MasterSystem;

interface

uses
  Windows, SysUtils, Classes, IniFiles, Grobal2, M2Share, ObjBase, ObjPlay;

const
  MAX_APPRENTICE_COUNT = 5;
  APPRENTICE_MIN_LEVEL = 40;
  APPRENTICE_MAX_LEVEL = 40; // level < 40 can be apprentice
  MASTER_MIN_LEVEL_DIFF = 10;
  MASTER_EXP_BONUS = 10;    // 10%
  APPRENTICE_EXP_BONUS = 20; // 20%
  DAILY_QUEST_LIMIT = 5;
  MENTOR_POINTS_PER_QUEST = 10;
  MENTOR_POINTS_PER_GRADUATE = 100;
  MASTER_RECALL_COST = 50;
  APPRENTICE_RECALL_COST = 30;

type
  TMasterSystem = class
  private
    m_MasterList: TList;
    m_boInitialized: Boolean;
    function GetRelation(sMasterName, sApprenticeName: string): pTMasterApprentice;
    function GetRelationsByMaster(sMasterName: string): TList;
    function GetRelationsByApprentice(sApprenticeName: string): TList;
    procedure SaveRelation(Relation: pTMasterApprentice);
    procedure LoadRelation(Relation: pTMasterApprentice);
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;

    // Create master-apprentice relationship
    function CreateRelation(sMasterName, sApprenticeName: string): Boolean;
    // Remove relationship
    function RemoveRelation(sMasterName, sApprenticeName: string): Boolean;
    // Complete apprenticeship (graduate)
    function CompleteApprenticeship(sApprenticeName: string): Boolean;
    // Get master name
    function GetMasterName(sApprenticeName: string): string;
    // Get apprentice names
    function GetApprenticeNames(sMasterName: string): TStringList;
    // Get apprentice count
    function GetApprenticeCount(sMasterName: string): Integer;
    // Check if can be master
    function CanBeMaster(PlayObject: TPlayObject): Boolean;
    // Check if can be apprentice
    function CanBeApprentice(PlayObject: TPlayObject): Boolean;
    // Check if can graduate
    function CanGraduate(sApprenticeName: string): Boolean;
    // Get master-apprentice relation type
    function GetRelationType(sCharName: string): TMasterRelation;
    // Get exp bonus
    function GetExpBonus(PlayObject: TPlayObject): Integer;
    // Add mentor points
    procedure AddMentorPoints(sMasterName: string; nPoints: Integer);
    // Get mentor points
    function GetMentorPoints(sMasterName: string): Integer;
    // Complete daily quest
    function CompleteDailyQuest(sMasterName, sApprenticeName: string): Boolean;
    // Reset daily quests
    procedure ResetDailyQuests;
    // Check if both online
    function IsBothOnline(sMasterName, sApprenticeName: string): Boolean;
    // Send master info to client
    procedure SendMasterInfo(PlayObject: TPlayObject);
    // Send apprentice info to client
    procedure SendApprenticeInfo(PlayObject: TPlayObject);
    // Load all relations from file
    procedure LoadConfig(sConfigFile: string);
    // Save all relations to file
    procedure SaveConfig(sConfigFile: string);
    // Get teleport location (master recall)
    function MasterRecall(PlayObject: TPlayObject): Boolean;
    // Get apprentice recall
    function ApprenticeRecall(PlayObject: TPlayObject): Boolean;
  end;

var
  g_MasterSystem: TMasterSystem;

implementation

uses
  HUtil32;

{ TMasterSystem }

constructor TMasterSystem.Create;
begin
  inherited Create;
  m_MasterList := TList.Create;
  m_boInitialized := False;
end;

destructor TMasterSystem.Destroy;
var
  I: Integer;
begin
  for I := 0 to m_MasterList.Count - 1 do begin
    if m_MasterList.Items[I] <> nil then
      Dispose(pTMasterApprentice(m_MasterList.Items[I]));
  end;
  m_MasterList.Free;
  inherited Destroy;
end;

procedure TMasterSystem.Initialize;
begin
  m_boInitialized := True;
  LoadConfig('');
end;

function TMasterSystem.GetRelation(sMasterName, sApprenticeName: string): pTMasterApprentice;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := nil;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) then begin
      Result := Relation;
      Exit;
    end;
  end;
end;

function TMasterSystem.GetRelationsByMaster(sMasterName: string): TList;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := TList.Create;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (not Relation.boCompleted) then begin
      Result.Add(Relation);
    end;
  end;
end;

function TMasterSystem.GetRelationsByApprentice(sApprenticeName: string): TList;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := TList.Create;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) then begin
      Result.Add(Relation);
    end;
  end;
end;

procedure TMasterSystem.SaveRelation(Relation: pTMasterApprentice);
var
  sFileName: string;
  IniFile: TIniFile;
  sSection: string;
begin
  if Relation = nil then
    Exit;
  sFileName := g_Config.sEnvirDir + 'MasterApprentice.txt';
  IniFile := TIniFile.Create(sFileName);
  try
    sSection := Relation.sMasterName + '_' + Relation.sApprenticeName;
    IniFile.WriteString(sSection, 'MasterName', Relation.sMasterName);
    IniFile.WriteString(sSection, 'ApprenticeName', Relation.sApprenticeName);
    IniFile.WriteInteger(sSection, 'CreateTime', Relation.dwCreateTime);
    IniFile.WriteInteger(sSection, 'MasterLevel', Relation.nMasterLevel);
    IniFile.WriteInteger(sSection, 'ApprenticeLevel', Relation.nApprenticeLevel);
    IniFile.WriteInteger(sSection, 'ApprenticeCount', Relation.nApprenticeCount);
    IniFile.WriteInteger(sSection, 'MasterExpBonus', Relation.nMasterExpBonus);
    IniFile.WriteInteger(sSection, 'ApprenticeExpBonus', Relation.nApprenticeExpBonus);
    IniFile.WriteBool(sSection, 'Completed', Relation.boCompleted);
    IniFile.WriteInteger(sSection, 'CompleteTime', Relation.dwCompleteTime);
    IniFile.WriteInteger(sSection, 'CompleteLevel', Relation.nCompleteLevel);
    IniFile.WriteInteger(sSection, 'DailyQuestCount', Relation.nDailyQuestCount);
    IniFile.WriteInteger(sSection, 'MaxDailyQuest', Relation.nMaxDailyQuest);
    IniFile.WriteInteger(sSection, 'MentorPoints', Relation.nMentorPoints);
    IniFile.WriteString(sSection, 'LastQuestDate', Relation.sLastQuestDate);
    IniFile.UpdateFile;
  finally
    IniFile.Free;
  end;
end;

procedure TMasterSystem.LoadRelation(Relation: pTMasterApprentice);
var
  sFileName: string;
  IniFile: TIniFile;
  sSection: string;
begin
  if Relation = nil then
    Exit;
  sFileName := g_Config.sEnvirDir + 'MasterApprentice.txt';
  if not FileExists(sFileName) then
    Exit;
  IniFile := TIniFile.Create(sFileName);
  try
    sSection := Relation.sMasterName + '_' + Relation.sApprenticeName;
    if IniFile.SectionExists(sSection) then begin
      Relation.nMasterLevel := IniFile.ReadInteger(sSection, 'MasterLevel', Relation.nMasterLevel);
      Relation.nApprenticeLevel := IniFile.ReadInteger(sSection, 'ApprenticeLevel', Relation.nApprenticeLevel);
      Relation.nApprenticeCount := IniFile.ReadInteger(sSection, 'ApprenticeCount', Relation.nApprenticeCount);
      Relation.nMasterExpBonus := IniFile.ReadInteger(sSection, 'MasterExpBonus', Relation.nMasterExpBonus);
      Relation.nApprenticeExpBonus := IniFile.ReadInteger(sSection, 'ApprenticeExpBonus', Relation.nApprenticeExpBonus);
      Relation.boCompleted := IniFile.ReadBool(sSection, 'Completed', Relation.boCompleted);
      Relation.dwCompleteTime := IniFile.ReadInteger(sSection, 'CompleteTime', Relation.dwCompleteTime);
      Relation.nCompleteLevel := IniFile.ReadInteger(sSection, 'CompleteLevel', Relation.nCompleteLevel);
      Relation.nDailyQuestCount := IniFile.ReadInteger(sSection, 'DailyQuestCount', Relation.nDailyQuestCount);
      Relation.nMaxDailyQuest := IniFile.ReadInteger(sSection, 'MaxDailyQuest', Relation.nMaxDailyQuest);
      Relation.nMentorPoints := IniFile.ReadInteger(sSection, 'MentorPoints', Relation.nMentorPoints);
      Relation.sLastQuestDate := IniFile.ReadString(sSection, 'LastQuestDate', '');
      Relation.dwCreateTime := IniFile.ReadInteger(sSection, 'CreateTime', Relation.dwCreateTime);
    end;
  finally
    IniFile.Free;
  end;
end;

function TMasterSystem.CreateRelation(sMasterName, sApprenticeName: string): Boolean;
var
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
  Relation: pTMasterApprentice;
  nCount: Integer;
begin
  Result := False;

  if sMasterName = sApprenticeName then
    Exit;

  if GetRelation(sMasterName, sApprenticeName) <> nil then
    Exit;

  MasterObj := UserEngine.GetPlayObject(sMasterName);
  ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);

  if (MasterObj = nil) or (ApprenticeObj = nil) then
    Exit;

  if not CanBeMaster(MasterObj) then
    Exit;

  if not CanBeApprentice(ApprenticeObj) then
    Exit;

  // Check level difference
  if (MasterObj.m_Abil.Level - ApprenticeObj.m_Abil.Level) < MASTER_MIN_LEVEL_DIFF then
    Exit;

  // Check if apprentice already has a master
  if GetMasterName(sApprenticeName) <> '' then
    Exit;

  // Check max apprentices
  nCount := GetApprenticeCount(sMasterName);
  if nCount >= MAX_APPRENTICE_COUNT then
    Exit;

  New(Relation);
  SafeFillChar(Relation^, SizeOf(TMasterApprentice), #0);
  Relation.sMasterName := sMasterName;
  Relation.sApprenticeName := sApprenticeName;
  Relation.dwCreateTime := GetTickCount;
  Relation.nMasterLevel := MasterObj.m_Abil.Level;
  Relation.nApprenticeLevel := ApprenticeObj.m_Abil.Level;
  Relation.nApprenticeCount := 0;
  Relation.nMasterExpBonus := 0;
  Relation.nApprenticeExpBonus := 0;
  Relation.boCompleted := False;
  Relation.dwCompleteTime := 0;
  Relation.nCompleteLevel := 0;
  Relation.nDailyQuestCount := 0;
  Relation.nMaxDailyQuest := DAILY_QUEST_LIMIT;
  Relation.nMentorPoints := 0;
  Relation.sLastQuestDate := FormatDateTime('yyyy-mm-dd', Now);

  m_MasterList.Add(Relation);
  SaveRelation(Relation);

  // Notify both players
  MasterObj.SysMsg(Format('恭喜你成功收徒: %s', [sApprenticeName]), c_Green, t_Hint);
  ApprenticeObj.SysMsg(Format('恭喜你成功拜师: %s', [sMasterName]), c_Green, t_Hint);

  // Send info updates
  SendMasterInfo(MasterObj);
  SendApprenticeInfo(ApprenticeObj);

  Result := True;
end;

function TMasterSystem.RemoveRelation(sMasterName, sApprenticeName: string): Boolean;
var
  I: Integer;
  Relation: pTMasterApprentice;
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
  sFileName: string;
  IniFile: TIniFile;
  sSection: string;
begin
  Result := False;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) then begin

      // Delete from file
      sFileName := g_Config.sEnvirDir + 'MasterApprentice.txt';
      if FileExists(sFileName) then begin
        IniFile := TIniFile.Create(sFileName);
        try
          sSection := Relation.sMasterName + '_' + Relation.sApprenticeName;
          IniFile.EraseSection(sSection);
          IniFile.UpdateFile;
        finally
          IniFile.Free;
        end;
      end;

      // Notify players
      MasterObj := UserEngine.GetPlayObject(sMasterName);
      if MasterObj <> nil then begin
        MasterObj.SysMsg(Format('师徒关系已解除: %s', [sApprenticeName]), c_Red, t_Hint);
        SendMasterInfo(MasterObj);
      end;

      ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);
      if ApprenticeObj <> nil then begin
        ApprenticeObj.SysMsg(Format('师徒关系已解除: %s', [sMasterName]), c_Red, t_Hint);
        SendApprenticeInfo(ApprenticeObj);
      end;

      Dispose(Relation);
      m_MasterList.Delete(I);
      Result := True;
      Exit;
    end;
  end;
end;

function TMasterSystem.CompleteApprenticeship(sApprenticeName: string): Boolean;
var
  I: Integer;
  Relation: pTMasterApprentice;
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
begin
  Result := False;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) and
      (not Relation.boCompleted) then begin

      ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);
      if ApprenticeObj = nil then
        Exit;

      if not CanGraduate(sApprenticeName) then
        Exit;

      Relation.boCompleted := True;
      Relation.dwCompleteTime := GetTickCount;
      Relation.nCompleteLevel := ApprenticeObj.m_Abil.Level;

      // Give mentor points to master
      Relation.nMentorPoints := Relation.nMentorPoints + MENTOR_POINTS_PER_GRADUATE;

      MasterObj := UserEngine.GetPlayObject(Relation.sMasterName);
      if MasterObj <> nil then begin
        MasterObj.m_Abil.Exp := MasterObj.m_Abil.Exp + 50000;
        MasterObj.SendAbility;
        MasterObj.SysMsg(Format('恭喜！你的徒弟 %s 已成功出师！获得 %d 师徒点数和额外经验奖励。',
          [sApprenticeName, MENTOR_POINTS_PER_GRADUATE]), c_Green, t_Hint);
        SendMasterInfo(MasterObj);
      end;

      ApprenticeObj.SysMsg(Format('恭喜你已成功出师！你的师傅是: %s',
        [Relation.sMasterName]), c_Green, t_Hint);
      SendApprenticeInfo(ApprenticeObj);

      SaveRelation(Relation);
      Result := True;
      Exit;
    end;
  end;
end;

function TMasterSystem.GetMasterName(sApprenticeName: string): string;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := '';
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) and
      (not Relation.boCompleted) then begin
      Result := Relation.sMasterName;
      Exit;
    end;
  end;
end;

function TMasterSystem.GetApprenticeNames(sMasterName: string): TStringList;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := TStringList.Create;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (not Relation.boCompleted) then begin
      Result.Add(Relation.sApprenticeName);
    end;
  end;
end;

function TMasterSystem.GetApprenticeCount(sMasterName: string): Integer;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := 0;
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (not Relation.boCompleted) then begin
      Inc(Result);
    end;
  end;
end;

function TMasterSystem.CanBeMaster(PlayObject: TPlayObject): Boolean;
begin
  Result := False;
  if PlayObject = nil then
    Exit;
  if PlayObject.m_Abil.Level < APPRENTICE_MIN_LEVEL then
    Exit;
  if GetApprenticeCount(PlayObject.m_sCharName) >= MAX_APPRENTICE_COUNT then
    Exit;
  Result := True;
end;

function TMasterSystem.CanBeApprentice(PlayObject: TPlayObject): Boolean;
begin
  Result := False;
  if PlayObject = nil then
    Exit;
  if PlayObject.m_Abil.Level >= APPRENTICE_MAX_LEVEL then
    Exit;
  if GetMasterName(PlayObject.m_sCharName) <> '' then
    Exit;
  Result := True;
end;

function TMasterSystem.CanGraduate(sApprenticeName: string): Boolean;
var
  PlayObject: TPlayObject;
begin
  Result := False;
  PlayObject := UserEngine.GetPlayObject(sApprenticeName);
  if PlayObject = nil then
    Exit;
  if PlayObject.m_Abil.Level < APPRENTICE_MIN_LEVEL then
    Exit;
  Result := True;
end;

function TMasterSystem.GetRelationType(sCharName: string): TMasterRelation;
var
  bIsMaster: Boolean;
  bIsApprentice: Boolean;
  I: Integer;
  Relation: pTMasterApprentice;
begin
  bIsMaster := False;
  bIsApprentice := False;

  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if Relation = nil then
      Continue;
    if Relation.boCompleted then
      Continue;

    if CompareText(Relation.sMasterName, sCharName) = 0 then
      bIsMaster := True;
    if CompareText(Relation.sApprenticeName, sCharName) = 0 then
      bIsApprentice := True;
  end;

  if bIsMaster and bIsApprentice then
    Result := mrBoth
  else if bIsMaster then
    Result := mrMaster
  else if bIsApprentice then
    Result := mrApprentice
  else
    Result := mrNone;
end;

function TMasterSystem.GetExpBonus(PlayObject: TPlayObject): Integer;
var
  I: Integer;
  sMasterName: string;
  sApprenticeName: string;
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
  Relation: pTMasterApprentice;
  RelationType: TMasterRelation;
begin
  Result := 0;
  if PlayObject = nil then
    Exit;

  RelationType := GetRelationType(PlayObject.m_sCharName);

  case RelationType of
    mrMaster:
      begin
        // Master gets bonus if apprentice is online and in same map
        sMasterName := PlayObject.m_sCharName;
        // Find the apprentice
        for I := 0 to m_MasterList.Count - 1 do begin
          Relation := pTMasterApprentice(m_MasterList.Items[I]);
          if (Relation <> nil) and
            (CompareText(Relation.sMasterName, sMasterName) = 0) and
            (not Relation.boCompleted) then begin
            ApprenticeObj := UserEngine.GetPlayObject(Relation.sApprenticeName);
            if (ApprenticeObj <> nil) and
              (CompareText(ApprenticeObj.m_sMapName, PlayObject.m_sMapName) = 0) then begin
              Result := MASTER_EXP_BONUS;
              Exit;
            end;
          end;
        end;
      end;
    mrApprentice:
      begin
        sApprenticeName := PlayObject.m_sCharName;
        sMasterName := GetMasterName(sApprenticeName);
        if sMasterName <> '' then begin
          MasterObj := UserEngine.GetPlayObject(sMasterName);
          if (MasterObj <> nil) and
            (CompareText(MasterObj.m_sMapName, PlayObject.m_sMapName) = 0) then begin
            Result := APPRENTICE_EXP_BONUS;
            Exit;
          end;
        end;
      end;
    mrBoth:
      begin
        // Both master and apprentice - check both relations
        // As master: check if any apprentice is online in same map
        for I := 0 to m_MasterList.Count - 1 do begin
          Relation := pTMasterApprentice(m_MasterList.Items[I]);
          if (Relation <> nil) and
            (CompareText(Relation.sMasterName, PlayObject.m_sCharName) = 0) and
            (not Relation.boCompleted) then begin
            ApprenticeObj := UserEngine.GetPlayObject(Relation.sApprenticeName);
            if (ApprenticeObj <> nil) and
              (CompareText(ApprenticeObj.m_sMapName, PlayObject.m_sMapName) = 0) then begin
              Result := MASTER_EXP_BONUS;
              Exit;
            end;
          end;
        end;
        // As apprentice: check if master is online in same map
        sMasterName := GetMasterName(PlayObject.m_sCharName);
        if sMasterName <> '' then begin
          MasterObj := UserEngine.GetPlayObject(sMasterName);
          if (MasterObj <> nil) and
            (CompareText(MasterObj.m_sMapName, PlayObject.m_sMapName) = 0) then begin
            Result := APPRENTICE_EXP_BONUS;
            Exit;
          end;
        end;
      end;
  end;
end;

procedure TMasterSystem.AddMentorPoints(sMasterName: string; nPoints: Integer);
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (not Relation.boCompleted) then begin
      Relation.nMentorPoints := Relation.nMentorPoints + nPoints;
      SaveRelation(Relation);
    end;
  end;
end;

function TMasterSystem.GetMentorPoints(sMasterName: string): Integer;
var
  I: Integer;
  Relation: pTMasterApprentice;
begin
  Result := 0;
  // Sum mentor points from all active relations as master
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (not Relation.boCompleted) then begin
      Result := Result + Relation.nMentorPoints;
    end;
  end;
end;

function TMasterSystem.CompleteDailyQuest(sMasterName, sApprenticeName: string): Boolean;
var
  Relation: pTMasterApprentice;
  sToday: string;
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
begin
  Result := False;
  Relation := GetRelation(sMasterName, sApprenticeName);
  if Relation = nil then
    Exit;
  if Relation.boCompleted then
    Exit;

  sToday := FormatDateTime('yyyy-mm-dd', Now);

  // Check if date changed, reset daily count
  if Relation.sLastQuestDate <> sToday then begin
    Relation.nDailyQuestCount := 0;
    Relation.sLastQuestDate := sToday;
  end;

  // Check daily limit
  if Relation.nDailyQuestCount >= Relation.nMaxDailyQuest then
    Exit;

  // Check if both online
  MasterObj := UserEngine.GetPlayObject(sMasterName);
  ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);
  if (MasterObj = nil) or (ApprenticeObj = nil) then
    Exit;

  Inc(Relation.nDailyQuestCount);
  Relation.nMentorPoints := Relation.nMentorPoints + MENTOR_POINTS_PER_QUEST;
  SaveRelation(Relation);

  MasterObj.SysMsg(Format('师徒每日任务完成！进度: %d/%d，获得 %d 师徒点数',
    [Relation.nDailyQuestCount, Relation.nMaxDailyQuest, MENTOR_POINTS_PER_QUEST]),
    c_Green, t_Hint);
  ApprenticeObj.SysMsg(Format('师徒每日任务完成！进度: %d/%d',
    [Relation.nDailyQuestCount, Relation.nMaxDailyQuest]),
    c_Green, t_Hint);

  Result := True;
end;

procedure TMasterSystem.ResetDailyQuests;
var
  I: Integer;
  Relation: pTMasterApprentice;
  sToday: string;
begin
  sToday := FormatDateTime('yyyy-mm-dd', Now);
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and (not Relation.boCompleted) then begin
      if Relation.sLastQuestDate <> sToday then begin
        Relation.nDailyQuestCount := 0;
        Relation.sLastQuestDate := sToday;
        SaveRelation(Relation);
      end;
    end;
  end;
end;

function TMasterSystem.IsBothOnline(sMasterName, sApprenticeName: string): Boolean;
var
  MasterObj: TPlayObject;
  ApprenticeObj: TPlayObject;
begin
  Result := False;
  MasterObj := UserEngine.GetPlayObject(sMasterName);
  if MasterObj = nil then
    Exit;
  ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);
  if ApprenticeObj = nil then
    Exit;
  Result := True;
end;

procedure TMasterSystem.SendMasterInfo(PlayObject: TPlayObject);
var
  RelationType: TMasterRelation;
  ApprenticeNames: TStringList;
  I: Integer;
  sInfo: string;
  nCount: Integer;
begin
  if PlayObject = nil then
    Exit;

  RelationType := GetRelationType(PlayObject.m_sCharName);

  if RelationType in [mrMaster, mrBoth] then begin
    ApprenticeNames := GetApprenticeNames(PlayObject.m_sCharName);
    try
      nCount := ApprenticeNames.Count;
      sInfo := '徒弟列表 (' + IntToStr(nCount) + '/' + IntToStr(MAX_APPRENTICE_COUNT) + '):';
      for I := 0 to ApprenticeNames.Count - 1 do begin
        sInfo := sInfo + ' ' + ApprenticeNames.Strings[I];
      end;
      PlayObject.SendMsg(PlayObject, SM_MASTERINFO, 0, 0, 0, 0, sInfo);
    finally
      ApprenticeNames.Free;
    end;
  end;
end;

procedure TMasterSystem.SendApprenticeInfo(PlayObject: TPlayObject);
var
  sMasterName: string;
  sInfo: string;
  MasterObj: TPlayObject;
  nPoints: Integer;
begin
  if PlayObject = nil then
    Exit;

  sMasterName := GetMasterName(PlayObject.m_sCharName);
  if sMasterName <> '' then begin
    MasterObj := UserEngine.GetPlayObject(sMasterName);
    nPoints := GetMentorPoints(sMasterName);
    if MasterObj <> nil then begin
      sInfo := '师傅: ' + sMasterName + ' (在线) 等级: ' +
        IntToStr(MasterObj.m_Abil.Level) + ' 师徒点数: ' + IntToStr(nPoints);
    end else begin
      sInfo := '师傅: ' + sMasterName + ' (离线) 师徒点数: ' + IntToStr(nPoints);
    end;
    PlayObject.SendMsg(PlayObject, SM_MASTERINFO, 1, 0, 0, 0, sInfo);
  end else begin
    PlayObject.SendMsg(PlayObject, SM_MASTERINFO, 1, 0, 0, 0, '你没有师傅');
  end;
end;

procedure TMasterSystem.LoadConfig(sConfigFile: string);
var
  sFileName: string;
  IniFile: TIniFile;
  Sections: TStringList;
  I: Integer;
  Relation: pTMasterApprentice;
  sSection: string;
  sMasterName: string;
  sApprenticeName: string;
begin
  // Clear existing list
  for I := 0 to m_MasterList.Count - 1 do begin
    if m_MasterList.Items[I] <> nil then
      Dispose(pTMasterApprentice(m_MasterList.Items[I]));
  end;
  m_MasterList.Clear;

  if sConfigFile <> '' then
    sFileName := sConfigFile
  else
    sFileName := g_Config.sEnvirDir + 'MasterApprentice.txt';

  if not FileExists(sFileName) then
    Exit;

  IniFile := TIniFile.Create(sFileName);
  Sections := TStringList.Create;
  try
    IniFile.ReadSections(Sections);
    for I := 0 to Sections.Count - 1 do begin
      sSection := Sections.Strings[I];
      sMasterName := IniFile.ReadString(sSection, 'MasterName', '');
      sApprenticeName := IniFile.ReadString(sSection, 'ApprenticeName', '');

      if (sMasterName = '') or (sApprenticeName = '') then
        Continue;

      New(Relation);
      SafeFillChar(Relation^, SizeOf(TMasterApprentice), #0);
      Relation.sMasterName := sMasterName;
      Relation.sApprenticeName := sApprenticeName;
      Relation.dwCreateTime := IniFile.ReadInteger(sSection, 'CreateTime', 0);
      Relation.nMasterLevel := IniFile.ReadInteger(sSection, 'MasterLevel', 0);
      Relation.nApprenticeLevel := IniFile.ReadInteger(sSection, 'ApprenticeLevel', 0);
      Relation.nApprenticeCount := IniFile.ReadInteger(sSection, 'ApprenticeCount', 0);
      Relation.nMasterExpBonus := IniFile.ReadInteger(sSection, 'MasterExpBonus', 0);
      Relation.nApprenticeExpBonus := IniFile.ReadInteger(sSection, 'ApprenticeExpBonus', 0);
      Relation.boCompleted := IniFile.ReadBool(sSection, 'Completed', False);
      Relation.dwCompleteTime := IniFile.ReadInteger(sSection, 'CompleteTime', 0);
      Relation.nCompleteLevel := IniFile.ReadInteger(sSection, 'CompleteLevel', 0);
      Relation.nDailyQuestCount := IniFile.ReadInteger(sSection, 'DailyQuestCount', 0);
      Relation.nMaxDailyQuest := IniFile.ReadInteger(sSection, 'MaxDailyQuest', DAILY_QUEST_LIMIT);
      Relation.nMentorPoints := IniFile.ReadInteger(sSection, 'MentorPoints', 0);
      Relation.sLastQuestDate := IniFile.ReadString(sSection, 'LastQuestDate', '');

      m_MasterList.Add(Relation);
    end;
  finally
    Sections.Free;
    IniFile.Free;
  end;
end;

procedure TMasterSystem.SaveConfig(sConfigFile: string);
var
  I: Integer;
  sFileName: string;
  IniFile: TIniFile;
  Relation: pTMasterApprentice;
  sSection: string;
begin
  if sConfigFile <> '' then
    sFileName := sConfigFile
  else
    sFileName := g_Config.sEnvirDir + 'MasterApprentice.txt';

  // Delete existing file to do a clean save
  if FileExists(sFileName) then
    DeleteFile(sFileName);

  if m_MasterList.Count = 0 then
    Exit;

  IniFile := TIniFile.Create(sFileName);
  try
    for I := 0 to m_MasterList.Count - 1 do begin
      Relation := pTMasterApprentice(m_MasterList.Items[I]);
      if Relation = nil then
        Continue;

      sSection := Relation.sMasterName + '_' + Relation.sApprenticeName;
      IniFile.WriteString(sSection, 'MasterName', Relation.sMasterName);
      IniFile.WriteString(sSection, 'ApprenticeName', Relation.sApprenticeName);
      IniFile.WriteInteger(sSection, 'CreateTime', Relation.dwCreateTime);
      IniFile.WriteInteger(sSection, 'MasterLevel', Relation.nMasterLevel);
      IniFile.WriteInteger(sSection, 'ApprenticeLevel', Relation.nApprenticeLevel);
      IniFile.WriteInteger(sSection, 'ApprenticeCount', Relation.nApprenticeCount);
      IniFile.WriteInteger(sSection, 'MasterExpBonus', Relation.nMasterExpBonus);
      IniFile.WriteInteger(sSection, 'ApprenticeExpBonus', Relation.nApprenticeExpBonus);
      IniFile.WriteBool(sSection, 'Completed', Relation.boCompleted);
      IniFile.WriteInteger(sSection, 'CompleteTime', Relation.dwCompleteTime);
      IniFile.WriteInteger(sSection, 'CompleteLevel', Relation.nCompleteLevel);
      IniFile.WriteInteger(sSection, 'DailyQuestCount', Relation.nDailyQuestCount);
      IniFile.WriteInteger(sSection, 'MaxDailyQuest', Relation.nMaxDailyQuest);
      IniFile.WriteInteger(sSection, 'MentorPoints', Relation.nMentorPoints);
      IniFile.WriteString(sSection, 'LastQuestDate', Relation.sLastQuestDate);
    end;
    IniFile.UpdateFile;
  finally
    IniFile.Free;
  end;
end;

function TMasterSystem.MasterRecall(PlayObject: TPlayObject): Boolean;
var
  sMasterName: string;
  MasterObj: TPlayObject;
  Relation: pTMasterApprentice;
  I: Integer;
begin
  Result := False;
  if PlayObject = nil then
    Exit;

  sMasterName := GetMasterName(PlayObject.m_sCharName);
  if sMasterName = '' then begin
    PlayObject.SysMsg('你没有师傅，无法使用师徒传送。', c_Red, t_Hint);
    Exit;
  end;

  MasterObj := UserEngine.GetPlayObject(sMasterName);
  if MasterObj = nil then begin
    PlayObject.SysMsg('你的师傅不在线。', c_Red, t_Hint);
    Exit;
  end;

  // Check if on same map or cost
  // Find the relation
  for I := 0 to m_MasterList.Count - 1 do begin
    Relation := pTMasterApprentice(m_MasterList.Items[I]);
    if (Relation <> nil) and
      (CompareText(Relation.sMasterName, sMasterName) = 0) and
      (CompareText(Relation.sApprenticeName, PlayObject.m_sCharName) = 0) then begin
      // Check mentor points
      if Relation.nMentorPoints < APPRENTICE_RECALL_COST then begin
        PlayObject.SysMsg('师徒点数不足，需要 ' + IntToStr(APPRENTICE_RECALL_COST) + ' 点。',
          c_Red, t_Hint);
        Exit;
      end;

      // Deduct points
      Dec(Relation.nMentorPoints, APPRENTICE_RECALL_COST);
      SaveRelation(Relation);

      // Teleport apprentice to master
      PlayObject.m_sMapName := MasterObj.m_sMapName;
      PlayObject.m_nCurrX := MasterObj.m_nCurrX;
      PlayObject.m_nCurrY := MasterObj.m_nCurrY;
      PlayObject.SendMsg(PlayObject, SM_CHANGEMAP, 0, 0, 0, 0, '');
      PlayObject.SysMsg('你已传送到师傅身边，消耗 ' + IntToStr(APPRENTICE_RECALL_COST) + ' 师徒点数。',
        c_Green, t_Hint);
      MasterObj.SysMsg(Format('你的徒弟 %s 传送到你身边。', [PlayObject.m_sCharName]),
        c_Green, t_Hint);
      Result := True;
      Exit;
    end;
  end;
end;

function TMasterSystem.ApprenticeRecall(PlayObject: TPlayObject): Boolean;
var
  ApprenticeNames: TStringList;
  I: Integer;
  J: Integer;
  Relation: pTMasterApprentice;
  ApprenticeObj: TPlayObject;
  sApprenticeName: string;
begin
  Result := False;
  if PlayObject = nil then
    Exit;

  ApprenticeNames := GetApprenticeNames(PlayObject.m_sCharName);
  try
    if ApprenticeNames.Count = 0 then begin
      PlayObject.SysMsg('你没有徒弟在线，无法使用师徒召唤。', c_Red, t_Hint);
      Exit;
    end;

    // Find the first online apprentice and recall
    for I := 0 to ApprenticeNames.Count - 1 do begin
      sApprenticeName := ApprenticeNames.Strings[I];
      ApprenticeObj := UserEngine.GetPlayObject(sApprenticeName);
      if ApprenticeObj = nil then
        Continue;

      // Find the relation for mentor points
      for J := 0 to m_MasterList.Count - 1 do begin
        Relation := pTMasterApprentice(m_MasterList.Items[J]);
        if (Relation <> nil) and
          (CompareText(Relation.sMasterName, PlayObject.m_sCharName) = 0) and
          (CompareText(Relation.sApprenticeName, sApprenticeName) = 0) then begin

          if Relation.nMentorPoints < MASTER_RECALL_COST then begin
            PlayObject.SysMsg('师徒点数不足，需要 ' + IntToStr(MASTER_RECALL_COST) + ' 点。',
              c_Red, t_Hint);
            Exit;
          end;

          Dec(Relation.nMentorPoints, MASTER_RECALL_COST);
          SaveRelation(Relation);

          // Teleport apprentice to master
          ApprenticeObj.m_sMapName := PlayObject.m_sMapName;
          ApprenticeObj.m_nCurrX := PlayObject.m_nCurrX;
          ApprenticeObj.m_nCurrY := PlayObject.m_nCurrY;
          ApprenticeObj.SendMsg(ApprenticeObj, SM_CHANGEMAP, 0, 0, 0, 0, '');
          PlayObject.SysMsg(Format('你已将徒弟 %s 召唤到身边，消耗 %d 师徒点数。',
            [sApprenticeName, MASTER_RECALL_COST]), c_Green, t_Hint);
          ApprenticeObj.SysMsg(Format('师傅 %s 将你召唤到身边。', [PlayObject.m_sCharName]),
            c_Green, t_Hint);
          Result := True;
          Exit;
        end;
      end;
    end;

    PlayObject.SysMsg('没有在线徒弟可召唤。', c_Red, t_Hint);
  finally
    ApprenticeNames.Free;
  end;
end;

end.