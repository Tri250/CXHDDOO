unit MonsterAI;

interface

uses
  Windows, SysUtils, Classes, IniFiles, Grobal2, M2Share, Envir, ObjBase, ObjPlay;

type
  TMonsterAIManager = class
  private
    m_AIConfigs: TList;           // TMonsterAIConfig list
    m_boInitialized: Boolean;
    function GetAIConfig(sMonsterName: string): pTMonsterAIConfig;
    function GetAIConfigByID(nMonsterID: Integer): pTMonsterAIConfig;
    // Behavior tree execution
    function ExecuteNode(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteSelector(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteSequence(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteCondition(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteAction(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteRandom(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    function ExecuteDecorator(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
    // Condition evaluation
    function EvaluateCondition(BaseObject: TBaseObject; sCondition: string; Target: TBaseObject): Boolean;
    // Action execution
    function ExecuteActionString(BaseObject: TBaseObject; sAction: string; Target: TBaseObject): Boolean;
    // Parse condition/action strings
    function ParseConditionParams(sCondition: string; var sCmd: string; var sParam1, sParam2: string): Boolean;
    procedure BuildDefaultBehaviorTree(Config: pTMonsterAIConfig);
  public
    constructor Create();
    destructor Destroy; override;
    procedure Initialize;
    procedure Run(BaseObject: TBaseObject);  // Process monster AI tick

    // Find target
    function FindTarget(BaseObject: TBaseObject): TBaseObject;
    // Execute behavior tree
    function ExecuteAI(BaseObject: TBaseObject; Target: TBaseObject): Boolean;
    // Smart skill selection
    function SelectSkill(BaseObject: TBaseObject; Target: TBaseObject): Integer;
    // Call for help
    procedure CallHelp(BaseObject: TBaseObject; Target: TBaseObject);
    // Check if should flee
    function ShouldFlee(BaseObject: TBaseObject): Boolean;
    // Execute flee behavior
    function ExecuteFlee(BaseObject: TBaseObject): Boolean;
    // Load AI config from file
    procedure LoadConfig(sConfigFile: string);
    // Register AI config for monster
    procedure RegisterMonsterAI(sMonsterName: string; Config: pTMonsterAIConfig);
  end;

var
  g_MonsterAIManager: TMonsterAIManager;

implementation

uses HUtil32;

// ============================================================================
// Helper: Create a new behavior node
// ============================================================================
function CreateBehaviorNode(sName: string; nType: TBehaviorNodeType): pTBehaviorNode;
begin
  New(Result);
  FillChar(Result^, SizeOf(TBehaviorNode), #0);
  Result.sNodeName := sName;
  Result.NodeType := nType;
  Result.Children := TList.Create;
  Result.nWeight := 100;
  Result.nMaxExecCount := -1;
  Result.nCoolDown := 0;
end;

// ============================================================================
// Helper: Free a behavior node tree recursively
// ============================================================================
procedure FreeBehaviorNode(Node: pTBehaviorNode);
var
  I: Integer;
begin
  if Node = nil then Exit;
  if Node.Children <> nil then begin
    for I := 0 to Node.Children.Count - 1 do begin
      FreeBehaviorNode(pTBehaviorNode(Node.Children.Items[I]));
    end;
    Node.Children.Free;
    Node.Children := nil;
  end;
  Dispose(Node);
end;

// ============================================================================
// TMonsterAIManager
// ============================================================================

constructor TMonsterAIManager.Create;
begin
  inherited Create;
  m_AIConfigs := TList.Create;
  m_boInitialized := False;
end;

destructor TMonsterAIManager.Destroy;
var
  I: Integer;
  Config: pTMonsterAIConfig;
begin
  for I := 0 to m_AIConfigs.Count - 1 do begin
    Config := pTMonsterAIConfig(m_AIConfigs.Items[I]);
    if Config <> nil then begin
      if Config.RootNode <> nil then
        FreeBehaviorNode(Config.RootNode);
      if Config.SkillList <> nil then
        Config.SkillList.Free;
      Dispose(Config);
    end;
  end;
  m_AIConfigs.Free;
  inherited Destroy;
end;

// ============================================================================
// GetAIConfig - Find AI config by monster name
// ============================================================================
function TMonsterAIManager.GetAIConfig(sMonsterName: string): pTMonsterAIConfig;
var
  I: Integer;
  Config: pTMonsterAIConfig;
begin
  Result := nil;
  for I := 0 to m_AIConfigs.Count - 1 do begin
    Config := pTMonsterAIConfig(m_AIConfigs.Items[I]);
    if Config <> nil then begin
      if CompareText(Config.sMonsterName, sMonsterName) = 0 then begin
        Result := Config;
        Exit;
      end;
    end;
  end;
end;

// ============================================================================
// GetAIConfigByID - Find AI config by monster ID
// ============================================================================
function TMonsterAIManager.GetAIConfigByID(nMonsterID: Integer): pTMonsterAIConfig;
var
  I: Integer;
  Config: pTMonsterAIConfig;
begin
  Result := nil;
  for I := 0 to m_AIConfigs.Count - 1 do begin
    Config := pTMonsterAIConfig(m_AIConfigs.Items[I]);
    if Config <> nil then begin
      if Config.nMonsterID = nMonsterID then begin
        Result := Config;
        Exit;
      end;
    end;
  end;
end;

// ============================================================================
// RegisterMonsterAI
// ============================================================================
procedure TMonsterAIManager.RegisterMonsterAI(sMonsterName: string; Config: pTMonsterAIConfig);
begin
  if Config = nil then Exit;
  Config.sMonsterName := sMonsterName;
  m_AIConfigs.Add(Config);
end;

// ============================================================================
// ParseConditionParams - Parse a condition/action string into command and params
// Format: "CMD param1 param2"
// ============================================================================
function TMonsterAIManager.ParseConditionParams(sCondition: string; var sCmd: string; var sParam1, sParam2: string): Boolean;
var
  nPos1, nPos2: Integer;
  sTrimmed: string;
begin
  Result := False;
  sCmd := '';
  sParam1 := '';
  sParam2 := '';
  sTrimmed := Trim(sCondition);
  if sTrimmed = '' then Exit;
  nPos1 := Pos(' ', sTrimmed);
  if nPos1 = 0 then begin
    sCmd := UpperCase(sTrimmed);
    Result := True;
    Exit;
  end;
  sCmd := UpperCase(Copy(sTrimmed, 1, nPos1 - 1));
  sTrimmed := Trim(Copy(sTrimmed, nPos1 + 1, Length(sTrimmed)));
  nPos2 := Pos(' ', sTrimmed);
  if nPos2 = 0 then begin
    sParam1 := sTrimmed;
    Result := True;
    Exit;
  end;
  sParam1 := Copy(sTrimmed, 1, nPos2 - 1);
  sParam2 := Trim(Copy(sTrimmed, nPos2 + 1, Length(sTrimmed)));
  Result := True;
end;

// ============================================================================
// EvaluateCondition - Evaluate a condition string against a BaseObject
// ============================================================================
function TMonsterAIManager.EvaluateCondition(BaseObject: TBaseObject; sCondition: string; Target: TBaseObject): Boolean;
var
  sCmd, sParam1, sParam2: string;
  nPercent: Integer;
  nRange: Integer;
  nDist: Integer;
begin
  Result := False;
  if BaseObject = nil then Exit;
  if not ParseConditionParams(sCondition, sCmd, sParam1, sParam2) then Exit;

  if sCmd = 'HP_BELOW_PERCENT' then begin
    nPercent := StrToIntDef(sParam1, 30);
    if BaseObject.m_WAbil.MaxHP > 0 then
      Result := (BaseObject.m_WAbil.HP * 100 div BaseObject.m_WAbil.MaxHP) < nPercent;
  end
  else if sCmd = 'HAS_TARGET' then begin
    Result := (Target <> nil) and (not Target.m_boDeath) and (not Target.m_boGhost);
  end
  else if sCmd = 'TARGET_IN_RANGE' then begin
    nRange := StrToIntDef(sParam1, 3);
    if Target <> nil then begin
      nDist := Abs(BaseObject.m_nCurrX - Target.m_nCurrX) + Abs(BaseObject.m_nCurrY - Target.m_nCurrY);
      Result := nDist <= nRange;
    end;
  end
  else if sCmd = 'CAN_CAST_SKILL' then begin
    Result := False;
    if Target <> nil then begin
      if SelectSkill(BaseObject, Target) > 0 then
        Result := True;
    end;
  end
  else if sCmd = 'IS_BOSS' then begin
    Result := (BaseObject.m_btRaceServer = RC_MONSTER) and (BaseObject.m_btRaceImg >= 80);
  end
  else if sCmd = 'HAS_BUFF' then begin
    Result := False;
    // Check if the monster has any status buffs active
    if BaseObject.m_wStatusTimeArr[STATUS_BUFF_ATKUP] > 0 then Result := True;
    if BaseObject.m_wStatusTimeArr[STATUS_BUFF_DEFUP] > 0 then Result := True;
    if BaseObject.m_wStatusTimeArr[STATUS_BUFF_SPEEDUP] > 0 then Result := True;
    if BaseObject.m_wStatusTimeArr[STATUS_BUFF_SHIELD] > 0 then Result := True;
  end
  else if sCmd = 'IS_ELITE' then begin
    Result := (BaseObject.m_btRaceImg >= 70) and (BaseObject.m_btRaceImg < 80);
  end
  else if sCmd = 'IS_NEAR_SPAWN' then begin
    nRange := StrToIntDef(sParam1, 5);
    Result := (Abs(BaseObject.m_nCurrX - BaseObject.m_nHomeX) <= nRange)
      and (Abs(BaseObject.m_nCurrY - BaseObject.m_nHomeY) <= nRange);
  end;
end;

// ============================================================================
// ExecuteActionString - Execute an action string for a BaseObject
// ============================================================================
function TMonsterAIManager.ExecuteActionString(BaseObject: TBaseObject; sAction: string; Target: TBaseObject): Boolean;
var
  sCmd, sParam1, sParam2: string;
  nSkillID: Integer;
  nDir: Byte;
  nNewX, nNewY: Integer;
  nX, nY: Integer;
begin
  Result := False;
  if BaseObject = nil then Exit;
  if not ParseConditionParams(sAction, sCmd, sParam1, sParam2) then Exit;

  if sCmd = 'ATTACK_TARGET' then begin
    if Target <> nil then begin
      if BaseObject.GetAttackDir(Target, nDir) then begin
        BaseObject.Attack(Target, nDir);
        Result := True;
      end;
    end;
  end
  else if sCmd = 'CAST_SKILL' then begin
    nSkillID := SelectSkill(BaseObject, Target);
    if nSkillID > 0 then begin
      // The monster uses its spell ability
      if Target <> nil then begin
        BaseObject.SendRefMsg(RM_SPELL, nSkillID, Target.m_nCurrX, Target.m_nCurrY, Target.m_nCurrX, Target.m_nCurrY, '');
        Result := True;
      end;
    end;
  end
  else if sCmd = 'MOVE_TO_TARGET' then begin
    if Target <> nil then begin
      nX := Target.m_nCurrX;
      nY := Target.m_nCurrY;
      BaseObject.m_PEnvir.GetNextPosition(nX, nY, BaseObject.m_btDirection, 1, nNewX, nNewY);
      if (nNewX <> nX) or (nNewY <> nY) then begin
        BaseObject.m_nTargetX := nNewX;
        BaseObject.m_nTargetY := nNewY;
        Result := True;
      end;
    end;
  end
  else if sCmd = 'FLEE' then begin
    // Move away from target
    if Target <> nil then begin
      if BaseObject.m_nCurrX < Target.m_nCurrX then
        nDir := DR_LEFT
      else
        nDir := DR_RIGHT;
      if BaseObject.m_nCurrY < Target.m_nCurrY then
        nDir := DR_UP
      else
        nDir := DR_DOWN;
      BaseObject.m_PEnvir.GetNextPosition(BaseObject.m_nCurrX, BaseObject.m_nCurrY, nDir, 1, nNewX, nNewY);
      if BaseObject.m_PEnvir.CanWalk(nNewX, nNewY, True) then begin
        BaseObject.m_nTargetX := nNewX;
        BaseObject.m_nTargetY := nNewY;
        Result := True;
      end;
    end;
  end
  else if sCmd = 'CALL_HELP' then begin
    CallHelp(BaseObject, Target);
    Result := True;
  end
  else if sCmd = 'RETURN_SPAWN' then begin
    BaseObject.m_nTargetX := BaseObject.m_nHomeX;
    BaseObject.m_nTargetY := BaseObject.m_nHomeY;
    Result := True;
  end
  else if sCmd = 'PATROL' then begin
    // Simple patrol: move randomly within a small range
    nX := BaseObject.m_nHomeX + Random(5) - 2;
    nY := BaseObject.m_nHomeY + Random(5) - 2;
    if BaseObject.m_PEnvir.CanWalk(nX, nY, True) then begin
      BaseObject.m_nTargetX := nX;
      BaseObject.m_nTargetY := nY;
      Result := True;
    end;
  end
  else if sCmd = 'TAUNT' then begin
    // Taunt: force target to attack this monster
    if (Target <> nil) and (Target.m_btRaceServer = RC_PLAYOBJECT) then begin
      Target.SetTargetCreat(BaseObject);
      Result := True;
    end;
  end;
end;

// ============================================================================
// ExecuteCondition - Execute condition node
// ============================================================================
function TMonsterAIManager.ExecuteCondition(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
var
  I: Integer;
begin
  Result := False;
  if Node = nil then Exit;
  if Node.sCondition <> '' then begin
    Result := EvaluateCondition(BaseObject, Node.sCondition, Target);
  end;
  // If condition passes, also execute children as a sequence
  if Result and (Node.Children <> nil) and (Node.Children.Count > 0) then begin
    for I := 0 to Node.Children.Count - 1 do begin
      if not ExecuteNode(BaseObject, pTBehaviorNode(Node.Children.Items[I]), Target) then begin
        Result := False;
        Break;
      end;
    end;
  end;
end;

// ============================================================================
// ExecuteAction - Execute action node
// ============================================================================
function TMonsterAIManager.ExecuteAction(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
begin
  Result := False;
  if Node = nil then Exit;
  if Node.sAction <> '' then begin
    Result := ExecuteActionString(BaseObject, Node.sAction, Target);
  end;
end;

// ============================================================================
// ExecuteSelector - Try each child until one succeeds (OR logic)
// ============================================================================
function TMonsterAIManager.ExecuteSelector(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
var
  I: Integer;
  Child: pTBehaviorNode;
begin
  Result := False;
  if (Node = nil) or (Node.Children = nil) then Exit;
  for I := 0 to Node.Children.Count - 1 do begin
    Child := pTBehaviorNode(Node.Children.Items[I]);
    if Child <> nil then begin
      if ExecuteNode(BaseObject, Child, Target) then begin
        Result := True;
        Exit;
      end;
    end;
  end;
end;

// ============================================================================
// ExecuteSequence - Execute all children in order, fail if any fails (AND logic)
// ============================================================================
function TMonsterAIManager.ExecuteSequence(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
var
  I: Integer;
  Child: pTBehaviorNode;
begin
  Result := True;
  if (Node = nil) or (Node.Children = nil) then Exit;
  for I := 0 to Node.Children.Count - 1 do begin
    Child := pTBehaviorNode(Node.Children.Items[I]);
    if Child <> nil then begin
      if not ExecuteNode(BaseObject, Child, Target) then begin
        Result := False;
        Exit;
      end;
    end;
  end;
end;

// ============================================================================
// ExecuteRandom - Randomly select one child based on weight
// ============================================================================
function TMonsterAIManager.ExecuteRandom(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
var
  I, nTotalWeight, nRand, nAccum: Integer;
  Child: pTBehaviorNode;
begin
  Result := False;
  if (Node = nil) or (Node.Children = nil) or (Node.Children.Count = 0) then Exit;

  nTotalWeight := 0;
  for I := 0 to Node.Children.Count - 1 do begin
    Child := pTBehaviorNode(Node.Children.Items[I]);
    if Child <> nil then
      Inc(nTotalWeight, Child.nWeight);
  end;

  if nTotalWeight <= 0 then Exit;

  nRand := Random(nTotalWeight);
  nAccum := 0;
  for I := 0 to Node.Children.Count - 1 do begin
    Child := pTBehaviorNode(Node.Children.Items[I]);
    if Child <> nil then begin
      Inc(nAccum, Child.nWeight);
      if nRand < nAccum then begin
        Result := ExecuteNode(BaseObject, Child, Target);
        Exit;
      end;
    end;
  end;
end;

// ============================================================================
// ExecuteDecorator - Execute with precondition check (invert, cooldown, etc.)
// ============================================================================
function TMonsterAIManager.ExecuteDecorator(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
var
  bConditionPass: Boolean;
  Child: pTBehaviorNode;
begin
  Result := False;
  if Node = nil then Exit;

  // Check condition if specified
  bConditionPass := True;
  if Node.sCondition <> '' then
    bConditionPass := EvaluateCondition(BaseObject, Node.sCondition, Target);

  if not bConditionPass then Exit;

  // Execute child node
  if (Node.Children <> nil) and (Node.Children.Count > 0) then begin
    Child := pTBehaviorNode(Node.Children.Items[0]);
    if Child <> nil then
      Result := ExecuteNode(BaseObject, Child, Target);
  end;
end;

// ============================================================================
// ExecuteNode - Dispatcher: route to correct node type handler
// ============================================================================
function TMonsterAIManager.ExecuteNode(BaseObject: TBaseObject; Node: pTBehaviorNode; Target: TBaseObject): Boolean;
begin
  Result := False;
  if (Node = nil) or (BaseObject = nil) then Exit;
  case Node.NodeType of
    bntSelector:  Result := ExecuteSelector(BaseObject, Node, Target);
    bntSequence:  Result := ExecuteSequence(BaseObject, Node, Target);
    bntCondition: Result := ExecuteCondition(BaseObject, Node, Target);
    bntAction:    Result := ExecuteAction(BaseObject, Node, Target);
    bntRandom:    Result := ExecuteRandom(BaseObject, Node, Target);
    bntDecorator: Result := ExecuteDecorator(BaseObject, Node, Target);
  end;
end;

// ============================================================================
// BuildDefaultBehaviorTree - Create a complete behavior tree for a config
// ============================================================================
procedure TMonsterAIManager.BuildDefaultBehaviorTree(Config: pTMonsterAIConfig);
var
  Root, Selector, Sequence, Node, FleeSeq, CombatSeq, SkillSeq, MoveSeq: pTBehaviorNode;
begin
  if Config = nil then Exit;

  // Root: Selector (try each branch until one succeeds)
  Root := CreateBehaviorNode('Root', bntSelector);
  Config.RootNode := Root;

  // Priority 1: Flee behavior (if HP low)
  FleeSeq := CreateBehaviorNode('FleeSequence', bntSequence);
  Root.Children.Add(FleeSeq);

  Node := CreateBehaviorNode('CheckHP', bntCondition);
  Node.sCondition := 'HP_BELOW_PERCENT 30';
  FleeSeq.Children.Add(Node);

  Node := CreateBehaviorNode('Flee', bntAction);
  Node.sAction := 'FLEE';
  FleeSeq.Children.Add(Node);

  // Priority 2: Combat behavior
  CombatSeq := CreateBehaviorNode('CombatSequence', bntSequence);
  Root.Children.Add(CombatSeq);

  Node := CreateBehaviorNode('CheckHasTarget', bntCondition);
  Node.sCondition := 'HAS_TARGET';
  CombatSeq.Children.Add(Node);

  // Skill usage sub-tree
  SkillSeq := CreateBehaviorNode('SkillSequence', bntSequence);
  CombatSeq.Children.Add(SkillSeq);

  Node := CreateBehaviorNode('CheckCanCast', bntCondition);
  Node.sCondition := 'CAN_CAST_SKILL';
  SkillSeq.Children.Add(Node);

  Node := CreateBehaviorNode('CastSkill', bntAction);
  Node.sAction := 'CAST_SKILL';
  SkillSeq.Children.Add(Node);

  // Move to target
  MoveSeq := CreateBehaviorNode('MoveSequence', bntSequence);
  CombatSeq.Children.Add(MoveSeq);

  Node := CreateBehaviorNode('CheckRange', bntCondition);
  Node.sCondition := 'TARGET_IN_RANGE 1';
  MoveSeq.Children.Add(Node);

  Node := CreateBehaviorNode('AttackTarget', bntAction);
  Node.sAction := 'ATTACK_TARGET';
  MoveSeq.Children.Add(Node);

  // Priority 3: Find target
  Node := CreateBehaviorNode('FindTarget', bntAction);
  Node.sAction := 'MOVE_TO_TARGET';
  Root.Children.Add(Node);

  // Priority 4: Patrol / Return to spawn
  Node := CreateBehaviorNode('ReturnToSpawn', bntAction);
  Node.sAction := 'RETURN_SPAWN';
  Root.Children.Add(Node);
end;

// ============================================================================
// Initialize - Create default AI configs for common monster types
// ============================================================================
procedure TMonsterAIManager.Initialize;
var
  Config: pTMonsterAIConfig;
begin
  if m_boInitialized then Exit;
  m_boInitialized := True;

  // Normal monster AI
  New(Config);
  FillChar(Config^, SizeOf(TMonsterAIConfig), #0);
  Config.sMonsterName := '__DEFAULT_NORMAL';
  Config.nAggroRange := 5;
  Config.nChaseRange := 15;
  Config.nReturnRange := 20;
  Config.boSmartSkill := False;
  Config.boCallHelp := False;
  Config.boFleeLowHP := False;
  Config.nFleeHPPercent := 0;
  Config.SkillList := TList.Create;
  BuildDefaultBehaviorTree(Config);
  m_AIConfigs.Add(Config);

  // Elite monster AI
  New(Config);
  FillChar(Config^, SizeOf(TMonsterAIConfig), #0);
  Config.sMonsterName := '__DEFAULT_ELITE';
  Config.nAggroRange := 7;
  Config.nChaseRange := 20;
  Config.nReturnRange := 25;
  Config.boSmartSkill := True;
  Config.boCallHelp := True;
  Config.boFleeLowHP := True;
  Config.nFleeHPPercent := 20;
  Config.SkillList := TList.Create;
  BuildDefaultBehaviorTree(Config);
  m_AIConfigs.Add(Config);

  // Boss monster AI
  New(Config);
  FillChar(Config^, SizeOf(TMonsterAIConfig), #0);
  Config.sMonsterName := '__DEFAULT_BOSS';
  Config.nAggroRange := 10;
  Config.nChaseRange := 30;
  Config.nReturnRange := 40;
  Config.boSmartSkill := True;
  Config.boCallHelp := True;
  Config.boFleeLowHP := True;
  Config.nFleeHPPercent := 10;
  Config.SkillList := TList.Create;
  BuildDefaultBehaviorTree(Config);
  m_AIConfigs.Add(Config);

  // Ranged monster AI
  New(Config);
  FillChar(Config^, SizeOf(TMonsterAIConfig), #0);
  Config.sMonsterName := '__DEFAULT_RANGED';
  Config.nAggroRange := 8;
  Config.nChaseRange := 12;
  Config.nReturnRange := 20;
  Config.boSmartSkill := True;
  Config.boCallHelp := False;
  Config.boFleeLowHP := True;
  Config.nFleeHPPercent := 15;
  Config.SkillList := TList.Create;
  BuildDefaultBehaviorTree(Config);
  m_AIConfigs.Add(Config);
end;

// ============================================================================
// FindTarget - Find the nearest enemy within aggro range
// ============================================================================
function TMonsterAIManager.FindTarget(BaseObject: TBaseObject): TBaseObject;
var
  ObjList: TList;
  I, nDist, nMinDist, nAggroRange: Integer;
  Config: pTMonsterAIConfig;
  BaseObj: TBaseObject;
begin
  Result := nil;
  if BaseObject = nil then Exit;
  if BaseObject.m_boDeath or BaseObject.m_boGhost then Exit;
  if BaseObject.m_PEnvir = nil then Exit;

  nAggroRange := 5;
  Config := GetAIConfig(BaseObject.m_sCharName);
  if Config = nil then
    Config := GetAIConfig('__DEFAULT_NORMAL');
  if Config <> nil then
    nAggroRange := Config.nAggroRange;

  ObjList := TList.Create;
  try
    BaseObject.m_PEnvir.GetRangeBaseObject(BaseObject.m_nCurrX, BaseObject.m_nCurrY,
      nAggroRange, True, ObjList);

    nMinDist := 9999;
    for I := 0 to ObjList.Count - 1 do begin
      BaseObj := TBaseObject(ObjList.Items[I]);
      if BaseObj = nil then Continue;
      if BaseObj = BaseObject then Continue;
      if BaseObj.m_boDeath or BaseObj.m_boGhost then Continue;
      if not BaseObj.m_boMapApoise then Continue;
      // Only target players
      if BaseObj.m_btRaceServer <> RC_PLAYOBJECT then Continue;
      nDist := Abs(BaseObject.m_nCurrX - BaseObj.m_nCurrX) + Abs(BaseObject.m_nCurrY - BaseObj.m_nCurrY);
      if nDist < nMinDist then begin
        nMinDist := nDist;
        Result := BaseObj;
      end;
    end;
  finally
    ObjList.Free;
  end;
end;

// ============================================================================
// SelectSkill - Select best skill based on range, cooldown, HP
// ============================================================================
function TMonsterAIManager.SelectSkill(BaseObject: TBaseObject; Target: TBaseObject): Integer;
var
  Config: pTMonsterAIConfig;
  I, nDist, nSkillID, nBestSkill, nBestPriority, nPriority: Integer;
begin
  Result := 0;
  if (BaseObject = nil) or (Target = nil) then Exit;

  Config := GetAIConfig(BaseObject.m_sCharName);
  if Config = nil then begin
    Config := GetAIConfig('__DEFAULT_NORMAL');
  end;
  if Config = nil then Exit;
  if Config.SkillList = nil then Exit;
  if Config.SkillList.Count = 0 then Exit;

  nDist := Abs(BaseObject.m_nCurrX - Target.m_nCurrX) + Abs(BaseObject.m_nCurrY - Target.m_nCurrY);
  nBestSkill := 0;
  nBestPriority := -1;

  for I := 0 to Config.SkillList.Count - 1 do begin
    nSkillID := Integer(Config.SkillList.Items[I]);
    if nSkillID <= 0 then Continue;
    nPriority := 0;

    // Ranged skills get higher priority when far
    if nDist > 3 then
      Inc(nPriority, 10);
    // Melee skills get higher priority when close
    if nDist <= 1 then
      Inc(nPriority, 5);

    // Boss-specific priorities
    if BaseObject.m_btRaceImg >= 80 then begin
      // Boss uses high-damage skills more often
      case nSkillID of
        SKILL_FIREBALL, SKILL_FIREBOOM, SKILL_LIGHTENING: Inc(nPriority, 3);
        SKILL_FIREWIND, SKILL_SNOWWIND, SKILL_GROUPLIGHTENING: Inc(nPriority, 5);
      end;
    end;

    if nPriority > nBestPriority then begin
      nBestPriority := nPriority;
      nBestSkill := nSkillID;
    end;
  end;

  Result := nBestSkill;
end;

// ============================================================================
// CallHelp - Find nearby allies and alert them
// ============================================================================
procedure TMonsterAIManager.CallHelp(BaseObject: TBaseObject; Target: TBaseObject);
var
  ObjList: TList;
  I: Integer;
  BaseObj: TBaseObject;
  nHelpRange: Integer;
begin
  if (BaseObject = nil) or (Target = nil) then Exit;
  if BaseObject.m_PEnvir = nil then Exit;

  nHelpRange := 10;
  ObjList := TList.Create;
  try
    BaseObject.m_PEnvir.GetRangeBaseObject(BaseObject.m_nCurrX, BaseObject.m_nCurrY,
      nHelpRange, True, ObjList);

    for I := 0 to ObjList.Count - 1 do begin
      BaseObj := TBaseObject(ObjList.Items[I]);
      if BaseObj = nil then Continue;
      if BaseObj = BaseObject then Continue;
      if BaseObj.m_boDeath or BaseObj.m_boGhost then Continue;
      // Only alert other monsters
      if BaseObj.m_btRaceServer < RC_ANIMAL then Continue;
      // Only alert monsters that don't already have a target
      if BaseObj.m_TargetCret = nil then begin
        BaseObj.m_TargetCret := Target;
        BaseObj.m_boCrazyMode := True;
      end;
    end;
  finally
    ObjList.Free;
  end;
end;

// ============================================================================
// ShouldFlee - Check if monster should flee based on HP
// ============================================================================
function TMonsterAIManager.ShouldFlee(BaseObject: TBaseObject): Boolean;
var
  Config: pTMonsterAIConfig;
  nHPPercent: Integer;
begin
  Result := False;
  if BaseObject = nil then Exit;
  if BaseObject.m_boDeath or BaseObject.m_boGhost then Exit;

  Config := GetAIConfig(BaseObject.m_sCharName);
  if Config = nil then begin
    Config := GetAIConfig('__DEFAULT_NORMAL');
  end;
  if Config = nil then Exit;

  if not Config.boFleeLowHP then Exit;
  if Config.nFleeHPPercent <= 0 then Exit;
  if BaseObject.m_WAbil.MaxHP <= 0 then Exit;

  nHPPercent := BaseObject.m_WAbil.HP * 100 div BaseObject.m_WAbil.MaxHP;
  Result := nHPPercent < Config.nFleeHPPercent;
end;

// ============================================================================
// ExecuteFlee - Execute flee behavior
// ============================================================================
function TMonsterAIManager.ExecuteFlee(BaseObject: TBaseObject): Boolean;
var
  nNewX, nNewY: Integer;
  nDir: Byte;
begin
  Result := False;
  if BaseObject = nil then Exit;
  if BaseObject.m_boDeath or BaseObject.m_boGhost then Exit;
  if BaseObject.m_PEnvir = nil then Exit;

  nDir := Byte(Random(8));
  BaseObject.m_PEnvir.GetNextPosition(BaseObject.m_nCurrX, BaseObject.m_nCurrY,
    nDir, 2, nNewX, nNewY);
  if (nNewX <> BaseObject.m_nCurrX) or (nNewY <> BaseObject.m_nCurrY) then begin
    if BaseObject.m_PEnvir.CanWalk(nNewX, nNewY, True) then begin
      BaseObject.m_nTargetX := nNewX;
      BaseObject.m_nTargetY := nNewY;
      BaseObject.m_boRunAwayMode := True;
      BaseObject.m_dwRunAwayStart := GetTickCount();
      BaseObject.m_dwRunAwayTime := 5000 + Random(3000);
      Result := True;
    end;
  end;
end;

// ============================================================================
// ExecuteAI - Execute behavior tree for a monster
// ============================================================================
function TMonsterAIManager.ExecuteAI(BaseObject: TBaseObject; Target: TBaseObject): Boolean;
var
  Config: pTMonsterAIConfig;
begin
  Result := False;
  if BaseObject = nil then Exit;
  if BaseObject.m_boDeath or BaseObject.m_boGhost then Exit;

  Config := GetAIConfig(BaseObject.m_sCharName);
  if Config = nil then begin
    Config := GetAIConfig('__DEFAULT_NORMAL');
  end;
  if Config = nil then Exit;
  if Config.RootNode = nil then Exit;

  Result := ExecuteNode(BaseObject, Config.RootNode, Target);
end;

// ============================================================================
// Run - Process monster AI tick
// ============================================================================
procedure TMonsterAIManager.Run(BaseObject: TBaseObject);
var
  Target: TBaseObject;
  Config: pTMonsterAIConfig;
  nDist: Integer;
begin
  if BaseObject = nil then Exit;
  if BaseObject.m_boDeath or BaseObject.m_boGhost then Exit;
  if not BaseObject.m_boMapApoise then Exit;
  if BaseObject.m_btRaceServer < RC_ANIMAL then Exit;

  // Initialize if not done
  if not m_boInitialized then Initialize;

  // Get AI config
  Config := GetAIConfig(BaseObject.m_sCharName);
  if Config = nil then begin
    Config := GetAIConfig('__DEFAULT_NORMAL');
  end;

  // Check if should flee
  if ShouldFlee(BaseObject) then begin
    if ExecuteFlee(BaseObject) then Exit;
  end;

  // Check current target
  Target := BaseObject.m_TargetCret;
  if Target <> nil then begin
    if Target.m_boDeath or Target.m_boGhost
      or (Target.m_PEnvir <> BaseObject.m_PEnvir) then begin
      BaseObject.m_TargetCret := nil;
      Target := nil;
    end;
  end;

  // Find target if none
  if Target = nil then begin
    Target := FindTarget(BaseObject);
    if Target <> nil then begin
      BaseObject.m_TargetCret := Target;
    end;
  end;

  // Check chase range - give up if too far
  if (Target <> nil) and (Config <> nil) then begin
    nDist := Abs(BaseObject.m_nCurrX - Target.m_nCurrX) + Abs(BaseObject.m_nCurrY - Target.m_nCurrY);
    if nDist > Config.nChaseRange then begin
      BaseObject.m_TargetCret := nil;
      Target := nil;
    end;
  end;

  // Execute AI
  if Target <> nil then begin
    ExecuteAI(BaseObject, Target);
  end
  else begin
    // If no target, return to spawn point
    if (Config <> nil) and (Config.RootNode <> nil) then begin
      ExecuteNode(BaseObject, Config.RootNode, nil);
    end;
  end;
end;

// ============================================================================
// LoadConfig - Load AI config from file
// ============================================================================
procedure TMonsterAIManager.LoadConfig(sConfigFile: string);
var
  IniFile: TIniFile;
  Sections: TStringList;
  I, J: Integer;
  sSection: string;
  Config: pTMonsterAIConfig;
  nSkillCount: Integer;
  sSkillStr: string;
  sSkillList: TStringList;
  nSkillID: Integer;
  sFileName: string;
begin
  sFileName := sConfigFile;
  if not FileExists(sFileName) then
    sFileName := g_Config.sEnvirDir + sConfigFile;
  if not FileExists(sFileName) then Exit;

  IniFile := TIniFile.Create(sFileName);
  Sections := TStringList.Create;
  try
    IniFile.ReadSections(Sections);
    for I := 0 to Sections.Count - 1 do begin
      sSection := Sections.Strings[I];
      if CompareText(Copy(sSection, 1, 2), 'AI') = 0 then begin
        New(Config);
        FillChar(Config^, SizeOf(TMonsterAIConfig), #0);
        Config.sMonsterName := IniFile.ReadString(sSection, 'MonsterName', '');
        Config.nMonsterID := IniFile.ReadInteger(sSection, 'MonsterID', 0);
        Config.nAggroRange := IniFile.ReadInteger(sSection, 'AggroRange', 5);
        Config.nChaseRange := IniFile.ReadInteger(sSection, 'ChaseRange', 15);
        Config.nReturnRange := IniFile.ReadInteger(sSection, 'ReturnRange', 20);
        Config.boSmartSkill := IniFile.ReadBool(sSection, 'SmartSkill', False);
        Config.boCallHelp := IniFile.ReadBool(sSection, 'CallHelp', False);
        Config.boFleeLowHP := IniFile.ReadBool(sSection, 'FleeLowHP', False);
        Config.nFleeHPPercent := IniFile.ReadInteger(sSection, 'FleeHPPercent', 0);
        Config.sOnDeathScript := IniFile.ReadString(sSection, 'OnDeathScript', '');
        Config.sOnSpawnScript := IniFile.ReadString(sSection, 'OnSpawnScript', '');
        Config.SkillList := TList.Create;

        // Load skill list
        sSkillStr := IniFile.ReadString(sSection, 'Skills', '');
        if sSkillStr <> '' then begin
          sSkillList := TStringList.Create;
          try
            ExtractStrings([','], [], PChar(sSkillStr), sSkillList);
            for J := 0 to sSkillList.Count - 1 do begin
              nSkillID := StrToIntDef(Trim(sSkillList.Strings[J]), 0);
              if nSkillID > 0 then
                Config.SkillList.Add(Pointer(nSkillID));
            end;
          finally
            sSkillList.Free;
          end;
        end;

        BuildDefaultBehaviorTree(Config);
        m_AIConfigs.Add(Config);
      end;
    end;
  finally
    Sections.Free;
    IniFile.Free;
  end;
end;

end.