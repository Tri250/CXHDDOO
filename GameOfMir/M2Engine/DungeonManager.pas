unit DungeonManager;

interface

uses
  Windows, SysUtils, Classes, IniFiles, Grobal2, M2Share, Envir, ObjBase, ObjPlay;

type
  TMapMonGen = packed record
    sMapName: string[16];
    sMonName: string[14];
    nX: Integer;
    nY: Integer;
    nRange: Integer;
    nCount: Integer;
    nWave: Integer;
    boBoss: Boolean;
  end;
  pTMapMonGen = ^TMapMonGen;

  TMonsterEntry = packed record
    sMonName: string[14];
    nX: Integer;
    nY: Integer;
    nWave: Integer;
    boBoss: Boolean;
    boSpawned: Boolean;
    nLiveCount: Integer;
    BaseObject: TBaseObject;
  end;
  pTMonsterEntry = ^TMonsterEntry;

  TRewardEntry = packed record
    sItemName: string[20];
    nItemCount: Integer;
    nRate: Integer;
    boBind: Boolean;
  end;
  pTRewardEntry = ^TRewardEntry;

  TPlayerEntry = packed record
    PlayObject: TPlayObject;
    sOriginalMap: string[16];
    nOriginalX: Integer;
    nOriginalY: Integer;
    boEntered: Boolean;
    dwEnterTick: LongWord;
  end;
  pTPlayerEntry = ^TPlayerEntry;

  TDungeonManager = class
  private
    m_DungeonTemplates: TList;     // pTDungeonTemplate list
    m_DungeonInstances: TList;     // pTDungeonInstance list
    m_boInitialized: Boolean;
    m_nInstanceCounter: LongWord;  // Instance ID counter
    function GetTemplate(nDungeonID: Word): pTDungeonTemplate;
    function GetInstance(nInstanceID: LongWord): pTDungeonInstance;
    procedure CleanupInstance(nInstanceID: LongWord);
    function FindPlayerInInstance(Instance: pTDungeonInstance; PlayObject: TPlayObject): pTPlayerEntry;
    function FindInstanceByPlayer(PlayObject: TPlayObject): pTDungeonInstance;
    procedure GiveReward(PlayObject: TPlayObject; nDungeonID: Word; nMultiplier: Integer);
    procedure NotifyInstancePlayers(Instance: pTDungeonInstance; sMsg: string);
    procedure SpawnMonsterWave(Instance: pTDungeonInstance; nWave: Integer);
    procedure SpawnMonster(Instance: pTDungeonInstance; Entry: pTMonsterEntry);
    function GetDungeonTypeName(DungeonType: TDungeonType): string;
    function GetDungeonStateName(nState: Integer): string;
    function GetDungeonRewardGold(nDungeonID: Word; nMultiplier: Integer): Integer;
    function GetDungeonRewardExp(nDungeonID: Word; nMultiplier: Integer): Integer;
    procedure AddTemplate(nDungeonID: Word; sName: string; sMapName: string;
      nMinLevel, nMaxLevel, nMaxPlayers, nMaxTime, nDailyLimit: Integer;
      nEnterGold, nEnterGameGold: Integer; DungeonType: TDungeonType;
      sEnterMap: string; nEnterX, nEnterY: Integer; sScript: string);
    procedure AddMonGen(Template: pTDungeonTemplate; sMonName: string;
      nX, nY, nRange, nCount, nWave: Integer; boBoss: Boolean);
    procedure AddReward(Template: pTDungeonTemplate; sItemName: string;
      nItemCount, nRate: Integer; boBind: Boolean);
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;
    procedure Run;

    function CreateInstance(PlayObject: TPlayObject; nDungeonID: Word): pTDungeonInstance;
    function EnterDungeon(PlayObject: TPlayObject; nInstanceID: LongWord): Boolean;
    function ExitDungeon(PlayObject: TPlayObject): Boolean;
    function GetPlayerInstance(PlayObject: TPlayObject): pTDungeonInstance;
    function CanEnterDungeon(PlayObject: TPlayObject; nDungeonID: Word): Boolean;
    function GetDailyRemaining(PlayObject: TPlayObject; nDungeonID: Word): Integer;
    procedure CompleteDungeon(PlayObject: TPlayObject; nInstanceID: LongWord);
    procedure FailDungeon(PlayObject: TPlayObject; nInstanceID: LongWord);
    procedure SpawnDungeonMonsters(Instance: pTDungeonInstance);
    procedure SendDungeonInfo(Instance: pTDungeonInstance);
    procedure SendDungeonCountdown(Instance: pTDungeonInstance);
    procedure OnMonsterKilled(Instance: pTDungeonInstance; sMonsterName: string; boBoss: Boolean);
    procedure LoadConfig(sConfigFile: string);
    procedure CheckDungeonTime(Instance: pTDungeonInstance);
  end;

var
  g_DungeonManager: TDungeonManager;

implementation

{ TDungeonManager }

constructor TDungeonManager.Create();
begin
  m_DungeonTemplates := TList.Create;
  m_DungeonInstances := TList.Create;
  m_boInitialized := False;
  m_nInstanceCounter := 0;
end;

destructor TDungeonManager.Destroy;
var
  I: Integer;
  pTemplate: pTDungeonTemplate;
  pInstance: pTDungeonInstance;
  pMonGen: pTMapMonGen;
  pReward: pTRewardEntry;
  pPlayer: pTPlayerEntry;
  pMonster: pTMonsterEntry;
