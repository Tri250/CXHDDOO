unit Guild;

interface

uses
  Windows, SysUtils, Classes, IniFiles, ObjBase, ObjPlay;

type
  pTGuildUserInfo = ^TGuildUserInfo;
  TGuildUserInfo = packed record
    PlayObject: TPlayObject;
    LoginTime: TDateTime;
    MemberIndex: Word;
  end;

  pTGuildRank = ^TGuildRank;
  TGuildRank = packed record
    nRankNo: Byte;
    sRankName: string[16];
    MembersList: TStringList;
  end;

  pTWarGuild = ^TWarGuild;
  TWarGuild = packed record
    Guild: TObject;
    dwWarTick: LongWord;
    dwWarTime: LongWord;
  end;

  TGuild = class
    m_sGuildName: string;
    m_NoticeList: TStringList;
    m_GuildWarList: TStringList;
    m_GuildAllList: TStringList;
    m_RankList: TList; // 职位列表

    m_sChiefName: string;
    m_sCreateGuildName: string;  //行会创始人
    m_dwCreateGuildTime: TDateTime; //行会创建时间
    m_dwCallGuildTime: TDateTime;
    m_btMaxMemberCount: Byte; //行会人员数量
    m_btKillMonExpRate: Byte;
    m_btKillMonAttackRate: Byte;
    m_AttackCastle: TObject;
    m_TeamFightDeadList: TStringList;
    m_nContestPoint: Integer;
    m_boTeamFight: Boolean;
    m_boEnableAuthAlly: Boolean;
    m_DynamicVarList: TList;

    m_GuildIndex: Integer;
    m_boChanged: Boolean;
    m_dwSaveTick: LongWord;
    m_WarArr: array of Boolean;
    m_AllyArr: array of Boolean;
    m_UpNoticeIndex: Word;
    m_UpMemberIndex: Word;
    m_UpSocietyIndex: Word;
    m_UpInfoIndex: Word;
    m_boInfoRead: Boolean;
    m_dwRunTick: LongWord;
  private
    m_btGuildLevel: Byte; //行会等级
    m_nMoneyCount: Integer; //行会资金
    m_nBuildPoint: Integer; //建筑度
    m_nActivityPoint: Integer; //人气度
    m_nStabilityPoint: Integer; //安定度
    m_nFlourishingPoint: Integer; //繁荣度
    m_nMaxActivityPoint: Integer; //最高人气度
    m_nKillMobCount: Integer;

    // === 新增: 行会科技树字段 ===
    m_GuildTechList: TList;              // 行会科技列表(TGuildTech)
    m_boTechChanged: Boolean;            // 科技是否变更
    m_nTechUpgradeCount: Integer;        // 科技升级次数

    // === 新增: 行会仓库字段 ===
    m_GuildStorageList: TList;           // 行会仓库物品列表(TUserItem)
    m_boStorageChanged: Boolean;         // 仓库是否变更
    m_nStorageGold: Integer;             // 行会仓库金币
    m_nStorageMaxSlots: Integer;         // 行会仓库最大容量
    m_boStoragePublic: Boolean;          // 行会仓库是否公开给盟友

    // === 新增: 行会活动积分 ===
    m_nGuildActivityScore: Integer;     // 行会活动积分
    m_dwLastActivityTime: LongWord;     // 上次活动时间
    procedure ClearRank(var RankList: TList);
    function LoadGuildFile(sGuildFileName: string): Boolean;
    procedure SaveGuildFile(sFileName: string);
    procedure SetWar(nGuildIndex: Integer; boTrue: Boolean);
    procedure SetAlly(nGuildIndex: Integer; boTrue: Boolean);
    function GetWar(nGuildIndex: Integer): Boolean;
    function GetAlly(nGuildIndex: Integer): Boolean;
    function SetGuildInfo(PlayObject: TPlayObject): Boolean;
    procedure SetActivityPoint(const Value: Integer);
    procedure SetBuildPoint(const Value: Integer);
    procedure SetFlourishingPoint(const Value: Integer);
    procedure SetMoneyCount(const Value: Integer);
    procedure SetStabilityPoint(const Value: Integer);
    procedure SetGuildLevel(const Value: Byte);
    procedure RefGuildMemberMaxCount;

  public

    constructor Create(sName: string);
    destructor Destroy; override;
    function LoadGuild(): Boolean;
    procedure initialize();
    procedure DelWarGuild(Guild: TGuild);
    procedure SendGuildMsg(sMsg: string);
    procedure SendGuildSocket(wIdent: Word; nRecog: Integer; nParam, nTag, nSeries: Word; sMsg: string);
    procedure CheckSaveGuildFile;
    procedure UpdateGuildFile;
    function IsMember(sName: string): Boolean;
    procedure DeleteGuild;
    procedure RefMemberName();
    function IsAllyGuild(Guild: TGuild): Boolean;
    function IsWarGuild(Guild: TGuild): Boolean;
    procedure TeamFightWhoDead(sName: string);
    procedure TeamFightWhoWinPoint(sName: string; nPoint: Integer);
    procedure StartTeamFight();
    procedure EndTeamFight();
    procedure AddTeamFightMember(sHumanName: string);
    function GetChiefName(): string;
    function AddMember(PlayObject: TPlayObject): Boolean;
    function DelMember(sHumName: string; nRankNo: Integer): Boolean;
    procedure MemberLogin(PlayObject: TPlayObject);
    procedure MemberGhost(PlayObject: TPlayObject);
    function IsNotWar(Guild: TGuild): Boolean;
    function AllyGuild(Guild: TGuild): Boolean;
    function DelAllyGuild(Guild: TGuild): Boolean;
    function AddWarGuild(Guild: TGuild): pTWarGuild;
    function UpdateRank(sRankData: string): Integer;
    function GoldChange(nWork, nGold: Integer; PlayObject: TPlayObject): Integer;
    function LevelUp: Boolean;
    function IsFull: Boolean;
    procedure IncNoticeIndex;
    procedure IncMemberIndex;
    procedure IncSocietyIndex;
    procedure IncInfoIndex;
    procedure IncLevel(nCount: Integer);
    procedure SetLevel(nLevel: Integer);
    procedure IncMoneyCount(nCount: Integer);
    procedure IncBuildPoint(nCount: Integer);
    procedure IncActivityPoint(nCount: Integer);
    procedure IncStabilityPoint(nCount: Integer);
    procedure IncFlourishingPoint(nCount: Integer);
    procedure IncKillMobCount;
    procedure DecLevel(nCount: Integer);
    procedure DecMoneyCount(nCount: Integer);
    procedure DecBuildPoint(nCount: Integer);
    procedure DecActivityPoint(nCount: Integer);
    procedure DecStabilityPoint(nCount: Integer);
    procedure DecFlourishingPoint(nCount: Integer);
    property nMoneyCount: Integer read m_nMoneyCount write SetMoneyCount;
    property nBuildPoint: Integer read m_nBuildPoint write SetBuildPoint;
    property nActivityPoint: Integer read m_nActivityPoint write SetActivityPoint;
    property nStabilityPoint: Integer read m_nStabilityPoint write SetStabilityPoint;
    property nFlourishingPoint: Integer read m_nFlourishingPoint write SetFlourishingPoint;
    property btLevel: Byte read m_btGuildLevel write SetGuildLevel;
    property nMaxActivityPoint: Integer read m_nMaxActivityPoint;

    // === 新增: 行会科技树方法 ===
    procedure InitGuildTechs;                    // 初始化科技树
    function UpgradeTech(nTechID: Word): Boolean; // 升级科技
    function GetTechLevel(nTechID: Word): Integer; // 获取科技等级
    function GetTechEffect(nTechID: Word): Integer; // 获取科技效果值
    function GetTechList: TList;                 // 获取科技列表
    procedure CalcTechModifiers(var AddAbility: TAddAbility); // 计算科技属性加成
    procedure SendTechInfo(PlayObject: TPlayObject); // 发送科技信息给客户端
    procedure CheckTechUpgrade;                  // 检查科技升级完成

    // === 新增: 行会仓库方法 ===
    function AddStorageItem(UserItem: pTUserItem): Boolean;  // 存入物品
    function RemoveStorageItem(nSlot: Integer): pTUserItem;  // 取出物品
    function GetStorageItem(nSlot: Integer): pTUserItem;     // 查看物品
    function GetStorageItemCount: Integer;                   // 获取物品数量
    function GetStorageUsedSlots: Integer;                   // 获取已用槽位
    procedure InitStorage;                                    // 初始化仓库
    procedure SaveStorage;                                    // 保存仓库
    procedure LoadStorage;                                    // 加载仓库
    procedure SendStorageInfo(PlayObject: TPlayObject);       // 发送仓库信息
    function CanAccessStorage(PlayObject: TPlayObject): Boolean; // 检查访问权限
    function DonateStorageGold(PlayObject: TPlayObject; nAmount: Integer): Boolean; // 捐献金币
  end;

  TGuildManager = class
    m_GuildList: TList;
  private

  public
    constructor Create();
    destructor Destroy; override;
    procedure LoadGuildInfo();
    procedure SaveGuildList();
    procedure Run();
    procedure ClearGuildAttackCastle();
    function FindGuild(sGuildName: string): TGuild;
    function MemberOfGuild(sCharName, sGuildName: string): TGuild;
    function DelGuild(sGuildName: string): Boolean;
    function AddGuild(sGuildName: string; PlayObject: TPlayObject): Boolean;
  end;

implementation

uses
  M2Share, HUtil32, Grobal2, EDcodeEx;

const
  MAXACTIVITYPOINT = 1500;

var
  GuildIndex: Word = 1;

function GetGuildIndex: Word;
begin
  Inc(GuildIndex);
  Result := GuildIndex;
end;

{ TGuild }

function TGuild.AddMember(PlayObject: TPlayObject): Boolean;
var
  I: Integer;
  GuildRank: pTGuildRank;
  GuildRank18: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
begin
  //  Result := False;
  GuildRank18 := nil;
  for I := m_RankList.Count - 1 downto 0 do begin
    GuildRank := m_RankList.Items[I];
    if GuildRank.nRankNo = 99 then begin
      GuildRank18 := GuildRank;
      break;
    end;
  end;
  if GuildRank18 = nil then begin
    New(GuildRank18);
    GuildRank18.nRankNo := 99;
    GuildRank18.sRankName := g_Config.sGuildMemberRank;
    GuildRank18.MembersList := TStringList.Create;
    m_RankList.Add(GuildRank18);
  end;
  PlayObject.m_dwGuildTick := GetTickCount + 60 * 60 * 1000;
  New(GuildUserInfo);
  GuildUserInfo.MemberIndex := 0;
  GuildUserInfo.PlayObject := PlayObject;
  GuildUserInfo.LoginTime := Now;
  GuildRank18.MembersList.AddObject(PlayObject.m_sCharName, TObject(GuildUserInfo));
  UpdateGuildFile();                  
  SendGuildSocket(SM_GUILDADDMEMBER_OK, GuildRank18.MembersList.Count, GuildRank18.nRankNo, 0, 0,
    EncodeString(GuildRank18.sRankName + '/' + PlayObject.m_sCharName));
  Result := True;
