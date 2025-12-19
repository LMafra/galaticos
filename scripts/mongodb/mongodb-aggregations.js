/**
 * MongoDB Aggregation Pipeline Examples
 * 
 * This file contains example aggregation queries that can be used in the
 * Clojure application for generating statistics and reports.
 * 
 * These are reference implementations that demonstrate:
 *   - Player statistics by championship
 *   - Average goals by position
 *   - Player performance evolution over time
 *   - Aggregated stats updates
 *   - Player search with filters
 *   - Championship comparisons
 *   - Top players by metric
 * 
 * Usage: These functions can be called directly in mongosh or adapted
 * for use in the Clojure application using the MongoDB driver.
 */

// ============================================================================
// 1. Estatísticas Agregadas por Jogador (por Campeonato)
// ============================================================================
function playerStatsByChampionship(championshipId) {
  return db.matches.aggregate([
    { $match: { "championship-id": ObjectId(championshipId) } },
    { $unwind: "$player-statistics" },
    {
      $group: {
        _id: "$player-statistics.player-id",
        games: { $sum: 1 },
        goals: { $sum: "$player-statistics.goals" },
        assists: { $sum: "$player-statistics.assists" },
        yellow-cards: { $sum: "$player-statistics.yellow-cards" },
        red-cards: { $sum: "$player-statistics.red-cards" },
        player-name: { $first: "$player-statistics.player-name" },
        position: { $first: "$player-statistics.position" }
      }
    },
    {
      $addFields: {
        "goals-per-game": {
          $cond: [
            { $gt: ["$games", 0] },
            { $divide: ["$goals", "$games"] },
            0
          ]
        },
        "assists-per-game": {
          $cond: [
            { $gt: ["$games", 0] },
            { $divide: ["$assists", "$games"] },
            0
          ]
        }
      }
    },
    { $sort: { goals: -1, assists: -1 } }
  ]);
}

// ============================================================================
// 2. Média de Gols por Posição (Tempo Real)
// ============================================================================
function avgGoalsByPosition(championshipId) {
  return db.matches.aggregate([
    { $match: { "championship-id": ObjectId(championshipId) } },
    { $unwind: "$player-statistics" },
    {
      $group: {
        _id: "$player-statistics.position",
        avg-goals: { $avg: "$player-statistics.goals" },
        total-goals: { $sum: "$player-statistics.goals" },
        total-assists: { $sum: "$player-statistics.assists" },
        player-count: { $sum: 1 },
        games-count: { $addToSet: "$_id" }
      }
    },
    {
      $addFields: {
        unique-games: { $size: "$games-count" }
      }
    },
    {
      $project: {
        position: "$_id",
        avg-goals: 1,
        total-goals: 1,
        total-assists: 1,
        player-count: 1,
        unique-games: 1,
        _id: 0
      }
    },
    { $sort: { avg-goals: -1 } }
  ]);
}

// ============================================================================
// 3. Evolução Temporal de Performance (Jogador)
// ============================================================================
function playerPerformanceEvolution(playerId) {
  return db.matches.aggregate([
    { $unwind: "$player-statistics" },
    { $match: { "player-statistics.player-id": ObjectId(playerId) } },
    {
      $group: {
        _id: {
          year: { $year: "$date" },
          month: { $month: "$date" },
          week: { $week: "$date" }
        },
        games: { $sum: 1 },
        goals: { $sum: "$player-statistics.goals" },
        assists: { $sum: "$player-statistics.assists" },
        yellow-cards: { $sum: "$player-statistics.yellow-cards" },
        red-cards: { $sum: "$player-statistics.red-cards" }
      }
    },
    {
      $addFields: {
        "goals-per-game": {
          $cond: [
            { $gt: ["$games", 0] },
            { $divide: ["$goals", "$games"] },
            0
          ]
        }
      }
    },
    { $sort: { "_id.year": 1, "_id.month": 1, "_id.week": 1 } }
  ]);
}

// ============================================================================
// 4. Atualização de Estatísticas Agregadas em Players
// ============================================================================
function updateAggregatedStats() {
  return db.matches.aggregate([
    { $unwind: "$player-statistics" },
    {
      $group: {
        _id: {
          "player-id": "$player-statistics.player-id",
          "championship-id": "$championship-id"
        },
        games: { $sum: 1 },
        goals: { $sum: "$player-statistics.goals" },
        assists: { $sum: "$player-statistics.assists" }
      }
    },
    {
      $lookup: {
        from: "championships",
        localField: "_id.championship-id",
        foreignField: "_id",
        as: "championship"
      }
    },
    { $unwind: "$championship" },
    {
      $group: {
        _id: "$_id.player-id",
        "by-championship": {
          $push: {
            "championship-id": "$_id.championship-id",
            "championship-name": "$championship.name",
            games: "$games",
            goals: "$goals",
            assists: "$assists"
          }
        },
        total: {
          $push: { games: "$games", goals: "$goals", assists: "$assists" }
        }
      }
    },
    {
      $project: {
        "player-id": "$_id",
        "aggregated-stats": {
          total: {
            games: { $sum: "$total.games" },
            goals: { $sum: "$total.goals" },
            assists: { $sum: "$total.assists" }
          },
          "by-championship": 1
        }
      }
    }
  ]);
}

