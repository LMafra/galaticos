// MongoDB index creation — runs only on first container init (empty /data/db).
// For existing databases (e.g. production), apply the same definitions with:
//   MONGO_URI=... DB_NAME=galaticos ./bin/galaticos db:setup
// Keep this file in sync with scripts/mongodb/mongodb-indexes.js (source of truth).
//
// MongoDB will execute all .js files in /docker-entrypoint-initdb.d/

// Switch to the database (MONGO_INITDB_DATABASE is set in docker-compose)
db = db.getSiblingDB('galaticos');

print("Initializing MongoDB database: galaticos");
print("Creating indexes...\n");

// Championships indexes
print("Creating indexes for championships collection...");
db.championships.createIndex({ "name": 1, "season": 1 }, { unique: true });
db.championships.createIndex({ "status": 1 });
db.championships.createIndex({ "start-date": 1, "end-date": 1 });

// Players indexes
print("Creating indexes for players collection...");
db.players.createIndex({ "name": 1 });
db.players.createIndex({ "team-id": 1, "active": 1 });
db.players.createIndex({ "position": 1 });
db.players.createIndex({ "aggregated-stats.total.games": -1 });
db.players.createIndex({ "aggregated-stats.by-championship.championship-id": 1 });
db.players.createIndex({ "nickname": 1 });

// Matches indexes
print("Creating indexes for matches collection...");
db.matches.createIndex({ "championship-id": 1, "date": -1 });
db.matches.createIndex({ "date": -1 });
db.matches.createIndex({ "player-statistics.player-id": 1 });
db.matches.createIndex({ "championship-id": 1, "date": 1 });

// Teams indexes
print("Creating indexes for teams collection...");
db.teams.createIndex({ "name": 1 }, { unique: true });

// Admins indexes
print("Creating indexes for admins collection...");
db.admins.createIndex({ "username": 1 }, { unique: true });

print("\nAll indexes created successfully!");

// List all indexes for verification
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

print("\nDatabase initialization complete!");