end;

procedure TGuild.AddTeamFightMember(sHumanName: string);
begin
  m_TeamFightDeadList.Add(sHumanName);
end;

function TGuild.AddWarGuild(Guild: TGuild): pTWarGuild;
var
  I: Integer;
  WarGuild: pTWarGuild;
begin
  Result := nil;
  if (Guild <> nil) and (Guild <> Self) then begin
    if not IsAllyGuild(Guild) then begin
      WarGuild := nil;
      for I := 0 to m_GuildWarList.Count - 1 do begin
        if pTWarGuild(m_GuildWarList.Objects[I]).Guild = Guild then begin
          SetWar(Guild.m_GuildIndex, True);
          WarGuild := pTWarGuild(m_GuildWarList.Objects[I]);
          WarGuild.dwWarTick := GetTickCount();
          WarGuild.dwWarTime := g_Config.dwGuildWarTime;
          SendGuildMsg('与[' + Guild.m_sGuildName + ']行会战争将持续' + IntToStr(g_Config.dwGuildWarTime div 1000 div 60) + '分钟。');
          break;
        end;
      end;
      if WarGuild = nil then begin
        SetWar(Guild.m_GuildIndex, True);
        New(WarGuild);
        WarGuild.Guild := Guild;
        WarGuild.dwWarTick := GetTickCount();
        WarGuild.dwWarTime := g_Config.dwGuildWarTime {10800000};
        m_GuildWarList.AddObject(Guild.m_sGuildName, TObject(WarGuild));
        SendGuildMsg('与[' + Guild.m_sGuildName + ']行会战争开始(' + IntToStr(g_Config.dwGuildWarTime div 1000 div 60) + '分钟)');
        RefMemberName();
        IncSocietyIndex;
      end;
      Result := WarGuild;
      UpdateGuildFile();
    end;
  end;
end;

function TGuild.AllyGuild(Guild: TGuild): Boolean;
var
  I: Integer;
begin
  Result := False;
  for I := 0 to m_GuildAllList.Count - 1 do begin
    if m_GuildAllList.Objects[I] = Guild then begin
      Exit;
    end;
  end;
  SetAlly(Guild.m_GuildIndex, True);
  m_GuildAllList.AddObject(Guild.m_sGuildName, Guild);
  UpdateGuildFile;
  IncSocietyIndex;
  Result := True;
end;

procedure TGuild.CheckSaveGuildFile;
begin
  if m_boChanged and ((GetTickCount - m_dwSaveTick) > 30 * 1000) then begin
    UpdateGuildFile;
  end;
end;

procedure TGuild.ClearRank(var RankList: TList);
var
  GuildRank: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
  i: Integer;
begin
  for GuildRank in RankList do begin
    for I := 0 to GuildRank.MembersList.Count - 1 do begin
      GuildUserInfo := pTGuildUserInfo(GuildRank.MembersList.Objects[I]);
      Dispose(GuildUserInfo);
    end;
    GuildRank.MembersList.Free;
    Dispose(GuildRank);
  end;
  RankList.Clear;
end;

constructor TGuild.Create(sName: string);
begin
  inherited Create;
  m_sGuildName := sName;
  m_NoticeList := TStringList.Create;
  m_GuildWarList := TStringList.Create;
  m_GuildAllList := TStringList.Create;
  m_RankList := TList.Create;

  m_dwRunTick := GetTickCount + 60 * 60 * 1000;

  m_sCreateGuildName := '';  //行会创始人
  m_dwCreateGuildTime := Now; //行会创建时间
  m_btGuildLevel := 0;
  m_btMaxMemberCount := g_Config.nDefGuildMemberLimit; //行会人员数量
  m_btKillMonExpRate := 0;
  m_btKillMonAttackRate := 0;
  m_nMoneyCount := 0; //行会资金
  m_nBuildPoint := 0; //建筑度
  m_nActivityPoint := 0; //人气度
  m_nStabilityPoint := 0; //安定度
  m_nFlourishingPoint := 0; //繁荣度
  m_nMaxActivityPoint := 0;
  m_nKillMobCount := 0;
  m_boChanged := False;
  m_dwSaveTick := GetTickCount;
  m_WarArr := nil;
  m_AllyArr := nil;
  m_dwCallGuildTime := Now;
  m_AttackCastle := nil;
  m_TeamFightDeadList := TStringList.Create;
  m_nContestPoint := 0;
  m_boTeamFight := False;
  m_boEnableAuthAlly := False;
  m_DynamicVarList := TList.Create;
  m_UpNoticeIndex := 1;
  m_UpMemberIndex := 1;
  m_UpSocietyIndex := 1;
  m_UpInfoIndex := 1;
  m_sChiefName := '';
  m_boInfoRead := False;
end;

procedure TGuild.DecActivityPoint(nCount: Integer);
begin
  IntegerChange(m_nActivityPoint, nCount, INT_DEL);
  IncInfoIndex;
end;

procedure TGuild.DecBuildPoint(nCount: Integer);
begin
  IntegerChange(m_nBuildPoint, nCount, INT_DEL);
  IncInfoIndex;
end;

procedure TGuild.DecFlourishingPoint(nCount: Integer);
begin
  IntegerChange(m_nFlourishingPoint, nCount, INT_DEL);
  IncInfoIndex;
end;

procedure TGuild.DecLevel(nCount: Integer);
begin
  ByteChange(m_btGuildLevel, nCount, INT_DEL);
  IncInfoIndex;
end;

procedure TGuild.DecMoneyCount(nCount: Integer);
begin
  IntegerChange(m_nMoneyCount, nCount, INT_DEL);
  IncInfoIndex;
end;

procedure TGuild.DecStabilityPoint(nCount: Integer);
begin
  IntegerChange(m_nStabilityPoint, nCount, INT_DEL);
  IncInfoIndex;
end;

function TGuild.DelAllyGuild(Guild: TGuild): Boolean;
var
  I: Integer;
begin
  Result := False;
  for I := 0 to m_GuildAllList.Count - 1 do begin
    if TGuild(m_GuildAllList.Objects[I]) = Guild then begin
      m_GuildAllList.Delete(I);
      SetAlly(Guild.m_GuildIndex, False);
      UpdateGuildFile;
      IncSocietyIndex;
      Result := True;
      break;
    end;
  end; // for
end;

procedure TGuild.DeleteGuild;
var
  I, II: Integer;
  PlayObject: TPlayObject;
  GuildRank: pTGuildRank;
begin
  SaveGuildFile(g_Config.sGuildDir + m_sGuildName + '.ini.bak');
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      PlayObject := TPlayObject(pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject);
      if PlayObject <> nil then begin
        PlayObject.m_MyGuild := nil;
        PlayObject.m_sGuildName := '';
        PlayObject.RefRankInfo(0, '');
        PlayObject.RefShowName(); //10/31
      end;
      Dispose(pTGuildUserInfo(GuildRank.MembersList.Objects[II]));
    end;
    GuildRank.MembersList.Free;
    Dispose(GuildRank);
  end;
  m_RankList.Clear;
  m_NoticeList.Clear;
  for I := 0 to m_GuildWarList.Count - 1 do begin
    Dispose(pTWarGuild(m_GuildWarList.Objects[I]));
  end;
  m_GuildWarList.Clear;
  m_GuildAllList.Clear;
end;

function TGuild.DelMember(sHumName: string; nRankNo: Integer): Boolean;
var
  I, II: Integer;
  GuildRank: pTGuildRank;
begin
  Result := False;
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      if CompareText(GuildRank.MembersList.Strings[II], sHumName) = 0 then begin
        if (nRankNo = 1) or (GuildRank.nRankNo > 3) then begin
          Dispose(pTGuildUserInfo(GuildRank.MembersList.Objects[ii]));
          GuildRank.MembersList.Delete(II);
          SendGuildSocket(SM_GUILDDELMEMBER_OK, 0, 0, 0, 0, EncodeString(sHumName));
          Result := True;
        end;
        break;
      end;
    end;
    if Result then
      break;
  end;
  if Result then
    UpdateGuildFile;
end;

procedure TGuild.DelWarGuild(Guild: TGuild);
begin
  SendGuildMsg('与[' + Guild.m_sGuildName + ']行会战争结束');
end;

destructor TGuild.Destroy;
var
  i: integer;
begin
  ClearRank(m_RankList);
  m_NoticeList.Free;
  m_GuildWarList.Free;
  m_RankList.Free;
  m_GuildAllList.Free;
  m_TeamFightDeadList.Free;
  for I := 0 to m_DynamicVarList.Count - 1 do begin
    Dispose(pTDynamicVar(m_DynamicVarList.Items[I]));
  end;
  m_DynamicVarList.Free;
  inherited;
end;

procedure TGuild.EndTeamFight;
begin
  m_boTeamFight := False;
end;

function TGuild.GetAlly(nGuildIndex: Integer): Boolean;
begin
  Result := False;
  if nGuildIndex in [Low(m_AllyArr)..High(m_AllyArr)] then
    Result := m_AllyArr[nGuildIndex]
  else
    MainOutMessage('GetAlly Error by ' + IntToStr(nGuildIndex));
end;

function TGuild.GetChiefName: string;
{var
  GuildRank: pTGuildRank;    }
begin
  Result := m_sChiefName;
{  Result := '';
  if m_RankList.Count <= 0 then Exit;
  GuildRank := m_RankList.Items[0];
  if GuildRank.MembersList.Count <= 0 then Exit;
  Result := GuildRank.MembersList.Strings[0];      }
end;

function TGuild.GetWar(nGuildIndex: Integer): Boolean;
begin
  Result := False;
  if nGuildIndex in [Low(m_WarArr)..High(m_WarArr)] then
    Result := m_WarArr[nGuildIndex]
  else
    MainOutMessage('GetWar Error by ' + IntToStr(nGuildIndex));
end;

function TGuild.LevelUp: Boolean;
var
  GuildExp: TGuildLevelNeedExp;
