unit AffixEngine;

interface

uses
  Windows, SysUtils, Classes, IniFiles, Grobal2, M2Share, ObjBase, ObjPlay;

type
  TAffixEngine = class
  private
    m_AffixList: TList;           // All affix definitions
    m_AffixGroups: TList;         // Affix groups
    m_boInitialized: Boolean;
    function GetAffixByID(nAffixID: Word): pTAffixEntry;
    function GetAffixGroup(sGroupName: string): pTAffixGroup;
    function GetEquipTypeName(StdItem: pTStdItem): Byte;
    function RandomValue(nMin, nMax: Integer): Integer;
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;

    // Generate random affixes for an item
    function GenerateAffixes(UserItem: pTUserItem; StdItem: pTStdItem; nCount: Integer): TList; // Returns list of pTItemAffix
    // Reforge affixes (keep locked ones)
    function ReforgeAffixes(UserItem: pTUserItem; StdItem: pTStdItem; LockedAffixes: TList): TList;
    // Calculate attribute bonuses from affixes
    procedure CalcAffixAbility(AffixList: TList; var AddAbility: TAddAbility);
    // Generate display name with affix prefix
    function GetAffixItemName(UserItem: pTUserItem; StdItem: pTStdItem; AffixList: TList): string;
    // Get affix quality color
    function GetAffixQualityColor(nQuality: Byte): Byte;
    // Lock/unlock an affix
    procedure LockAffix(Affix: pTItemAffix; boLock: Boolean);
    // Get affix count by quality
    function GetAffixCountByQuality(AffixList: TList; nQuality: Byte): Integer;
    // Clean up affix list
    procedure FreeAffixList(AffixList: TList);
    // Load affix configuration from file
    procedure LoadConfig(sConfigFile: string);
  end;

var
  g_AffixEngine: TAffixEngine;

const
  // Affix quality constants
  AFFIX_QUALITY_COMMON    = 0;
  AFFIX_QUALITY_RARE      = 1;
  AFFIX_QUALITY_EPIC      = 2;
  AFFIX_QUALITY_LEGENDARY = 3;

  // Affix attribute type constants
  AFFIX_ATTR_DC            = 0;
  AFFIX_ATTR_MC            = 1;
  AFFIX_ATTR_SC            = 2;
  AFFIX_ATTR_AC            = 3;
  AFFIX_ATTR_MAC           = 4;
  AFFIX_ATTR_HP            = 5;
  AFFIX_ATTR_MP            = 6;
  AFFIX_ATTR_HITSPEED      = 7;
  AFFIX_ATTR_DEADLINESS    = 8;
  AFFIX_ATTR_VAMPIRE       = 9;
  AFFIX_ATTR_EXPRATE       = 10;
  AFFIX_ATTR_DROPRATE      = 11;
  AFFIX_ATTR_ADDATTACK     = 12;
  AFFIX_ATTR_DELDAMAGE     = 13;
  AFFIX_ATTR_LUCK          = 14;
  AFFIX_ATTR_HITPOINT      = 15;
  AFFIX_ATTR_SPEEDPOINT    = 16;
  AFFIX_ATTR_ANTIMAGIC     = 17;
  AFFIX_ATTR_HEALTHRECOVER = 18;
  AFFIX_ATTR_SPELLRECOVER  = 19;
  AFFIX_ATTR_ADDWUXIN      = 20;
  AFFIX_ATTR_DELWUXIN      = 21;
  AFFIX_ATTR_STRONG        = 22;
  AFFIX_ATTR_HPMPRATE      = 23;
  AFFIX_ATTR_AC2RATE       = 24;
  AFFIX_ATTR_MAC2RATE      = 25;
  AFFIX_ATTR_POISONRECOVER = 26;
  AFFIX_ATTR_POISONMAGIC   = 27;
  AFFIX_ATTR_ANTIPOISON    = 28;

  // Equip type constants
  EQUIP_TYPE_ALL       = 0;
  EQUIP_TYPE_WEAPON    = 1;
  EQUIP_TYPE_DRESS     = 2;
  EQUIP_TYPE_JEWELRY   = 3;
  EQUIP_TYPE_HELMET    = 4;
  EQUIP_TYPE_BELT      = 5;
  EQUIP_TYPE_BOOTS     = 6;
  EQUIP_TYPE_STONE     = 7;

  // Quality weight ranges for random selection
  QUALITY_WEIGHT_COMMON    = 5000;
  QUALITY_WEIGHT_RARE      = 2000;
  QUALITY_WEIGHT_EPIC      = 600;
  QUALITY_WEIGHT_LEGENDARY = 100;

  // Quality color values
  AFFIX_COLOR_COMMON    = 0;   // 白色
  AFFIX_COLOR_RARE      = 68;  // 绿色
  AFFIX_COLOR_EPIC      = 222; // 紫色
  AFFIX_COLOR_LEGENDARY = 249; // 橙色

  // Quality display prefix
  AFFIX_PREFIX_COMMON    = '';
  AFFIX_PREFIX_RARE      = '[稀有]';
  AFFIX_PREFIX_EPIC      = '[史诗]';
  AFFIX_PREFIX_LEGENDARY = '[传说]';