begin
  for I := m_DungeonTemplates.Count - 1 downto 0 do begin
    pTemplate := pTDungeonTemplate(m_DungeonTemplates.Items[I]);
    if pTemplate <> nil then begin
      if pTemplate.MonGenList <> nil then begin
        while pTemplate.MonGenList.Count > 0 do begin
          pMonGen := pTMapMonGen(pTemplate.MonGenList.Items[0]);
          pTemplate.MonGenList.Delete(0);
          if pMonGen <> nil then Dispose(pMonGen);
        end;
        pTemplate.MonGenList.Free;
      end;
      if pTemplate.BossList <> nil then begin
        while pTemplate.BossList.Count > 0 do begin
          pMonGen := pTMapMonGen(pTemplate.BossList.Items[0]);
          pTemplate.BossList.Delete(0);
          if pMonGen <> nil then Dispose(pMonGen);
        end;
        pTemplate.BossList.Free;
      end;
      if pTemplate.RewardList <> nil then begin
        while pTemplate.RewardList.Count > 0 do begin
          pReward := pTRewardEntry(pTemplate.RewardList.Items[0]);
          pTemplate.RewardList.Delete(0);
          if pReward <> nil then Dispose(pReward);
        end;
        pTemplate.RewardList.Free;
      end;
      Dispose(pTemplate);
    end;
  end;
  m_DungeonTemplates.Free;

  for I := m_DungeonInstances.Count - 1 downto 0 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if pInstance <> nil then begin
      if pInstance.PlayerList <> nil then begin
        while pInstance.PlayerList.Count > 0 do begin
          pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[0]);
          pInstance.PlayerList.Delete(0);
          if pPlayer <> nil then Dispose(pPlayer);
        end;
        pInstance.PlayerList.Free;
      end;
      if pInstance.MonsterList <> nil then begin
        while pInstance.MonsterList.Count > 0 do begin
          pMonster := pTMonsterEntry(pInstance.MonsterList.Items[0]);
          pInstance.MonsterList.Delete(0);
          if pMonster <> nil then Dispose(pMonster);
        end;
        pInstance.MonsterList.Free;
      end;
      Dispose(pInstance);
    end;
  end;
  m_DungeonInstances.Free;

  inherited;
end;

function TDungeonManager.GetTemplate(nDungeonID: Word): pTDungeonTemplate;
var
  I: Integer;
  pTemplate: pTDungeonTemplate;
begin
  Result := nil;
  for I := 0 to m_DungeonTemplates.Count - 1 do begin
    pTemplate := pTDungeonTemplate(m_DungeonTemplates.Items[I]);
    if (pTemplate <> nil) and (pTemplate.nDungeonID = nDungeonID) then begin
      Result := pTemplate;
      Exit;
    end;
  end;
end;

function TDungeonManager.GetInstance(nInstanceID: LongWord): pTDungeonInstance;
var
  I: Integer;
  pInstance: pTDungeonInstance;
begin
  Result := nil;
  for I := 0 to m_DungeonInstances.Count - 1 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if (pInstance <> nil) and (pInstance.nInstanceID = nInstanceID) then begin
      Result := pInstance;
      Exit;
    end;
  end;
end;

function TDungeonManager.FindPlayerInInstance(Instance: pTDungeonInstance;
  PlayObject: TPlayObject): pTPlayerEntry;
var
  I: Integer;
  pPlayer: pTPlayerEntry;
begin
  Result := nil;
  if (Instance = nil) or (Instance.PlayerList = nil) then Exit;
  for I := 0 to Instance.PlayerList.Count - 1 do begin
    pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
    if (pPlayer <> nil) and (pPlayer.PlayObject = PlayObject) then begin
      Result := pPlayer;
      Exit;
    end;
  end;
end;

function TDungeonManager.FindInstanceByPlayer(PlayObject: TPlayObject): pTDungeonInstance;
var
  I: Integer;
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
begin
  Result := nil;
  for I := 0 to m_DungeonInstances.Count - 1 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if pInstance = nil then Continue;
    pPlayer := FindPlayerInInstance(pInstance, PlayObject);
    if pPlayer <> nil then begin
      Result := pInstance;
      Exit;
    end;
  end;
end;

function TDungeonManager.GetDungeonTypeName(DungeonType: TDungeonType): string;
begin
  case DungeonType of
    dtNormal: Result := '普通';
    dtElite: Result := '精英';
    dtHero: Result := '英雄';
    dtRaid: Result := '团队';
  else
    Result := '未知';
  end;
end;

function TDungeonManager.GetDungeonStateName(nState: Integer): string;
begin
  case nState of
    0: Result := '等待中';
    1: Result := '进行中';
    2: Result := '已完成';
    3: Result := '已失败';
  else
    Result := '未知';
  end;
end;

function TDungeonManager.GetDungeonRewardGold(nDungeonID: Word; nMultiplier: Integer): Integer;
var
  pTemplate: pTDungeonTemplate;
begin
  Result := 0;
  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;
  case pTemplate.DungeonType of
    dtNormal: Result := 5000 * nMultiplier;
    dtElite: Result := 15000 * nMultiplier;
    dtHero: Result := 50000 * nMultiplier;
    dtRaid: Result := 200000 * nMultiplier;
  end;
end;

function TDungeonManager.GetDungeonRewardExp(nDungeonID: Word; nMultiplier: Integer): Integer;
var
  pTemplate: pTDungeonTemplate;
begin
  Result := 0;
  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;
  case pTemplate.DungeonType of
    dtNormal: Result := 10000 * nMultiplier;
    dtElite: Result := 50000 * nMultiplier;
    dtHero: Result := 200000 * nMultiplier;
    dtRaid: Result := 1000000 * nMultiplier;
  end;
end;

procedure TDungeonManager.AddTemplate(nDungeonID: Word; sName: string;
  sMapName: string; nMinLevel, nMaxLevel, nMaxPlayers, nMaxTime,
  nDailyLimit: Integer; nEnterGold, nEnterGameGold: Integer;
  DungeonType: TDungeonType; sEnterMap: string; nEnterX, nEnterY: Integer;
  sScript: string);
var
  pTemplate: pTDungeonTemplate;
begin
  New(pTemplate);
  pTemplate.nDungeonID := nDungeonID;
  pTemplate.sDungeonName := sName;
  pTemplate.sMapName := sMapName;
  pTemplate.nMinLevel := nMinLevel;
  pTemplate.nMaxLevel := nMaxLevel;
  pTemplate.nMaxPlayers := nMaxPlayers;
  pTemplate.nMaxTime := nMaxTime;
  pTemplate.nDailyLimit := nDailyLimit;
  pTemplate.nEnterGold := nEnterGold;
  pTemplate.nEnterGameGold := nEnterGameGold;
  pTemplate.DungeonType := DungeonType;
  pTemplate.sEnterMap := sEnterMap;
  pTemplate.nEnterX := nEnterX;
  pTemplate.nEnterY := nEnterY;
  pTemplate.MonGenList := TList.Create;
  pTemplate.BossList := TList.Create;
  pTemplate.RewardList := TList.Create;
  pTemplate.sScript := sScript;
  m_DungeonTemplates.Add(pTemplate);