begin
  Result := False;
  if m_btGuildLevel < MAXGUILDLEVEL then begin
    GuildExp := g_Config.GuildLevelExp[m_btGuildLevel + 1];
    if (m_nMoneyCount >= GuildExp.nGold) and (m_nBuildPoint >= GuildExp.nBuildPoint) and
      (m_nFlourishingPoint >= GuildExp.nFlourishingPoint) and (m_nStabilityPoint >= GuildExp.nStabilityPoint) and
      (m_nActivityPoint >= GuildExp.nActivityPoint) then begin
      DecMoneyCount(GuildExp.nGold);
      DecBuildPoint(GuildExp.nBuildPoint);
      DecFlourishingPoint(GuildExp.nFlourishingPoint);
      DecStabilityPoint(GuildExp.nStabilityPoint);
      IncLevel(1);
      RefGuildMemberMaxCount;
      SendGuildMsg('行会成功升级到了' + IntToStr(m_btGuildLevel) + '级.');
      Result := True;
    end;
  end;

end;

function TGuild.GoldChange(nWork, nGold: Integer; PlayObject: TPlayObject): Integer;
var
  GuildRank: pTGuildRank;
  I, II: integer;
  SendObject: TPlayObject;
  SendList: TList;
  nPlayGold: Integer;
begin
  Result := 0;
  case nWork of
    0: begin  //存入
      if (nGold > 9999) and (PlayObject.m_nGold >= nGold) then begin
        IncMoneyCount(nGold);
        Dec(PlayObject.m_nGold, nGold);
        PlayObject.GoldChanged;
        SendGuildMsg('[' + PlayObject.m_sCharName + ']存入行会资金 ' + IntToStr(nGold) + '金币！');
      end else
        Result := 2;
    end;
    1: begin
      {if PlayObject.m_nGuildRankNo = 1 then begin
        if (nGold > 0) and (m_nMoneyCount >= nGold) then begin
          Dec(m_nMoneyCount, nGold);
          if IntegerChange(PlayObject.m_nGold, nGold, INT_ADD) then;
            PlayObject.GoldChanged;
        end else
          Result := 3;
      end else
        Result := 1; }
    end;
    2: begin
      if PlayObject.m_nGuildRankNo = 1 then begin
        if (nGold > 99999) and (m_nMoneyCount >= nGold) then begin
          SendList := TList.Create;
          for I := 0 to m_RankList.Count - 1 do begin
            GuildRank := m_RankList.Items[I];
            if GuildRank.MembersList = nil then Continue;
            for II := 0 to GuildRank.MembersList.Count - 1 do begin
              SendObject := pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject;
              if (SendObject <> nil) and (not SendObject.m_boGhost) then
                SendList.Add(SendObject);
            end;
          end;
          if SendList.Count > 0 then begin
            nPlayGold := nGold div SendList.Count;
            if nPlayGold > 0 then begin
              Dec(m_nMoneyCount, nPlayGold * SendList.Count);
              for i := 0 to SendList.Count - 1 do begin
                SendObject := TPlayObject(SendList[i]);
                if IntegerChange(SendObject.m_nGold, nPlayGold, INT_ADD) then
                  SendObject.GoldChanged;
                SendObject.SendMsg(SendObject, RM_GUILDMESSAGE, 0, g_Config.nGuildMsgFColor, g_Config.nGuildMsgBColor,
                    0, '恭喜在线成员每人获得' + IntToStr(nPlayGold) + '金币奖励！');
              end;
            end else
              Result := 3;
          end;
          SendList.Free;
        end else
          Result := 3;
      end else
        Result := 1;
    end;
  end;
end;

procedure TGuild.IncActivityPoint(nCount: Integer);
begin
  if (m_nActivityPoint + nCount) < m_nMaxActivityPoint then Inc(m_nActivityPoint, nCount)
  else m_nActivityPoint := m_nMaxActivityPoint;