implementation

{ TAffixEngine }

constructor TAffixEngine.Create;
begin
  inherited Create;
  m_AffixList := TList.Create;
  m_AffixGroups := TList.Create;
  m_boInitialized := False;
end;

destructor TAffixEngine.Destroy;
var
  I: Integer;
  AffixEntry: pTAffixEntry;
  AffixGroup: pTAffixGroup;
begin
  for I := 0 to m_AffixList.Count - 1 do
  begin
    AffixEntry := pTAffixEntry(m_AffixList.Items[I]);
    if AffixEntry <> nil then
      Dispose(AffixEntry);
  end;
  m_AffixList.Free;

  for I := 0 to m_AffixGroups.Count - 1 do
  begin
    AffixGroup := pTAffixGroup(m_AffixGroups.Items[I]);
    if AffixGroup <> nil then
    begin
      if AffixGroup.AffixList <> nil then
        AffixGroup.AffixList.Free;
      Dispose(AffixGroup);
    end;
  end;
  m_AffixGroups.Free;

  inherited Destroy;
end;

function TAffixEngine.RandomValue(nMin, nMax: Integer): Integer;
begin
  if nMin >= nMax then
    Result := nMin
  else
    Result := nMin + Random(nMax - nMin + 1);
end;

function TAffixEngine.GetEquipTypeName(StdItem: pTStdItem): Byte;
begin
  Result := EQUIP_TYPE_ALL;
  if StdItem = nil then Exit;
  case StdItem.StdMode of
    tm_Weapon: Result := EQUIP_TYPE_WEAPON;
    tm_Dress: Result := EQUIP_TYPE_DRESS;
    tm_Helmet: Result := EQUIP_TYPE_HELMET;
    tm_Necklace, tm_Ring, tm_ArmRing, tm_Amulet: Result := EQUIP_TYPE_JEWELRY;
    tm_Belt: Result := EQUIP_TYPE_BELT;
    tm_Boot: Result := EQUIP_TYPE_BOOTS;
    tm_Stone: Result := EQUIP_TYPE_STONE;
  end;
end;

function TAffixEngine.GetAffixByID(nAffixID: Word): pTAffixEntry;
var
  I: Integer;
  Affix: pTAffixEntry;
begin
  Result := nil;
  for I := 0 to m_AffixList.Count - 1 do
  begin
    Affix := pTAffixEntry(m_AffixList.Items[I]);
    if Affix <> nil then
    begin
      if Affix.nAffixID = nAffixID then
      begin
        Result := Affix;
        Exit;
      end;
    end;
  end;
end;

function TAffixEngine.GetAffixGroup(sGroupName: string): pTAffixGroup;
var
  I: Integer;
  Group: pTAffixGroup;
begin
  Result := nil;
  for I := 0 to m_AffixGroups.Count - 1 do
  begin
    Group := pTAffixGroup(m_AffixGroups.Items[I]);
    if Group <> nil then
    begin
      if CompareText(Group.sGroupName, sGroupName) = 0 then
      begin
        Result := Group;
        Exit;
      end;
    end;
  end;
end;

procedure TAffixEngine.Initialize;
var
  Affix: pTAffixEntry;

  procedure AddAffix(nID: Word; sName: string; nQuality: Byte; nAttrType: Byte;
    nMinValue, nMaxValue: Integer; nWeight: Integer; nEquipType: Byte;
    nLevelMin, nLevelMax: Integer);
  begin
    New(Affix);
    Affix.nAffixID := nID;
    Affix.sAffixName := sName;
    Affix.nQuality := nQuality;
    Affix.nAttrType := nAttrType;
    Affix.nMinValue := nMinValue;
    Affix.nMaxValue := nMaxValue;
    Affix.nWeight := nWeight;
    Affix.nEquipType := nEquipType;
    Affix.nLevelMin := nLevelMin;
    Affix.nLevelMax := nLevelMax;
    m_AffixList.Add(Affix);
  end;