end;

procedure TDungeonManager.AddMonGen(Template: pTDungeonTemplate;
  sMonName: string; nX, nY, nRange, nCount, nWave: Integer; boBoss: Boolean);
var
  pMonGen: pTMapMonGen;
begin
  if Template = nil then Exit;
  New(pMonGen);
  pMonGen.sMapName := Template.sMapName;
  pMonGen.sMonName := sMonName;
  pMonGen.nX := nX;
  pMonGen.nY := nY;
  pMonGen.nRange := nRange;
  pMonGen.nCount := nCount;
  pMonGen.nWave := nWave;
  pMonGen.boBoss := boBoss;
  if boBoss then
    Template.BossList.Add(pMonGen)
  else
    Template.MonGenList.Add(pMonGen);
end;

procedure TDungeonManager.AddReward(Template: pTDungeonTemplate;
  sItemName: string; nItemCount, nRate: Integer; boBind: Boolean);
var
  pReward: pTRewardEntry;
begin
  if Template = nil then Exit;
  New(pReward);
  pReward.sItemName := sItemName;
  pReward.nItemCount := nItemCount;
  pReward.nRate := nRate;
  pReward.boBind := boBind;
  Template.RewardList.Add(pReward);
end;

procedure TDungeonManager.Initialize;
var
  pTemplate: pTDungeonTemplate;
begin
  if m_boInitialized then Exit;

  // 1. 普通副本 - 僵尸洞
  AddTemplate(1, '僵尸洞穴', 'D001', 20, 200, 5, 1800, 5,
    10000, 0, dtNormal, 'D001', 50, 50, '');
  pTemplate := GetTemplate(1);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '僵尸', 50, 50, 20, 10, 1, False);
    AddMonGen(pTemplate, '僵尸', 50, 50, 20, 10, 2, False);
    AddMonGen(pTemplate, '僵尸', 50, 50, 20, 10, 3, False);
    AddMonGen(pTemplate, '尸王', 50, 50, 5, 1, 3, True);
    AddReward(pTemplate, '金创药(小量)', 5, 100, False);
    AddReward(pTemplate, '魔法药(小量)', 5, 100, False);
  end;

  // 2. 精英副本 - 沃玛神殿
  AddTemplate(2, '沃玛神殿', 'D021', 30, 200, 5, 2400, 3,
    30000, 0, dtElite, 'D021', 30, 40, '');
  pTemplate := GetTemplate(2);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '沃玛战士', 30, 40, 25, 8, 1, False);
    AddMonGen(pTemplate, '沃玛战士', 30, 40, 25, 8, 2, False);
    AddMonGen(pTemplate, '沃玛勇士', 30, 40, 25, 6, 3, False);
    AddMonGen(pTemplate, '火焰沃玛', 30, 40, 25, 4, 4, False);
    AddMonGen(pTemplate, '沃玛教主', 30, 40, 5, 1, 4, True);
    AddReward(pTemplate, '沃玛号角', 1, 30, True);
    AddReward(pTemplate, '龙之戒指', 1, 15, True);
    AddReward(pTemplate, '铂金戒指', 1, 15, True);
  end;

  // 3. 英雄副本 - 祖玛神殿
  AddTemplate(3, '祖玛神殿', 'D10161', 40, 200, 5, 3600, 2,
    50000, 0, dtHero, 'D10161', 30, 30, '');
  pTemplate := GetTemplate(3);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '祖玛弓箭手', 30, 30, 30, 6, 1, False);
    AddMonGen(pTemplate, '祖玛雕像', 30, 30, 30, 6, 2, False);
    AddMonGen(pTemplate, '祖玛卫士', 30, 30, 30, 5, 3, False);
    AddMonGen(pTemplate, '祖玛教主', 30, 30, 5, 1, 4, True);
    AddMonGen(pTemplate, '祖玛教主', 30, 30, 5, 1, 5, True);
    AddReward(pTemplate, '黑铁头盔', 1, 20, True);
    AddReward(pTemplate, '绿色项链', 1, 15, True);
    AddReward(pTemplate, '骑士手镯', 1, 15, True);
    AddReward(pTemplate, '力量戒指', 1, 15, True);
    AddReward(pTemplate, '裁决之杖', 1, 10, True);
  end;

  // 4. 团队副本 - 赤月峡谷
  AddTemplate(4, '赤月峡谷', 'D1004', 50, 200, 10, 4800, 1,
    100000, 0, dtRaid, 'D1004', 150, 150, '');
  pTemplate := GetTemplate(4);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '月魔蜘蛛', 150, 150, 40, 8, 1, False);
    AddMonGen(pTemplate, '黑锷蜘蛛', 150, 150, 40, 8, 2, False);
    AddMonGen(pTemplate, '幻影蜘蛛', 150, 150, 40, 6, 3, False);
    AddMonGen(pTemplate, '赤月恶魔', 150, 150, 10, 1, 4, True);
    AddMonGen(pTemplate, '双头血魔', 150, 150, 10, 1, 5, True);
    AddMonGen(pTemplate, '双头金刚', 150, 150, 10, 1, 5, True);
    AddReward(pTemplate, '圣战头盔', 1, 20, True);
    AddReward(pTemplate, '圣战项链', 1, 15, True);
    AddReward(pTemplate, '圣战手镯', 1, 15, True);
    AddReward(pTemplate, '圣战戒指', 1, 15, True);
    AddReward(pTemplate, '屠龙', 1, 5, True);
    AddReward(pTemplate, '嗜魂法杖', 1, 5, True);
  end;

  // 5. 普通副本 - 石墓阵
  AddTemplate(5, '石墓阵', 'D71601', 25, 200, 5, 1500, 10,
    5000, 0, dtNormal, 'D71601', 50, 50, '');
  pTemplate := GetTemplate(5);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '红野猪', 50, 50, 20, 8, 1, False);
    AddMonGen(pTemplate, '黑野猪', 50, 50, 20, 8, 2, False);
    AddMonGen(pTemplate, '蝎蛇', 50, 50, 20, 6, 2, False);
    AddMonGen(pTemplate, '白野猪', 50, 50, 10, 1, 3, True);
    AddReward(pTemplate, '金创药(中量)', 5, 100, False);
    AddReward(pTemplate, '魔法药(中量)', 5, 100, False);
  end;

  // 6. 精英副本 - 牛魔寺庙
  AddTemplate(6, '牛魔寺庙', 'D2079', 35, 200, 5, 3000, 3,
    35000, 0, dtElite, 'D2079', 40, 40, '');
  pTemplate := GetTemplate(6);
  if pTemplate <> nil then begin
    AddMonGen(pTemplate, '牛头魔', 40, 40, 30, 8, 1, False);
    AddMonGen(pTemplate, '牛魔斗士', 40, 40, 30, 6, 2, False);
    AddMonGen(pTemplate, '牛魔将军', 40, 40, 30, 4, 3, False);
    AddMonGen(pTemplate, '牛魔王', 40, 40, 10, 1, 4, True);
    AddReward(pTemplate, '泰坦戒指', 1, 15, True);
    AddReward(pTemplate, '三眼手镯', 1, 15, True);
    AddReward(pTemplate, '灵魂项链', 1, 15, True);
  end;

  m_boInitialized := True;
