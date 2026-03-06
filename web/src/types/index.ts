// ==================== Firestore Data Types ====================

export interface MatchDocument {
  id: string;
  team1Name: string;
  team2Name: string;
  jokerPlayerName?: string;
  team1CaptainName?: string;
  team2CaptainName?: string;
  firstInningsRuns: number;
  firstInningsWickets: number;
  secondInningsRuns: number;
  secondInningsWickets: number;
  winnerTeam: string;
  winningMargin: string;
  matchDate: number;
  groupId?: string;
  groupName?: string;
  shortPitch: boolean;
  playerOfTheMatchId?: string;
  playerOfTheMatchName?: string;
  playerOfTheMatchTeam?: string;
  playerOfTheMatchImpact?: number;
  playerOfTheMatchSummary?: string;
  matchSettingsJson?: string;
  ownerId: string;
  updatedAt: number;
  createdAt: number;
}

export interface PlayerStatDocument {
  playerId: string;
  name: string;
  team: string;
  role: string; // "BAT" or "BOWL"
  runs: number;
  ballsFaced: number;
  dots: number;
  singles: number;
  twos: number;
  threes: number;
  fours: number;
  sixes: number;
  wickets: number;
  runsConceded: number;
  oversBowled: number;
  maidenOvers: number;
  isOut: boolean;
  isRetired: boolean;
  isJoker: boolean;
  catches: number;
  runOuts: number;
  stumpings: number;
  dismissalType?: string;
  bowlerName?: string;
  fielderName?: string;
  battingPosition: number;
  bowlingPosition: number;
}

export interface PartnershipDocument {
  batsman1Name: string;
  batsman2Name: string;
  runs: number;
  balls: number;
  batsman1Runs: number;
  batsman2Runs: number;
  isActive: boolean;
  index: number;
  innings?: number; // 1 or 2 (may be absent for older data)
  partnershipNumber?: number;
}

export interface FallOfWicketDocument {
  batsmanName: string;
  runs: number;
  overs: number;
  wicketNumber: number;
  dismissalType?: string;
  bowlerName?: string;
  fielderName?: string;
}

export interface DeliveryDocument {
  inning: number;
  over: number;
  ballInOver: number;
  outcome: string;
  highlight: boolean;
  strikerName: string;
  nonStrikerName: string;
  bowlerName: string;
  runs: number;
}

export interface PlayerImpactDocument {
  id: string;
  name: string;
  team: string;
  impact: number;
  summary: string;
  isJoker: boolean;
  runs: number;
  balls: number;
  fours: number;
  sixes: number;
  wickets: number;
  runsConceded: number;
  oversBowled: number;
}

export interface GroupDocument {
  id: string;
  name: string;
  inviteCode?: string;
  memberIds: string[];
  memberDeviceIds: string[];
  ownerId: string;
  updatedAt: number;
  defaults?: {
    groundName: string;
    format: string;
    shortPitch: boolean;
    matchSettingsJson?: string;
  };
}

export interface PlayerDocument {
  id: string;
  name: string;
  isJoker: boolean;
  ownerId: string;
  updatedAt: number;
}

export interface SharedMatchDocument {
  code: string;
  ownerId: string;
  matchId: string;
  ownerName: string;
  createdAt: number;
  expiresAt: number;
  viewCount: number;
  isActive: boolean;
}

export interface InProgressMatchDocument {
  matchId: string;
  team1Name: string;
  team2Name: string;
  jokerName: string;
  groupId?: string;
  groupName?: string;
  tossWinner?: string;
  tossChoice?: string;
  matchSettingsJson: string;
  team1PlayerIds: string;
  team2PlayerIds: string;
  team1PlayerNames: string;
  team2PlayerNames: string;
  currentInnings: number;
  currentOver: number;
  ballsInOver: number;
  totalWickets: number;
  team1PlayersJson: string;
  team2PlayersJson: string;
  strikerIndex?: number;
  nonStrikerIndex?: number;
  bowlerIndex?: number;
  firstInningsRuns: number;
  firstInningsWickets: number;
  firstInningsOvers: number;
  firstInningsBalls: number;
  totalExtras: number;
  calculatedTotalRuns: number;
  completedBattersInnings1Json?: string;
  completedBattersInnings2Json?: string;
  completedBowlersInnings1Json?: string;
  completedBowlersInnings2Json?: string;
  firstInningsBattingPlayersJson?: string;
  firstInningsBowlingPlayersJson?: string;
  jokerOutInCurrentInnings: boolean;
  jokerBallsBowledInnings1: number;
  jokerBallsBowledInnings2: number;
  powerplayRunsInnings1: number;
  powerplayRunsInnings2: number;
  powerplayDoublingDoneInnings1: boolean;
  powerplayDoublingDoneInnings2: boolean;
  allDeliveriesJson?: string;
  lastSavedAt: number;
  startedAt: number;
  ownerId: string;
}

// ==================== Derived / Computed Types ====================

export interface MatchPerformance {
  matchId: string;
  matchDate: number;
  opposingTeam: string;
  myTeam: string;
  runs: number;
  ballsFaced: number;
  fours: number;
  sixes: number;
  isOut: boolean;
  wickets: number;
  runsConceded: number;
  ballsBowled: number;
  catches: number;
  runOuts: number;
  stumpings: number;
  isWinner: boolean;
  isJoker: boolean;
  groupId?: string;
  isShortPitch: boolean;
  matchTotalOvers: number;
  maidenOvers: number;
}

export interface PlayerDetailedStats {
  playerId: string;
  playerName: string;
  totalRuns: number;
  totalBallsFaced: number;
  totalBallsBowled: number;
  totalWickets: number;
  totalCatches: number;
  totalRunOuts: number;
  totalStumpings: number;
  matchPerformances: MatchPerformance[];
  matchesPlayed: number;
}

export interface FullMatch {
  match: MatchDocument;
  stats: PlayerStatDocument[];
  partnerships: PartnershipDocument[];
  fallOfWickets: FallOfWicketDocument[];
  deliveries: DeliveryDocument[];
  impacts: PlayerImpactDocument[];
}

export interface LivePlayerState {
  id: string;
  name: string;
  runs: number;
  ballsFaced: number;
  dots: number;
  singles: number;
  twos: number;
  threes: number;
  fours: number;
  sixes: number;
  isOut: boolean;
  isRetired: boolean;
  wickets: number;
  runsConceded: number;
  ballsBowled: number;
  maidenOvers: number;
  isJoker: boolean;
  catches: number;
  runOuts: number;
  stumpings: number;
  dismissalType?: string;
  bowlerName?: string;
  fielderName?: string;
}

export interface MatchSettings {
  totalOvers: number;
  playersPerTeam: number;
  oversPerBowler: number;
  wideReball: boolean;
  noBallReball: boolean;
  noBallRuns: number;
  wideRuns: number;
  powerPlayOvers: number;
  powerPlayDoubling: boolean;
}