begin
  if m_boInitialized then Exit;

  Randomize;

  // ===== Common Quality (nQuality=0) =====
  // ID 1-10: 普通词缀
  AddAffix(1,  '微弱的攻击', AFFIX_QUALITY_COMMON, AFFIX_ATTR_DC, 1, 3, 800, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(2,  '微弱的魔法', AFFIX_QUALITY_COMMON, AFFIX_ATTR_MC, 1, 3, 800, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(3,  '微弱的道术', AFFIX_QUALITY_COMMON, AFFIX_ATTR_SC, 1, 3, 800, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(4,  '微弱的防御', AFFIX_QUALITY_COMMON, AFFIX_ATTR_AC, 1, 2, 800, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(5,  '微弱的魔御', AFFIX_QUALITY_COMMON, AFFIX_ATTR_MAC, 1, 2, 800, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(6,  '微弱的生命', AFFIX_QUALITY_COMMON, AFFIX_ATTR_HP, 10, 30, 700, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(7,  '微弱的魔法值', AFFIX_QUALITY_COMMON, AFFIX_ATTR_MP, 10, 30, 700, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(8,  '微弱的准确', AFFIX_QUALITY_COMMON, AFFIX_ATTR_HITPOINT, 1, 2, 600, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(9,  '微弱的敏捷', AFFIX_QUALITY_COMMON, AFFIX_ATTR_SPEEDPOINT, 1, 2, 600, EQUIP_TYPE_ALL, 1, 80);
  AddAffix(10, '微弱的体力恢复', AFFIX_QUALITY_COMMON, AFFIX_ATTR_HEALTHRECOVER, 10, 20, 500, EQUIP_TYPE_ALL, 1, 80);

  // ===== Rare Quality (nQuality=1) =====
  // ID 11-25: 稀有词缀
  AddAffix(11, '锋利的攻击', AFFIX_QUALITY_RARE, AFFIX_ATTR_DC, 3, 7, 600, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(12, '强大的魔法', AFFIX_QUALITY_RARE, AFFIX_ATTR_MC, 3, 7, 600, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(13, '强大的道术', AFFIX_QUALITY_RARE, AFFIX_ATTR_SC, 3, 7, 600, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(14, '坚实的防御', AFFIX_QUALITY_RARE, AFFIX_ATTR_AC, 2, 5, 600, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(15, '坚韧的魔御', AFFIX_QUALITY_RARE, AFFIX_ATTR_MAC, 2, 5, 600, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(16, '充沛的生命', AFFIX_QUALITY_RARE, AFFIX_ATTR_HP, 30, 80, 550, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(17, '充沛的魔法值', AFFIX_QUALITY_RARE, AFFIX_ATTR_MP, 30, 80, 550, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(18, '精准的命中', AFFIX_QUALITY_RARE, AFFIX_ATTR_HITPOINT, 2, 4, 500, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(19, '迅捷的身法', AFFIX_QUALITY_RARE, AFFIX_ATTR_SPEEDPOINT, 2, 4, 500, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(20, '攻击速度', AFFIX_QUALITY_RARE, AFFIX_ATTR_HITSPEED, 1, 3, 400, EQUIP_TYPE_WEAPON, 10, 255);
  AddAffix(21, '幸运之星', AFFIX_QUALITY_RARE, AFFIX_ATTR_LUCK, 1, 1, 300, EQUIP_TYPE_WEAPON, 15, 255);
  AddAffix(22, '伤害加成', AFFIX_QUALITY_RARE, AFFIX_ATTR_ADDATTACK, 1, 3, 400, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(23, '经验加成', AFFIX_QUALITY_RARE, AFFIX_ATTR_EXPRATE, 5, 15, 400, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(24, '魔法恢复', AFFIX_QUALITY_RARE, AFFIX_ATTR_SPELLRECOVER, 10, 30, 400, EQUIP_TYPE_ALL, 10, 255);
  AddAffix(25, '武器强度', AFFIX_QUALITY_RARE, AFFIX_ATTR_STRONG, 1, 3, 350, EQUIP_TYPE_WEAPON, 15, 255);

  // ===== Epic Quality (nQuality=2) =====
  // ID 26-41: 史诗词缀
  AddAffix(26, '毁灭的攻击', AFFIX_QUALITY_EPIC, AFFIX_ATTR_DC, 6, 15, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(27, '毁灭的魔法', AFFIX_QUALITY_EPIC, AFFIX_ATTR_MC, 6, 15, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(28, '毁灭的道术', AFFIX_QUALITY_EPIC, AFFIX_ATTR_SC, 6, 15, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(29, '钢铁的防御', AFFIX_QUALITY_EPIC, AFFIX_ATTR_AC, 4, 10, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(30, '钢铁的魔御', AFFIX_QUALITY_EPIC, AFFIX_ATTR_MAC, 4, 10, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(31, '旺盛的生命', AFFIX_QUALITY_EPIC, AFFIX_ATTR_HP, 60, 180, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(32, '旺盛的魔法值', AFFIX_QUALITY_EPIC, AFFIX_ATTR_MP, 60, 180, 350, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(33, '致命一击', AFFIX_QUALITY_EPIC, AFFIX_ATTR_DEADLINESS, 1, 4, 250, EQUIP_TYPE_WEAPON, 25, 255);
  AddAffix(34, '吸血之力', AFFIX_QUALITY_EPIC, AFFIX_ATTR_VAMPIRE, 1, 3, 250, EQUIP_TYPE_WEAPON, 25, 255);
  AddAffix(35, '加速攻击', AFFIX_QUALITY_EPIC, AFFIX_ATTR_HITSPEED, 2, 5, 250, EQUIP_TYPE_WEAPON, 20, 255);
  AddAffix(36, '伤害吸收', AFFIX_QUALITY_EPIC, AFFIX_ATTR_DELDAMAGE, 1, 4, 280, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(37, '魔法躲避', AFFIX_QUALITY_EPIC, AFFIX_ATTR_ANTIMAGIC, 5, 15, 280, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(38, '五行攻击', AFFIX_QUALITY_EPIC, AFFIX_ATTR_ADDWUXIN, 1, 4, 250, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(39, '五行防御', AFFIX_QUALITY_EPIC, AFFIX_ATTR_DELWUXIN, 1, 4, 250, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(40, '毒物躲避', AFFIX_QUALITY_EPIC, AFFIX_ATTR_POISONMAGIC, 5, 15, 250, EQUIP_TYPE_ALL, 20, 255);
  AddAffix(41, '毒物恢复', AFFIX_QUALITY_EPIC, AFFIX_ATTR_POISONRECOVER, 10, 30, 250, EQUIP_TYPE_ALL, 20, 255);

  // ===== Legendary Quality (nQuality=3) =====
  // ID 42-52: 传说词缀
  AddAffix(42, '神灵的攻击', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_DC, 12, 28, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(43, '神灵的魔法', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_MC, 12, 28, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(44, '神灵的道术', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_SC, 12, 28, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(45, '神圣的防御', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_AC, 8, 18, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(46, '神圣的魔御', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_MAC, 8, 18, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(47, '不灭的生命', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_HP, 100, 350, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(48, '不灭的魔法值', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_MP, 100, 350, 150, EQUIP_TYPE_ALL, 30, 255);
  AddAffix(49, '弑神之力', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_DEADLINESS, 3, 7, 120, EQUIP_TYPE_WEAPON, 35, 255);
  AddAffix(50, '血魔之噬', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_VAMPIRE, 2, 6, 120, EQUIP_TYPE_WEAPON, 35, 255);
  AddAffix(51, '天降鸿运', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_LUCK, 1, 3, 80, EQUIP_TYPE_WEAPON, 35, 255);
  AddAffix(52, '经验如潮', AFFIX_QUALITY_LEGENDARY, AFFIX_ATTR_EXPRATE, 15, 40, 120, EQUIP_TYPE_ALL, 30, 255);

  m_boInitialized := True;
end;

function TAffixEngine.GenerateAffixes(UserItem: pTUserItem; StdItem: pTStdItem; nCount: Integer): TList;
var
  I, J, nEquipType, nItemLevel, nTotalWeight, nRand, nCurWeight: Integer;
  nAffixCount: Integer;
  Affix: pTAffixEntry;
  ItemAffix: pTItemAffix;
  CandidateList: TList;
  SelectedIDs: array of Word;
  boAlreadySelected: Boolean;
begin
  Result := TList.Create;
  if (not m_boInitialized) or (StdItem = nil) or (nCount <= 0) then Exit;

  nEquipType := GetEquipTypeName(StdItem);
  nItemLevel := StdItem.NeedLevel;

  // Build candidate list based on equip type and item level
  CandidateList := TList.Create;
  try
    for I := 0 to m_AffixList.Count - 1 do
    begin
      Affix := pTAffixEntry(m_AffixList.Items[I]);
      if Affix = nil then Continue;

      // Check equip type restriction
      if (Affix.nEquipType <> EQUIP_TYPE_ALL) and (Affix.nEquipType <> nEquipType) then
        Continue;

      // Check level restriction
      if (nItemLevel < Affix.nLevelMin) or (nItemLevel > Affix.nLevelMax) then
        Continue;

      CandidateList.Add(Affix);
    end;

    if CandidateList.Count = 0 then Exit;

    // Determine how many affixes to generate (min of nCount and candidates)
    nAffixCount := nCount;
    if nAffixCount > CandidateList.Count then
      nAffixCount := CandidateList.Count;

    SetLength(SelectedIDs, nAffixCount);
    for I := 0 to nAffixCount - 1 do
      SelectedIDs[I] := 0;

    // Weighted random selection
    for I := 0 to nAffixCount - 1 do
    begin
      // Calculate total weight of remaining candidates
      nTotalWeight := 0;
      for J := 0 to CandidateList.Count - 1 do
      begin
        Affix := pTAffixEntry(CandidateList.Items[J]);
        if Affix = nil then Continue;

        boAlreadySelected := False;
        for nCurWeight := 0 to I - 1 do
        begin
          if SelectedIDs[nCurWeight] = Affix.nAffixID then
          begin
            boAlreadySelected := True;
            Break;
          end;
        end;
        if not boAlreadySelected then
          Inc(nTotalWeight, Affix.nWeight);
      end;

      if nTotalWeight <= 0 then Break;

      nRand := Random(nTotalWeight);
      nCurWeight := 0;
      for J := 0 to CandidateList.Count - 1 do
      begin
        Affix := pTAffixEntry(CandidateList.Items[J]);
        if Affix = nil then Continue;

        boAlreadySelected := False;
        for nCurWeight := 0 to I - 1 do
        begin
          if SelectedIDs[nCurWeight] = Affix.nAffixID then
          begin
            boAlreadySelected := True;
            Break;
          end;
        end;
        if boAlreadySelected then Continue;

        Inc(nCurWeight, Affix.nWeight);
        if nCurWeight > nRand then
        begin
          SelectedIDs[I] := Affix.nAffixID;

          // Create item affix
          New(ItemAffix);
          ItemAffix.nAffixID := Affix.nAffixID;
          ItemAffix.nQuality := Affix.nQuality;
          ItemAffix.nAttrType := Affix.nAttrType;
          ItemAffix.nValue := RandomValue(Affix.nMinValue, Affix.nMaxValue);
          ItemAffix.bLocked := False;
          Result.Add(ItemAffix);
          Break;
        end;
      end;
    end;
  finally
    CandidateList.Free;
  end;
end;

function TAffixEngine.ReforgeAffixes(UserItem: pTUserItem; StdItem: pTStdItem; LockedAffixes: TList): TList;
var
  I, nLockedCount: Integer;
  ItemAffix: pTItemAffix;
  NewAffixes: TList;
begin
  Result := TList.Create;
  if (not m_boInitialized) or (StdItem = nil) then Exit;

  // Count locked affixes
  nLockedCount := 0;
  if LockedAffixes <> nil then
  begin
    for I := 0 to LockedAffixes.Count - 1 do
    begin
      ItemAffix := pTItemAffix(LockedAffixes.Items[I]);
      if (ItemAffix <> nil) and ItemAffix.bLocked then
      begin
        New(ItemAffix);
        ItemAffix.nAffixID := pTItemAffix(LockedAffixes.Items[I]).nAffixID;
        ItemAffix.nQuality := pTItemAffix(LockedAffixes.Items[I]).nQuality;
        ItemAffix.nAttrType := pTItemAffix(LockedAffixes.Items[I]).nAttrType;
        ItemAffix.nValue := pTItemAffix(LockedAffixes.Items[I]).nValue;
        ItemAffix.bLocked := True;
        Result.Add(ItemAffix);
        Inc(nLockedCount);
      end;
    end;
  end;

  // Generate new affixes for the remaining slots
  if nLockedCount < 6 then
  begin
    NewAffixes := GenerateAffixes(UserItem, StdItem, 6 - nLockedCount);
    try
      for I := 0 to NewAffixes.Count - 1 do
      begin
        ItemAffix := pTItemAffix(NewAffixes.Items[I]);
        if ItemAffix <> nil then
        begin
          New(ItemAffix);
          ItemAffix.nAffixID := pTItemAffix(NewAffixes.Items[I]).nAffixID;
          ItemAffix.nQuality := pTItemAffix(NewAffixes.Items[I]).nQuality;
          ItemAffix.nAttrType := pTItemAffix(NewAffixes.Items[I]).nAttrType;
          ItemAffix.nValue := pTItemAffix(NewAffixes.Items[I]).nValue;
          ItemAffix.bLocked := False;
          Result.Add(ItemAffix);
        end;
      end;
    finally
      FreeAffixList(NewAffixes);
    end;
  end;
end;

procedure TAffixEngine.CalcAffixAbility(AffixList: TList; var AddAbility: TAddAbility);
var
  I: Integer;
  ItemAffix: pTItemAffix;
begin
  if AffixList = nil then Exit;

  for I := 0 to AffixList.Count - 1 do
  begin
    ItemAffix := pTItemAffix(AffixList.Items[I]);
    if ItemAffix = nil then Continue;

    case ItemAffix.nAttrType of
      AFFIX_ATTR_DC:
        begin
          Inc(AddAbility.DC, ItemAffix.nValue);
          Inc(AddAbility.DC2, ItemAffix.nValue);
        end;
      AFFIX_ATTR_MC:
        begin
          Inc(AddAbility.MC, ItemAffix.nValue);
          Inc(AddAbility.MC2, ItemAffix.nValue);
        end;
      AFFIX_ATTR_SC:
        begin
          Inc(AddAbility.SC, ItemAffix.nValue);
          Inc(AddAbility.SC2, ItemAffix.nValue);
        end;
      AFFIX_ATTR_AC:
        begin
          Inc(AddAbility.AC, ItemAffix.nValue);
          Inc(AddAbility.AC2, ItemAffix.nValue);
        end;
      AFFIX_ATTR_MAC:
        begin
          Inc(AddAbility.MAC, ItemAffix.nValue);
          Inc(AddAbility.MAC2, ItemAffix.nValue);
        end;
      AFFIX_ATTR_HP:
        Inc(AddAbility.HP, Word(ItemAffix.nValue));
      AFFIX_ATTR_MP:
        Inc(AddAbility.MP, Word(ItemAffix.nValue));
      AFFIX_ATTR_HITSPEED:
        Inc(AddAbility.nHitSpeed, ItemAffix.nValue);
      AFFIX_ATTR_DEADLINESS:
        Inc(AddAbility.btDeadliness, Byte(ItemAffix.nValue));
      AFFIX_ATTR_VAMPIRE:
        // Vampire is handled via HitSpeed or we use a custom byte
        Inc(AddAbility.nHitSpeed, ItemAffix.nValue);
      AFFIX_ATTR_EXPRATE:
        Inc(AddAbility.btExpRate, Byte(ItemAffix.nValue));
      AFFIX_ATTR_ADDATTACK:
        Inc(AddAbility.wAddAttack, Byte(ItemAffix.nValue));
      AFFIX_ATTR_DELDAMAGE:
        Inc(AddAbility.wDelDamage, Byte(ItemAffix.nValue));
      AFFIX_ATTR_LUCK:
        Inc(AddAbility.btLuck, ItemAffix.nValue);
      AFFIX_ATTR_HITPOINT:
        Inc(AddAbility.wHitPoint, Word(ItemAffix.nValue));
      AFFIX_ATTR_SPEEDPOINT:
        Inc(AddAbility.wSpeedPoint, Word(ItemAffix.nValue));
      AFFIX_ATTR_ANTIMAGIC:
        Inc(AddAbility.wAntiMagic, Word(ItemAffix.nValue));
      AFFIX_ATTR_HEALTHRECOVER:
        Inc(AddAbility.wHealthRecover, Word(ItemAffix.nValue));
      AFFIX_ATTR_SPELLRECOVER:
        Inc(AddAbility.wSpellRecover, Word(ItemAffix.nValue));
      AFFIX_ATTR_ADDWUXIN:
        Inc(AddAbility.wAddWuXinAttack, Byte(ItemAffix.nValue));
      AFFIX_ATTR_DELWUXIN:
        Inc(AddAbility.wDelWuXinAttack, Byte(ItemAffix.nValue));
      AFFIX_ATTR_STRONG:
        Inc(AddAbility.btWeaponStrong, Byte(ItemAffix.nValue));
      AFFIX_ATTR_HPMPRATE:
        Inc(AddAbility.btHPorMPRate, Byte(ItemAffix.nValue));
      AFFIX_ATTR_AC2RATE:
        Inc(AddAbility.btAC2Rate, Byte(ItemAffix.nValue));
      AFFIX_ATTR_MAC2RATE:
        Inc(AddAbility.btMAC2Rate, Byte(ItemAffix.nValue));
      AFFIX_ATTR_POISONRECOVER:
        Inc(AddAbility.wPoisonRecover, Word(ItemAffix.nValue));
      AFFIX_ATTR_POISONMAGIC:
        Inc(AddAbility.wAntiPoison, Word(ItemAffix.nValue));
      AFFIX_ATTR_ANTIPOISON:
        Inc(AddAbility.wAntiPoison, Word(ItemAffix.nValue));
    end;
  end;
end;

function TAffixEngine.GetAffixItemName(UserItem: pTUserItem; StdItem: pTStdItem; AffixList: TList): string;
var
  I, nHighestQuality: Integer;
  ItemAffix: pTItemAffix;
  sBaseName: string;
begin
  if StdItem = nil then
  begin
    Result := '';
    Exit;
  end;

  sBaseName := StdItem.Name;
  nHighestQuality := AFFIX_QUALITY_COMMON;

  if AffixList <> nil then
  begin
    for I := 0 to AffixList.Count - 1 do
    begin
      ItemAffix := pTItemAffix(AffixList.Items[I]);
      if ItemAffix <> nil then
      begin
        if ItemAffix.nQuality > nHighestQuality then
          nHighestQuality := ItemAffix.nQuality;
      end;
    end;
  end;

  case nHighestQuality of
    AFFIX_QUALITY_COMMON:
      Result := sBaseName;
    AFFIX_QUALITY_RARE:
      Result := AFFIX_PREFIX_RARE + sBaseName;
    AFFIX_QUALITY_EPIC:
      Result := AFFIX_PREFIX_EPIC + sBaseName;
    AFFIX_QUALITY_LEGENDARY:
      Result := AFFIX_PREFIX_LEGENDARY + sBaseName;
  else
    Result := sBaseName;
  end;
end;

function TAffixEngine.GetAffixQualityColor(nQuality: Byte): Byte;
begin
  case nQuality of
    AFFIX_QUALITY_COMMON:
      Result := AFFIX_COLOR_COMMON;
    AFFIX_QUALITY_RARE:
      Result := AFFIX_COLOR_RARE;
    AFFIX_QUALITY_EPIC:
      Result := AFFIX_COLOR_EPIC;
    AFFIX_QUALITY_LEGENDARY:
      Result := AFFIX_COLOR_LEGENDARY;
  else
    Result := AFFIX_COLOR_COMMON;
  end;
end;

procedure TAffixEngine.LockAffix(Affix: pTItemAffix; boLock: Boolean);
begin
  if Affix <> nil then
    Affix.bLocked := boLock;
end;

function TAffixEngine.GetAffixCountByQuality(AffixList: TList; nQuality: Byte): Integer;
var
  I: Integer;
  ItemAffix: pTItemAffix;
begin
  Result := 0;
  if AffixList = nil then Exit;

  for I := 0 to AffixList.Count - 1 do
  begin
    ItemAffix := pTItemAffix(AffixList.Items[I]);
    if ItemAffix <> nil then
    begin
      if ItemAffix.nQuality = nQuality then
        Inc(Result);
    end;
  end;
end;

procedure TAffixEngine.FreeAffixList(AffixList: TList);
var
  I: Integer;
  ItemAffix: pTItemAffix;
begin
  if AffixList = nil then Exit;
  for I := 0 to AffixList.Count - 1 do
  begin
    ItemAffix := pTItemAffix(AffixList.Items[I]);
    if ItemAffix <> nil then
      Dispose(ItemAffix);
  end;
  AffixList.Free;
end;

procedure TAffixEngine.LoadConfig(sConfigFile: string);
var
  Ini: TIniFile;
  I, nCount, nAffixID, nQuality, nAttrType, nMinValue, nMaxValue, nWeight, nEquipType, nLevelMin, nLevelMax: Integer;
  sSection, sAffixName: string;
  Affix: pTAffixEntry;
  Group: pTAffixGroup;
  sGroupName: string;
  nGroupCount, nMinAffix, nMaxAffix: Integer;
begin
  if not FileExists(sConfigFile) then Exit;

  Ini := TIniFile.Create(sConfigFile);
  try
    // Load custom affix definitions
    nCount := Ini.ReadInteger('AffixConfig', 'Count', 0);
    if nCount > 0 then
    begin
      // Clear existing affixes
      for I := m_AffixList.Count - 1 downto 0 do
      begin
        Affix := pTAffixEntry(m_AffixList.Items[I]);
        if Affix <> nil then Dispose(Affix);
        m_AffixList.Delete(I);
      end;

      for I := 1 to nCount do
      begin
        sSection := 'Affix' + IntToStr(I);
        nAffixID := Ini.ReadInteger(sSection, 'ID', 0);
        sAffixName := Ini.ReadString(sSection, 'Name', '');
        nQuality := Ini.ReadInteger(sSection, 'Quality', 0);
        nAttrType := Ini.ReadInteger(sSection, 'AttrType', 0);
        nMinValue := Ini.ReadInteger(sSection, 'MinValue', 0);
        nMaxValue := Ini.ReadInteger(sSection, 'MaxValue', 0);
        nWeight := Ini.ReadInteger(sSection, 'Weight', 100);
        nEquipType := Ini.ReadInteger(sSection, 'EquipType', 0);
        nLevelMin := Ini.ReadInteger(sSection, 'LevelMin', 0);
        nLevelMax := Ini.ReadInteger(sSection, 'LevelMax', 255);

        if (nAffixID > 0) and (sAffixName <> '') then
        begin
          New(Affix);
          Affix.nAffixID := nAffixID;
          Affix.sAffixName := sAffixName;
          Affix.nQuality := nQuality;
          Affix.nAttrType := nAttrType;
          Affix.nMinValue := nMinValue;
          Affix.nMaxValue := nMaxValue;
          Affix.nWeight := nWeight;
          Affix.nEquipType := nEquipType;
          Affix.nLevelMin := nLevelMin;
          Affix.nLevelMax := nLevelMax;
          m_AffixList.Add(Affix);
        end;
      end;
    end;

    // Load affix groups
    nGroupCount := Ini.ReadInteger('GroupConfig', 'Count', 0);
    if nGroupCount > 0 then
    begin
      // Clear existing groups
      for I := m_AffixGroups.Count - 1 downto 0 do
      begin
        Group := pTAffixGroup(m_AffixGroups.Items[I]);
        if Group <> nil then
        begin
          if Group.AffixList <> nil then
            Group.AffixList.Free;
          Dispose(Group);
        end;
        m_AffixGroups.Delete(I);
      end;

      for I := 1 to nGroupCount do
      begin
        sSection := 'Group' + IntToStr(I);
        sGroupName := Ini.ReadString(sSection, 'Name', '');
        nMinAffix := Ini.ReadInteger(sSection, 'MinAffix', 1);
        nMaxAffix := Ini.ReadInteger(sSection, 'MaxAffix', 3);

        if sGroupName <> '' then
        begin
          New(Group);
          Group.sGroupName := sGroupName;
          Group.nMinAffix := nMinAffix;
          Group.nMaxAffix := nMaxAffix;
          Group.AffixList := TList.Create;
          m_AffixGroups.Add(Group);
        end;
      end;
    end;
  finally
    Ini.Free;
  end;
end;

initialization
  begin
    g_AffixEngine := TAffixEngine.Create;
  end;

finalization
  begin
    if g_AffixEngine <> nil then
    begin
      g_AffixEngine.Free;
      g_AffixEngine := nil;
    end;
  end;

end.