end;

procedure TDungeonManager.Run;
var
  I: Integer;
  pInstance: pTDungeonInstance;
  dwNow: LongWord;
  pTemplate: pTDungeonTemplate;
  J: Integer;
  pMonster: pTMonsterEntry;
  nRemaining: Integer;
  boAllBossDead: Boolean;
  boAllMonsterDead: Boolean;
  pPlayer: pTPlayerEntry;
  nRemainSec: Integer;
begin
  if not m_boInitialized then Exit;

  dwNow := GetTickCount;
  for I := m_DungeonInstances.Count - 1 downto 0 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if pInstance = nil then Continue;

    // 只处理进行中的副本
    if pInstance.nState <> 1 then Continue;

    pTemplate := GetTemplate(pInstance.nTemplateID);
    if pTemplate = nil then Continue;

    // 检查超时
    CheckDungeonTime(pInstance);

    // 如果已超时，标记失败
    if pInstance.nState = 3 then Continue;

    // 检查是否所有玩家都离开了
    if (pInstance.PlayerList <> nil) and (pInstance.PlayerList.Count = 0) then begin
      pInstance.nState := 3;
      CleanupInstance(pInstance.nInstanceID);
      Continue;
    end;

    // 检查怪物存活情况
    boAllBossDead := True;
    boAllMonsterDead := True;
    nRemaining := 0;
    if pInstance.MonsterList <> nil then begin
      for J := 0 to pInstance.MonsterList.Count - 1 do begin
        pMonster := pTMonsterEntry(pInstance.MonsterList.Items[J]);
        if pMonster = nil then Continue;
        if (pMonster.BaseObject <> nil) and (not pMonster.BaseObject.m_boDeath) then begin
          boAllMonsterDead := False;
          Inc(nRemaining);
          if pMonster.boBoss then
            boAllBossDead := False;
        end;
      end;
    end;

    // 如果所有怪物都死了，检查是否还有下一波
    if boAllMonsterDead and (pInstance.nCurrentWave < pInstance.nMaxWave) then begin
      Inc(pInstance.nCurrentWave);
      SpawnMonsterWave(pInstance, pInstance.nCurrentWave);
      if pInstance.PlayerList <> nil then begin
        for J := 0 to pInstance.PlayerList.Count - 1 do begin
          pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[J]);
          if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
            pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
              '副本第' + IntToStr(pInstance.nCurrentWave) + '波怪物已刷新！');
          end;
        end;
      end;
      SendDungeonInfo(pInstance);
    end;

    // 检查胜利条件：所有波次完成且所有怪物死亡
    if boAllMonsterDead and (pInstance.nCurrentWave >= pInstance.nMaxWave) then begin
      pInstance.nState := 2;
      pInstance.nProgress := 100;
      if pInstance.PlayerList <> nil then begin
        for J := 0 to pInstance.PlayerList.Count - 1 do begin
          pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[J]);
          if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
            pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
              '副本通关！恭喜完成！');
            GiveReward(pPlayer.PlayObject, pInstance.nTemplateID, pInstance.nRewardMultiplier);
          end;
        end;
      end;
      SendDungeonInfo(pInstance);
    end;

    // 每30秒发送倒计时信息
    if pInstance.dwEndTime > dwNow then begin
      nRemainSec := (pInstance.dwEndTime - dwNow) div 1000;
      if (nRemainSec mod 30 = 0) or (nRemainSec <= 10) then
        SendDungeonCountdown(pInstance);
    end;
  end;
end;

procedure TDungeonManager.CleanupInstance(nInstanceID: LongWord);
var
  I: Integer;
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  pMonster: pTMonsterEntry;
begin
  for I := m_DungeonInstances.Count - 1 downto 0 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if (pInstance <> nil) and (pInstance.nInstanceID = nInstanceID) then begin
      // 清理玩家
      if pInstance.PlayerList <> nil then begin
        while pInstance.PlayerList.Count > 0 do begin
          pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[0]);
          pInstance.PlayerList.Delete(0);
          if pPlayer <> nil then Dispose(pPlayer);
        end;
        pInstance.PlayerList.Free;
        pInstance.PlayerList := nil;
      end;
      // 清理怪物
      if pInstance.MonsterList <> nil then begin
        while pInstance.MonsterList.Count > 0 do begin
          pMonster := pTMonsterEntry(pInstance.MonsterList.Items[0]);
          pInstance.MonsterList.Delete(0);
          if pMonster <> nil then Dispose(pMonster);
        end;
        pInstance.MonsterList.Free;
        pInstance.MonsterList := nil;
      end;
      m_DungeonInstances.Delete(I);
      Dispose(pInstance);
      Break;
    end;
  end;
