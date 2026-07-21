unit BuffManager;

interface

uses
  Windows, SysUtils, Classes, Grobal2, ObjBase;

type
  TBuffManager = class
  private
    m_BuffList: TList;              // 全局BUFF定义列表
    m_BuffConfigs: TStringList;     // BUFF配置缓存
    m_boInitialized: Boolean;
    function FindBuffDef(nBuffType: Word): pTBuffInfo;
    procedure LoadBuffDefsFromFile(sFileName: string);
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;
    procedure LoadConfig(sConfigFile: string);

    // BUFF生命周期管理
    function AddBuff(BaseObject: TBaseObject; nBuffType: Word; nDuration: LongWord;
      nValue: Integer; nValue2: Integer; nSourceObj: Integer): Boolean;
    function RemoveBuff(BaseObject: TBaseObject; nBuffID: Word): Boolean;
    function RemoveBuffByType(BaseObject: TBaseObject; nBuffType: Word): Boolean;
    procedure RemoveAllBuffs(BaseObject: TBaseObject);
    procedure RemoveAllDebuffs(BaseObject: TBaseObject);
    procedure RemoveBuffsOnDeath(BaseObject: TBaseObject);

    // BUFF查询
    function HasBuff(BaseObject: TBaseObject; nBuffType: Word): Boolean;
    function GetBuffCount(BaseObject: TBaseObject): Integer;
    function GetBuffInfo(BaseObject: TBaseObject; nBuffType: Word): pTBuffInfo;
    function GetBuffList(BaseObject: TBaseObject): pTBuffList;

    // BUFF tick处理
    procedure Run(BaseObject: TBaseObject);

    // BUFF属性计算
    procedure CalcBuffAbility(BaseObject: TBaseObject; var AddAbility: TAddAbility);

    // 状态同步
    procedure SyncBuffToClient(BaseObject: TBaseObject; BuffInfo: pTBuffInfo; boAdd: Boolean);

    // 预定义BUFF创建
    function CreateBuffAtkUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateBuffDefUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateBuffSpeedUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateBuffCritUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateBuffVampire(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateBuffShield(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffSlow(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffSilence(nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffWeak(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffBleed(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffBurn(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
    function CreateDebuffFreeze(nDuration: LongWord; nSourceObj: Integer): Boolean;
  end;

  // 全局BUFF定义模板
  TBuffDef = packed record
    nBuffType: Word;
    sBuffName: string[20];
    nBuffCategory: Word;          // 0=BUFF, 1=DEBUFF
    nDefaultDuration: LongWord;   // 默认持续时间
    nDefaultInterval: LongWord;   // 默认tick间隔
    nMaxOverlay: Byte;            // 最大叠加层数
    boRemoveOnDeath: Boolean;     // 死亡是否移除
    nEffectID: Word;              // 默认特效ID
    sDescription: string[100];    // 描述
  end;

const
  BUFF_CATEGORY_BUFF = 0;
  BUFF_CATEGORY_DEBUFF = 1;

  // 预定义BUFF类型ID
  BUFF_TYPE_ATK_UP = 1;
  BUFF_TYPE_DEF_UP = 2;
  BUFF_TYPE_SPEED_UP = 3;
  BUFF_TYPE_CRIT_UP = 4;
  BUFF_TYPE_VAMPIRE = 5;
  BUFF_TYPE_SHIELD = 6;
  BUFF_TYPE_IMMUNE_CONTROL = 7;
  BUFF_TYPE_REFLECT = 8;
  BUFF_TYPE_CONTINUOUS_HEAL = 9;
  BUFF_TYPE_THORNS = 10;
  BUFF_TYPE_EXP_BOOST = 11;
  BUFF_TYPE_DROP_BOOST = 12;
  BUFF_TYPE_INVINCIBLE = 13;

  DEBUFF_TYPE_SLOW = 100;
  DEBUFF_TYPE_SILENCE = 101;
  DEBUFF_TYPE_WEAK = 102;
  DEBUFF_TYPE_BLEED = 103;
  DEBUFF_TYPE_BURN = 104;
  DEBUFF_TYPE_FREEZE = 105;
  DEBUFF_TYPE_POISON_PLUS = 106;
  DEBUFF_TYPE_ARMOR_BREAK = 107;
  DEBUFF_TYPE_CONFUSE = 108;
  DEBUFF_TYPE_FEAR = 109;

  MAX_BUFF_COUNT = 32;
  BUFF_TICK_INTERVAL = 1000;  // 默认1秒tick

var
  g_BuffManager: TBuffManager;

implementation

uses
  M2Share, ObjPlay;

{ TBuffManager }

constructor TBuffManager.Create;
begin
  inherited Create;
  m_BuffList := TList.Create;
  m_BuffConfigs := TStringList.Create;
  m_boInitialized := False;
end;

destructor TBuffManager.Destroy;
var
  i: Integer;
begin
  for i := 0 to m_BuffList.Count - 1 do
  begin
    if m_BuffList[i] <> nil then
      Dispose(pTBuffInfo(m_BuffList[i]));
  end;
  m_BuffList.Free;
  m_BuffConfigs.Free;
  inherited;
end;

procedure TBuffManager.Initialize;
begin
  if m_boInitialized then
    Exit;
  m_boInitialized := True;
  // 加载默认BUFF定义
  LoadBuffDefsFromFile(g_Config.sEnvirDir + 'BuffDefs.ini');
end;

function TBuffManager.FindBuffDef(nBuffType: Word): pTBuffInfo;
var
  i: Integer;
begin
  Result := nil;
  for i := 0 to m_BuffList.Count - 1 do
  begin
    if pTBuffInfo(m_BuffList[i]).nBuffType = nBuffType then
    begin
      Result := pTBuffInfo(m_BuffList[i]);
      Exit;
    end;
  end;
end;

procedure TBuffManager.LoadBuffDefsFromFile(sFileName: string);
var
  BuffInfo: pTBuffInfo;
begin
  // 如果配置文件不存在，使用内置默认定义
  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_ATK_UP;
  BuffInfo.sBuffName := '攻击强化';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 1;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_DEF_UP;
  BuffInfo.sBuffName := '防御强化';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 2;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_SPEED_UP;
  BuffInfo.sBuffName := '速度提升';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 20000;
  BuffInfo.nMaxDuration := 20000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 3;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_CRIT_UP;
  BuffInfo.sBuffName := '暴击提升';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 4;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_VAMPIRE;
  BuffInfo.sBuffName := '吸血';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 5;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_SHIELD;
  BuffInfo.sBuffName := '护盾';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 6;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_IMMUNE_CONTROL;
  BuffInfo.sBuffName := '霸体';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 15000;
  BuffInfo.nMaxDuration := 15000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 7;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_REFLECT;
  BuffInfo.sBuffName := '伤害反弹';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 20000;
  BuffInfo.nMaxDuration := 20000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 8;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_CONTINUOUS_HEAL;
  BuffInfo.sBuffName := '持续回血';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 10000;
  BuffInfo.nMaxDuration := 10000;
  BuffInfo.nInterval := 1000;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 9;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_THORNS;
  BuffInfo.sBuffName := '荆棘光环';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 30000;
  BuffInfo.nMaxDuration := 30000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 10;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_EXP_BOOST;
  BuffInfo.sBuffName := '经验加成';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 3600000;
  BuffInfo.nMaxDuration := 3600000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := False;
  BuffInfo.nEffectID := 11;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_DROP_BOOST;
  BuffInfo.sBuffName := '掉率加成';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 3600000;
  BuffInfo.nMaxDuration := 3600000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := False;
  BuffInfo.nEffectID := 12;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := BUFF_TYPE_INVINCIBLE;
  BuffInfo.sBuffName := '无敌';
  BuffInfo.BuffType := btBuff;
  BuffInfo.nDuration := 5000;
  BuffInfo.nMaxDuration := 5000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 13;
  m_BuffList.Add(BuffInfo);

  // ===== DEBUFF定义 =====
  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_SLOW;
  BuffInfo.sBuffName := '减速';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 10000;
  BuffInfo.nMaxDuration := 10000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 100;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_SILENCE;
  BuffInfo.sBuffName := '沉默';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 5000;
  BuffInfo.nMaxDuration := 5000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 101;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_WEAK;
  BuffInfo.sBuffName := '虚弱';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 15000;
  BuffInfo.nMaxDuration := 15000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 102;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_BLEED;
  BuffInfo.sBuffName := '流血';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 8000;
  BuffInfo.nMaxDuration := 8000;
  BuffInfo.nInterval := 1000;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 103;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_BURN;
  BuffInfo.sBuffName := '灼烧';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 8000;
  BuffInfo.nMaxDuration := 8000;
  BuffInfo.nInterval := 1000;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 104;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_FREEZE;
  BuffInfo.sBuffName := '冰冻';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 3000;
  BuffInfo.nMaxDuration := 3000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 105;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_POISON_PLUS;
  BuffInfo.sBuffName := '剧毒';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 10000;
  BuffInfo.nMaxDuration := 10000;
  BuffInfo.nInterval := 1000;
  BuffInfo.nMaxOverlay := 5;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 106;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_ARMOR_BREAK;
  BuffInfo.sBuffName := '破甲';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 10000;
  BuffInfo.nMaxDuration := 10000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 3;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 107;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_CONFUSE;
  BuffInfo.sBuffName := '混乱';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 5000;
  BuffInfo.nMaxDuration := 5000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 108;
  m_BuffList.Add(BuffInfo);

  New(BuffInfo);
  FillChar(BuffInfo^, SizeOf(TBuffInfo), #0);
  BuffInfo.nBuffID := 0;
  BuffInfo.nBuffType := DEBUFF_TYPE_FEAR;
  BuffInfo.sBuffName := '恐惧';
  BuffInfo.BuffType := btDebuff;
  BuffInfo.nDuration := 3000;
  BuffInfo.nMaxDuration := 3000;
  BuffInfo.nInterval := 0;
  BuffInfo.nMaxOverlay := 1;
  BuffInfo.boRemoveOnDeath := True;
  BuffInfo.nEffectID := 109;
  m_BuffList.Add(BuffInfo);
end;

procedure TBuffManager.LoadConfig(sConfigFile: string);
begin
  // 从配置文件加载额外BUFF定义
  m_BuffConfigs.LoadFromFile(sConfigFile);
end;

function TBuffManager.AddBuff(BaseObject: TBaseObject; nBuffType: Word;
  nDuration: LongWord; nValue: Integer; nValue2: Integer; nSourceObj: Integer): Boolean;
var
  BuffDef: pTBuffInfo;
  BuffList: pTBuffList;
  i: Integer;
  pExisting: pTBuffInfo;
  nNewBuffID: Word;
begin
  Result := False;
  if BaseObject = nil then
    Exit;
  if BaseObject.m_boGhost then
    Exit;

  BuffDef := FindBuffDef(nBuffType);
  if BuffDef = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  // 检查是否已有同类型BUFF，尝试叠加
  for i := 0 to BuffList.nCount - 1 do
  begin
    pExisting := @BuffList.Buffs[i];
    if pExisting.nBuffType = nBuffType then
    begin
      if pExisting.nOverlay < pExisting.nMaxOverlay then
      begin
        Inc(pExisting.nOverlay);
        pExisting.nDuration := nDuration;
        pExisting.nMaxDuration := nDuration;
        pExisting.nValue := nValue;
        pExisting.nValue2 := nValue2;
        pExisting.dwLastTick := GetTickCount;
        SyncBuffToClient(BaseObject, pExisting, True);
        Result := True;
      end
      else
      begin
        // 已满层，刷新持续时间
        pExisting.nDuration := nDuration;
        pExisting.nMaxDuration := nDuration;
        pExisting.dwLastTick := GetTickCount;
        SyncBuffToClient(BaseObject, pExisting, True);
        Result := True;
      end;
      Exit;
    end;
  end;

  // 已达到最大BUFF数量
  if BuffList.nCount >= MAX_BUFF_COUNT then
  begin
    // 尝试移除一个可移除的BUFF
    for i := 0 to BuffList.nCount - 1 do
    begin
      if (not BuffList.Buffs[i].boPermanent) and (BuffList.Buffs[i].BuffType = btDebuff) then
      begin
        RemoveBuff(BaseObject, BuffList.Buffs[i].nBuffID);
        Break;
      end;
    end;
    if BuffList.nCount >= MAX_BUFF_COUNT then
      Exit;
  end;

  // 生成新BUFF ID
  nNewBuffID := 0;
  repeat
    nNewBuffID := Random(65535) + 1;
    for i := 0 to BuffList.nCount - 1 do
    begin
      if BuffList.Buffs[i].nBuffID = nNewBuffID then
      begin
        nNewBuffID := 0;
        Break;
      end;
    end;
  until nNewBuffID > 0;

  // 添加新BUFF
  with BuffList.Buffs[BuffList.nCount] do
  begin
    nBuffID := nNewBuffID;
    nBuffType := BuffDef.nBuffType;
    sBuffName := BuffDef.sBuffName;
    BuffType := BuffDef.BuffType;
    nDuration := nDuration;
    nMaxDuration := nDuration;
    nInterval := BuffDef.nInterval;
    dwLastTick := GetTickCount;
    nValue := nValue;
    nValue2 := nValue2;
    nSourceObj := nSourceObj;
    nEffectID := BuffDef.nEffectID;
    nOverlay := 1;
    nMaxOverlay := BuffDef.nMaxOverlay;
    boPermanent := (nDuration = 0);
    boRemoveOnDeath := BuffDef.boRemoveOnDeath;
  end;

  Inc(BuffList.nCount);
  SyncBuffToClient(BaseObject, @BuffList.Buffs[BuffList.nCount - 1], True);
  Result := True;
end;

function TBuffManager.RemoveBuff(BaseObject: TBaseObject; nBuffID: Word): Boolean;
var
  BuffList: pTBuffList;
  i, j: Integer;
begin
  Result := False;
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := 0 to BuffList.nCount - 1 do
  begin
    if BuffList.Buffs[i].nBuffID = nBuffID then
    begin
      SyncBuffToClient(BaseObject, @BuffList.Buffs[i], False);
      // 移动后面的BUFF
      for j := i to BuffList.nCount - 2 do
      begin
        BuffList.Buffs[j] := BuffList.Buffs[j + 1];
      end;
      FillChar(BuffList.Buffs[BuffList.nCount - 1], SizeOf(TBuffInfo), #0);
      Dec(BuffList.nCount);
      Result := True;
      Exit;
    end;
  end;
end;

function TBuffManager.RemoveBuffByType(BaseObject: TBaseObject; nBuffType: Word): Boolean;
var
  BuffList: pTBuffList;
  i: Integer;
begin
  Result := False;
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := BuffList.nCount - 1 downto 0 do
  begin
    if BuffList.Buffs[i].nBuffType = nBuffType then
    begin
      RemoveBuff(BaseObject, BuffList.Buffs[i].nBuffID);
      Result := True;
    end;
  end;
end;

procedure TBuffManager.RemoveAllBuffs(BaseObject: TBaseObject);
var
  BuffList: pTBuffList;
  i: Integer;
begin
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := BuffList.nCount - 1 downto 0 do
  begin
    SyncBuffToClient(BaseObject, @BuffList.Buffs[i], False);
  end;
  FillChar(BuffList^, SizeOf(TBuffList), #0);
end;

procedure TBuffManager.RemoveAllDebuffs(BaseObject: TBaseObject);
var
  BuffList: pTBuffList;
  i: Integer;
begin
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := BuffList.nCount - 1 downto 0 do
  begin
    if BuffList.Buffs[i].BuffType = btDebuff then
    begin
      RemoveBuff(BaseObject, BuffList.Buffs[i].nBuffID);
    end;
  end;
end;

procedure TBuffManager.RemoveBuffsOnDeath(BaseObject: TBaseObject);
var
  BuffList: pTBuffList;
  i: Integer;
begin
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := BuffList.nCount - 1 downto 0 do
  begin
    if BuffList.Buffs[i].boRemoveOnDeath then
    begin
      RemoveBuff(BaseObject, BuffList.Buffs[i].nBuffID);
    end;
  end;
end;

function TBuffManager.HasBuff(BaseObject: TBaseObject; nBuffType: Word): Boolean;
var
  BuffList: pTBuffList;
  i: Integer;
begin
  Result := False;
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := 0 to BuffList.nCount - 1 do
  begin
    if BuffList.Buffs[i].nBuffType = nBuffType then
    begin
      Result := True;
      Exit;
    end;
  end;
end;

function TBuffManager.GetBuffCount(BaseObject: TBaseObject): Integer;
var
  BuffList: pTBuffList;
begin
  Result := 0;
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  Result := BuffList.nCount;
end;

function TBuffManager.GetBuffInfo(BaseObject: TBaseObject; nBuffType: Word): pTBuffInfo;
var
  BuffList: pTBuffList;
  i: Integer;
begin
  Result := nil;
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := 0 to BuffList.nCount - 1 do
  begin
    if BuffList.Buffs[i].nBuffType = nBuffType then
    begin
      Result := @BuffList.Buffs[i];
      Exit;
    end;
  end;
end;

function TBuffManager.GetBuffList(BaseObject: TBaseObject): pTBuffList;
begin
  Result := nil;
  if BaseObject = nil then
    Exit;
  // 使用TBaseObject的m_MapQuestList作为BUFF列表指针存储
  // 实际应用中应在TBaseObject中添加专用字段
  Result := pTBuffList(BaseObject.m_MapQuestList);
  if Result = nil then
  begin
    New(Result);
    FillChar(Result^, SizeOf(TBuffList), #0);
    BaseObject.m_MapQuestList := TList(Result);
  end;
end;

procedure TBuffManager.Run(BaseObject: TBaseObject);
var
  BuffList: pTBuffList;
  i: Integer;
  dwNow: LongWord;
  nDamage: Integer;
  PlayObject: TPlayObject;
begin
  if BaseObject = nil then
    Exit;
  if BaseObject.m_boGhost then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  dwNow := GetTickCount;
  i := 0;
  while i < BuffList.nCount do
  begin
    with BuffList.Buffs[i] do
    begin
      // 检查到期
      if (not boPermanent) and (nDuration > 0) then
      begin
        if (GetTickCount - dwLastTick) >= nDuration then
        begin
          RemoveBuff(BaseObject, nBuffID);
          Continue;
        end;
      end;

      // 处理周期性tick
      if (nInterval > 0) and (GetTickCount - dwLastTick >= nInterval) then
      begin
        dwLastTick := GetTickCount;

        case nBuffType of
          BUFF_TYPE_CONTINUOUS_HEAL:
          begin
            // 持续回血
            if BaseObject.m_WAbil.HP < BaseObject.m_WAbil.MaxHP then
            begin
              nDamage := nValue * nOverlay;
              BaseObject.m_WAbil.HP := _MIN(High(Word), Integer(BaseObject.m_WAbil.HP) + nDamage);
              if BaseObject.m_btRaceServer = RC_PLAYOBJECT then
              begin
                PlayObject := TPlayObject(BaseObject);
                PlayObject.SendDefMsg(PlayObject, SM_ABILITY, PlayObject.m_WAbil.HP,
                  PlayObject.m_WAbil.MaxHP, 0, 0, '');
              end;
            end;
          end;

          DEBUFF_TYPE_BLEED:
          begin
            // 流血伤害
            nDamage := nValue * nOverlay;
            if BaseObject.m_WAbil.HP > nDamage then
              BaseObject.m_WAbil.HP := BaseObject.m_WAbil.HP - nDamage
            else
              BaseObject.m_WAbil.HP := 0;
            if BaseObject.m_btRaceServer = RC_PLAYOBJECT then
            begin
              PlayObject := TPlayObject(BaseObject);
              PlayObject.SendDefMsg(PlayObject, SM_ABILITY, PlayObject.m_WAbil.HP,
                PlayObject.m_WAbil.MaxHP, 0, 0, '');
            end;
          end;

          DEBUFF_TYPE_BURN:
          begin
            // 灼烧伤害
            nDamage := nValue * nOverlay;
            if BaseObject.m_WAbil.HP > nDamage then
              BaseObject.m_WAbil.HP := BaseObject.m_WAbil.HP - nDamage
            else
              BaseObject.m_WAbil.HP := 0;
            if BaseObject.m_btRaceServer = RC_PLAYOBJECT then
            begin
              PlayObject := TPlayObject(BaseObject);
              PlayObject.SendDefMsg(PlayObject, SM_ABILITY, PlayObject.m_WAbil.HP,
                PlayObject.m_WAbil.MaxHP, 0, 0, '');
            end;
          end;

          DEBUFF_TYPE_POISON_PLUS:
          begin
            // 剧毒伤害
            nDamage := nValue * nOverlay;
            if BaseObject.m_WAbil.HP > nDamage then
              BaseObject.m_WAbil.HP := BaseObject.m_WAbil.HP - nDamage
            else
              BaseObject.m_WAbil.HP := 0;
            if BaseObject.m_btRaceServer = RC_PLAYOBJECT then
            begin
              PlayObject := TPlayObject(BaseObject);
              PlayObject.SendDefMsg(PlayObject, SM_ABILITY, PlayObject.m_WAbil.HP,
                PlayObject.m_WAbil.MaxHP, 0, 0, '');
            end;
          end;
        end;
      end;
    end;
    Inc(i);
  end;
end;

procedure TBuffManager.CalcBuffAbility(BaseObject: TBaseObject; var AddAbility: TAddAbility);
var
  BuffList: pTBuffList;
  i: Integer;
begin
  if BaseObject = nil then
    Exit;

  BuffList := GetBuffList(BaseObject);
  if BuffList = nil then
    Exit;

  for i := 0 to BuffList.nCount - 1 do
  begin
    with BuffList.Buffs[i] do
    begin
      case nBuffType of
        BUFF_TYPE_ATK_UP:
        begin
          Inc(AddAbility.DC, nValue * nOverlay);
          Inc(AddAbility.MC, nValue * nOverlay);
          Inc(AddAbility.SC, nValue * nOverlay);
        end;
        BUFF_TYPE_DEF_UP:
        begin
          Inc(AddAbility.AC, nValue * nOverlay);
          Inc(AddAbility.MAC, nValue * nOverlay);
        end;
        BUFF_TYPE_SPEED_UP:
        begin
          Inc(AddAbility.wSpeedPoint, nValue * nOverlay);
          Inc(AddAbility.nHitSpeed, nValue * nOverlay);
        end;
        BUFF_TYPE_CRIT_UP:
        begin
          Inc(AddAbility.btDeadliness, nValue * nOverlay);
        end;
        BUFF_TYPE_EXP_BOOST:
        begin
          Inc(AddAbility.btExpRate, nValue * nOverlay);
        end;
        DEBUFF_TYPE_WEAK:
        begin
          Dec(AddAbility.DC, nValue * nOverlay);
          Dec(AddAbility.MC, nValue * nOverlay);
          Dec(AddAbility.SC, nValue * nOverlay);
        end;
        DEBUFF_TYPE_ARMOR_BREAK:
        begin
          Dec(AddAbility.AC, nValue * nOverlay);
          Dec(AddAbility.MAC, nValue * nOverlay);
        end;
        DEBUFF_TYPE_SLOW:
        begin
          Dec(AddAbility.wSpeedPoint, nValue * nOverlay);
          Dec(AddAbility.nHitSpeed, nValue * nOverlay);
        end;
      end;
    end;
  end;
end;

procedure TBuffManager.SyncBuffToClient(BaseObject: TBaseObject; BuffInfo: pTBuffInfo; boAdd: Boolean);
var
  sMsg: string;
  PlayObject: TPlayObject;
begin
  if BaseObject = nil then
    Exit;
  if BuffInfo = nil then
    Exit;
  if BaseObject.m_btRaceServer <> RC_PLAYOBJECT then
    Exit;

  PlayObject := TPlayObject(BaseObject);
  // 使用自定义消息同步BUFF状态到客户端
  sMsg := EncodeString(IntToStr(BuffInfo.nBuffType) + '/' + IntToStr(BuffInfo.nValue) + '/' +
    IntToStr(BuffInfo.nDuration) + '/' + IntToStr(BuffInfo.nOverlay) + '/' +
    BuffInfo.sBuffName);
  if boAdd then
    PlayObject.SendDefMsg(PlayObject, SM_BUFFADDEFFECT, BuffInfo.nBuffID,
      BuffInfo.nEffectID, BuffInfo.nDuration, 0, sMsg)
  else
    PlayObject.SendDefMsg(PlayObject, SM_BUFFDELEFFECT, BuffInfo.nBuffID, 0, 0, 0, sMsg);
end;

// ===== 预定义BUFF创建快捷方法 =====

function TBuffManager.CreateBuffAtkUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False; // 需要传入BaseObject，由外部调用
end;

function TBuffManager.CreateBuffDefUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateBuffSpeedUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateBuffCritUp(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateBuffVampire(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateBuffShield(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffSlow(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffSilence(nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffWeak(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffBleed(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffBurn(nValue: Integer; nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

function TBuffManager.CreateDebuffFreeze(nDuration: LongWord; nSourceObj: Integer): Boolean;
begin
  Result := False;
end;

initialization
  g_BuffManager := TBuffManager.Create;

finalization
  g_BuffManager.Free;

end.