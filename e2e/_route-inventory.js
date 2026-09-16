/**
 * Route inventory for Galáticos E2E coverage (@routes).
 * Source of truth mirrors src/galaticos/routes/* and src-cljs/galaticos/routes.cljs.
 */

/** @typedef {{ method: string, path: string, auth: boolean, success?: number|number[], kind?: 'json'|'health'|'csv'|'login' }} ApiRoute */

/** Static API routes (no path params). Parametric GETs are resolved at runtime. */
const API_PUBLIC_GETS = [
  { method: 'GET', path: '/health', auth: false, success: 200, kind: 'health' },
  { method: 'GET', path: '/api/auth/check', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/players', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/players/duplicates', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/championships', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/matches', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/aggregations/stats', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/aggregations/players/search', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/aggregations/championships/comparison', auth: false, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/aggregations/players/top', auth: false, success: 200, kind: 'json' },
];

/** Parametric GET templates; `resolve(ids)` returns concrete path. */
const API_PUBLIC_PARAM_GETS = [
  {
    name: 'GET /api/players/:id',
    auth: false,
    resolve: (ids) => `/api/players/${ids.playerId}`,
  },
  {
    name: 'GET /api/players/:id/detail',
    auth: false,
    resolve: (ids) => `/api/players/${ids.playerId}/detail`,
  },
  {
    name: 'GET /api/championships/:id',
    auth: false,
    resolve: (ids) => `/api/championships/${ids.championshipId}`,
  },
  {
    name: 'GET /api/championships/:id/players',
    auth: false,
    resolve: (ids) => `/api/championships/${ids.championshipId}/players`,
  },
  {
    name: 'GET /api/championships/:id/seasons',
    auth: false,
    resolve: (ids) => `/api/championships/${ids.championshipId}/seasons`,
  },
  {
    name: 'GET /api/seasons/:id',
    auth: false,
    resolve: (ids) => `/api/seasons/${ids.seasonId}`,
  },
  {
    name: 'GET /api/seasons/:id/players',
    auth: false,
    resolve: (ids) => `/api/seasons/${ids.seasonId}/players`,
  },
  {
    name: 'GET /api/matches/:id',
    auth: false,
    resolve: (ids) => `/api/matches/${ids.matchId}`,
  },
  {
    name: 'GET /api/aggregations/players/stats/:championship-id',
    auth: false,
    resolve: (ids) => `/api/aggregations/players/stats/${ids.championshipId}`,
  },
  {
    name: 'GET /api/aggregations/championships/:id/tab-stats',
    auth: false,
    resolve: (ids) => `/api/aggregations/championships/${ids.championshipId}/tab-stats`,
  },
  {
    name: 'GET /api/aggregations/championships/:id/leaderboards',
    auth: false,
    resolve: (ids) => `/api/aggregations/championships/${ids.championshipId}/leaderboards`,
  },
  {
    name: 'GET /api/aggregations/positions/:championship-id',
    auth: false,
    resolve: (ids) => `/api/aggregations/positions/${ids.championshipId}`,
  },
  {
    name: 'GET /api/aggregations/players/:player-id/evolution',
    auth: false,
    resolve: (ids) => `/api/aggregations/players/${ids.playerId}/evolution`,
  },
];

const API_AUTH_GETS = [
  { method: 'GET', path: '/api/teams', auth: true, success: 200, kind: 'json' },
  {
    name: 'GET /api/teams/:id',
    auth: true,
    resolve: (ids) => `/api/teams/${ids.teamId}`,
  },
  {
    name: 'GET /api/players/:id/merge-candidates',
    auth: true,
    resolve: (ids) => `/api/players/${ids.playerId}/merge-candidates`,
  },
  {
    name: 'GET /api/aggregations/players/:player-id/insights',
    auth: true,
    resolve: (ids) => `/api/aggregations/players/${ids.playerId}/insights`,
  },
  { method: 'GET', path: '/api/aggregations/player-stats-jobs', auth: true, success: 200, kind: 'json' },
  { method: 'GET', path: '/api/exports/dashboard.csv', auth: true, success: 200, kind: 'csv' },
  {
    name: 'GET /api/exports/championships/:id.csv',
    auth: true,
    kind: 'csv',
    resolve: (ids) => `/api/exports/championships/${ids.championshipId}.csv`,
  },
];

/**
 * SPA routes (hash path without #). heading may be string or string[].
 * needsIds: keys required from seed list IDs.
 */
const SPA_STATIC_ROUTES = [
  { path: '/', heading: 'Dashboard', auth: false },
  { path: '/login', heading: ['Login - Galáticos', 'Dashboard'], auth: false },
  { path: '/dashboard', heading: 'Dashboard', auth: false },
  { path: '/stats', heading: 'Estatísticas', auth: false },
  { path: '/players', heading: 'Jogadores', auth: false },
  { path: '/players/new', heading: 'Novo Jogador', auth: true },
  { path: '/matches', heading: 'Partidas', auth: false },
  { path: '/matches/new', heading: 'Nova Partida', auth: true },
  { path: '/championships', heading: 'Campeonatos', auth: false },
  { path: '/championships/new', heading: 'Novo Campeonato', auth: true },
  { path: '/teams', heading: 'Times', auth: true },
  { path: '/teams/new', heading: 'Novo Time', auth: true },
  { path: '/ui-lab', heading: 'UI Lab', auth: false },
];