//  IntegerChange(m_nActivityPoint, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.IncBuildPoint(nCount: Integer);
begin
  IntegerChange(m_nBuildPoint, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.IncFlourishingPoint(nCount: Integer);
begin
  IntegerChange(m_nFlourishingPoint, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.IncInfoIndex;
begin
  m_boChanged := True;
  if m_boInfoRead then begin
    m_boInfoRead := False;
    if m_UpInfoIndex >= High(Word) then m_UpInfoIndex := 1
    else Inc(m_UpInfoIndex);
  end;
end;

procedure TGuild.IncKillMobCount;
begin
  Inc(m_nKillMobCount);
  if m_nKillMobCount >= 100 then begin
    m_nKillMobCount := 0;
    IncFlourishingPoint(1);  //每消灭100个怪物，增加一点行会繁荣值
  end;
end;

procedure TGuild.IncLevel(nCount: Integer);
begin
  ByteChange(m_btGuildLevel, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.IncMemberIndex;
begin
  if m_UpMemberIndex >= High(Word) then m_UpMemberIndex := 1
  else Inc(m_UpMemberIndex);
end;

procedure TGuild.IncMoneyCount(nCount: Integer);
begin
  IntegerChange(m_nMoneyCount, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.IncNoticeIndex;
begin
  if m_UpNoticeIndex >= High(Word) then m_UpNoticeIndex := 1
  else Inc(m_UpNoticeIndex);
end;

procedure TGuild.IncSocietyIndex;
begin
  if m_UpSocietyIndex >= High(Word) then m_UpSocietyIndex := 1
  else Inc(m_UpSocietyIndex);
end;

procedure TGuild.IncStabilityPoint(nCount: Integer);
begin
  IntegerChange(m_nStabilityPoint, nCount, INT_ADD);
  IncInfoIndex;
end;

procedure TGuild.initialize;
var
  GuildWar: pTWarGuild;
  i: integer;
  Guild: TGuild;
begin
  SafeFillChar(m_WarArr[0], Length(m_WarArr), False);
  SafeFillChar(m_AllyArr[0], Length(m_AllyArr), False);
  for I := m_GuildWarList.Count - 1 downto 0 do begin
    Guild := g_GuildManager.FindGuild(m_GuildWarList[i]);
    if Guild <> nil then begin
      New(GuildWar);
      GuildWar.Guild := Guild;
      GuildWar.dwWarTick := GetTickCount;
      GuildWar.dwWarTime := Integer(m_GuildWarList.Objects[i]);
      m_GuildWarList.Objects[i] := TObject(GuildWar);
      SetWar(Guild.m_GuildIndex, True);
    end else
      m_GuildWarList.Delete(I);
  end;
  for I := m_GuildAllList.Count - 1 downto 0 do begin
    Guild := g_GuildManager.FindGuild(m_GuildAllList[i]);
    if Guild <> nil then begin
      m_GuildAllList.Objects[I] := Guild;
      SetAlly(Guild.m_GuildIndex, True);
    end else m_GuildAllList.Delete(I);
  end;
end;

function TGuild.IsAllyGuild(Guild: TGuild): Boolean;
begin
  Result := False;
  if Guild = nil then exit;
  Result := GetAlly(Guild.m_GuildIndex);
end;

function TGuild.IsFull: Boolean;
var
  nCount: Integer;
  GuildRank: pTGuildRank;
begin
  Result := False;
  nCount := 0;
  for GuildRank in m_RankList do begin
    Inc(nCount, GuildRank.MembersList.Count);
    if nCount >= m_btMaxMemberCount then begin
      Result := True;
      break;
    end;
  end;
end;

function TGuild.IsMember(sName: string): Boolean;
var
  GuildRank: pTGuildRank;
  sCharName: string;
begin
  Result := False;
  for GuildRank in m_RankList do begin
    for sCharName in GuildRank.MembersList do begin
      if CompareText(sCharName, sName) = 0 then begin
        Result := True;
        exit;
      end;
    end;
  end;
end;

function TGuild.IsNotWar(Guild: TGuild): Boolean;
begin
  Result := True;
  if Guild = nil then exit;
  Result := not GetWar(Guild.m_GuildIndex);
end;

function TGuild.IsWarGuild(Guild: TGuild): Boolean;
begin
  Result := False;
  if Guild = nil then exit;
  Result := GetWar(Guild.m_GuildIndex);
end;

function TGuild.LoadGuild: Boolean;
begin
  Result := LoadGuildFile(m_sGuildName + '.ini');
end;

procedure TGuild.SaveGuildFile(sFileName: string);
var
  SaveList: TStringList;
  I, n14, II: Integer;
  WarGuild: pTWarGuild;
  nTime: LongWord;
  GuildRank: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
begin
  SaveList := TStringList.Create;
  SaveList.Add('[' + g_Config.sGuildInfo + ']');
  SaveList.Add('CreateGuildName=' + m_sCreateGuildName);
  SaveList.Add('CreateGuildTime=' + DateTimeToStr(m_dwCreateGuildTime));
  SaveList.Add('CallGuildTime=' + DateTimeToStr(m_dwCallGuildTime));
  SaveList.Add('GuildLevel=' + IntToStr(m_btGuildLevel));
  //SaveList.Add('MaxMemberCount=' + IntToStr(m_btMaxMemberCount));
  SaveList.Add('MoneyCount=' + IntToStr(m_nMoneyCount));
  SaveList.Add('BuildPoint=' + IntToStr(m_nBuildPoint));
  SaveList.Add('ActivityPoint=' + IntToStr(m_nActivityPoint));
  SaveList.Add('StabilityPoint=' + IntToStr(m_nStabilityPoint));
  SaveList.Add('FlourishingPoint=' + IntToStr(m_nFlourishingPoint));
  SaveList.Add('');
  SaveList.Add('[' + g_Config.sGuildNotice + ']');
  for I := 0 to m_NoticeList.Count - 1 do
    SaveList.Add(IntToStr(I) + '=' + m_NoticeList[I]);
  SaveList.Add('');
  SaveList.Add('[' + g_Config.sGuildWar + ']');
  nTime := GetTickCount;
  for I := 0 to m_GuildWarList.Count - 1 do begin
    WarGuild := pTWarGuild(m_GuildWarList.Objects[I]);
    n14 := WarGuild.dwWarTime - (nTime - WarGuild.dwWarTick);
    if n14 <= 0 then Continue;
    SaveList.Add(m_GuildWarList.Strings[I] + '=' + IntToStr(n14));
  end;
  SaveList.Add('');
  SaveList.Add('[' + g_Config.sGuildAll + ']');
  for I := 0 to m_GuildAllList.Count - 1 do begin
    SaveList.Add(m_GuildAllList.Strings[I] + '=' + IntToStr(I));
  end;
  SaveList.Add('');
  SaveList.Add('[' + g_Config.sGuildMember + ']');
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList[I];
    if GuildRank.MembersList.Count > 0 then
      SaveList.Add(IntToStr(GuildRank.nRankNo) + '=' + GuildRank.sRankName);
  end;
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList[I];
    if GuildRank.MembersList.Count > 0 then begin
      SaveList.Add('');
      SaveList.Add('[' + g_Config.sGuildMember + '_' + IntToStr(GuildRank.nRankNo) + '_' + GuildRank.sRankName + ']');
      for II := 0 to GuildRank.MembersList.Count - 1 do begin
        GuildUserInfo := pTGuildUserInfo(GuildRank.MembersList.Objects[II]);
        SaveList.Add(GuildRank.MembersList[II] + '=' + DateTimeToStr(GuildUserInfo.LoginTime));
      end;
    end;
  end;
  try
    SaveList.SaveToFile(sFileName);
  except
    MainOutMessage('保存行会信息失败. ' + sFileName);
  end;
  SaveList.Free;
end;

function TGuild.LoadGuildFile(sGuildFileName: string): Boolean;
var
  sFileName, sSection, sValue, sMemberValue, s18, sGuildName, sTime: string;
  INI: TINIFile;
  SectionList, SectionValueList, MemberList: TStringList;
  nRank: Integer;
  sRankName: string[16];
  sUserName: string;
  GuildRank: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
begin
  Result := False;
  sFileName := g_Config.sGuildDir + sGuildFileName;
  if not FileExists(sFileName) then Exit;
  m_NoticeList.Clear;
  m_GuildWarList.Clear;
  m_GuildAllList.Clear;
  ClearRank(m_RankList);
  Ini := TINIFile.Create(sFileName);
  SectionList := TStringList.Create;
  SectionValueList := TStringList.Create;
  MemberList := TStringList.Create;
  Try
    Ini.ReadSections(SectionList);
    for sSection in SectionList do begin
      SectionValueList.Clear;
      if sSection = g_Config.sGuildInfo then begin
        m_sCreateGuildName := Ini.ReadString(sSection, 'CreateGuildName', m_sCreateGuildName);
        m_dwCreateGuildTime := Ini.ReadDateTime(sSection, 'CreateGuildTime', m_dwCreateGuildTime);
        m_dwCallGuildTime := Ini.ReadDateTime(sSection, 'CallGuildTime', m_dwCallGuildTime);
        m_btGuildLevel := Ini.ReadInteger(sSection, 'GuildLevel', m_btGuildLevel);
        //m_btMaxMemberCount := Ini.ReadInteger(sSection, 'MaxMemberCount', m_btMaxMemberCount);
        m_nMoneyCount := Ini.ReadInteger(sSection, 'MoneyCount', m_nMoneyCount);
        m_nBuildPoint := Ini.ReadInteger(sSection, 'BuildPoint', m_nBuildPoint);
        m_nActivityPoint := Ini.ReadInteger(sSection, 'ActivityPoint', m_nActivityPoint);
        m_nStabilityPoint := Ini.ReadInteger(sSection, 'StabilityPoint', m_nStabilityPoint);
        m_nFlourishingPoint := Ini.ReadInteger(sSection, 'FlourishingPoint', m_nFlourishingPoint);
        RefGuildMemberMaxCount;
      end else
      if sSection = g_Config.sGuildNotice then begin
        Ini.ReadSectionValues(sSection, SectionValueList);
        for sValue in  SectionValueList do begin
          m_NoticeList.Add(GetValidStrEx(sValue, s18, ['=']));
        end;
      end else
      if sSection = g_Config.sGuildWar then begin
        Ini.ReadSectionValues(sSection, SectionValueList);
        for sValue in  SectionValueList do begin
          sTime := GetValidStrEx(sValue, sGuildName, ['=']);
          if (sGuildName <> '') and (sTime <> '') then
            m_GuildWarList.AddObject(Trim(sGuildName), TObject(StrToIntDef(Trim(sTime), 0)));
        end;
      end else
      if sSection = g_Config.sGuildAll then begin
        Ini.ReadSectionValues(sSection, SectionValueList);
        for sValue in  SectionValueList do begin
          GetValidStrEx(sValue, sGuildName, ['=']);
          if (sGuildName <> '') then
            m_GuildAllList.Add(Trim(sGuildName));
        end;
      end else
      if sSection = g_Config.sGuildMember then begin
        Ini.ReadSectionValues(sSection, SectionValueList);
        for sValue in  SectionValueList do begin
          sRankName := GetValidStrEx(sValue, s18, ['=']);
          nRank := StrToIntDef(s18, -1);
          sRankName := Trim(sRankName);
          MemberList.Clear;
          if (nRank in [1..99]) and (sRankName <> '') then begin
            Ini.ReadSectionValues(sSection + '_' + IntToStr(nRank) + '_' + sRankName, MemberList);
            if MemberList.Count > 0 then begin
              New(GuildRank);
              GuildRank.nRankNo := nRank;
              GuildRank.sRankName := sRankName;
              GuildRank.MembersList := TStringList.Create;
              m_RankList.Add(GuildRank);
              for sMemberValue in MemberList do begin
                sTime := GetValidStrEx(sMemberValue, sUserName, ['=']);
                sUserName := Trim(sUserName);
                if (sUserName <> '') and (sTime <> '') then begin
                  if nRank = 1 then m_sChiefName := sUserName;
                  New(GuildUserInfo);
                  GuildUserInfo.MemberIndex := 0;
                  GuildUserInfo.PlayObject := nil;
                  GuildUserInfo.LoginTime := StrToDateTimeDef(sTime, 0);
                  GuildRank.MembersList.AddObject(sUserName, TObject(GuildUserInfo));
                end;
              end;
            end;
          end;
        end;
      end;
    end;
  Finally
    SectionList.Free;
    SectionValueList.Free;
    MemberList.Free;
    Ini.Free;
  End;
  Result := True;
end;

procedure TGuild.MemberGhost(PlayObject: TPlayObject);
var
  I, II: Integer;
  GuildRank: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
begin
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      GuildUserInfo := pTGuildUserInfo(GuildRank.MembersList.Objects[ii]);
      if GuildUserInfo.PlayObject = PlayObject then begin
        SendGuildSocket(SM_GUILDCHANGE, 0, 0, 0, 2, EncodeString(PlayObject.m_sCharName));
        GuildUserInfo.MemberIndex := 0;
        GuildUserInfo.PlayObject := nil;
        GuildUserInfo.LoginTime := Now;
        m_boChanged := True;
        Exit;
      end;
    end;
  end;
end;

procedure TGuild.MemberLogin(PlayObject: TPlayObject);
var
  I, II: Integer;
  GuildRank: pTGuildRank;
begin
  PlayObject.m_sGuildRankName := '';
  PlayObject.m_nGuildRankNo := 0;
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      if CompareText(GuildRank.MembersList.Strings[II], PlayObject.m_sCharName) = 0 then begin
        pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject := PlayObject;
        pTGuildUserInfo(GuildRank.MembersList.Objects[II]).MemberIndex := 0;
        PlayObject.m_nGuildRankNo := GuildRank.nRankNo;
        PlayObject.m_sGuildRankName := GuildRank.sRankName;
        SendGuildSocket(SM_GUILDCHANGE, 0, 0, 0, 1, EncodeString(PlayObject.m_sCharName));
        //PlayObject.SendDefMsg(PlayObject, SM_CHANGEGUILDNAME, 0, 0, 0, 0, m_sGuildName + '/' + GuildRank.sRankName);
        m_dwCallGuildTime := Now;
        Exit;
      end;
    end;
  end;
end;

procedure TGuild.RefGuildMemberMaxCount;
begin
  if m_btGuildLevel > 0 then begin
    if m_btGuildLevel <= 10 then m_btKillMonExpRate := m_btGuildLevel
    else m_btKillMonExpRate := Round((1 + (m_btGuildLevel - 10)) * (m_btGuildLevel - 10) / 2) + 10;
    m_btKillMonAttackRate := m_btKillMonExpRate;
  end else begin
    m_btKillMonExpRate := 0;
    m_btKillMonAttackRate := 0;
  end;
  m_btMaxMemberCount := g_Config.nDefGuildMemberLimit + m_btGuildLevel * g_Config.nGuildMemberLevelInc;
  m_nMaxActivityPoint := MAXACTIVITYPOINT + m_btGuildLevel * 100;
end;

procedure TGuild.RefMemberName;
var
  I, II: Integer;
  GuildRank: pTGuildRank;
  BaseObject: TBaseObject;
begin
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      BaseObject := TBaseObject(pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject);
      if BaseObject <> nil then
        BaseObject.RefShowName;
    end;
  end;
end;

procedure TGuild.SendGuildMsg(sMsg: string);
var
  I: Integer;
  II: Integer;
  GuildRank: pTGuildRank;
  BaseObject: TBaseObject;
  nCheckCode: Integer;
begin
  nCheckCode := 0;
  try
    if g_Config.boShowPreFixMsg then
      sMsg := g_Config.sGuildMsgPreFix + sMsg;
    nCheckCode := 1;
    for I := 0 to m_RankList.Count - 1 do begin
      GuildRank := m_RankList.Items[I];
      nCheckCode := 2;
      if GuildRank.MembersList = nil then Continue;
      for II := 0 to GuildRank.MembersList.Count - 1 do begin
        nCheckCode := 3;
        BaseObject := TBaseObject(pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject);
        if BaseObject = nil then Continue;
        if BaseObject.m_btRaceServer <> RC_PLAYOBJECT then Continue;
        nCheckCode := 4;
        if BaseObject.m_boBanGuildChat and (not TPlayObject(BaseObject).m_boSafeOffLine) then begin
          nCheckCode := 5;
          BaseObject.SendMsg(BaseObject, RM_GUILDMESSAGE, 0, g_Config.nGuildMsgFColor, g_Config.nGuildMsgBColor, 0, sMsg);
          nCheckCode := 6;
        end;
      end;
    end;
  except
    on e: Exception do begin
      MainOutMessage('[Exceptiion] TGuild.SendGuildMsg CheckCode: ' +
        IntToStr(nCheckCode) + ' GuildName = ' + m_sGuildName + ' Msg = ' + sMsg);
      MainOutMessage(e.Message);
    end;
  end;
end;

procedure TGuild.SendGuildSocket(wIdent: Word; nRecog: Integer; nParam, nTag, nSeries: Word; sMsg: string);
var
  I: Integer;
  II: Integer;
  GuildRank: pTGuildRank;
  BaseObject: TPlayObject;
  nCheckCode: Integer;
begin
  nCheckCode := 0;
  try
    if g_Config.boShowPreFixMsg then
      sMsg := g_Config.sGuildMsgPreFix + sMsg;
    nCheckCode := 1;
    for I := 0 to m_RankList.Count - 1 do begin
      GuildRank := m_RankList.Items[I];
      nCheckCode := 2;
      if GuildRank.MembersList = nil then Continue;
      for II := 0 to GuildRank.MembersList.Count - 1 do begin
        nCheckCode := 3;
        BaseObject := TPlayObject(pTGuildUserInfo(GuildRank.MembersList.Objects[II]).PlayObject);
        if BaseObject = nil then Continue;
        nCheckCode := 4;
        if pTGuildUserInfo(GuildRank.MembersList.Objects[II]).MemberIndex = m_UpMemberIndex then begin
          nCheckCode := 5;
          BaseObject.SendDefSocket(BaseObject, wIdent, nRecog, nParam, nTag, nSeries, sMsg);
          nCheckCode := 6;
        end;
      end;
    end;
  except
    on e: Exception do begin
      MainOutMessage('[Exceptiion] TGuild.SendGuildSocket CheckCode: ' +
        IntToStr(nCheckCode) + ' GuildName = ' + m_sGuildName + ' Msg = ' + sMsg);
      MainOutMessage(e.Message);
    end;
  end;
end;

procedure TGuild.SetActivityPoint(const Value: Integer);
begin
  IntegerChange(m_nActivityPoint, Value, INT_SET);
  IncInfoIndex;
end;

procedure TGuild.SetAlly(nGuildIndex: Integer; boTrue: Boolean);
begin
  if nGuildIndex in [Low(m_AllyArr)..High(m_AllyArr)] then
    m_AllyArr[nGuildIndex] := boTrue
  else
    MainOutMessage('SetAlly Error by ' + IntToStr(nGuildIndex));
end;

procedure TGuild.SetBuildPoint(const Value: Integer);
begin
  IntegerChange(m_nBuildPoint, Value, INT_SET);
  IncInfoIndex;
end;

procedure TGuild.SetFlourishingPoint(const Value: Integer);
begin
  IntegerChange(m_nFlourishingPoint, Value, INT_SET);
  IncInfoIndex;
end;

function TGuild.SetGuildInfo(PlayObject: TPlayObject): Boolean;
var
  GuildRank: pTGuildRank;
  GuildUserInfo: pTGuildUserInfo;
begin
  RefGuildMemberMaxCount;
  if m_RankList.Count = 0 then begin
    New(GuildRank);
    GuildRank.nRankNo := 1;
    GuildRank.sRankName := g_Config.sGuildChief;
    GuildRank.MembersList := TStringList.Create;
    m_sChiefName := PlayObject.m_sCharName;
    m_sCreateGuildName := PlayObject.m_sCharName;
    m_dwCreateGuildTime := Now;
    m_dwCallGuildTime := Now;
    m_nActivityPoint := m_nMaxActivityPoint;
    New(GuildUserInfo);
    GuildUserInfo.MemberIndex := 0;
    GuildUserInfo.PlayObject := PlayObject;
    GuildUserInfo.LoginTime := Now;
    GuildRank.MembersList.AddObject(PlayObject.m_sCharName, TObject(GuildUserInfo));
    m_RankList.Add(GuildRank);
    UpdateGuildFile;
  end;
  Result := True;
end;

procedure TGuild.SetGuildLevel(const Value: Byte);
begin
  ByteChange(m_btGuildLevel, Value, INT_SET);
  IncInfoIndex;
end;

procedure TGuild.SetLevel(nLevel: Integer);
begin
  if nLevel < 0 then nLevel := 0;
  if nLevel > MAXGUILDLEVEL then nLevel := MAXGUILDLEVEL;
  m_btGuildLevel := nLevel;
  IncInfoIndex;
  RefGuildMemberMaxCount;
  SendGuildMsg('行会成功升级到了' + IntToStr(m_btGuildLevel) + '级.');
end;

procedure TGuild.SetMoneyCount(const Value: Integer);
begin
  IntegerChange(m_nMoneyCount, Value, INT_SET);
  IncInfoIndex;
end;

procedure TGuild.SetStabilityPoint(const Value: Integer);
begin
  IntegerChange(m_nStabilityPoint, Value, INT_SET);
  IncInfoIndex;
end;

procedure TGuild.SetWar(nGuildIndex: Integer; boTrue: Boolean);
begin
  if nGuildIndex in [Low(m_WarArr)..High(m_WarArr)] then
    m_WarArr[nGuildIndex] := boTrue
  else
    MainOutMessage('SetWar Error by ' + IntToStr(nGuildIndex));
end;

procedure TGuild.StartTeamFight;
begin
  m_nContestPoint := 0;
  m_boTeamFight := True;
  m_TeamFightDeadList.Clear;
end;

procedure TGuild.TeamFightWhoDead(sName: string);
var
  I, n10: Integer;
begin
  if not m_boTeamFight then Exit;
  for I := 0 to m_TeamFightDeadList.Count - 1 do begin
    if m_TeamFightDeadList.Strings[I] = sName then begin
      n10 := Integer(m_TeamFightDeadList.Objects[I]);
      m_TeamFightDeadList.Objects[I] := TObject(MakeLong(LoWord(n10) + 1, HiWord(n10)));
    end;
  end;
end;

procedure TGuild.TeamFightWhoWinPoint(sName: string; nPoint: Integer);
var
  I, n14: Integer;
begin
  if not m_boTeamFight then Exit;
  Inc(m_nContestPoint, nPoint);
  for I := 0 to m_TeamFightDeadList.Count - 1 do begin
    if m_TeamFightDeadList.Strings[I] = sName then begin
      n14 := Integer(m_TeamFightDeadList.Objects[I]);
      m_TeamFightDeadList.Objects[I] := TObject(MakeLong(LoWord(n14), HiWord(n14) + nPoint));
    end;
  end;
end;

procedure TGuild.UpdateGuildFile;
begin
  m_boChanged := False;
  m_dwSaveTick := GetTickCount();
  SaveGuildFile(g_Config.sGuildDir + m_sGuildName + '.ini');
end;

function TGuild.UpdateRank(sRankData: string): Integer;
  procedure ClearRankList(var RankList: TList; boDispose: Boolean);
  var
    I, ii: Integer;
    GuildRank: pTGuildRank;
    GuildUserInfo: pTGuildUserInfo;
  begin
    for I := 0 to RankList.Count - 1 do begin
      GuildRank := RankList.Items[I];
      if boDispose then begin
        for ii := 0 to GuildRank.MembersList.Count - 1 do begin
          GuildUserInfo := pTGuildUserInfo(GuildRank.MembersList.Objects[ii]);
          if GuildUserInfo <> nil then
            Dispose(GuildUserInfo);
        end;
      end;
      FreeAndNil(GuildRank.MembersList);
      Dispose(GuildRank);
    end;
    FreeAndNil(RankList);
  end;
var
  I: Integer;
  II: Integer;
  III: Integer;
  GuildRankList: TList;
  GuildRank: pTGuildRank;
  NewGuildRank: pTGuildRank;
  sRankInfo: string;
  sRankNo: string;
  sRankName: string;
  sMemberName: string;
  n28: Integer;
//  n3C: Integer;
//  n40: Integer;
  nAdminCount: Integer;
  nAdmin2Count: Integer;
  boCheckChange: Boolean;
  nMemberCount: Integer;
  GuildUserInfo: pTGuildUserInfo;
  nRankNo: Integer;
  RankArr: array[1..99] of Boolean;
begin
//  Result := -1;
  nMemberCount := 0;
  nAdminCount := 0;
  nAdmin2Count := 0;
  nRankNo := 0;
  GuildRankList := TList.Create;
  GuildRank := nil;
  SafeFillChar(RankArr, SizeOf(RankArr), False);
  while (True) do begin
    if sRankData = '' then break;
    sRankData := GetValidStr3(sRankData, sRankInfo, [#$0D]);
    sRankInfo := Trim(sRankInfo);
    if sRankInfo = '' then Continue;
    if sRankInfo[1] = '#' then begin //取得职称的名称
      sRankInfo := Copy(sRankInfo, 2, Length(sRankInfo) - 1);
      sRankInfo := GetValidStr3(sRankInfo, sRankNo, [' ', '<']);
      sRankInfo := GetValidStr3(sRankInfo, sRankName, ['<', '>']);
      if Length(sRankName) > 16 then //Jacky 限制职称的长度
        sRankName := Copy(sRankName, 1, 16);
      sRankName := Trim(sRankName);
      if GuildRank <> nil then begin
        GuildRankList.Add(GuildRank);
      end;
      sRankName := Trim(sRankName);
      nRankNo := StrToIntDef(sRankNo, -1);
      if sRankName = '' then begin
        Result := -3; //RankName 不能为空
        ClearRankList(GuildRankList, False);
        Exit;
      end else
      if not CheckCorpsChr(sRankName) then begin
        Result := -8; //RankName 中包含特殊字符
        ClearRankList(GuildRankList, False);
        Exit;
      end else
      if not (nRankNo in [1..99]) then begin
        Result := -11; //RankName 检查职位号是否非法
        ClearRankList(GuildRankList, False);
        Exit;
      end else
      if RankArr[nRankNo] then begin
        Result := -7; //RankName 检查职位号是否非法
        ClearRankList(GuildRankList, False);
        Exit;
      end;
      RankArr[nRankNo] := True;
      New(GuildRank);
      GuildRank.nRankNo := nRankNo;
      GuildRank.sRankName := sRankName;
      GuildRank.MembersList := TStringList.Create;
      Continue;
    end;

    if GuildRank = nil then Continue;

    while (True) do begin //将成员名称加入职称表里
      if sRankInfo = '' then break;
      sRankInfo := GetValidStr3(sRankInfo, sMemberName, [' ', ',']);
      if sMemberName <> '' then begin
        if nRankNo = 1 then Inc(nAdminCount)
        else if (nRankNo in [2, 3]) then Inc(nAdmin2Count);
        Inc(nMemberCount);
        GuildRank.MembersList.Add(sMemberName);
      end;
    end;
  end;
  if GuildRank <> nil then begin
    GuildRankList.Add(GuildRank);
  end;
  if nAdminCount < 1 then begin
    Result := -5; //RankName 检查掌门数量
    ClearRankList(GuildRankList, False);
    Exit;
  end;
  if nAdminCount > 1 then begin
    Result := -4; //RankName 检查掌门数量
    ClearRankList(GuildRankList, False);
    Exit;
  end;
  if nAdmin2Count > 2 then begin
    Result := -9; //RankName 检查副掌门数量
    ClearRankList(GuildRankList, False);
    Exit;
  end;

  //校验成员列表是否有改变，如果未修改则退出
  //boCheckChange := False;
  if m_RankList.Count = GuildRankList.Count then begin
    boCheckChange := True;
    for I := 0 to m_RankList.Count - 1 do begin
      GuildRank := m_RankList.Items[I];
      NewGuildRank := GuildRankList.Items[I];
      if (GuildRank.nRankNo = NewGuildRank.nRankNo) and
        (GuildRank.sRankName = NewGuildRank.sRankName) and
        (GuildRank.MembersList.Count = NewGuildRank.MembersList.Count) then begin
        for II := 0 to GuildRank.MembersList.Count - 1 do begin
          if GuildRank.MembersList.Strings[II] <> NewGuildRank.MembersList.Strings[II] then begin
            boCheckChange := False; //如果有改变则将其置为FALSE
            break;
          end;
        end;
        if not boCheckChange then 
          break;
      end
      else begin
        boCheckChange := False;
        break;
      end;
    end;
    if boCheckChange then begin
      Result := -1;       //未发生改变,直接退出
      ClearRankList(GuildRankList, False);
      Exit;
    end;
  end;

  //检查新表人物数量是否与旧表相同
  n28 := 0;
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    Inc(n28, GuildRank.MembersList.Count);
  end;
  if n28 <> nMemberCount then begin
    Result := -6;
    ClearRankList(GuildRankList, False);
    Exit;
  end;

  //检查职位号是否重复及非法
  {for I := 0 to GuildRankList.Count - 1 do begin
    n28 := pTNewGuildRank(GuildRankList.Items[I]).nRankNo;
    for III := I + 1 to GuildRankList.Count - 1 do begin
      if (pTNewGuildRank(GuildRankList.Items[III]).nRankNo = n28) or (n28 < 1) or (n28 > 99) then begin
        Result := -7;
        ClearRankList(GuildRankList, False);
        Exit;
      end;
    end;
  end;   }

  //检查行会掌门人是否在线
  {n28 := 0;
  for I := 0 to GuildRankList.Count - 1 do begin
    GuildRank := pTNewGuildRank(GuildRankList.Items[I]);
    if GuildRank.nRankNo = 1 then begin
      Inc(n28);

      break;
    end;
  end;
  if UserEngine.GetPlayObject(GuildRank.MembersList[I]) = nil then begin
    Result := -5;
    ClearRankList(GuildRankList, False);
    Exit;
  end;   }

  (*n3C := 0;
  n28 := 0;
  n40 := 0;
  for I := 0 to GuildRankList.Count - 1 do begin
    GuildRank := pTNewGuildRank(GuildRankList.Items[I]);
    if GuildRank.nRankNo = 1 then begin
      Inc(n40, GuildRank.MembersList.Count);
      if n40 > 1 then begin   //检查掌门数量
        Result := -4;
        ClearRankList(GuildRankList, False);
        Exit;
      end;
    end else
    if (GuildRank.nRankNo = 2) or (GuildRank.nRankNo = 3) then begin  //检查副掌门数量
      Inc(n3C, GuildRank.MembersList.Count);
      if n3C > 2 then begin
        Result := -9;
        ClearRankList(GuildRankList, False);
        Exit;
      end;
    end else
    if (GuildRank.nRankNo in [4..13]) then begin   //检查长老数量
      Inc(n28);
      if n28 > 10 then begin
        Result := -10;
        ClearRankList(GuildRankList, False);
        Exit;
      end;
    end;
  end;
  if n40 <> 1 then begin
    Result := -5;
    ClearRankList(GuildRankList, False);
    Exit;
  end;      *)

  for III := 0 to GuildRankList.Count - 1 do begin
    NewGuildRank := GuildRankList.Items[III];
    for n28 := 0 to NewGuildRank.MembersList.Count - 1 do begin
      sMemberName := NewGuildRank.MembersList[n28];
      boCheckChange := False;
      for I := 0 to m_RankList.Count - 1 do begin
        GuildRank := m_RankList.Items[I];
        for II := 0 to GuildRank.MembersList.Count - 1 do begin
          if GuildRank.MembersList[ii] = sMemberName then begin
            Dec(nMemberCount);
            NewGuildRank.MembersList.Objects[n28] := GuildRank.MembersList.Objects[ii];
            boCheckChange := True;
            break;
          end;
        end;
        if boCheckChange then break;
      end;
      if not boCheckChange then begin
        Result := -6;         //新表中删除了成员
        ClearRankList(GuildRankList, False);
        Exit;
      end;
    end;
  end;
  if nMemberCount <> 0 then begin
    Result := -6;         //新表中删除了成员
    ClearRankList(GuildRankList, False);
    Exit;
  end;

  ClearRankList(m_RankList, False);
  m_RankList := GuildRankList;
  //更新在线人物职位表
  for I := 0 to m_RankList.Count - 1 do begin
    GuildRank := m_RankList.Items[I];
    for II := 0 to GuildRank.MembersList.Count - 1 do begin
      GuildUserInfo := pTGuildUserInfo(GuildRank.MembersList.Objects[ii]);
      if GuildRank.nRankNo = 1 then m_sChiefName := GuildRank.MembersList[ii];
      if GuildUserInfo.PlayObject <> nil then begin
        if GuildUserInfo.PlayObject.RefRankInfo(GuildRank.nRankNo, GuildRank.sRankName) then;
          GuildUserInfo.PlayObject.RefShowName();
      end;
    end;
  end;
  UpdateGuildFile();
  IncMemberIndex;
  Result := 0;
end;

{ TGuildManager }

function TGuildManager.AddGuild(sGuildName: string; PlayObject: TPlayObject): Boolean;
var
  Guild: TGuild;
begin
  Result := False;
  if FindGuild(sGuildName) = nil then begin
    Guild := TGuild.Create(sGuildName);
    Guild.SetGuildInfo(PlayObject);
    SetLength(Guild.m_WarArr, _MAX(m_GuildList.Count * 2, 500));
    SetLength(Guild.m_AllyArr, _MAX(m_GuildList.Count * 2, 500));
    Guild.initialize;
    m_GuildList.Add(Guild);
    SaveGuildList();
    Result := True;
  end;
end;

procedure TGuildManager.ClearGuildAttackCastle;
var
  I: Integer;
begin
  for I := 0 to m_GuildList.Count - 1 do begin
    TGuild(m_GuildList.Items[I]).m_AttackCastle := nil;
  end;
end;

constructor TGuildManager.Create;
begin
  inherited;
  m_GuildList := TList.Create;
end;

function TGuildManager.DelGuild(sGuildName: string): Boolean;
var
  I: Integer;
  Guild: TGuild;
begin
  Result := False;
  for I := 0 to m_GuildList.Count - 1 do begin
    Guild := TGuild(m_GuildList.Items[I]);
    if CompareText(Guild.m_sGuildName, sGuildName) = 0 then begin
      Guild.DeleteGuild;
      m_GuildList.Delete(I);
      SaveGuildList();
      Result := True;
      break;
    end;
  end;
end;

destructor TGuildManager.Destroy;
begin
  m_GuildList.Free;
  inherited;
end;

function TGuildManager.FindGuild(sGuildName: string): TGuild;
var
  I: Integer;
begin
  Result := nil;
  for I := 0 to m_GuildList.Count - 1 do begin
    if CompareText(TGuild(m_GuildList.Items[I]).m_sGuildName, sGuildName) = 0 then begin
      Result := TGuild(m_GuildList.Items[I]);
      break;
    end;
  end;
end;

procedure TGuildManager.LoadGuildInfo;
var
  LoadList: TStringList;
  sGuildName: string[14];
  I: Integer;
  Guild: TGuild;
begin
  if FileExists(g_Config.sGuildFile) then begin
    LoadList := TStringList.Create;
    LoadList.LoadFromFile(g_Config.sGuildFile);
    for I := 0 to LoadList.Count - 1 do begin
      sGuildName := LoadList.Strings[I];
      if Trim(sGuildName) <> '' then begin
        Guild := TGuild.Create(Trim(sGuildName));
        m_GuildList.Add(Guild);
      end;
    end;
    LoadList.Free;
    for I := m_GuildList.Count - 1 downto 0 do begin
      Guild := m_GuildList.Items[I];
      if not Guild.LoadGuild() then begin
        MainOutMessage(Guild.m_sGuildName + ' 读取出错.');
        Guild.Free;
        m_GuildList.Delete(I);
      end else
      if Guild.m_nActivityPoint <= 0 then begin
        Guild.DeleteGuild;
        MainOutMessage(Guild.m_sGuildName + ' 已强制解散.');
        Guild.Free;
        m_GuildList.Delete(I);
      end else
        Guild.m_GuildIndex := GetGuildIndex;
    end;
    SaveGuildList();
    for I := m_GuildList.Count - 1 downto 0 do begin
      Guild := m_GuildList.Items[I];
      SetLength(Guild.m_WarArr, _MAX(m_GuildList.Count * 2, 500));
      SetLength(Guild.m_AllyArr, _MAX(m_GuildList.Count * 2, 500));
      Guild.initialize;
    end;
    MainOutMessage('已读取 ' + IntToStr(m_GuildList.Count) + '个行会信息...');
  end
  else begin
    MainOutMessage('行会信息文件未找到.');
  end;
end;

function TGuildManager.MemberOfGuild(sCharName, sGuildName: string): TGuild;
var
  I: Integer;
  Guild: TGuild;
begin
  Result := nil;
  if sGuildName = '' then exit;
  for I := 0 to m_GuildList.Count - 1 do begin
    Guild := TGuild(m_GuildList.Items[I]);
    if CompareText(Guild.m_sGuildName, sGuildName) = 0 then begin
      if Guild.IsMember(sCharName) then Result := Guild;
      break;
    end;
  end;
end;

procedure TGuildManager.Run;
var
  I: Integer;
  II: Integer;
  Guild: TGuild;
  WarGuild: pTWarGuild;
  boChange: Boolean;   
  dwTick: LongWord;
begin
  dwTick := GetTickCount;
  for I := m_GuildList.Count - 1 downto 0 do begin
    Guild := TGuild(m_GuildList.Items[I]);
    boChange := False;
    //检测行会战争是否到时间了
    for II := Guild.m_GuildWarList.Count - 1 downto 0 do begin
      WarGuild := pTWarGuild(Guild.m_GuildWarList.Objects[II]);
      if (GetTickCount - WarGuild.dwWarTick) > WarGuild.dwWarTime then begin
        Guild.SetWar(TGuild(WarGuild.Guild).m_GuildIndex, False);
        Guild.DelWarGuild(TGuild(WarGuild.Guild));
        Guild.m_GuildWarList.Delete(II);
        Dispose(WarGuild);
        Guild.m_boChanged := True;
        boChange := True;
      end;
    end;
    if (dwTick > Guild.m_dwRunTick) then begin
      Guild.m_dwRunTick := dwTick + 60 * 60 * 1000;
      Guild.DecActivityPoint(3);
      if Guild.m_nActivityPoint <= 0 then begin
        Guild.DeleteGuild;
        m_GuildList.Delete(I);
        SaveGuildList;
        Continue;
      end;
    end;
    if boChange then
      Guild.IncSocietyIndex;
    Guild.CheckSaveGuildFile;
  end;
end;

procedure TGuildManager.SaveGuildList;
var
  I: Integer;
  SaveList: TStringList;
begin
  SaveList := TStringList.Create;
  for I := 0 to m_GuildList.Count - 1 do begin
    SaveList.Add(TGuild(m_GuildList.Items[I]).m_sGuildName);
  end;
  try
    SaveList.SaveToFile(g_Config.sGuildFile);
  except
    MainOutMessage('行会信息保存失败.');
  end;
  SaveList.Free;
end;

// === 新增: 行会科技树方法实现 ===

procedure TGuild.InitGuildTechs;
var
  Tech: pTGuildTech;
begin
  if m_GuildTechList <> nil then
  begin
    while m_GuildTechList.Count > 0 do
    begin
      Dispose(pTGuildTech(m_GuildTechList[0]));
      m_GuildTechList.Delete(0);
    end;
  end
  else
    m_GuildTechList := TList.Create;

  // 战斗科技
  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 1;
  Tech.sTechName := '攻击强化';
  Tech.TechType := gttCombat;
  Tech.nMaxLevel := 10;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 100000;
  Tech.nUpgradeBuildPoint := 100;
  Tech.nUpgradeStability := 50;
  Tech.nUpgradeTime := 3600;
  Tech.sDescription := '提升行会成员攻击力';
  Tech.sEffect := '每级提升1%攻击力';
  Tech.EffectValue[0] := 1; Tech.EffectValue[1] := 2; Tech.EffectValue[2] := 3;
  Tech.EffectValue[3] := 5; Tech.EffectValue[4] := 7;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 2;
  Tech.sTechName := '防御强化';
  Tech.TechType := gttDefense;
  Tech.nMaxLevel := 10;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 100000;
  Tech.nUpgradeBuildPoint := 100;
  Tech.nUpgradeStability := 50;
  Tech.nUpgradeTime := 3600;
  Tech.sDescription := '提升行会成员防御力';
  Tech.sEffect := '每级提升1%防御力';
  Tech.EffectValue[0] := 1; Tech.EffectValue[1] := 2; Tech.EffectValue[2] := 3;
  Tech.EffectValue[3] := 5; Tech.EffectValue[4] := 7;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 3;
  Tech.sTechName := '生命强化';
  Tech.TechType := gttCombat;
  Tech.nMaxLevel := 10;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 150000;
  Tech.nUpgradeBuildPoint := 150;
  Tech.nUpgradeStability := 80;
  Tech.nUpgradeTime := 7200;
  Tech.sDescription := '提升行会成员生命值';
  Tech.sEffect := '每级提升50点生命上限';
  Tech.EffectValue[0] := 50; Tech.EffectValue[1] := 100; Tech.EffectValue[2] := 150;
  Tech.EffectValue[3] := 200; Tech.EffectValue[4] := 250;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 4;
  Tech.sTechName := '经验加成';
  Tech.TechType := gttSupport;
  Tech.nMaxLevel := 5;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 200000;
  Tech.nUpgradeBuildPoint := 200;
  Tech.nUpgradeStability := 100;
  Tech.nUpgradeTime := 10800;
  Tech.sDescription := '提升行会成员打怪经验';
  Tech.sEffect := '每级提升2%经验获取';
  Tech.EffectValue[0] := 2; Tech.EffectValue[1] := 4; Tech.EffectValue[2] := 6;
  Tech.EffectValue[3] := 8; Tech.EffectValue[4] := 10;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 5;
  Tech.sTechName := '掉率加成';
  Tech.TechType := gttSupport;
  Tech.nMaxLevel := 5;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 300000;
  Tech.nUpgradeBuildPoint := 300;
  Tech.nUpgradeStability := 150;
  Tech.nUpgradeTime := 14400;
  Tech.sDescription := '提升行会成员物品掉落率';
  Tech.sEffect := '每级提升1%物品掉落率';
  Tech.EffectValue[0] := 1; Tech.EffectValue[1] := 2; Tech.EffectValue[2] := 3;
  Tech.EffectValue[3] := 4; Tech.EffectValue[4] := 5;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 6;
  Tech.sTechName := '行会仓库扩容';
  Tech.TechType := gttProduction;
  Tech.nMaxLevel := 5;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 100000;
  Tech.nUpgradeBuildPoint := 100;
  Tech.nUpgradeStability := 50;
  Tech.nUpgradeTime := 3600;
  Tech.sDescription := '扩大行会仓库容量';
  Tech.sEffect := '每级增加10个仓库槽位';
  Tech.EffectValue[0] := 10; Tech.EffectValue[1] := 20; Tech.EffectValue[2] := 30;
  Tech.EffectValue[3] := 40; Tech.EffectValue[4] := 50;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 7;
  Tech.sTechName := 'PK减伤';
  Tech.TechType := gttDefense;
  Tech.nMaxLevel := 5;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 250000;
  Tech.nUpgradeBuildPoint := 250;
  Tech.nUpgradeStability := 120;
  Tech.nUpgradeTime := 10800;
  Tech.sDescription := '降低行会成员受到的PK伤害';
  Tech.sEffect := '每级降低2%PK伤害';
  Tech.EffectValue[0] := 2; Tech.EffectValue[1] := 4; Tech.EffectValue[2] := 6;
  Tech.EffectValue[3] := 8; Tech.EffectValue[4] := 10;
  m_GuildTechList.Add(Tech);

  New(Tech);
  FillChar(Tech^, SizeOf(TGuildTech), #0);
  Tech.nTechID := 8;
  Tech.sTechName := '技能冷却缩减';
  Tech.TechType := gttSpecial;
  Tech.nMaxLevel := 5;
  Tech.nCurrentLevel := 0;
  Tech.nUpgradeCost := 500000;
  Tech.nUpgradeBuildPoint := 500;
  Tech.nUpgradeStability := 200;
  Tech.nUpgradeTime := 21600;
  Tech.sDescription := '减少行会成员技能冷却时间';
  Tech.sEffect := '每级降低3%技能冷却时间';
  Tech.EffectValue[0] := 3; Tech.EffectValue[1] := 6; Tech.EffectValue[2] := 9;
  Tech.EffectValue[3] := 12; Tech.EffectValue[4] := 15;
  m_GuildTechList.Add(Tech);

  m_boTechChanged := True;
end;

function TGuild.UpgradeTech(nTechID: Word): Boolean;
var
  i: Integer;
  Tech: pTGuildTech;
begin
  Result := False;
  if m_GuildTechList = nil then
    Exit;

  for i := 0 to m_GuildTechList.Count - 1 do
  begin
    Tech := pTGuildTech(m_GuildTechList[i]);
    if Tech.nTechID = nTechID then
    begin
      if Tech.nCurrentLevel >= Tech.nMaxLevel then
        Exit;
      if Tech.boUpgrading then
        Exit;
      if m_nMoneyCount < Tech.nUpgradeCost then
        Exit;
      if m_nBuildPoint < Tech.nUpgradeBuildPoint then
        Exit;
      if m_nStabilityPoint < Tech.nUpgradeStability then
        Exit;

      // 消耗资源
      DecMoneyCount(Tech.nUpgradeCost);
      DecBuildPoint(Tech.nUpgradeBuildPoint);
      DecStabilityPoint(Tech.nUpgradeStability);

      // 开始升级
      Tech.boUpgrading := True;
      Tech.dwUpgradeStartTime := GetTickCount;
      m_boTechChanged := True;
      Inc(m_nTechUpgradeCount);
      Result := True;
      Exit;
    end;
  end;
end;

function TGuild.GetTechLevel(nTechID: Word): Integer;
var
  i: Integer;
  Tech: pTGuildTech;
begin
  Result := 0;
  if m_GuildTechList = nil then
    Exit;

  for i := 0 to m_GuildTechList.Count - 1 do
  begin
    Tech := pTGuildTech(m_GuildTechList[i]);
    if Tech.nTechID = nTechID then
    begin
      Result := Tech.nCurrentLevel;
      Exit;
    end;
  end;
end;

function TGuild.GetTechEffect(nTechID: Word): Integer;
var
  i: Integer;
  Tech: pTGuildTech;
  nLevel: Integer;
begin
  Result := 0;
  if m_GuildTechList = nil then
    Exit;

  for i := 0 to m_GuildTechList.Count - 1 do
  begin
    Tech := pTGuildTech(m_GuildTechList[i]);
    if Tech.nTechID = nTechID then
    begin
      nLevel := Tech.nCurrentLevel;
      if (nLevel > 0) and (nLevel <= Tech.nMaxLevel) then
      begin
        if nLevel <= 5 then
          Result := Tech.EffectValue[nLevel - 1]
        else
          Result := Tech.EffectValue[4] + (nLevel - 5) * Tech.EffectValue[4];
      end;
      Exit;
    end;
  end;
end;

function TGuild.GetTechList: TList;
begin
  Result := m_GuildTechList;
end;

procedure TGuild.CalcTechModifiers(var AddAbility: TAddAbility);
var
  nAtkLevel, nDefLevel, nHPLevel, nExpLevel, nDropLevel, nPKDefLevel, nCDLevel: Integer;
begin
  if m_GuildTechList = nil then
    Exit;

  nAtkLevel := GetTechLevel(1);
  nDefLevel := GetTechLevel(2);
  nHPLevel := GetTechLevel(3);
  nExpLevel := GetTechLevel(4);
  nDropLevel := GetTechLevel(5);
  nPKDefLevel := GetTechLevel(7);
  nCDLevel := GetTechLevel(8);

  // 攻击强化
  if nAtkLevel > 0 then
  begin
    Inc(AddAbility.DC, (nAtkLevel * 1) * 100);
    Inc(AddAbility.MC, (nAtkLevel * 1) * 100);
    Inc(AddAbility.SC, (nAtkLevel * 1) * 100);
  end;

  // 防御强化
  if nDefLevel > 0 then
  begin
    Inc(AddAbility.AC, (nDefLevel * 1) * 100);
    Inc(AddAbility.MAC, (nDefLevel * 1) * 100);
  end;

  // 生命强化
  if nHPLevel > 0 then
    Inc(AddAbility.HP, nHPLevel * 50);

  // 经验加成
  if nExpLevel > 0 then
    Inc(AddAbility.btExpRate, nExpLevel * 2);

  // 掉率加成
  if nDropLevel > 0 then
    Inc(AddAbility.btDropRate, nDropLevel * 1);

  // PK减伤
  if nPKDefLevel > 0 then
    Inc(AddAbility.btPKDefense, nPKDefLevel * 2);

  // 技能冷却缩减
  if nCDLevel > 0 then
    Inc(AddAbility.btSkillCDReduction, nCDLevel * 3);
end;

procedure TGuild.SendTechInfo(PlayObject: TPlayObject);
var
  i: Integer;
  Tech: pTGuildTech;
  sMsg: string;
begin
  if (m_GuildTechList = nil) or (PlayObject = nil) then
    Exit;

  for i := 0 to m_GuildTechList.Count - 1 do
  begin
    Tech := pTGuildTech(m_GuildTechList[i]);
    sMsg := EncodeString(IntToStr(Tech.nTechID) + '/' + Tech.sTechName + '/' +
      IntToStr(Tech.nCurrentLevel) + '/' + IntToStr(Tech.nMaxLevel) + '/' +
      IntToStr(Ord(Tech.boUpgrading)));
    PlayObject.SendDefMsg(PlayObject, SM_GUILDTECHINFO, Tech.nTechID,
      Tech.nCurrentLevel, Tech.nMaxLevel, Ord(Tech.boUpgrading), sMsg);
  end;
end;

procedure TGuild.CheckTechUpgrade;
var
  i: Integer;
  Tech: pTGuildTech;
begin
  if m_GuildTechList = nil then
    Exit;

  for i := 0 to m_GuildTechList.Count - 1 do
  begin
    Tech := pTGuildTech(m_GuildTechList[i]);
    if Tech.boUpgrading then
    begin
      if (GetTickCount - Tech.dwUpgradeStartTime) >= (Tech.nUpgradeTime * 1000) then
      begin
        Tech.boUpgrading := False;
        Inc(Tech.nCurrentLevel);
        m_boTechChanged := True;
        SendGuildMsg(Format('行会科技 [%s] 升级完成! 当前等级: %d/%d',
          [Tech.sTechName, Tech.nCurrentLevel, Tech.nMaxLevel]));
      end;
    end;
  end;
end;

// === 新增: 行会仓库方法实现 ===

procedure TGuild.InitStorage;
begin
  if m_GuildStorageList = nil then
    m_GuildStorageList := TList.Create;
  m_nStorageMaxSlots := 50; // 基础50槽位
  m_nStorageGold := 0;
  m_boStoragePublic := False;
  m_boStorageChanged := False;
end;

function TGuild.AddStorageItem(UserItem: pTUserItem): Boolean;
var
  pItem: pTUserItem;
begin
  Result := False;
  if m_GuildStorageList = nil then
    InitStorage;
  if m_GuildStorageList.Count >= m_nStorageMaxSlots then
    Exit;

  New(pItem);
  pItem^ := UserItem^;
  m_GuildStorageList.Add(pItem);
  m_boStorageChanged := True;
  Result := True;
end;

function TGuild.RemoveStorageItem(nSlot: Integer): pTUserItem;
begin
  Result := nil;
  if m_GuildStorageList = nil then
    Exit;
  if (nSlot < 0) or (nSlot >= m_GuildStorageList.Count) then
    Exit;

  Result := pTUserItem(m_GuildStorageList[nSlot]);
  m_GuildStorageList.Delete(nSlot);
  m_boStorageChanged := True;
end;

function TGuild.GetStorageItem(nSlot: Integer): pTUserItem;
begin
  Result := nil;
  if m_GuildStorageList = nil then
    Exit;
  if (nSlot < 0) or (nSlot >= m_GuildStorageList.Count) then
    Exit;
  Result := pTUserItem(m_GuildStorageList[nSlot]);
end;

function TGuild.GetStorageItemCount: Integer;
begin
  if m_GuildStorageList = nil then
    Result := 0
  else
    Result := m_GuildStorageList.Count;
end;

function TGuild.GetStorageUsedSlots: Integer;
begin
  Result := GetStorageItemCount;
end;

procedure TGuild.SaveStorage;
var
  SaveList: TStringList;
  i: Integer;
  Item: pTUserItem;
  sFileName: string;
  sLine: string;
begin
  if (m_GuildStorageList = nil) or (not m_boStorageChanged) then
    Exit;

  SaveList := TStringList.Create;
  try
    SaveList.Add(IntToStr(m_nStorageMaxSlots));
    SaveList.Add(IntToStr(m_nStorageGold));
    SaveList.Add(IntToStr(Ord(m_boStoragePublic)));
    for i := 0 to m_GuildStorageList.Count - 1 do
    begin
      Item := pTUserItem(m_GuildStorageList[i]);
      sLine := Format('%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d',
        [Item.wIndex, Item.Dura, Item.DuraMax, Item.Value[0], Item.Value[1],
         Item.Value[2], Item.Value[3], Item.Value[4], Item.Value[5], Item.Value[6],
         Item.Value[7]]);
      SaveList.Add(sLine);
    end;
    sFileName := g_Config.sEnvirDir + 'GuildStorage\' + m_sGuildName + '.txt';
    ForceDirectories(ExtractFilePath(sFileName));
    SaveList.SaveToFile(sFileName);
    m_boStorageChanged := False;
  except
    MainOutMessage('行会仓库保存失败: ' + m_sGuildName);
  end;
  SaveList.Free;
end;

procedure TGuild.LoadStorage;
var
  LoadList: TStringList;
  i: Integer;
  Item: pTUserItem;
  sFileName: string;
  sLine: string;
  Params: TStringList;
begin
  if m_GuildStorageList <> nil then
  begin
    while m_GuildStorageList.Count > 0 do
    begin
      Dispose(pTUserItem(m_GuildStorageList[0]));
      m_GuildStorageList.Delete(0);
    end;
  end
  else
    m_GuildStorageList := TList.Create;

  sFileName := g_Config.sEnvirDir + 'GuildStorage\' + m_sGuildName + '.txt';
  if not FileExists(sFileName) then
  begin
    InitStorage;
    Exit;
  end;

  LoadList := TStringList.Create;
  Params := TStringList.Create;
  try
    LoadList.LoadFromFile(sFileName);
    if LoadList.Count >= 3 then
    begin
      m_nStorageMaxSlots := StrToIntDef(LoadList[0], 50);
      m_nStorageGold := StrToIntDef(LoadList[1], 0);
      m_boStoragePublic := StrToIntDef(LoadList[2], 0) = 1;

      for i := 3 to LoadList.Count - 1 do
      begin
        sLine := LoadList[i];
        Params.CommaText := sLine;
        if Params.Count >= 11 then
        begin
          New(Item);
          FillChar(Item^, SizeOf(TUserItem), #0);
          Item.wIndex := StrToIntDef(Params[0], 0);
          Item.Dura := StrToIntDef(Params[1], 0);
          Item.DuraMax := StrToIntDef(Params[2], 0);
          Item.Value[0] := StrToIntDef(Params[3], 0);
          Item.Value[1] := StrToIntDef(Params[4], 0);
          Item.Value[2] := StrToIntDef(Params[5], 0);
          Item.Value[3] := StrToIntDef(Params[6], 0);
          Item.Value[4] := StrToIntDef(Params[7], 0);
          Item.Value[5] := StrToIntDef(Params[8], 0);
          Item.Value[6] := StrToIntDef(Params[9], 0);
          Item.Value[7] := StrToIntDef(Params[10], 0);
          m_GuildStorageList.Add(Item);
        end;
      end;
    end
    else
      InitStorage;
  except
    MainOutMessage('行会仓库加载失败: ' + m_sGuildName);
    InitStorage;
  end;
  Params.Free;
  LoadList.Free;
  m_boStorageChanged := False;
end;

procedure TGuild.SendStorageInfo(PlayObject: TPlayObject);
var
  i: Integer;
  Item: pTUserItem;
  sMsg: string;
begin
  if (m_GuildStorageList = nil) or (PlayObject = nil) then
    Exit;

  sMsg := EncodeString(Format('行会仓库 [%s] (%d/%d) 金币: %d',
    [m_sGuildName, m_GuildStorageList.Count, m_nStorageMaxSlots, m_nStorageGold]));
  PlayObject.SendDefMsg(PlayObject, SM_GUILDSTORAGE, m_GuildStorageList.Count,
    m_nStorageMaxSlots, m_nStorageGold, 0, sMsg);

  for i := 0 to m_GuildStorageList.Count - 1 do
  begin
    Item := pTUserItem(m_GuildStorageList[i]);
    sMsg := EncodeString(Format('%d/%d/%d/%d/%d', [i, Item.wIndex, Item.Dura, Item.DuraMax, Item.Value[0]]));
    PlayObject.SendDefMsg(PlayObject, SM_GUILDSTORAGE, i, Item.wIndex, Item.Dura, Item.DuraMax, sMsg);
  end;
end;

function TGuild.CanAccessStorage(PlayObject: TPlayObject): Boolean;
begin
  Result := False;
  if PlayObject = nil then
    Exit;
  Result := IsMember(PlayObject.m_sCharName);
end;

function TGuild.DonateStorageGold(PlayObject: TPlayObject; nAmount: Integer): Boolean;
begin
  Result := False;
  if PlayObject = nil then
    Exit;
  if nAmount <= 0 then
    Exit;
  if PlayObject.m_nGold < nAmount then
    Exit;

  Dec(PlayObject.m_nGold, nAmount);
  Inc(m_nStorageGold, nAmount);
  m_boStorageChanged := True;
  PlayObject.SendMsg(PlayObject, RM_MYSTATUS, PlayObject.m_nGold, 0, 0, 0, '');
  SendGuildMsg(Format('[%s] 向行会仓库捐献了 %d 金币!', [PlayObject.m_sCharName, nAmount]));
  Result := True;
end;

end.