end;

function TDungeonManager.CreateInstance(PlayObject: TPlayObject;
  nDungeonID: Word): pTDungeonInstance;
var
  pTemplate: pTDungeonTemplate;
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  pMonGen: pTMapMonGen;
  pMonster: pTMonsterEntry;
  I: Integer;
  nMaxWave: Integer;
begin
  Result := nil;
  if PlayObject = nil then Exit;

  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;

  Inc(m_nInstanceCounter);
  if m_nInstanceCounter = 0 then
    Inc(m_nInstanceCounter);

  New(pInstance);
  pInstance.nInstanceID := m_nInstanceCounter;
  pInstance.nTemplateID := nDungeonID;
  pInstance.sMapName := pTemplate.sMapName;
  pInstance.nCurrX := pTemplate.nEnterX;
  pInstance.nCurrY := pTemplate.nEnterY;
  pInstance.dwCreateTime := GetTickCount;
  pInstance.dwEndTime := GetTickCount + LongWord(pTemplate.nMaxTime * 1000);
  pInstance.PlayerList := TList.Create;
  pInstance.MonsterList := TList.Create;
  pInstance.nState := 0;        // 等待
  pInstance.nProgress := 0;
  pInstance.nCurrentWave := 0;
  pInstance.nKillCount := 0;
  pInstance.nBossKillCount := 0;
  pInstance.nDeathCount := 0;
  pInstance.nRewardMultiplier := 1;

  // 计算最大波次
  nMaxWave := 0;
  if pTemplate.MonGenList <> nil then begin
    for I := 0 to pTemplate.MonGenList.Count - 1 do begin
      pMonGen := pTMapMonGen(pTemplate.MonGenList.Items[I]);
      if (pMonGen <> nil) and (pMonGen.nWave > nMaxWave) then
        nMaxWave := pMonGen.nWave;
    end;
  end;
  if pTemplate.BossList <> nil then begin
    for I := 0 to pTemplate.BossList.Count - 1 do begin
      pMonGen := pTMapMonGen(pTemplate.BossList.Items[I]);
      if (pMonGen <> nil) and (pMonGen.nWave > nMaxWave) then
        nMaxWave := pMonGen.nWave;
    end;
  end;
  pInstance.nMaxWave := nMaxWave;
  pInstance.nKillTarget := 0; // 将在SpawnDungeonMonsters中计算

  // 创建玩家入口
  New(pPlayer);
  pPlayer.PlayObject := PlayObject;
  pPlayer.sOriginalMap := PlayObject.m_sMapName;
  pPlayer.nOriginalX := PlayObject.m_nCurrX;
  pPlayer.nOriginalY := PlayObject.m_nCurrY;
  pPlayer.boEntered := False;
  pPlayer.dwEnterTick := 0;
  pInstance.PlayerList.Add(pPlayer);

  m_DungeonInstances.Add(pInstance);

  Result := pInstance;
end;

function TDungeonManager.EnterDungeon(PlayObject: TPlayObject;
  nInstanceID: LongWord): Boolean;
var
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  pTemplate: pTDungeonTemplate;
  Envir: TEnvirnoment;
begin
  Result := False;
  if PlayObject = nil then Exit;

  pInstance := GetInstance(nInstanceID);
  if pInstance = nil then Exit;

  pTemplate := GetTemplate(pInstance.nTemplateID);
  if pTemplate = nil then Exit;

  pPlayer := FindPlayerInInstance(pInstance, PlayObject);
  if pPlayer = nil then Exit;

  // 查找地图
  Envir := g_MapManager.FindMap(pTemplate.sMapName);
  if Envir = nil then begin
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '副本地图不存在，无法进入。');
    Exit;
  end;

  // 保存玩家原始位置
  pPlayer.sOriginalMap := PlayObject.m_sMapName;
  pPlayer.nOriginalX := PlayObject.m_nCurrX;
  pPlayer.nOriginalY := PlayObject.m_nCurrY;

  // 传送玩家到副本
  PlayObject.SpaceMove(pTemplate.sMapName, pTemplate.nEnterX, pTemplate.nEnterY, 0);
  pPlayer.boEntered := True;
  pPlayer.dwEnterTick := GetTickCount;

  // 第一个玩家进入时，启动副本
  if pInstance.nState = 0 then begin
    pInstance.nState := 1;
    pInstance.nCurrentWave := 1;
    SpawnDungeonMonsters(pInstance);
  end;

  SendDungeonInfo(pInstance);
  Result := True;
end;

function TDungeonManager.ExitDungeon(PlayObject: TPlayObject): Boolean;
var
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
begin
  Result := False;
  if PlayObject = nil then Exit;

  pInstance := FindInstanceByPlayer(PlayObject);
  if pInstance = nil then Exit;

  pPlayer := FindPlayerInInstance(pInstance, PlayObject);
  if pPlayer = nil then Exit;

  // 传送回原地图
  if pPlayer.boEntered and (pPlayer.sOriginalMap <> '') then begin
    PlayObject.SpaceMove(pPlayer.sOriginalMap, pPlayer.nOriginalX, pPlayer.nOriginalY, 0);
  end;

  // 从玩家列表中移除
  pInstance.PlayerList.Remove(pPlayer);
  Dispose(pPlayer);

  // 如果所有玩家都离开了且副本仍在进行中，标记失败
  if (pInstance.PlayerList.Count = 0) and (pInstance.nState = 1) then begin
    pInstance.nState := 3;
  end;

  SendDungeonInfo(pInstance);
  Result := True;
end;

function TDungeonManager.GetPlayerInstance(PlayObject: TPlayObject): pTDungeonInstance;
begin
  Result := FindInstanceByPlayer(PlayObject);
end;

function TDungeonManager.CanEnterDungeon(PlayObject: TPlayObject;
  nDungeonID: Word): Boolean;