const SPA_PARAM_ROUTES = [
  {
    name: 'player-detail',
    resolve: (ids) => `/players/${ids.playerId}`,
    headingFrom: 'playerName',
    auth: false,
  },
  {
    name: 'player-edit',
    resolve: (ids) => `/players/${ids.playerId}/edit`,
    heading: 'Editar Jogador',
    auth: true,
  },
  {
    name: 'match-detail',
    resolve: (ids) => `/matches/${ids.matchId}`,
    headingFrom: 'matchHeading',
    auth: false,
  },
  {
    name: 'match-edit',
    resolve: (ids) => `/matches/${ids.matchId}/edit`,
    heading: 'Editar Partida',
    auth: true,
  },
  {
    name: 'matches-by-championship',
    resolve: (ids) => `/matches/championship/${ids.championshipId}`,
    headingFrom: 'championshipName',
    auth: false,
  },
  {
    name: 'match-new-in-championship',
    resolve: (ids) => `/matches/by-championship/${ids.championshipId}/new`,
    heading: 'Nova Partida',
    auth: true,
  },
  {
    name: 'championship-detail',
    resolve: (ids) => `/championships/${ids.championshipId}`,
    headingFrom: 'championshipName',
    auth: false,
  },
  {
    name: 'championship-edit',
    resolve: (ids) => `/championships/${ids.championshipId}/edit`,
    heading: 'Editar Campeonato',
    auth: true,
  },
  {
    name: 'championship-season-detail',
    resolve: (ids) => `/championships/${ids.championshipId}/seasons/${ids.seasonId}`,
    // Season page h2 may be "Name · 2026" or "· 2026" when championship-name is absent.
    heading: ['Temporada', 'Detalhes', 'Inscritos'],
    auth: false,
  },
  {
    name: 'team-detail',
    resolve: (ids) => `/teams/${ids.teamId}`,
    headingFrom: 'teamName',
    auth: true,
  },
  {
    name: 'team-edit',
    resolve: (ids) => `/teams/${ids.teamId}/edit`,
    heading: 'Editar Time',
    auth: true,
  },
];

const SPA_PT_STATIC = [
  { path: '/entrar', heading: ['Login - Galáticos', 'Dashboard'] },
  { path: '/painel', heading: 'Dashboard' },
  { path: '/estatisticas', heading: 'Estatísticas' },
  { path: '/jogadores', heading: 'Jogadores' },
  { path: '/jogadores/novo', heading: 'Novo Jogador' },
  { path: '/partidas', heading: 'Partidas' },
  { path: '/partidas/nova', heading: 'Nova Partida' },
  { path: '/campeonatos', heading: 'Campeonatos' },
  { path: '/campeonatos/novo', heading: 'Novo Campeonato' },
  { path: '/times/novo', heading: 'Novo Time' },
];

const SPA_PT_PARAM = [
  {
    name: 'pt-player-detail',
    resolve: (ids) => `/jogadores/${ids.playerId}`,
    headingFrom: 'playerName',
  },
  {
    name: 'pt-player-edit',
    resolve: (ids) => `/jogadores/${ids.playerId}/editar`,
    heading: 'Editar Jogador',
  },
  {
    name: 'pt-match-detail',
    resolve: (ids) => `/partidas/${ids.matchId}`,
    headingFrom: 'matchHeading',
  },
  {
    name: 'pt-match-edit',
    resolve: (ids) => `/partidas/${ids.matchId}/editar`,
    heading: 'Editar Partida',
  },
  {
    name: 'pt-matches-by-championship',
    resolve: (ids) => `/partidas/campeonato/${ids.championshipId}`,
    headingFrom: 'championshipName',
  },
  {
    name: 'pt-match-new-in-championship',
    resolve: (ids) => `/partidas/campeonato/${ids.championshipId}/nova`,
    heading: 'Nova Partida',
  },
  {
    name: 'pt-championship-detail',
    resolve: (ids) => `/campeonatos/${ids.championshipId}`,
    headingFrom: 'championshipName',
  },
  {
    name: 'pt-championship-edit',
    resolve: (ids) => `/campeonatos/${ids.championshipId}/editar`,
    heading: 'Editar Campeonato',
  },
  {
    name: 'pt-championship-season-detail',
    resolve: (ids) => `/campeonatos/${ids.championshipId}/temporadas/${ids.seasonId}`,
    heading: ['Temporada', 'Detalhes', 'Inscritos'],
  },
  {
    name: 'pt-team-edit',
    resolve: (ids) => `/times/${ids.teamId}/editar`,
    heading: 'Editar Time',
  },
];

module.exports = {
  API_PUBLIC_GETS,
  API_PUBLIC_PARAM_GETS,
  API_AUTH_GETS,
  SPA_STATIC_ROUTES,
  SPA_PARAM_ROUTES,
  SPA_PT_STATIC,
  SPA_PT_PARAM,
};
