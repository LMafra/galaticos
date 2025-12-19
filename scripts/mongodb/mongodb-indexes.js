/**
 * MongoDB Index Creation Scripts
 * 
 * This script creates all necessary indexes for optimal query performance
 * in the Galáticos MongoDB database.
 * 
 * Usage:
 *   mongosh mongodb://localhost:27017/galaticos --file scripts/mongodb/mongodb-indexes.js
 *   or: mongo mongodb://localhost:27017/galaticos scripts/mongodb/mongodb-indexes.js
 * 
 * Note: This script explicitly switches to the 'galaticos' database to ensure
 * indexes are created in the correct database regardless of how it's called.
 * 
 * Collections indexed:
 *   - championships: name+season (unique), status, date range
 *   - players: name, team+active, position, stats, nickname
 *   - matches: championship+date, date, player statistics
 *   - teams: name (unique)
 *   - admins: username (unique)
 */

// Explicitly switch to the galaticos database
// This ensures indexes are created in the correct database regardless of connection method
db = db.getSiblingDB('galaticos');

print("Using database: " + db.getName());
print("Creating indexes for championships collection...");
db.championships.createIndex({ "name": 1, "season": 1 }, { unique: true });
db.championships.createIndex({ "status": 1 });
db.championships.createIndex({ "start-date": 1, "end-date": 1 });

print("Creating indexes for players collection...");
db.players.createIndex({ "name": 1 });
db.players.createIndex({ "team-id": 1, "active": 1 });
db.players.createIndex({ "position": 1 });
db.players.createIndex({ "aggregated-stats.total.games": -1 });
db.players.createIndex({ "aggregated-stats.by-championship.championship-id": 1 });
db.players.createIndex({ "nickname": 1 }); // For searching by nickname

print("Creating indexes for matches collection...");
db.matches.createIndex({ "championship-id": 1, "date": -1 });
db.matches.createIndex({ "date": -1 });
db.matches.createIndex({ "player-statistics.player-id": 1 });
db.matches.createIndex({ "championship-id": 1, "date": 1 });

print("Creating indexes for teams collection...");
db.teams.createIndex({ "name": 1 }, { unique: true });

print("Creating indexes for admins collection...");
db.admins.createIndex({ "username": 1 }, { unique: true });

print("All indexes created successfully!");

// List all indexes
print("\n=== Indexes Summary ===");
print("\nchampionships indexes:");
db.championships.getIndexes().forEach(function(index) {
  print("  " + JSON.stringify(index.key));
});

print("\nplayers indexes:");
db.players.getIndexes().forEach(function(index) {
  print("  " + JSON.stringify(index.key));
});

print("\nmatches indexes:");
db.matches.getIndexes().forEach(function(index) {
  print("  " + JSON.stringify(index.key));
});

print("\nteams indexes:");
db.teams.getIndexes().forEach(function(index) {
  print("  " + JSON.stringify(index.key));
});

print("\nadmins indexes:");
db.admins.getIndexes().forEach(function(index) {
  print("  " + JSON.stringify(index.key));
});