var
  pTemplate: pTDungeonTemplate;
  nRemaining: Integer;
begin
  Result := False;
  if PlayObject = nil then Exit;

  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;

  // 检查等级
  if PlayObject.m_Abil.Level < pTemplate.nMinLevel then begin
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '等级不足，需要等级' + IntToStr(pTemplate.nMinLevel) + '级。');
    Exit;
  end;

  if PlayObject.m_Abil.Level > pTemplate.nMaxLevel then begin
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '等级超过上限，最高等级' + IntToStr(pTemplate.nMaxLevel) + '级。');
    Exit;
  end;

  // 检查每日次数
  nRemaining := GetDailyRemaining(PlayObject, nDungeonID);
  if nRemaining <= 0 then begin
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '今日进入次数已用完。');
    Exit;
  end;

  // 检查金币
  if pTemplate.nEnterGold > 0 then begin
    if PlayObject.m_nGold < pTemplate.nEnterGold then begin
      PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
        '金币不足，需要' + IntToStr(pTemplate.nEnterGold) + '金币。');
      Exit;
    end;
  end;

  // 检查元宝
  if pTemplate.nEnterGameGold > 0 then begin
    // 元宝检测需要调用游戏币系统，这里预留接口
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '需要' + IntToStr(pTemplate.nEnterGameGold) + '元宝。');
    Exit;
  end;

  Result := True;
end;

function TDungeonManager.GetDailyRemaining(PlayObject: TPlayObject;
  nDungeonID: Word): Integer;
var
  pTemplate: pTDungeonTemplate;
  nTodayEntries: Integer;
  I: Integer;
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  dwToday: LongWord;
begin
  Result := 0;
  if PlayObject = nil then Exit;

  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;

  dwToday := LongWord(Trunc(Date));
  nTodayEntries := 0;

  // 统计今日已进入次数
  for I := 0 to m_DungeonInstances.Count - 1 do begin
    pInstance := pTDungeonInstance(m_DungeonInstances.Items[I]);
    if pInstance = nil then Continue;
    if pInstance.nTemplateID <> nDungeonID then Continue;
    if pInstance.PlayerList = nil then Continue;
    pPlayer := FindPlayerInInstance(pInstance, PlayObject);
    if pPlayer <> nil then
      Inc(nTodayEntries);
  end;

  Result := pTemplate.nDailyLimit - nTodayEntries;
  if Result < 0 then Result := 0;
end;

procedure TDungeonManager.CompleteDungeon(PlayObject: TPlayObject;
  nInstanceID: LongWord);
var
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  I: Integer;
begin
  if PlayObject = nil then Exit;

  pInstance := GetInstance(nInstanceID);
  if pInstance = nil then Exit;
  if pInstance.nState = 2 then Exit; // 已通关

  pInstance.nState := 2;
  pInstance.nProgress := 100;

  // 给所有玩家发放奖励
  if pInstance.PlayerList <> nil then begin
    for I := 0 to pInstance.PlayerList.Count - 1 do begin
      pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[I]);
      if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
        GiveReward(pPlayer.PlayObject, pInstance.nTemplateID, pInstance.nRewardMultiplier);
        pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
          '恭喜通关副本！获得奖励。');
      end;
    end;
  end;

  SendDungeonInfo(pInstance);

  // 延迟清理副本
  CleanupInstance(nInstanceID);
end;

procedure TDungeonManager.FailDungeon(PlayObject: TPlayObject;
  nInstanceID: LongWord);
var
  pInstance: pTDungeonInstance;
  pPlayer: pTPlayerEntry;
  I: Integer;
begin
  if PlayObject = nil then Exit;

  pInstance := GetInstance(nInstanceID);
  if pInstance = nil then Exit;
  if pInstance.nState = 3 then Exit; // 已失败

  pInstance.nState := 3;

  // 通知所有玩家
  if pInstance.PlayerList <> nil then begin
    for I := 0 to pInstance.PlayerList.Count - 1 do begin
      pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[I]);
      if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
        pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
          '副本失败！');
      end;
    end;
  end;

  SendDungeonInfo(pInstance);

  // 将所有玩家传送回原地图
  if pInstance.PlayerList <> nil then begin
    for I := pInstance.PlayerList.Count - 1 downto 0 do begin
      pPlayer := pTPlayerEntry(pInstance.PlayerList.Items[I]);
      if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
        if pPlayer.boEntered and (pPlayer.sOriginalMap <> '') then begin
          pPlayer.PlayObject.SpaceMove(pPlayer.sOriginalMap, pPlayer.nOriginalX, pPlayer.nOriginalY, 0);
        end;
      end;
    end;
  end;

  CleanupInstance(nInstanceID);
end;

procedure TDungeonManager.SpawnDungeonMonsters(Instance: pTDungeonInstance);
var
  pTemplate: pTDungeonTemplate;
  pMonGen: pTMapMonGen;
  I: Integer;
begin
  if Instance = nil then Exit;

  pTemplate := GetTemplate(Instance.nTemplateID);
  if pTemplate = nil then Exit;

  // 清除旧怪物列表
  if Instance.MonsterList <> nil then begin
    while Instance.MonsterList.Count > 0 do begin
      pTMonsterEntry(Instance.MonsterList.Items[0]);
      Instance.MonsterList.Delete(0);
    end;
  end;

  Instance.nKillTarget := 0;

  // 生成第一波怪物
  SpawnMonsterWave(Instance, 1);
end;

procedure TDungeonManager.SpawnMonsterWave(Instance: pTDungeonInstance; nWave: Integer);
var
  pTemplate: pTDungeonTemplate;
  pMonGen: pTMapMonGen;
  pMonster: pTMonsterEntry;
  I: Integer;