// ============================================================================
// 5. Busca de Jogadores com Filtros Múltiplos
// ============================================================================
function searchPlayers(filters) {
  var matchStage = { "active": true };
  
  if (filters.position) {
    matchStage.position = filters.position;
  }
  
  if (filters.minGames !== undefined) {
    matchStage["aggregated-stats.total.games"] = { $gte: filters.minGames };
  }
  
  if (filters.minGoals !== undefined) {
    matchStage["aggregated-stats.total.goals"] = { $gte: filters.minGoals };
  }
  
  var pipeline = [
    { $match: matchStage }
  ];
  
  // Calculate age if birth-date exists
  if (filters.minAge !== undefined || filters.maxAge !== undefined) {
    pipeline.push({
      $addFields: {
        age: {
          $cond: [
            { $ne: ["$birth-date", null] },
            {
              $subtract: [
                { $year: new Date() },
                { $year: "$birth-date" }
              ]
            },
            null
          ]
        }
      }
    });
    
    var ageMatch = {};
    if (filters.minAge !== undefined) {
      ageMatch.age = { $gte: filters.minAge };
    }
    if (filters.maxAge !== undefined) {
      if (ageMatch.age) {
        ageMatch.age.$lte = filters.maxAge;
      } else {
        ageMatch.age = { $lte: filters.maxAge };
      }
    }
    if (Object.keys(ageMatch).length > 0) {
      pipeline.push({ $match: ageMatch });
    }
  }
  
  pipeline.push({
    $addFields: {
      "goals-per-game": {
        $cond: [
          { $gt: ["$aggregated-stats.total.games", 0] },
          { $divide: ["$aggregated-stats.total.goals", "$aggregated-stats.total.games"] },
          0
        ]
      },
      "assists-per-game": {
        $cond: [
          { $gt: ["$aggregated-stats.total.games", 0] },
          { $divide: ["$aggregated-stats.total.assists", "$aggregated-stats.total.games"] },
          0
        ]
      }
    }
  });
  
  if (filters.sortBy) {
    var sortObj = {};
    sortObj[filters.sortBy] = filters.sortOrder || -1;
    pipeline.push({ $sort: sortObj });
  } else {
    pipeline.push({ $sort: { "goals-per-game": -1 } });
  }
  
  if (filters.limit) {
    pipeline.push({ $limit: filters.limit });
  }
  
  return db.players.aggregate(pipeline);
}

// ============================================================================
// 6. Comparativo entre Campeonatos
// ============================================================================
function championshipComparison() {
  return db.matches.aggregate([
    {
      $lookup: {
        from: "championships",
        localField: "championship-id",
        foreignField: "_id",
        as: "championship"
      }
    },
    { $unwind: "$championship" },
    { $unwind: "$player-statistics" },
    {
      $group: {
        _id: "$championship-id",
        championship-name: { $first: "$championship.name" },
        championship-format: { $first: "$championship.format" },
        total-matches: { $addToSet: "$_id" },
        total-goals: { $sum: "$player-statistics.goals" },
        total-assists: { $sum: "$player-statistics.assists" },
        unique-players: { $addToSet: "$player-statistics.player-id" }
      }
    },
    {
      $addFields: {
        matches-count: { $size: "$total-matches" },
        players-count: { $size: "$unique-players" },
        "avg-goals-per-match": {
          $cond: [
            { $gt: [{ $size: "$total-matches" }, 0] },
            { $divide: ["$total-goals", { $size: "$total-matches" }] },
            0
          ]
        }
      }
    },
    {
      $project: {
        championship-name: 1,
        championship-format: 1,
        "matches-count": 1,
        "players-count": 1,
        "total-goals": 1,
        "total-assists": 1,
        "avg-goals-per-match": 1,
        _id: 0
      }
    },
    { $sort: { "matches-count": -1 } }
  ]);
}

// ============================================================================
// 7. Top Jogadores por Métrica
// ============================================================================
function topPlayersByMetric(metric, limit, championshipId) {
  var matchStage = { "active": true };
  
  if (championshipId) {
    matchStage["aggregated-stats.by-championship.championship-id"] = ObjectId(championshipId);
  }
  
  var pipeline = [
    { $match: matchStage },
    { $unwind: "$aggregated-stats.by-championship" }
  ];
  
  if (championshipId) {
    pipeline.push({
      $match: {
        "aggregated-stats.by-championship.championship-id": ObjectId(championshipId)
      }
    });
  }
  
  var sortField = championshipId 
    ? `aggregated-stats.by-championship.${metric}`
    : `aggregated-stats.total.${metric}`;
  
  pipeline.push({
    $sort: { [sortField]: -1 }
  });
  
  pipeline.push({ $limit: limit || 10 });
  
  return db.players.aggregate(pipeline);
}

// Example usage:
// playerStatsByChampionship("507f1f77bcf86cd799439011")
// avgGoalsByPosition("507f1f77bcf86cd799439011")
// playerPerformanceEvolution("507f1f77bcf86cd799439012")
// searchPlayers({ position: "Atacante", minGames: 10, sortBy: "goals-per-game", limit: 20 })