begin
  if Instance = nil then Exit;

  pTemplate := GetTemplate(Instance.nTemplateID);
  if pTemplate = nil then Exit;

  // 生成普通怪物
  if pTemplate.MonGenList <> nil then begin
    for I := 0 to pTemplate.MonGenList.Count - 1 do begin
      pMonGen := pTMapMonGen(pTemplate.MonGenList.Items[I]);
      if (pMonGen <> nil) and (pMonGen.nWave = nWave) then begin
        New(pMonster);
        pMonster.sMonName := pMonGen.sMonName;
        pMonster.nX := pMonGen.nX;
        pMonster.nY := pMonGen.nY;
        pMonster.nWave := pMonGen.nWave;
        pMonster.boBoss := False;
        pMonster.boSpawned := False;
        pMonster.nLiveCount := pMonGen.nCount;
        pMonster.BaseObject := nil;
        Instance.MonsterList.Add(pMonster);
        Inc(Instance.nKillTarget, pMonGen.nCount);
        SpawnMonster(Instance, pMonster);
      end;
    end;
  end;

  // 生成Boss怪物
  if pTemplate.BossList <> nil then begin
    for I := 0 to pTemplate.BossList.Count - 1 do begin
      pMonGen := pTMapMonGen(pTemplate.BossList.Items[I]);
      if (pMonGen <> nil) and (pMonGen.nWave = nWave) then begin
        New(pMonster);
        pMonster.sMonName := pMonGen.sMonName;
        pMonster.nX := pMonGen.nX;
        pMonster.nY := pMonGen.nY;
        pMonster.nWave := pMonGen.nWave;
        pMonster.boBoss := True;
        pMonster.boSpawned := False;
        pMonster.nLiveCount := pMonGen.nCount;
        pMonster.BaseObject := nil;
        Instance.MonsterList.Add(pMonster);
        Inc(Instance.nKillTarget, pMonGen.nCount);
        SpawnMonster(Instance, pMonster);
      end;
    end;
  end;
end;

procedure TDungeonManager.SpawnMonster(Instance: pTDungeonInstance;
  Entry: pTMonsterEntry);
begin
  // 怪物生成由引擎的怪物生成系统处理
  // 这里标记为已生成，实际怪物对象由引擎管理
  Entry.boSpawned := True;
end;

procedure TDungeonManager.SendDungeonInfo(Instance: pTDungeonInstance);
var
  pTemplate: pTDungeonTemplate;
  pPlayer: pTPlayerEntry;
  I: Integer;
  sInfo: string;
  nRemainSec: Integer;
begin
  if Instance = nil then Exit;
  if Instance.PlayerList = nil then Exit;

  pTemplate := GetTemplate(Instance.nTemplateID);
  if pTemplate = nil then Exit;

  if Instance.dwEndTime > GetTickCount then
    nRemainSec := (Instance.dwEndTime - GetTickCount) div 1000
  else
    nRemainSec := 0;

  sInfo := '副本: ' + pTemplate.sDungeonName +
    ' 类型: ' + GetDungeonTypeName(pTemplate.DungeonType) +
    ' 状态: ' + GetDungeonStateName(Instance.nState) +
    ' 进度: ' + IntToStr(Instance.nProgress) + '%' +
    ' 波次: ' + IntToStr(Instance.nCurrentWave) + '/' + IntToStr(Instance.nMaxWave) +
    ' 击杀: ' + IntToStr(Instance.nKillCount) + '/' + IntToStr(Instance.nKillTarget) +
    ' 剩余时间: ' + IntToStr(nRemainSec) + '秒';

  for I := 0 to Instance.PlayerList.Count - 1 do begin
    pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
    if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
      pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_DUNGEONINFO, 0,
        Instance.nState, Instance.nProgress, Instance.nCurrentWave, sInfo);
    end;
  end;
end;

procedure TDungeonManager.SendDungeonCountdown(Instance: pTDungeonInstance);
var
  pPlayer: pTPlayerEntry;
  I: Integer;
  nRemainSec: Integer;
begin
  if Instance = nil then Exit;
  if Instance.nState <> 1 then Exit;
  if Instance.PlayerList = nil then Exit;

  if Instance.dwEndTime > GetTickCount then
    nRemainSec := (Instance.dwEndTime - GetTickCount) div 1000
  else
    nRemainSec := 0;

  for I := 0 to Instance.PlayerList.Count - 1 do begin
    pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
    if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
      pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_DUNGEONCOUNTDOWN, 0,
        nRemainSec, 0, 0, '');
    end;
  end;
end;

procedure TDungeonManager.OnMonsterKilled(Instance: pTDungeonInstance;
  sMonsterName: string; boBoss: Boolean);
var
  pPlayer: pTPlayerEntry;
  I: Integer;
  pTemplate: pTDungeonTemplate;
begin
  if Instance = nil then Exit;
  if Instance.nState <> 1 then Exit;

  pTemplate := GetTemplate(Instance.nTemplateID);
  if pTemplate = nil then Exit;

  Inc(Instance.nKillCount);
  if boBoss then
    Inc(Instance.nBossKillCount);

  // 计算进度
  if Instance.nKillTarget > 0 then
    Instance.nProgress := (Instance.nKillCount * 100) div Instance.nKillTarget
  else
    Instance.nProgress := 0;

  if Instance.nProgress > 100 then
    Instance.nProgress := 100;

  // 通知玩家
  if Instance.PlayerList <> nil then begin
    for I := 0 to Instance.PlayerList.Count - 1 do begin
      pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
      if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
        if boBoss then
          pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
            'Boss ' + sMonsterName + ' 已被击杀！(' +
            IntToStr(Instance.nBossKillCount) + '/' + IntToStr(Instance.nKillTarget) + ')')
        else
          pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
            '击杀 ' + sMonsterName + ' (' +
            IntToStr(Instance.nKillCount) + '/' + IntToStr(Instance.nKillTarget) + ')');
      end;
    end;
  end;

  SendDungeonInfo(Instance);
end;

procedure TDungeonManager.GiveReward(PlayObject: TPlayObject; nDungeonID: Word;
  nMultiplier: Integer);
var
  pTemplate: pTDungeonTemplate;
  pReward: pTRewardEntry;
  I: Integer;
  nGold: Integer;
  nExp: Integer;
  nRandom: Integer;
begin
  if PlayObject = nil then Exit;

  pTemplate := GetTemplate(nDungeonID);
  if pTemplate = nil then Exit;

  // 发放金币
  nGold := GetDungeonRewardGold(nDungeonID, nMultiplier);
  if nGold > 0 then begin
    Inc(PlayObject.m_nGold, nGold);
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '获得金币: ' + IntToStr(nGold));
  end;

  // 发放经验
  nExp := GetDungeonRewardExp(nDungeonID, nMultiplier);
  if nExp > 0 then begin
    Inc(PlayObject.m_Abil.Exp, nExp);
    PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
      '获得经验: ' + IntToStr(nExp));
  end;

  // 发放物品奖励
  if pTemplate.RewardList <> nil then begin
    for I := 0 to pTemplate.RewardList.Count - 1 do begin
      pReward := pTRewardEntry(pTemplate.RewardList.Items[I]);
      if pReward = nil then Continue;
      nRandom := Random(100) + 1;
      if nRandom <= pReward.nRate then begin
        // 物品发放由引擎的物品系统处理
        PlayObject.SendDefMsg(PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0,
          '获得物品: ' + pReward.sItemName + ' x' + IntToStr(pReward.nItemCount));
      end;
    end;
  end;
end;

procedure TDungeonManager.NotifyInstancePlayers(Instance: pTDungeonInstance;
  sMsg: string);
var
  I: Integer;
  pPlayer: pTPlayerEntry;
begin
  if Instance = nil then Exit;
  if Instance.PlayerList = nil then Exit;
  for I := 0 to Instance.PlayerList.Count - 1 do begin
    pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
    if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
      pPlayer.PlayObject.SendDefMsg(pPlayer.PlayObject, SM_SYSMESSAGE, 0, 0, 0, 0, sMsg);
    end;
  end;
end;

procedure TDungeonManager.LoadConfig(sConfigFile: string);
var
  Ini: TIniFile;
  sSection: string;
  nCount: Integer;
  I: Integer;
  nDungeonID: Word;
  sName: string;
  sMapName: string;
  nMinLevel: Integer;
  nMaxLevel: Integer;
  nMaxPlayers: Integer;
  nMaxTime: Integer;
  nDailyLimit: Integer;
  nEnterGold: Integer;
  nEnterGameGold: Integer;
  nDungeonType: Integer;
  sEnterMap: string;
  nEnterX: Integer;
  nEnterY: Integer;
  sScript: string;
  sFullPath: string;
begin
  if not FileExists(sConfigFile) then Exit;

  // 清除现有模板
  if m_boInitialized then begin
    while m_DungeonTemplates.Count > 0 do begin
      pTDungeonTemplate(m_DungeonTemplates.Items[0]);
      m_DungeonTemplates.Delete(0);
    end;
    m_boInitialized := False;
  end;

  Ini := TIniFile.Create(sConfigFile);
  try
    nCount := Ini.ReadInteger('General', 'DungeonCount', 0);
    for I := 0 to nCount - 1 do begin
      sSection := 'Dungeon' + IntToStr(I);
      nDungeonID := Ini.ReadInteger(sSection, 'DungeonID', 0);
      sName := Ini.ReadString(sSection, 'Name', '');
      sMapName := Ini.ReadString(sSection, 'MapName', '');
      nMinLevel := Ini.ReadInteger(sSection, 'MinLevel', 1);
      nMaxLevel := Ini.ReadInteger(sSection, 'MaxLevel', 200);
      nMaxPlayers := Ini.ReadInteger(sSection, 'MaxPlayers', 5);
      nMaxTime := Ini.ReadInteger(sSection, 'MaxTime', 1800);
      nDailyLimit := Ini.ReadInteger(sSection, 'DailyLimit', 5);
      nEnterGold := Ini.ReadInteger(sSection, 'EnterGold', 0);
      nEnterGameGold := Ini.ReadInteger(sSection, 'EnterGameGold', 0);
      nDungeonType := Ini.ReadInteger(sSection, 'DungeonType', 0);
      sEnterMap := Ini.ReadString(sSection, 'EnterMap', sMapName);
      nEnterX := Ini.ReadInteger(sSection, 'EnterX', 50);
      nEnterY := Ini.ReadInteger(sSection, 'EnterY', 50);
      sScript := Ini.ReadString(sSection, 'Script', '');

      if nDungeonID > 0 then begin
        AddTemplate(nDungeonID, sName, sMapName, nMinLevel, nMaxLevel,
          nMaxPlayers, nMaxTime, nDailyLimit, nEnterGold, nEnterGameGold,
          TDungeonType(nDungeonType), sEnterMap, nEnterX, nEnterY, sScript);
      end;
    end;
  finally
    Ini.Free;
  end;

  m_boInitialized := True;
end;

procedure TDungeonManager.CheckDungeonTime(Instance: pTDungeonInstance);
var
  dwNow: LongWord;
  pPlayer: pTPlayerEntry;
  I: Integer;
  pTemplate: pTDungeonTemplate;
begin
  if Instance = nil then Exit;
  if Instance.nState <> 1 then Exit;

  pTemplate := GetTemplate(Instance.nTemplateID);
  if pTemplate = nil then Exit;

  dwNow := GetTickCount;
  if dwNow >= Instance.dwEndTime then begin
    // 超时，副本失败
    Instance.nState := 3;
    NotifyInstancePlayers(Instance, '副本时间已到，挑战失败！');

    // 将所有玩家传送回原地图
    if Instance.PlayerList <> nil then begin
      for I := Instance.PlayerList.Count - 1 downto 0 do begin
        pPlayer := pTPlayerEntry(Instance.PlayerList.Items[I]);
        if (pPlayer <> nil) and (pPlayer.PlayObject <> nil) then begin
          if pPlayer.boEntered and (pPlayer.sOriginalMap <> '') then begin
            pPlayer.PlayObject.SpaceMove(pPlayer.sOriginalMap, pPlayer.nOriginalX, pPlayer.nOriginalY, 0);
          end;
        end;
      end;
    end;

    SendDungeonInfo(Instance);

    // 延迟清理（等状态同步后）
    CleanupInstance(Instance.nInstanceID);
  end;
end;

end.