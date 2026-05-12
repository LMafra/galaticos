#!/usr/bin/env python3
"""
MongoDB Seed Script for Galáticos
Reads data from Excel .xlsm and seeds MongoDB.

Default seed path (no legacy flags):
- Players: Excel sheet "Base de dados" (summary one-row-per-player, or long format
  atleta+campeonato per row). Extra names only in the canonical BASE_DADOS source.
- Championships and per-championship table stats: canonical BASE_DADOS — first the
  Excel sheet "Base de dados" if it has long-format rows (atleta + campeonato), else
  data/BASE_DADOS.csv.
- Other Excel championship sheets and other data/*.csv files are not used by the default
  seed (ignored unless legacy import flags are set).

Optional legacy import: --import-excel-championships and --import-data-csv re-enable
the old behavior (Excel abas de campeonato + CSV row-layout imports).

Championship names from BASE_DADOS use canonical_championship_name(): a trailing
' (qualifier)' is removed so e.g. 'ASTCU (campo)' is stored as 'ASTCU' (one document).

Env DEFAULT_SEASON (default 2025) is used for championships from BASE_DADOS and,
when legacy CSV import is on, as the single stored season for those CSVs.

Env EXCEL_FILE (or CLI --excel) overrides the default Excel path (default data/galaticos.xlsm).

Env GALATICOS_ENV=production (or prod): when set, --reset is refused unless
ALLOW_DESTRUCTIVE_SEED is 1/true/yes (reduces accidental wipe against production).

If the database contains the smoke/E2E dataset (galaticos.tasks.seed-smoke), official seed
without --reset is refused so test data is not mixed with Excel data. Use --reset to replace
everything, or --ignore-smoke-dataset to force (not recommended).
"""

import argparse
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    import pandas as pd
    import openpyxl
    import bcrypt
    from bson import ObjectId
    from pymongo import MongoClient
    from pymongo.errors import DuplicateKeyError, ServerSelectionTimeoutError
except ImportError as e:
    print(f"Error: Missing required dependency: {e}", file=sys.stderr)
    print("Please install dependencies: pip install -r requirements.txt", file=sys.stderr)
    sys.exit(1)


# MongoDB connection settings
# Allow MONGO_URI to be overridden via environment variable
import os
MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017")
DB_NAME = os.getenv("DB_NAME", "galaticos")

# Excel file path (relative to project root)
EXCEL_FILE = "data/galaticos.xlsm"

# Consolidated per-player, per-championship stats (not the same layout as row-based championship CSVs)
BASE_DADOS_FILENAME = "BASE_DADOS.csv"

# Default season for BASE_DADOS and for CSV rows without an explicit year in the label
DEFAULT_SEASON = os.getenv("DEFAULT_SEASON", "2025")

# Championship sheet names to skip (summary/aggregate sheets)
SKIP_SHEETS = ["Base de dados", "PLACAS"]

# New collections for enriched data
NEW_COLLECTIONS = ["standings", "records"]

# CSV files under data/ that are not processed as row-layout championship exports
SKIP_CSV_FILENAMES = frozenset({"galaticos.csv", "base_dados.csv"})

# Fingerprints of galaticos.tasks.seed-smoke — keep in sync with src/galaticos/tasks/seed_smoke.clj
SMOKE_PLAYER_NAME = "Smoke Player"
SMOKE_CHAMPIONSHIP_NAME = "Smoke Championship"
SMOKE_MATCH_OPPONENT = "Smoke Opponent"

# Position mapping (if needed)
POSITION_OPTIONS = ["Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"]


def _galaticos_env_is_production() -> bool:
    return os.getenv("GALATICOS_ENV", "").strip().lower() in ("production", "prod")


def _destructive_seed_explicitly_allowed() -> bool:
    return os.getenv("ALLOW_DESTRUCTIVE_SEED", "").strip().lower() in ("1", "true", "yes")


def database_contains_smoke_dataset(db) -> bool:
    """
    True if the DB still has E2E/smoke seed data. Official Excel seed without --reset
    would merge with it; refuse unless the user resets or explicitly overrides.
    """
    if db.players.find_one({"name": SMOKE_PLAYER_NAME}, {"_id": 1}):
        return True
    if db.championships.find_one({"name": SMOKE_CHAMPIONSHIP_NAME}, {"_id": 1}):
        return True
    if db.matches.find_one({"opponent": SMOKE_MATCH_OPPONENT}, {"_id": 1}):
        return True
    return False


def infer_position_from_name(name: str, explicit_position: Optional[str] = None) -> Optional[str]:
    """
    Infer player position from name, respecting explicit position precedence.

    Business rule (Q-05 / BRM-16):
    - If an explicit position is provided and non-empty, it always wins.
    - Only when position is missing/empty and name contains 'GK' (case-insensitive),
      we infer position as 'Goleiro'.
    """
    if explicit_position:
        # Explicit position always prevails
        return explicit_position

    if not name:
        return None

    if "GK" in str(name).upper():
        return "Goleiro"

    return None


def safe_int(value, default=0):
    """
    Safely convert a value to int, handling NaN and None values.
    """
    if pd.isna(value) or value is None:
        return default
    try:
        return int(float(value))
    except (ValueError, TypeError):
        return default

# Championship format mapping
CHAMPIONSHIP_FORMATS = {
    "society": "society-7",
    "fut7": "society-7",
    "futsal": "futsal",
    "campo": "campo-11",
    "copa": "society-7",
    "arena": "society-7",
}


def normalize_player_name(name: str) -> str:
    """Normalize player name for matching"""
    if pd.isna(name) or not name:
        return ""
    name = str(name).strip()
    # Remove extra spaces
    name = re.sub(r'\s+', ' ', name)
    return name


def infer_championship_format(name: str) -> str:
    """Infer championship format from name"""
    name_lower = name.lower()
    for key, format_type in CHAMPIONSHIP_FORMATS.items():
        if key in name_lower:
            return format_type
    return "society-7"  # default


def get_mongo_client() -> MongoClient:
    """
    Create MongoDB client with connection testing
    
    Returns:
        MongoClient instance
        
    Raises:
        SystemExit: If connection fails
    """
    try:
        client = MongoClient(MONGO_URI, serverSelectionTimeoutMS=5000)
        # Test connection
        client.admin.command('ping')
        print(f"✓ Connected to MongoDB at {MONGO_URI}")
        return client
    except ServerSelectionTimeoutError:
        print(f"✗ Failed to connect to MongoDB: Connection timeout", file=sys.stderr)
        print(f"  Make sure MongoDB is running at {MONGO_URI}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"✗ Failed to connect to MongoDB: {e}", file=sys.stderr)
        sys.exit(1)


def create_team(db, team_name: str = "Galáticos") -> ObjectId:
    """Create or get team"""
    teams_collection = db.teams
    
    # Check if team exists
    team = teams_collection.find_one({"name": team_name})
    if team:
        print(f"✓ Team '{team_name}' already exists")
        return team["_id"]
    
    # Create team
    now = datetime.now(timezone.utc)
    team_doc = {
        "name": team_name,
        "active-player-ids": [],
        "created-at": now,
        "updated-at": now
    }
    
    result = teams_collection.insert_one(team_doc)
    print(f"✓ Created team '{team_name}' with ID: {result.inserted_id}")
    return result.inserted_id


def create_admin(db, username: str = "admin", password: str = "admin") -> ObjectId:
    """Create or get admin user"""
    admins_collection = db.admins
    
    # Check if admin exists
    admin = admins_collection.find_one({"username": username})
    if admin:
        # Verify hash format is valid (starts with $2a$, $2b$, or $2y$)
        password_hash = admin.get("password-hash", "")
        needs_rehash = (
            not password_hash
            or not password_hash.startswith(("$2a$", "$2b$", "$2y$"))
            # Force rewrite of 2b/2y to 2a for maximum compatibility with jBCrypt 0.4
            or password_hash.startswith(("$2b$", "$2y$"))
        )
        if needs_rehash:
            print(f"⚠ Admin '{username}' exists but hash is incompatible. Updating...")
            # Recreate hash with correct format
            password_bytes = password.encode('utf-8')
            # Force prefix 2a for maximum compatibility with jBCrypt
            salt = bcrypt.gensalt(prefix=b"2a")
            new_password_hash = bcrypt.hashpw(password_bytes, salt).decode('utf-8')
            admins_collection.update_one(
                {"username": username},
                {"$set": {"password-hash": new_password_hash}}
            )
            print(f"✓ Updated password hash for admin '{username}' (2a)")
        else:
            print(f"✓ Admin '{username}' already exists with compatible hash")
        return admin["_id"]
    
    # Hash password using bcrypt
    password_bytes = password.encode('utf-8')
    # Force prefix 2a for maximum compatibility with jBCrypt (server side)
    salt = bcrypt.gensalt(prefix=b"2a")
    password_hash = bcrypt.hashpw(password_bytes, salt).decode('utf-8')
    
    # Verify hash format (should start with $2a$, $2b$, or $2y$)
    if not password_hash.startswith(("$2a$", "$2b$", "$2y$")):
        raise ValueError(f"Invalid bcrypt hash format: {password_hash[:10]}...")
    
    # Create admin
    now = datetime.now(timezone.utc)
    admin_doc = {
        "username": username,
        "password-hash": password_hash,
        "created-at": now
    }
    
    try:
        result = admins_collection.insert_one(admin_doc)
        print(f"✓ Created admin '{username}' with ID: {result.inserted_id}")
        return result.inserted_id
    except DuplicateKeyError:
        # Admin was created between check and insert
        admin = admins_collection.find_one({"username": username})
        if admin:
            print(f"✓ Admin '{username}' already exists")
            return admin["_id"]
        raise


def create_players(db, players_df: pd.DataFrame, team_id: ObjectId) -> Dict[str, ObjectId]:
    """
    Create players from DataFrame
    Returns dict mapping player name -> player ObjectId
    """
    players_collection = db.players
    player_map = {}
    now = datetime.now(timezone.utc)
    
    # Get existing players (name -> full document) for conflict resolution
    existing_players = {
        p["name"]: p for p in players_collection.find({}, {"name": 1, "position": 1, "team-id": 1, "aggregated-stats": 1})
    }
    
    for _, row in players_df.iterrows():
        player_name = normalize_player_name(row.get("Atleta", ""))
        if not player_name or player_name in player_map:
            continue
        
        # Prepare base stats from sheet
        sheet_stats = {
            "games": safe_int(row.get("Jogos", 0)),
            "goals": safe_int(row.get("Gols", 0)),
            "assists": safe_int(row.get("Assistências", 0)),
            "titles": safe_int(row.get("Títulos", 0)),
        }

        # Infer position from sheet (if column exists) + GK in name, with precedence
        explicit_position_raw = row.get("Posição") if "Posição" in players_df.columns else None
        explicit_position = (
            str(explicit_position_raw).strip()
            if explicit_position_raw and not pd.isna(explicit_position_raw)
            else None
        )
        inferred_position = infer_position_from_name(player_name, explicit_position)

        existing = existing_players.get(player_name)

        if existing:
            # Policy: latest sheet wins for stats; preserve structural fields
            existing_stats = existing.get("aggregated-stats", {})
            existing_total = existing_stats.get(
                "total",
                {"games": 0, "goals": 0, "assists": 0, "titles": 0},
            )

            # Build new total stats from sheet (overwriting prior totals)
            new_total = {
                "games": sheet_stats["games"],
                "goals": sheet_stats["goals"],
                "assists": sheet_stats["assists"],
                "titles": sheet_stats["titles"],
            }

            updated_stats = {
                "total": new_total,
                # Keep by-championship as-is; detailed per-champ data is handled later
                "by-championship": existing_stats.get("by-championship", []),
            }

            players_collection.update_one(
                {"_id": existing["_id"]},
                {
                    "$set": {
                        # Respect explicit position if present; otherwise keep existing / infer
                        "position": inferred_position or existing.get("position", "Atacante"),
                        "aggregated-stats": updated_stats,
                        "updated-at": now,
                    }
                },
            )

            player_map[player_name] = existing["_id"]
            print(f"  ✓ Updated existing player from sheet (latest wins): {player_name}")
            continue

        # Create new player
        player_doc = {
            "name": player_name,
            "nickname": None,
            "position": inferred_position or "Atacante",  # Default position, can be updated later
            "team-id": team_id,
            "active": True,
            "aggregated-stats": {
                "total": sheet_stats,
                "by-championship": [],
            },
            "created-at": now,
            "updated-at": now,
        }

        try:
            result = players_collection.insert_one(player_doc)
            player_map[player_name] = result.inserted_id
            print(f"  ✓ Created player: {player_name}")
        except Exception as e:
            print(f"  ✗ Error creating player {player_name}: {e}")
    
    print(f"\n✓ Processed {len(player_map)} players")
    return player_map


def create_players_from_long_base_dados_sheet(
    db,
    players_df: pd.DataFrame,
    df_norm: pd.DataFrame,
    team_id: ObjectId,
) -> Dict[str, ObjectId]:
    """
    One row per (atleta, campeonato): create each distinct atleta once with zero totals.
    Does not overwrite stats for players that already exist (Step 3.7 fills aggregates).
    """
    players_collection = db.players
    player_map: Dict[str, ObjectId] = {}
    now = datetime.now(timezone.utc)
    existing_players = {
        p["name"]: p
        for p in players_collection.find(
            {}, {"name": 1, "position": 1, "team-id": 1, "aggregated-stats": 1}
        )
    }

    ordered_unique: List[str] = []
    seen = set()
    for _, row in df_norm.iterrows():
        n = normalize_player_name(row.get("atleta", ""))
        if not n:
            continue
        if n.lower() == "atleta" and safe_int(row.get("jogos", 0), 0) == 0:
            continue
        if n not in seen:
            seen.add(n)
            ordered_unique.append(n)

    created = 0
    for player_name in ordered_unique:
        existing = existing_players.get(player_name)
        if existing:
            player_map[player_name] = existing["_id"]
            continue

        pos_explicit = None
        for idx, row in df_norm.iterrows():
            if normalize_player_name(row.get("atleta", "")) != player_name:
                continue
            if idx in players_df.index and "Posição" in players_df.columns:
                raw = players_df.loc[idx, "Posição"]
                if raw is not None and not pd.isna(raw):
                    s = str(raw).strip()
                    pos_explicit = s if s else None
            break

        inferred = infer_position_from_name(player_name, pos_explicit)
        player_doc = {
            "name": player_name,
            "nickname": None,
            "position": inferred or "Atacante",
            "team-id": team_id,
            "active": True,
            "aggregated-stats": {
                "total": {"games": 0, "goals": 0, "assists": 0, "titles": 0},
                "by-championship": [],
            },
            "created-at": now,
            "updated-at": now,
        }
        result = players_collection.insert_one(player_doc)
        player_map[player_name] = result.inserted_id
        existing_players[player_name] = {"_id": result.inserted_id}
        created += 1
        print(f"  ✓ Created player (long-format sheet): {player_name}")

    print(f"\n✓ Long-format sheet: {len(player_map)} player(s) in map ({created} newly created)")
    return player_map


def find_player_by_name(player_map: Dict[str, ObjectId], name: str) -> Optional[ObjectId]:
    """Find player ID by name (with fuzzy matching)"""
    normalized_name = normalize_player_name(name)
    
    # Exact match
    if normalized_name in player_map:
        return player_map[normalized_name]
    
    # Try partial matches (e.g., "Leal" matches "Gabriel Leal")
    for player_name, player_id in player_map.items():
        if normalized_name.lower() in player_name.lower() or player_name.lower() in normalized_name.lower():
            return player_id
    
    # Try last name match
    name_parts = normalized_name.split()
    if len(name_parts) > 0:
        last_name = name_parts[-1]
        for player_name, player_id in player_map.items():
            if last_name.lower() in player_name.lower():
                return player_id
    
    return None


def create_championship(
    db,
    name: str,
    season: str = "2025",
    titles_count: int = 0,
    *,
    status: str = "active",
) -> ObjectId:
    """Create or get championship. Excel seed uses status=active; data/*.csv and BASE use completed."""
    championships_collection = db.championships

    championship = championships_collection.find_one({
        "name": name,
        "season": season
    })

    if championship:
        return championship["_id"]

    now = datetime.now(timezone.utc)
    format_type = infer_championship_format(name)

    championship_doc = {
        "name": name,
        "season": season,
        "format": format_type,
        "start-date": datetime(2025, 1, 1),
        "end-date": datetime(2025, 12, 31),
        "status": status,
        "titles-count": titles_count,
        "created-at": now,
        "updated-at": now
    }
    if status == "completed":
        championship_doc["finished-at"] = now

    result = championships_collection.insert_one(championship_doc)
    print(f"  ✓ Created championship: {name} ({format_type}) [{status}]")
    return result.inserted_id


def mark_championship_completed_from_data_import(db, championship_id: ObjectId) -> None:
    """Mark championship as completed (historical data from data/*.csv or BASE_DADOS)."""
    now = datetime.now(timezone.utc)
    db.championships.update_one(
        {"_id": championship_id},
        {
            "$set": {
                "status": "completed",
                "finished-at": now,
                "updated-at": now,
            }
        },
    )


def canonical_championship_name(raw: str) -> str:
    """
    Single stored/display name for one tournament: strip one trailing qualifier
    in parentheses so 'ASTCU (campo)' and 'ASTCU' map to the same championship.
    """
    s = str(raw).strip()
    if not s:
        return s
    s = re.sub(r"\s*\([^)]*\)\s*$", "", s).strip()
    return s or str(raw).strip()


def normalize_championship_key(label: str) -> str:
    """
    Normalize championship labels for matching BASE Time column to CSV stems.
    Trailing '(...)' is removed first so 'ASTCU (campo)' matches stem 'astcu'.
    """
    if not label or str(label).strip().lower() in ("", "nan", "time"):
        return ""
    s = canonical_championship_name(str(label)).lower()
    s = s.replace("_", " ")
    s = re.sub(r"\s+", " ", s).strip()
    s = re.sub(r"[^a-z0-9áàâãéêíóôõúçüñ ]+", "", s, flags=re.IGNORECASE)
    s = s.replace(" ", "")
    return s


def process_championship_sheet(
    db,
    sheet_name: str,
    sheet_df: pd.DataFrame,
    player_map: Dict[str, ObjectId],
    season: str = "2025"
) -> Tuple[ObjectId, int]:
    """
    Process a championship sheet and update player statistics
    Returns (championship_id, players_processed_count)
    """
    players_collection = db.players
    players_processed = 0
    
    # Determine column names (handle variations)
    player_col = None
    games_col = None
    goals_col = None
    assists_col = None
    titles_col = None
    
    for col in sheet_df.columns:
        col_lower = str(col).lower()
        if "atleta" in col_lower and player_col is None:
            player_col = col
        elif "jogo" in col_lower and games_col is None:
            games_col = col
        elif "gol" in col_lower and goals_col is None:
            goals_col = col
        elif "assist" in col_lower and assists_col is None:
            assists_col = col
        elif "título" in col_lower and titles_col is None:
            titles_col = col
    
    # Calculate max titles from the titles column
    titles_count = 0
    if titles_col:
        titles_values = sheet_df[titles_col].dropna()
        if not titles_values.empty:
            # Convert to numeric, handling any non-numeric values
            numeric_values = pd.to_numeric(titles_values, errors='coerce').dropna()
            if not numeric_values.empty:
                titles_count = int(numeric_values.max())
    
    # Create championship with titles_count
    championship_id = create_championship(db, sheet_name, season, titles_count)
    
    if not player_col:
        print(f"  ⚠ Warning: Could not find player column in sheet '{sheet_name}'")
        return championship_id, 0
    
    # Process each player in the championship
    for _, row in sheet_df.iterrows():
        player_name = normalize_player_name(row.get(player_col, ""))
        if not player_name:
            continue
        
        player_id = find_player_by_name(player_map, player_name)
        if not player_id:
            print(f"  ⚠ Warning: Player '{player_name}' not found in player database")
            continue
        
        # Get statistics
        games = safe_int(row.get(games_col, 0)) if games_col else 0
        goals = safe_int(row.get(goals_col, 0)) if goals_col else 0
        assists = safe_int(row.get(assists_col, 0)) if assists_col else 0
        titles = safe_int(row.get(titles_col, 0)) if titles_col else 0
        
        # Update player's aggregated stats for this championship
        player = players_collection.find_one({"_id": player_id})
        if not player:
            continue
        
        aggregated_stats = player.get("aggregated-stats", {
            "total": {"games": 0, "goals": 0, "assists": 0, "titles": 0},
            "by-championship": []
        })
        
        # Update or add championship stats
        by_championship = aggregated_stats.get("by-championship", [])
        champ_stats = None
        for stats in by_championship:
            if stats.get("championship-id") == championship_id:
                champ_stats = stats
                break
        
        if champ_stats:
            champ_stats.update({
                "championship-name": sheet_name,
                "games": games,
                "goals": goals,
                "assists": assists,
                "titles": titles
            })
        else:
            by_championship.append({
                "championship-id": championship_id,
                "championship-name": sheet_name,
                "games": games,
                "goals": goals,
                "assists": assists,
                "titles": titles
            })
        
        aggregated_stats["by-championship"] = by_championship
        
        # Update total stats (sum all championships)
        total_games = sum(s.get("games", 0) for s in by_championship)
        total_goals = sum(s.get("goals", 0) for s in by_championship)
        total_assists = sum(s.get("assists", 0) for s in by_championship)
        total_titles = sum(s.get("titles", 0) for s in by_championship)
        
        aggregated_stats["total"] = {
            "games": total_games,
            "goals": total_goals,
            "assists": total_assists,
            "titles": total_titles
        }
        
        # Update player document
        players_collection.update_one(
            {"_id": player_id},
            {
                "$set": {
                    "aggregated-stats": aggregated_stats,
                    "updated-at": datetime.now(timezone.utc)
                }
            }
        )
        
        # Enroll player in championship (so UI shows "Inscrições" correctly)
        db.championships.update_one(
            {"_id": championship_id},
            {
                "$addToSet": {"enrolled-player-ids": player_id},
                "$set": {"updated-at": datetime.now(timezone.utc)},
            },
        )
        
        players_processed += 1
    
    return championship_id, players_processed


def parse_championship_label(label: str, fallback_name: str) -> Tuple[str, str]:
    """
    Parse a free-form championship label into (name, season).

    Examples:
        "AABR Society 2020" -> ("AABR Society", "2020")
        "Copa Cerrado 2021" -> ("Copa Cerrado", "2021")
    """
    if not label:
        return fallback_name, "2025"

    text = str(label).strip()
    # Find a 4-digit year starting with 20
    m = re.search(r"(20\d{2})", text)
    if m:
        season = m.group(1)
        name = text.replace(season, "").strip(" -")
        if not name:
            name = fallback_name
    else:
        season = "2025"
        name = text or fallback_name

    return name, season


def placeholder_match_date_for_season(season: str) -> datetime:
    """Placeholder match date 01/01/XXXX (XXXX = season year) in local calendars worldwide.

    Stored as 12:00 UTC on that day so JSON/JS (UTC midnight would shift to prior local
    evening, e.g. 12/31/2017 in US for 2018-01-01T00:00:00Z).
    """
    try:
        year = int(str(season).strip())
    except (ValueError, TypeError):
        try:
            year = int(str(DEFAULT_SEASON).strip())
        except (ValueError, TypeError):
            year = 2025
    return datetime(year, 1, 1, 12, 0, 0, tzinfo=timezone.utc)


def normalize_text_basic(value: str) -> str:
    """
    Basic text normalization: strip, collapse spaces, lowercase.
    (We intentionally keep accents here; player matching already does fuzzy logic.)
    """
    if not value:
        return ""
    text = str(value).strip()
    text = re.sub(r"\s+", " ", text)
    return text


def parse_score(text: str) -> Optional[Tuple[int, int]]:
    """
    Parse a score like '5 X 1' or '4 x 3' into (our_score, opponent_score).
    """
    if not text:
        return None
    # Match patterns like "5 X 1" or "4 x 3"
    m = re.search(r"(\d+)\s*[xX]\s*(\d+)", str(text))
    if not m:
        return None
    return int(m.group(1)), int(m.group(2))


def parse_goals_string(goals_text: str) -> List[Tuple[str, int]]:
    """
    Parse a goals description string into (player_name, goals) tuples.

    Examples:
        "PelÈ (2), Markin, Leal e Queiroz"
        -> [("PelÈ", 2), ("Markin", 1), ("Leal", 1), ("Queiroz", 1)]
    """
    if not goals_text:
        return []

    text = str(goals_text)
    # Replace common connectors with commas to simplify splitting
    text = text.replace(" e ", ",")
    # Normalize multiple commas/spaces
    parts = [p.strip() for p in text.split(",") if p and p.strip()]

    results: List[Tuple[str, int]] = []
    for part in parts:
        # Match patterns like "Pelé (2)" or "João(3)"
        m = re.match(r"^(.*?)(?:\((\d+)\))?$", part)
        if not m:
            continue
        raw_name = m.group(1).strip()
        if not raw_name:
            continue
        goals_str = m.group(2)
        goals = int(goals_str) if goals_str else 1
        results.append((raw_name, goals))

    return results


def _update_player_stats_from_table(
    players_collection,
    player_id: ObjectId,
    championship_id: ObjectId,
    championship_name: str,
    games: int,
    goals: int,
    assists: int,
    titles: int,
    season: str,
) -> None:
    """
    Update a player's aggregated-stats with table (initial columns) values for one championship season.
    Rows are keyed by (championship-id, season) so multiple seasons under the same root sum in leaderboards.
    """
    player = players_collection.find_one({"_id": player_id})
    if not player:
        return
    aggregated = player.get("aggregated-stats", {
        "total": {"games": 0, "goals": 0, "assists": 0, "titles": 0},
        "by-championship": [],
    })
    by_champ = aggregated.get("by-championship", [])
    found = False
    for entry in by_champ:
        if entry.get("championship-id") == championship_id and entry.get("season") == season:
            entry["championship-name"] = championship_name
            entry["games"] = games
            entry["goals"] = goals
            entry["assists"] = assists
            entry["titles"] = titles
            found = True
            break
    if not found:
        by_champ.append({
            "championship-id": championship_id,
            "championship-name": championship_name,
            "season": season,
            "games": games,
            "goals": goals,
            "assists": assists,
            "titles": titles,
        })
    aggregated["by-championship"] = by_champ
    aggregated["total"] = {
        "games": sum(e.get("games", 0) for e in by_champ),
        "goals": sum(e.get("goals", 0) for e in by_champ),
        "assists": sum(e.get("assists", 0) for e in by_champ),
        "titles": sum(e.get("titles", 0) for e in by_champ),
    }
    players_collection.update_one(
        {"_id": player_id},
        {
            "$set": {
                "aggregated-stats": aggregated,
                "updated-at": datetime.now(timezone.utc),
            }
        },
    )


def read_csv_with_encoding(csv_path: Path) -> Optional[pd.DataFrame]:
    """
    Read a CSV file trying common encodings (UTF-8, Latin-1, CP1252).
    Brazilian/Portuguese exports often use Latin-1 or Windows-1252.
    """
    for encoding in ("utf-8", "latin-1", "cp1252"):
        try:
            return pd.read_csv(
                csv_path, header=None, dtype=str, keep_default_na=False, encoding=encoding
            )
        except UnicodeDecodeError:
            continue
    return None


def _normalize_base_dados_columns(df: pd.DataFrame) -> pd.DataFrame:
    """Map Portuguese/variant headers to internal names: atleta, jogos, gols, assistencias, titulos, campeonato."""
    colmap = {}
    for c in df.columns:
        k = str(c).strip().lower()
        k = re.sub(r"\s+", " ", k)
        if k in ("atleta", "nome", "jogador"):
            colmap[c] = "atleta"
        elif "jogo" in k or "partida" in k:
            colmap[c] = "jogos"
        elif "gol" in k and "assist" not in k:
            colmap[c] = "gols"
        elif "assist" in k:
            colmap[c] = "assistencias"
        elif "tít" in k or k.startswith("titul") or k == "titulos":
            colmap[c] = "titulos"
        elif k in ("time", "campeonato", "competição", "competicao"):
            colmap[c] = "campeonato"
    out = df.rename(columns=colmap)
    return out


def read_base_dados_dataframe(csv_path: Path) -> Optional[pd.DataFrame]:
    """Read BASE_DADOS.csv with ; or , and common encodings."""
    if not csv_path.exists():
        return None
    for encoding in ("utf-8", "latin-1", "cp1252"):
        for sep in (";", ","):
            try:
                df = pd.read_csv(
                    csv_path, sep=sep, dtype=str, keep_default_na=False, encoding=encoding
                )
                if df.shape[1] < 3:
                    continue
                df = _normalize_base_dados_columns(df)
                if is_valid_base_dados_dataframe(df):
                    return df
            except (UnicodeDecodeError, pd.errors.ParserError, ValueError, TypeError):
                continue
    return None


def is_valid_base_dados_dataframe(df: pd.DataFrame) -> bool:
    """True if normalized frame has atleta + campeonato and at least one data row."""
    if df is None or df.empty or df.shape[1] < 3:
        return False
    if "atleta" not in df.columns or "campeonato" not in df.columns:
        return False
    for _, row in df.iterrows():
        player_name = normalize_player_name(row.get("atleta", ""))
        ch_raw = str(row.get("campeonato", "")).strip()
        if not player_name or not ch_raw or ch_raw.lower() == "time":
            continue
        return True
    return False


def read_base_dados_from_excel_sheet(excel_path: Path) -> Optional[pd.DataFrame]:
    """Read long-format BASE_DADOS from sheet 'Base de dados' if structure is valid."""
    if not excel_path.exists():
        return None
    try:
        df = pd.read_excel(excel_path, sheet_name="Base de dados", engine="openpyxl")
    except ValueError:
        return None
    if df.empty or df.shape[1] < 3:
        return None
    df = _normalize_base_dados_columns(df)
    if not is_valid_base_dados_dataframe(df):
        return None
    return df


def load_canonical_base_dados(excel_path: Path, csv_path: Path) -> Tuple[Optional[pd.DataFrame], str]:
    """
    Prefer Excel sheet 'Base de dados' (long format); fallback to BASE_DADOS.csv.
    Returns (dataframe_or_none, human-readable source label for logs).
    """
    df_xl = read_base_dados_from_excel_sheet(excel_path)
    if df_xl is not None:
        return df_xl, "Excel sheet 'Base de dados'"
    df_csv = read_base_dados_dataframe(csv_path)
    if df_csv is not None and not df_csv.empty:
        return df_csv, f"data/{BASE_DADOS_FILENAME}"
    return None, ""


def is_long_format_base_dados_sheet(df_norm: pd.DataFrame) -> bool:
    """Whether the normalized 'Base de dados' sheet is long-format (same as canonical BASE_DADOS)."""
    return is_valid_base_dados_dataframe(df_norm)


def resolve_excel_path(cli_excel: Optional[str]) -> Path:
    """Path to .xlsm: --excel, then env EXCEL_FILE, then default; try data/<name> if missing."""
    raw = (cli_excel or os.getenv("EXCEL_FILE") or EXCEL_FILE).strip()
    p = Path(raw).expanduser()
    if p.exists():
        return p.resolve()
    if not p.is_absolute():
        alt = Path("data") / p.name
        if alt.exists():
            return alt.resolve()
    return p


def ensure_players_from_base_dados(
    db,
    team_id: ObjectId,
    player_map: Dict[str, ObjectId],
    base_df: Optional[pd.DataFrame],
) -> None:
    """Insert any players that appear in canonical BASE_DADOS but not yet in player_map."""
    df = base_df
    if df is None or df.empty:
        return
    players_collection = db.players
    now = datetime.now(timezone.utc)
    for _, row in df.iterrows():
        player_name = normalize_player_name(row.get("atleta", ""))
        if not player_name:
            continue
        if player_name.lower() == "atleta" and safe_int(row.get("jogos", 0), 0) == 0:
            continue
        if player_name in player_map:
            continue
        inferred_position = infer_position_from_name(player_name, None)
        player_doc = {
            "name": player_name,
            "nickname": None,
            "position": inferred_position or "Atacante",
            "team-id": team_id,
            "active": True,
            "aggregated-stats": {
                "total": {"games": 0, "goals": 0, "assists": 0, "titles": 0},
                "by-championship": [],
            },
            "created-at": now,
            "updated-at": now,
        }
        result = players_collection.insert_one(player_doc)
        player_map[player_name] = result.inserted_id
        print(f"  ✓ Created player from BASE_DADOS: {player_name}")


def apply_base_dados_to_db(
    db,
    player_map: Dict[str, ObjectId],
    team_id: ObjectId,
    season: str,
    base_df: Optional[pd.DataFrame],
    source_label: str,
) -> int:
    """
    Overwrite per-championship table stats from canonical BASE_DADOS (Excel or CSV).
    Championships are created or marked completed for this season.
    """
    _ = team_id  # reserved for future team checks
    df = base_df
    if df is None or df.empty:
        print(
            "\n⚠ No canonical BASE_DADOS data (Excel long-format 'Base de dados' or "
            f"data/{BASE_DADOS_FILENAME}); skipping apply step"
        )
        return 0

    print("\n" + "=" * 60)
    print(f"Step 3.7: Applying canonical table stats ({source_label})")
    print("=" * 60)

    players_collection = db.players
    championships_collection = db.championships
    seasons_collection = db.seasons
    rows_applied = 0
    enroll_sets: Dict[ObjectId, set] = {}
    # Per season (season document _id): player_id -> table stats from BASE_DADOS (max if duplicate rows)
    per_season_player_stats: Dict[ObjectId, Dict[ObjectId, Dict[str, int]]] = {}

    def get_or_create_championship_root(root_name: str, format_type: str) -> ObjectId:
        existing_root = championships_collection.find_one({"name": root_name})
        if existing_root:
            return existing_root["_id"]
        now = datetime.now(timezone.utc)
        root_doc = {
            "name": root_name,
            "format": format_type,
            "season-ids": [],
            "created-at": now,
            "updated-at": now,
        }
        result = championships_collection.insert_one(root_doc)
        return result.inserted_id

    def get_or_create_season(championship_root_id: ObjectId, season_value: str, status_value: str, *,
                              championship_name: str, format_type: str) -> ObjectId:
        existing_season = seasons_collection.find_one(
            {"championship-id": championship_root_id, "season": season_value}
        )
        now = datetime.now(timezone.utc)
        if existing_season:
            set_fields = {
                "status": status_value,
                "format": format_type,
                "championship-name": championship_name,
                "updated-at": now,
            }
            if status_value == "completed":
                set_fields["finished-at"] = now
            seasons_collection.update_one(
                {"_id": existing_season["_id"]},
                {"$set": set_fields},
            )
            # Ensure root linkage exists (idempotent)
            championships_collection.update_one(
                {"_id": championship_root_id},
                {"$addToSet": {"season-ids": existing_season["_id"]}, "$set": {"updated-at": now}},
            )
            return existing_season["_id"]

        # Default date placeholders: keep year boundaries when possible
        try:
            year = int(season_value)
            start_dt = datetime(year, 1, 1)
            end_dt = datetime(year, 12, 31, 23, 59, 59)
        except Exception:
            start_dt = datetime(2025, 1, 1)
            end_dt = datetime(2025, 12, 31, 23, 59, 59)

        season_doc = {
            "championship-id": championship_root_id,
            "championship-name": championship_name,
            "season": season_value,
            "format": format_type,
            "status": status_value,
            "enrolled-player-ids": [],
            "match-ids": [],
            "winner-player-ids": [],
            "titles-award-count": 0,
            "titles-count": 0,
            "start-date": start_dt,
            "end-date": end_dt,
            "finished-at": now if status_value == "completed" else None,
            "created-at": now,
            "updated-at": now,
        }
        result = seasons_collection.insert_one(season_doc)
        season_id = result.inserted_id
        championships_collection.update_one(
            {"_id": championship_root_id},
            {"$addToSet": {"season-ids": season_id}, "$set": {"updated-at": now}},
        )
        return season_id

    for _, row in df.iterrows():
        player_name = normalize_player_name(row.get("atleta", ""))
        ch_raw = str(row.get("campeonato", "")).strip()
        if not player_name or not ch_raw or ch_raw.lower() == "time":
            continue

        champ_name_display = canonical_championship_name(ch_raw)
        format_type = infer_championship_format(ch_raw)
        # BASE_DADOS is historical table data: season is treated as completed.
        championship_root_id = get_or_create_championship_root(champ_name_display, format_type)
        cid = get_or_create_season(
            championship_root_id,
            season,
            status_value="completed",
            championship_name=champ_name_display,
            format_type=format_type,
        )

        player_id = find_player_by_name(player_map, player_name)
        if not player_id:
            print(f"  ⚠ BASE_DADOS: player not in map '{player_name}' — skipping row")
            continue

        games = safe_int(row.get("jogos", 0))
        goals = safe_int(row.get("gols", 0))
        assists = safe_int(row.get("assistencias", 0))
        titles = safe_int(row.get("titulos", 0))

        _update_player_stats_from_table(
            players_collection,
            player_id,
            championship_root_id,
            champ_name_display,
            games,
            goals,
            assists,
            titles,
            season,
        )
        enroll_sets.setdefault(cid, set()).add(player_id)
        season_stats = per_season_player_stats.setdefault(cid, {})
        prev = season_stats.get(player_id)
        row_stats = {
            "games": int(games),
            "goals": int(goals),
            "assists": int(assists),
            "titles": int(titles),
        }
        if prev is None:
            season_stats[player_id] = row_stats
        else:
            season_stats[player_id] = {
                "games": max(prev["games"], row_stats["games"]),
                "goals": max(prev["goals"], row_stats["goals"]),
                "assists": max(prev["assists"], row_stats["assists"]),
                "titles": max(prev["titles"], row_stats["titles"]),
            }
        rows_applied += 1

    def _season_table_leaders(
        stats: Dict[ObjectId, Dict[str, int]],
    ) -> Tuple[int, List[ObjectId], List[ObjectId], List[ObjectId], int]:
        """Max titles (championship title depth), winners, top scorers/assisters, max games (proxy for matches)."""
        if not stats:
            return 0, [], [], [], 0
        max_titles = max((s["titles"] for s in stats.values()), default=0)
        winner_ids = [pid for pid, s in stats.items() if s["titles"] == max_titles and max_titles > 0]
        max_goals = max((s["goals"] for s in stats.values()), default=0)
        top_scorer_ids = [pid for pid, s in stats.items() if s["goals"] == max_goals and max_goals > 0]
        max_assists = max((s["assists"] for s in stats.values()), default=0)
        top_assister_ids = [pid for pid, s in stats.items() if s["assists"] == max_assists and max_assists > 0]
        max_games = max((s["games"] for s in stats.values()), default=0)
        return max_titles, winner_ids, top_scorer_ids, top_assister_ids, max_games

    now = datetime.now(timezone.utc)
    for cid, pids in enroll_sets.items():
        stats = per_season_player_stats.get(cid, {})
        max_titles, winner_ids, top_scorer_ids, top_assister_ids, matches_count = _season_table_leaders(
            stats
        )
        seasons_collection.update_one(
            {"_id": cid},
            {
                "$addToSet": {"enrolled-player-ids": {"$each": list(pids)}},
                "$set": {
                    "titles-count": int(max_titles),
                    "titles-award-count": int(max_titles),
                    "winner-player-ids": winner_ids,
                    "top-scorer-ids": top_scorer_ids,
                    "top-assister-ids": top_assister_ids,
                    "matches-count": int(matches_count),
                    "updated-at": now,
                },
            },
        )

    root_union: Dict[ObjectId, set] = {}
    for season_id, pids in enroll_sets.items():
        doc = seasons_collection.find_one({"_id": season_id}, {"championship-id": 1})
        if doc and doc.get("championship-id"):
            rid = doc["championship-id"]
            root_union.setdefault(rid, set()).update(pids)
    for rid, pids in root_union.items():
        if not pids:
            continue
        championships_collection.update_one(
            {"_id": rid},
            {
                "$addToSet": {"enrolled-player-ids": {"$each": list(pids)}},
                "$set": {"updated-at": now},
            },
        )

    print(f"✓ Canonical BASE_DADOS applied ({source_label}): {rows_applied} row(s)")
    return rows_applied


def process_csv_championship_file(
    db,
    csv_path: Path,
    player_map: Dict[str, ObjectId],
    team_id: ObjectId,
) -> Tuple[int, int, int]:
    """
    Legacy import: one row-layout championship CSV (matches + athlete tables).
    Not used in the default seed; use --import-data-csv to run process_all_csv_championships.

    Returns:
        (championships_touched, players_enrolled, matches_created_or_updated)
    """
    print(f"\n📄 Processing CSV file: {csv_path.name}")

    df = read_csv_with_encoding(csv_path)
    if df is None:
        print(f"  ✗ Error reading CSV '{csv_path}': could not decode with utf-8, latin-1, or cp1252")
        return 0, 0, 0

    championships_collection = db.championships
    matches_collection = db.matches
    players_collection = db.players

    # State while scanning the file
    current_champ_label: Optional[str] = None
    current_phase: Optional[str] = None
    header_row_idx: Optional[int] = None
    header_cols: Dict[str, int] = {}

    # Per-championship data
    champ_id_by_label: Dict[str, ObjectId] = {}
    enrolled_players_by_champ: Dict[ObjectId, set] = {}
    champ_name_by_id: Dict[ObjectId, str] = {}

    championships_touched = 0
    players_enrolled_count = 0
    matches_created_or_updated = 0
    # Sum table stats per (championship, player) across multiple year blocks in one file
    table_stats_acc: Dict[Tuple[ObjectId, ObjectId], Dict[str, int]] = {}

    # Helper to get or create championship for a given label
    def get_or_create_championship_for_label(label: Optional[str]) -> Optional[ObjectId]:
        nonlocal championships_touched

        base_name = csv_path.stem.upper()
        effective_label = label or base_name

        if effective_label in champ_id_by_label:
            return champ_id_by_label[effective_label]

        # One document per championship name: always use DEFAULT_SEASON (ignore year in label).
        name, _ = parse_championship_label(effective_label, base_name)
        season = DEFAULT_SEASON

        # Try to find existing championship
        existing = championships_collection.find_one({"name": name, "season": season})
        if existing:
            champ_id = existing["_id"]
            champ_name_by_id[champ_id] = existing.get("name", name)
            mark_championship_completed_from_data_import(db, champ_id)
        else:
            champ_id = create_championship(
                db,
                name,
                season,
                titles_count=0,
                status="completed",
            )
            championships_touched += 1
            champ_name_by_id[champ_id] = name

        champ_id_by_label[effective_label] = champ_id
        return champ_id

    def register_enrollment(championship_id: ObjectId, player_id: ObjectId) -> None:
        nonlocal players_enrolled_count

        if championship_id not in enrolled_players_by_champ:
            enrolled_players_by_champ[championship_id] = set()
        if player_id not in enrolled_players_by_champ[championship_id]:
            enrolled_players_by_champ[championship_id].add(player_id)
            players_enrolled_count += 1

    def upsert_match(
        championship_id: ObjectId,
        phase: Optional[str],
        opponent: str,
        our_score: int,
        opponent_score: int,
        player_stats: List[Dict],
        champ_label: Optional[str] = None,
    ) -> None:
        nonlocal matches_created_or_updated

        _, season_str = parse_championship_label(champ_label or "", csv_path.stem)
        match_date = placeholder_match_date_for_season(season_str)

        # Compute home-score from player statistics (our team only)
        home_goals = sum(
            int(stat.get("goals", 0) or 0)
            for stat in player_stats
            if stat.get("team-id") == team_id
        )

        key_filter = {
            "championship-id": championship_id,
            "opponent": opponent,
            "round": phase,
            "home-team-id": team_id,
            "away-score": opponent_score,
        }

        now = datetime.now(timezone.utc)

        existing = matches_collection.find_one(key_filter)
        if existing:
            # Update player-statistics and scores, keep other fields
            # Preserve data-source as python-seed for historical data tracking
            update_doc = {
                "$set": {
                    "player-statistics": player_stats,
                    "home-score": home_goals,
                    "away-score": opponent_score,
                    "data-source": "python-seed",
                    "updated-at": now,
                },
                "$setOnInsert": {
                    "version": 1,
                }
            }
            if existing.get("date") is None:
                update_doc["$set"]["date"] = match_date
            matches_collection.update_one({"_id": existing["_id"]}, update_doc)
            matches_created_or_updated += 1
        else:
            match_doc = {
                "championship-id": championship_id,
                "home-team-id": team_id,
                "away-team-id": None,
                "date": match_date,
                "location": None,
                "round": phase,
                "status": "finished",
                "opponent": opponent,
                "venue": None,
                "result": {
                    "our-score": our_score,
                    "opponent-score": opponent_score,
                    "outcome": (
                        "win"
                        if our_score > opponent_score
                        else "loss"
                        if our_score < opponent_score
                        else "draw"
                    ),
                },
                "away-score": opponent_score,
                "home-score": home_goals,
                "player-statistics": player_stats,
                "data-source": "python-seed",
                "version": 1,
                "created-at": now,
                "updated-at": now,
            }
            matches_collection.insert_one(match_doc)
            matches_created_or_updated += 1

    # Cache for player docs (to avoid repeated lookups)
    player_doc_cache: Dict[ObjectId, Dict] = {}

    def build_player_stat(player_name_raw: str, goals: int) -> Optional[Dict]:
        player_id = find_player_by_name(player_map, player_name_raw)
        if not player_id:
            print(f"  ⚠ Warning: Could not resolve player for goal: '{player_name_raw}'")
            return None

        if player_id not in player_doc_cache:
            doc = players_collection.find_one(
                {"_id": player_id}, {"name": 1, "position": 1, "team-id": 1}
            )
            if not doc:
                return None
            player_doc_cache[player_id] = doc
        else:
            doc = player_doc_cache[player_id]

        stat = {
            "player-id": player_id,
            "player-name": doc.get("name"),
            "position": doc.get("position"),
            "team-id": doc.get("team-id") or team_id,
            "goals": int(goals),
            "assists": 0,
            "yellow-cards": 0,
            "red-cards": 0,
            "minutes-played": None,
        }
        return stat

    # Scan rows to detect header, labels, phases, players, and matches
    num_rows, num_cols = df.shape

    for row_idx in range(num_rows):
        row = [str(df.iat[row_idx, col]).strip() for col in range(num_cols)]
        # Treat "nan" / empty strings as empty
        row = [cell if cell and cell.lower() != "nan" else "" for cell in row]

        # Skip completely empty rows
        if not any(row):
            continue

        # Detect championship label (e.g., "AABR Society 2020")
        for cell in row:
            if not cell:
                continue
            if re.search(r"20\d{2}", cell):
                current_champ_label = cell
                break

        # Detect phase lines (e.g. "Quartas de Final", "Semi Final", "Final", "Oitavas de Final")
        joined_text = " ".join(row)
        if re.search(r"(?i)quartas? de final", joined_text):
            current_phase = "Quartas de Final"
        elif re.search(r"(?i)semi\s*final", joined_text):
            current_phase = "Semi Final"
        elif re.search(r"(?i)oitavas? de final", joined_text):
            current_phase = "Oitavas de Final"
        elif re.search(r"(?i)final", joined_text) and "quartas" not in joined_text.lower() and "semi" not in joined_text.lower():
            current_phase = "Final"

        # Detect players header row (Atletas, Jogos, Gols, Assistencias, Títulos, ...)
        if header_row_idx is None:
            lowered = [c.lower() for c in row]
            if any("atletas" in c or "atleta" in c for c in lowered) and any(
                "jogos" in c for c in lowered
            ):
                header_row_idx = row_idx
                header_cols = {}
                for idx, col_name in enumerate(row):
                    col_lower = col_name.lower()
                    if "atleta" in col_lower:
                        header_cols["player"] = idx
                    elif "jogo" in col_lower:
                        header_cols["games"] = idx
                    elif "gol" in col_lower:
                        header_cols["goals"] = idx
                    elif "assist" in col_lower:
                        header_cols["assists"] = idx
                    elif "tít" in col_lower or "tit" in col_lower:
                        header_cols["titles"] = idx
                continue

        # Handle player rows (zone of athletes)
        if header_row_idx is not None and row_idx > header_row_idx:
            player_col_idx = header_cols.get("player")
            if player_col_idx is not None and player_col_idx < len(row):
                raw_name = row[player_col_idx].strip()
                # Heuristic: treat as player row if we have a non-empty name and at least one numeric stat
                if raw_name and raw_name.lower() not in {
                    "aproveitamento",
                    "curiosidades",
                    "temporadas",
                    "arilheiro",
                    "artilheiro",
                    "garçom",
                }:
                    def get_int_from_col(key: str) -> int:
                        idx = header_cols.get(key)
                        if idx is None or idx >= len(row):
                            return 0
                        return safe_int(row[idx], default=0)

                    games = get_int_from_col("games")
                    goals = get_int_from_col("goals")
                    assists = get_int_from_col("assists")
                    titles = get_int_from_col("titles")

                    if any(v != 0 for v in [games, goals, assists, titles]):
                        champ_id = get_or_create_championship_for_label(current_champ_label)
                        if champ_id:
                            player_id = find_player_by_name(player_map, raw_name)
                            if not player_id:
                                print(
                                    f"  ⚠ Warning: Player from CSV not found in database: '{raw_name}'"
                                )
                            else:
                                register_enrollment(champ_id, player_id)
                                key = (champ_id, player_id)
                                if key not in table_stats_acc:
                                    table_stats_acc[key] = {
                                        "games": 0,
                                        "goals": 0,
                                        "assists": 0,
                                        "titles": 0,
                                    }
                                acc = table_stats_acc[key]
                                acc["games"] += games
                                acc["goals"] += goals
                                acc["assists"] += assists
                                acc["titles"] += titles

        # Handle match rows (zone of matches)
        # Look for "GALÁTICOS" / "Galáticos" in any cell
        has_galaticos = any(
            re.search(r"(?i)gal[áa]ticos", cell) for cell in row if cell
        )
        if has_galaticos:
            # Identify score and opponent columns
            score_idx = None
            our_score = None
            opp_score = None
            for idx, cell in enumerate(row):
                parsed = parse_score(cell)
                if parsed:
                    score_idx = idx
                    our_score, opp_score = parsed
                    break

            if score_idx is None or our_score is None or opp_score is None:
                continue

            opponent = ""
            if score_idx + 1 < len(row):
                opponent = row[score_idx + 1].strip()

            # Goals description is usually after opponent
            goals_text = ""
            for idx in range(score_idx + 2, len(row)):
                if row[idx]:
                    goals_text = row[idx]
                    break

            champ_id = get_or_create_championship_for_label(current_champ_label)
            if not champ_id:
                continue

            # Build player-statistics from goals description
            goals_entries = parse_goals_string(goals_text)
            stats_by_player_id: Dict[ObjectId, Dict] = {}

            for player_name_raw, goals in goals_entries:
                stat = build_player_stat(player_name_raw, goals)
                if not stat:
                    continue
                pid = stat["player-id"]
                if pid in stats_by_player_id:
                    stats_by_player_id[pid]["goals"] += stat["goals"]
                else:
                    stats_by_player_id[pid] = stat

            player_stats_list = list(stats_by_player_id.values())

            upsert_match(
                championship_id=champ_id,
                phase=current_phase,
                opponent=opponent or "Unknown",
                our_score=our_score,
                opponent_score=opp_score,
                player_stats=player_stats_list,
                champ_label=current_champ_label,
            )

    # Flush accumulated athlete table stats (all blocks / seasons in this file)
    for (champ_id, player_id), acc in table_stats_acc.items():
        champ_name = champ_name_by_id.get(champ_id, "")
        _update_player_stats_from_table(
            players_collection,
            player_id,
            champ_id,
            champ_name,
            acc["games"],
            acc["goals"],
            acc["assists"],
            acc["titles"],
            DEFAULT_SEASON,
        )

    # After scanning rows, persist enrollments in championships
    for champ_id, player_ids in enrolled_players_by_champ.items():
        if not player_ids:
            continue
        championships_collection.update_one(
            {"_id": champ_id},
            {
                "$addToSet": {
                    "enrolled-player-ids": {"$each": list(player_ids)},
                },
                "$set": {"updated-at": datetime.now(timezone.utc)},
            },
        )

    return championships_touched, players_enrolled_count, matches_created_or_updated


def process_all_csv_championships(
    db,
    player_map: Dict[str, ObjectId],
    team_id: ObjectId,
) -> None:
    """
    Legacy: process all CSV files under data/ as championship + matches sources.
    Not called from main() unless --import-data-csv is set.
    """
    data_dir = Path("data")
    if not data_dir.exists():
        print("\n⚠ data/ directory not found; skipping CSV import")
        return

    csv_files = sorted(p for p in data_dir.glob("*.csv"))
    csv_files = [p for p in csv_files if p.name.lower() not in SKIP_CSV_FILENAMES]

    if not csv_files:
        print("\n⚠ No CSV championship files found in data/; skipping CSV import")
        return

    print("\n" + "=" * 60)
    print("Step 3.5: Importing championships and matches from CSV files")
    print("=" * 60)
    print(
        f"  Season for championships from CSV: {DEFAULT_SEASON} "
        "(env DEFAULT_SEASON). Years inside sheets are not stored as separate tournaments."
    )

    total_championships = 0
    total_enrollments = 0
    total_matches = 0

    for csv_path in csv_files:
        champs, enrollments, matches_created = process_csv_championship_file(
            db, csv_path, player_map, team_id
        )
        total_championships += champs
        total_enrollments += enrollments
        total_matches += matches_created

    print(
        f"\n✓ CSV import summary: championships touched={total_championships}, "
        f"player enrollments registered={total_enrollments}, "
        f"matches created/updated={total_matches}"
    )


def rebuild_aggregated_stats_from_matches(db) -> None:
    """
    Merge match-derived stats with table (initial columns) stats.

    - Table values (from abas iniciais / CSV/Excel table) are the baseline.
    - Match data serves as validator: where we have match data for a (player, championship),
      we use match-derived games/goals/assists; titles always come from the table (matches
      don't have title info).
    - When there is no or insufficient match data for a championship, table values are kept.
    """
    matches_collection = db.matches
    players_collection = db.players

    print("\n" + "=" * 60)
    print("Step 3.6: Merging match stats with table stats (matches as validators)")
    print("=" * 60)

    pipeline = [
        {"$unwind": "$player-statistics"},
        {
            "$group": {
                "_id": {
                    "player-id": "$player-statistics.player-id",
                    "championship-id": "$championship-id",
                },
                "games": {"$sum": 1},
                "goals": {"$sum": "$player-statistics.goals"},
                "assists": {"$sum": "$player-statistics.assists"},
            }
        },
        {
            "$lookup": {
                "from": "championships",
                "localField": "_id.championship-id",
                "foreignField": "_id",
                "as": "championship",
            }
        },
        {"$unwind": "$championship"},
        {
            "$group": {
                "_id": "$_id.player-id",
                "by-championship": {
                    "$push": {
                        "championship-id": "$_id.championship-id",
                        "championship-name": "$championship.name",
                        "games": "$games",
                        "goals": "$goals",
                        "assists": "$assists",
                    }
                },
            }
        },
    ]

    # Build map: player_id -> list of { championship-id, championship-name, games, goals, assists } from matches
    match_derived_by_player: Dict[ObjectId, List[Dict]] = {}
    for doc in matches_collection.aggregate(pipeline):
        player_id = doc.get("_id")
        by_champ = doc.get("by-championship", [])
        if player_id and by_champ:
            match_derived_by_player[player_id] = by_champ

    # For each player that has match-derived data, merge with current (table) aggregated-stats
    updated_count = 0
    for player_id, match_by_champ in match_derived_by_player.items():
        player = players_collection.find_one(
            {"_id": player_id},
            {"aggregated-stats": 1},
        )
        if not player:
            continue
        current = player.get("aggregated-stats", {})
        current_by = current.get("by-championship", [])
        match_champ_ids = {e.get("championship-id") for e in match_by_champ if e.get("championship-id")}
        match_by_champ_map = {e.get("championship-id"): e for e in match_by_champ if e.get("championship-id")}

        def _safe_int(v, default=0):
            if v is None:
                return default
            try:
                return int(v)
            except (TypeError, ValueError):
                return default

        merged_by = []
        for entry in current_by:
            cid = entry.get("championship-id")
            if cid in match_by_champ_map:
                # Use match-derived as validator; fall back to table when match data is missing/zero
                md = match_by_champ_map[cid]
                md_games = _safe_int(md.get("games"), 0)
                md_goals = _safe_int(md.get("goals"), 0)
                md_assists = _safe_int(md.get("assists"), 0)
                tbl_games = _safe_int(entry.get("games"), 0)
                tbl_goals = _safe_int(entry.get("goals"), 0)
                tbl_assists = _safe_int(entry.get("assists"), 0)
                # Prefer match-derived when present; otherwise keep table (jogos/gols/assistências da tabela)
                games = md_games if md_games > 0 else tbl_games
                goals = md_goals if md_goals > 0 else tbl_goals
                assists = md_assists if md_assists > 0 else tbl_assists  # partidas não têm assistências, manter tabela
                merged_by.append({
                    "championship-id": cid,
                    "championship-name": md.get("championship-name", entry.get("championship-name", "")),
                    "games": games,
                    "goals": goals,
                    "assists": assists,
                    "titles": _safe_int(entry.get("titles"), 0),
                })
            else:
                # No match data for this championship: keep table values
                merged_by.append(dict(entry))
        # Add championships that exist only in match-derived (no table entry)
        for cid in match_champ_ids:
            if not any(e.get("championship-id") == cid for e in merged_by):
                md = match_by_champ_map.get(cid, {})
                merged_by.append({
                    "championship-id": cid,
                    "championship-name": md.get("championship-name", ""),
                    "games": _safe_int(md.get("games"), 0),
                    "goals": _safe_int(md.get("goals"), 0),
                    "assists": _safe_int(md.get("assists"), 0),
                    "titles": 0,
                })

        total_games = sum(e.get("games", 0) for e in merged_by)
        total_goals = sum(e.get("goals", 0) for e in merged_by)
        total_assists = sum(e.get("assists", 0) for e in merged_by)
        total_titles = sum(e.get("titles", 0) for e in merged_by)
        aggregated_stats = {
            "total": {"games": total_games, "goals": total_goals, "assists": total_assists, "titles": total_titles},
            "by-championship": merged_by,
        }
        players_collection.update_one(
            {"_id": player_id},
            {
                "$set": {
                    "aggregated-stats": aggregated_stats,
                    "updated-at": datetime.now(timezone.utc),
                }
            },
        )
        updated_count += 1

    # Players with only table data (no match-derived) are left unchanged
    print(f"✓ Merged match stats with table stats for {updated_count} players (table values kept when no match data)")


def process_excel_tournament_sheets(
    db,
    excel_path: Path,
    sheet_names: List[str],
    player_map: Dict[str, ObjectId],
    team_id: ObjectId,
) -> Tuple[int, int]:
    """
    Process tournament sheets from Excel to extract matches into existing matches collection.
    
    Returns:
        (matches_created, matches_updated)
    """
    print("\n" + "=" * 60)
    print("Step 5: Processing tournament sheets for matches")
    print("=" * 60)

    matches_collection = db.matches
    seasons_collection = db.seasons
    championships_collection = db.championships
    players_collection = db.players

    tournament_sheets = [s for s in sheet_names if s not in SKIP_SHEETS]
    matches_created = 0
    matches_updated = 0
    now = datetime.now(timezone.utc)

    # Cache for player docs
    player_doc_cache: Dict[ObjectId, Dict] = {}

    def get_player_doc(player_id: ObjectId) -> Optional[Dict]:
        if player_id in player_doc_cache:
            return player_doc_cache[player_id]
        doc = players_collection.find_one(
            {"_id": player_id}, {"name": 1, "position": 1, "team-id": 1}
        )
        if doc:
            player_doc_cache[player_id] = doc
        return doc

    def build_player_stat(player_name_raw: str, goals: int) -> Optional[Dict]:
        player_id = find_player_by_name(player_map, player_name_raw)
        if not player_id:
            return None
        doc = get_player_doc(player_id)
        if not doc:
            return None
        return {
            "player-id": player_id,
            "player-name": doc.get("name"),
            "position": doc.get("position"),
            "team-id": doc.get("team-id") or team_id,
            "goals": int(goals),
            "assists": 0,
            "yellow-cards": 0,
            "red-cards": 0,
            "minutes-played": None,
        }

    for sheet_name in tournament_sheets:
        try:
            df = pd.read_excel(excel_path, sheet_name=sheet_name, header=None, engine="openpyxl")
        except Exception as e:
            print(f"  ⚠ Error reading sheet '{sheet_name}': {e}")
            continue

        if df.empty:
            continue

        num_rows, num_cols = df.shape
        current_tournament_label: Optional[str] = None
        current_phase: Optional[str] = None
        sheet_matches = 0

        for row_idx in range(num_rows):
            row = [str(df.iat[row_idx, col]).strip() if pd.notna(df.iat[row_idx, col]) else "" for col in range(num_cols)]
            row = [cell if cell.lower() != "nan" else "" for cell in row]

            if not any(row):
                continue

            # Detect tournament label with year (e.g., "AABR Society 2020")
            for cell in row:
                if cell and re.search(r"20\d{2}", cell):
                    current_tournament_label = cell
                    break

            # Detect phase markers
            joined_text = " ".join(row)
            if re.search(r"(?i)1a?\s*fase", joined_text):
                current_phase = "1a fase"
            elif re.search(r"(?i)quartas?\s*de\s*final", joined_text):
                current_phase = "Quartas de Final"
            elif re.search(r"(?i)semi\s*final", joined_text):
                current_phase = "Semi Final"
            elif re.search(r"(?i)oitavas?\s*de\s*final", joined_text):
                current_phase = "Oitavas de Final"
            elif re.search(r"(?i)final", joined_text) and "quartas" not in joined_text.lower() and "semi" not in joined_text.lower():
                current_phase = "Final"

            # Detect match rows (contains "GALÁTICOS")
            has_galaticos = any(re.search(r"(?i)gal[áa]ticos", cell) for cell in row if cell)
            if not has_galaticos:
                continue

            # Find score
            score_idx = None
            our_score = None
            opp_score = None
            for idx, cell in enumerate(row):
                parsed = parse_score(cell)
                if parsed:
                    score_idx = idx
                    our_score, opp_score = parsed
                    break

            if score_idx is None or our_score is None or opp_score is None:
                continue

            # Find opponent (usually after score)
            opponent = ""
            if score_idx + 1 < len(row):
                opponent = row[score_idx + 1].strip()
            if not opponent:
                opponent = "Unknown"

            # Find goals description
            goals_text = ""
            for idx in range(score_idx + 2, len(row)):
                if row[idx]:
                    goals_text = row[idx]
                    break

            # Parse tournament name and season
            if current_tournament_label:
                champ_name, season = parse_championship_label(current_tournament_label, sheet_name)
            else:
                champ_name = sheet_name
                season = DEFAULT_SEASON

            champ_name = canonical_championship_name(champ_name)
            format_type = infer_championship_format(champ_name)
            match_date = placeholder_match_date_for_season(season)

            # Get or create championship
            championship = championships_collection.find_one({"name": champ_name})
            if not championship:
                championship_doc = {
                    "name": champ_name,
                    "format": format_type,
                    "season-ids": [],
                    "created-at": now,
                    "updated-at": now,
                }
                result = championships_collection.insert_one(championship_doc)
                championship_id = result.inserted_id
            else:
                championship_id = championship["_id"]

            # Get or create season
            season_doc = seasons_collection.find_one({
                "championship-id": championship_id,
                "season": season
            })
            if not season_doc:
                try:
                    year = int(season)
                    start_dt = datetime(year, 1, 1)
                    end_dt = datetime(year, 12, 31, 23, 59, 59)
                except:
                    start_dt = datetime(2025, 1, 1)
                    end_dt = datetime(2025, 12, 31, 23, 59, 59)

                new_season = {
                    "championship-id": championship_id,
                    "championship-name": champ_name,
                    "season": season,
                    "format": format_type,
                    "status": "completed",
                    "enrolled-player-ids": [],
                    "match-ids": [],
                    "winner-player-ids": [],
                    "titles-award-count": 0,
                    "titles-count": 0,
                    "start-date": start_dt,
                    "end-date": end_dt,
                    "finished-at": now,
                    "created-at": now,
                    "updated-at": now,
                }
                result = seasons_collection.insert_one(new_season)
                season_id = result.inserted_id
                championships_collection.update_one(
                    {"_id": championship_id},
                    {"$addToSet": {"season-ids": season_id}, "$set": {"updated-at": now}},
                )
            else:
                season_id = season_doc["_id"]

            # Build player statistics from goals text
            goals_entries = parse_goals_string(goals_text)
            player_stats: List[Dict] = []
            for player_name_raw, goals in goals_entries:
                stat = build_player_stat(player_name_raw, goals)
                if stat:
                    player_stats.append(stat)

            # Determine walkover
            walkover = False
            if our_score == 3 and opp_score == 0 and not player_stats:
                walkover = True

            # Compute outcome
            if our_score > opp_score:
                outcome = "win"
            elif our_score < opp_score:
                outcome = "loss"
            else:
                outcome = "draw"

            # Upsert match using natural key
            match_filter = {
                "season-id": season_id,
                "opponent": opponent,
                "round": current_phase,
                "home-score": our_score,
                "away-score": opp_score,
            }

            existing_match = matches_collection.find_one(match_filter)
            if existing_match:
                update_fields = {
                    "player-statistics": player_stats,
                    "walkover": walkover,
                    "data-source": "excel-seed",
                    "updated-at": now,
                }
                if existing_match.get("date") is None:
                    update_fields["date"] = match_date
                matches_collection.update_one(
                    {"_id": existing_match["_id"]},
                    {"$set": update_fields},
                )
                matches_updated += 1
            else:
                match_doc = {
                    "championship-id": championship_id,
                    "season-id": season_id,
                    "home-team-id": team_id,
                    "away-team-id": None,
                    "opponent": opponent,
                    "date": match_date,
                    "location": None,
                    "round": current_phase,
                    "status": "finished",
                    "home-score": our_score,
                    "away-score": opp_score,
                    "result": {
                        "our-score": our_score,
                        "opponent-score": opp_score,
                        "outcome": outcome,
                    },
                    "player-statistics": player_stats,
                    "walkover": walkover,
                    "data-source": "excel-seed",
                    "version": 1,
                    "created-at": now,
                    "updated-at": now,
                }
                result = matches_collection.insert_one(match_doc)
                # Link match to season
                seasons_collection.update_one(
                    {"_id": season_id},
                    {"$addToSet": {"match-ids": result.inserted_id}, "$set": {"updated-at": now}},
                )
                matches_created += 1
            sheet_matches += 1

        if sheet_matches > 0:
            print(f"  ✓ {sheet_name}: {sheet_matches} matches processed")

    print(f"\n✓ Tournament sheets: {matches_created} created, {matches_updated} updated")
    return matches_created, matches_updated


def compute_season_performance(db) -> int:
    """
    Compute and update performance stats for each season based on matches.
    
    Returns:
        Number of seasons updated
    """
    print("\n" + "=" * 60)
    print("Step 6: Computing season performance stats")
    print("=" * 60)

    seasons_collection = db.seasons
    matches_collection = db.matches
    now = datetime.now(timezone.utc)
    updated_count = 0

    # Get all seasons
    for season in seasons_collection.find({}):
        season_id = season["_id"]
        
        # Get matches for this season
        matches = list(matches_collection.find({"season-id": season_id}))
        if not matches:
            continue

        games = len(matches)
        wins = 0
        draws = 0
        losses = 0
        goals_for = 0
        goals_against = 0

        for match in matches:
            home_score = match.get("home-score", 0) or 0
            away_score = match.get("away-score", 0) or 0
            goals_for += home_score
            goals_against += away_score

            if home_score > away_score:
                wins += 1
            elif home_score < away_score:
                losses += 1
            else:
                draws += 1

        win_rate = round(wins / games, 3) if games > 0 else 0.0

        performance = {
            "games": games,
            "wins": wins,
            "draws": draws,
            "losses": losses,
            "goals-for": goals_for,
            "goals-against": goals_against,
            "win-rate": win_rate,
        }

        seasons_collection.update_one(
            {"_id": season_id},
            {"$set": {"performance": performance, "updated-at": now}},
        )
        updated_count += 1

    print(f"✓ Updated performance stats for {updated_count} seasons")
    return updated_count


def parse_asbac_standings(
    db,
    excel_path: Path,
) -> int:
    """
    Parse ASBAC sheet SIMULADOR section for league standings.
    
    Returns:
        Number of standings documents created
    """
    print("\n" + "=" * 60)
    print("Step 7: Parsing ASBAC standings (SIMULADOR)")
    print("=" * 60)

    try:
        df = pd.read_excel(excel_path, sheet_name="ASBAC", header=None, engine="openpyxl")
    except Exception as e:
        print(f"  ⚠ Could not read ASBAC sheet: {e}")
        return 0

    if df.empty:
        print("  ⚠ ASBAC sheet is empty")
        return 0

    standings_collection = db.standings
    now = datetime.now(timezone.utc)
    created_count = 0

    # Find SIMULADOR section
    num_rows, num_cols = df.shape
    simulador_row = None
    simulador_col = None

    for i in range(num_rows):
        for j in range(num_cols):
            cell = df.iat[i, j]
            if pd.notna(cell) and "SIMULADOR" in str(cell).upper():
                simulador_row = i
                simulador_col = j
                break
        if simulador_row is not None:
            break

    if simulador_row is None:
        print("  ⚠ SIMULADOR section not found in ASBAC")
        return 0

    # Parse header row (should be right after SIMULADOR)
    header_row = simulador_row + 1
    if header_row >= num_rows:
        print("  ⚠ No data after SIMULADOR header")
        return 0

    # Parse standings data (rows after header)
    teams_data: List[Dict] = []
    for i in range(header_row + 1, min(header_row + 20, num_rows)):  # Max 20 teams
        row = [df.iat[i, j] if pd.notna(df.iat[i, j]) else "" for j in range(simulador_col, min(simulador_col + 8, num_cols))]
        
        if not row or not row[0]:
            continue

        # Expected format: position, team, V, E, D, PTS, SG
        try:
            position = safe_int(row[0], 0)
            if position == 0:
                continue
            team_name = str(row[1]).strip() if len(row) > 1 else ""
            if not team_name:
                continue

            wins = safe_int(row[2], 0) if len(row) > 2 else 0
            draws = safe_int(row[3], 0) if len(row) > 3 else 0
            losses = safe_int(row[4], 0) if len(row) > 4 else 0
            points = safe_int(row[5], 0) if len(row) > 5 else 0
            goal_diff = safe_int(row[6], 0) if len(row) > 6 else 0

            teams_data.append({
                "position": position,
                "name": team_name,
                "points": points,
                "wins": wins,
                "draws": draws,
                "losses": losses,
                "goal-diff": goal_diff,
            })
        except Exception:
            continue

    if not teams_data:
        print("  ⚠ No standings data found")
        return 0

    # Determine tournament name (look for year in nearby cells)
    tournament = "ASBAC Society"
    for i in range(max(0, simulador_row - 5), simulador_row):
        for j in range(num_cols):
            cell = df.iat[i, j]
            if pd.notna(cell):
                cell_str = str(cell)
                m = re.search(r"20\d{2}", cell_str)
                if m:
                    tournament = f"ASBAC Society {m.group(0)}"
                    break

    # Upsert standings document
    existing = standings_collection.find_one({"tournament": tournament})
    if existing:
        standings_collection.update_one(
            {"_id": existing["_id"]},
            {"$set": {"teams": teams_data, "updated-at": now}},
        )
        print(f"  ✓ Updated standings for {tournament}: {len(teams_data)} teams")
    else:
        standings_doc = {
            "tournament": tournament,
            "teams": teams_data,
            "created-at": now,
            "updated-at": now,
        }
        standings_collection.insert_one(standings_doc)
        created_count += 1
        print(f"  ✓ Created standings for {tournament}: {len(teams_data)} teams")

    return created_count


def parse_asbac_records(
    db,
    excel_path: Path,
    player_map: Dict[str, ObjectId],
) -> int:
    """
    Parse ASBAC sheet CURIOSIDADES section for records.
    
    Returns:
        Number of records documents created
    """
    print("\n" + "=" * 60)
    print("Step 8: Parsing ASBAC records (CURIOSIDADES)")
    print("=" * 60)

    try:
        df = pd.read_excel(excel_path, sheet_name="ASBAC", header=None, engine="openpyxl")
    except Exception as e:
        print(f"  ⚠ Could not read ASBAC sheet: {e}")
        return 0

    if df.empty:
        return 0

    records_collection = db.records
    now = datetime.now(timezone.utc)
    created_count = 0

    # Find CURIOSIDADES section
    num_rows, num_cols = df.shape
    curiosidades_row = None
    curiosidades_col = None

    for i in range(num_rows):
        for j in range(num_cols):
            cell = df.iat[i, j]
            if pd.notna(cell) and "CURIOSIDADES" in str(cell).upper():
                curiosidades_row = i
                curiosidades_col = j
                break
        if curiosidades_row is not None:
            break

    if curiosidades_row is None:
        print("  ⚠ CURIOSIDADES section not found in ASBAC")
        return 0

    # Parse records (rows after CURIOSIDADES header)
    record_types = {
        "artilheiro": "top-scorer",
        "maior artilheiro": "top-scorer",
        "garçom": "top-assister",
        "maior vitória": "biggest-win",
        "maior vitoria": "biggest-win",
        "pior derrota": "worst-loss",
        "invicto": "unbeaten-streak",
        "sequência": "streak",
    }

    for i in range(curiosidades_row + 1, min(curiosidades_row + 15, num_rows)):
        row = [df.iat[i, j] if pd.notna(df.iat[i, j]) else "" for j in range(curiosidades_col, min(curiosidades_col + 6, num_cols))]
        
        if not row:
            continue

        for col_idx, cell in enumerate(row):
            cell_str = str(cell).strip().lower()
            for pt_key, en_type in record_types.items():
                if pt_key in cell_str:
                    # Extract value from adjacent cells
                    value_parts = []
                    for k in range(col_idx + 1, min(col_idx + 4, len(row))):
                        if row[k]:
                            value_parts.append(str(row[k]).strip())
                    
                    if not value_parts:
                        continue

                    description = " ".join(value_parts)
                    value = value_parts[0] if value_parts else ""

                    # Try to find player ID if it's a player record
                    player_id = None
                    if en_type in ("top-scorer", "top-assister"):
                        for part in value_parts:
                            pid = find_player_by_name(player_map, part)
                            if pid:
                                player_id = pid
                                break

                    # Upsert record
                    record_filter = {"type": en_type, "tournament": "ASBAC"}
                    existing = records_collection.find_one(record_filter)
                    
                    record_doc = {
                        "type": en_type,
                        "description": description,
                        "tournament": "ASBAC",
                        "value": value,
                        "updated-at": now,
                    }
                    if player_id:
                        record_doc["player-id"] = player_id

                    if existing:
                        records_collection.update_one(
                            {"_id": existing["_id"]},
                            {"$set": record_doc},
                        )
                    else:
                        record_doc["created-at"] = now
                        records_collection.insert_one(record_doc)
                        created_count += 1
                        print(f"  ✓ Created record: {en_type} - {description[:50]}")
                    break

    print(f"✓ Processed {created_count} new records")
    return created_count


def update_season_winners_from_excel(
    db,
    excel_path: Path,
    sheet_names: List[str],
    player_map: Dict[str, ObjectId],
) -> int:
    """
    Update seasons.winner-player-ids from Excel sheet title columns.
    
    Returns:
        Number of seasons updated
    """
    print("\n" + "=" * 60)
    print("Step 9: Updating season winners from Excel")
    print("=" * 60)

    seasons_collection = db.seasons
    championships_collection = db.championships
    now = datetime.now(timezone.utc)
    updated_count = 0

    tournament_sheets = [s for s in sheet_names if s not in SKIP_SHEETS]

    for sheet_name in tournament_sheets:
        try:
            df = pd.read_excel(excel_path, sheet_name=sheet_name, header=None, engine="openpyxl")
        except Exception:
            continue

        if df.empty:
            continue

        num_rows, num_cols = df.shape
        current_tournament_label: Optional[str] = None

        # Find tournament label with year
        for i in range(min(5, num_rows)):
            for j in range(min(15, num_cols)):
                cell = df.iat[i, j]
                if pd.notna(cell):
                    cell_str = str(cell)
                    if re.search(r"20\d{2}", cell_str):
                        current_tournament_label = cell_str
                        break
            if current_tournament_label:
                break

        if not current_tournament_label:
            continue

        champ_name, season = parse_championship_label(current_tournament_label, sheet_name)
        champ_name = canonical_championship_name(champ_name)

        # Find championship and season
        championship = championships_collection.find_one({"name": champ_name})
        if not championship:
            continue

        season_doc = seasons_collection.find_one({
            "championship-id": championship["_id"],
            "season": season
        })
        if not season_doc:
            continue

        # Find header row with "Títulos" column
        header_row_idx = None
        titles_col_idx = None
        player_col_idx = None

        for i in range(min(10, num_rows)):
            row = [str(df.iat[i, j]).strip().lower() if pd.notna(df.iat[i, j]) else "" for j in range(num_cols)]
            for j, cell in enumerate(row):
                if "atleta" in cell and player_col_idx is None:
                    player_col_idx = j
                    header_row_idx = i
                if "título" in cell or "titulos" in cell:
                    titles_col_idx = j
            if header_row_idx is not None and titles_col_idx is not None:
                break

        if header_row_idx is None or titles_col_idx is None or player_col_idx is None:
            continue

        # Find players with titles > 0
        winner_ids: List[ObjectId] = []
        for i in range(header_row_idx + 1, num_rows):
            player_cell = df.iat[i, player_col_idx]
            titles_cell = df.iat[i, titles_col_idx]

            if not pd.notna(player_cell):
                continue

            player_name = normalize_player_name(str(player_cell))
            if not player_name:
                continue

            titles = safe_int(titles_cell, 0)
            if titles > 0:
                player_id = find_player_by_name(player_map, player_name)
                if player_id and player_id not in winner_ids:
                    winner_ids.append(player_id)

        if winner_ids:
            seasons_collection.update_one(
                {"_id": season_doc["_id"]},
                {
                    "$set": {
                        "winner-player-ids": winner_ids,
                        "titles-count": 1,
                        "titles-award-count": 1,
                        "updated-at": now,
                    }
                },
            )
            updated_count += 1

    print(f"✓ Updated winners for {updated_count} seasons")
    return updated_count


def clear_database(db, keep_admins: bool = False) -> None:
    """
    Clear all seed data from database
    
    Args:
        db: MongoDB database instance
        keep_admins: If True, keep admin users (default: False)
    """
    print("\n" + "=" * 60)
    print("Clearing database collections...")
    print("=" * 60)
    
    collections_to_clear = {
        "teams": "Teams",
        "players": "Players",
        "championships": "Championships",
        "seasons": "Seasons",
        "matches": "Matches",
        "standings": "Standings",
        "records": "Records",
    }
    
    if not keep_admins:
        collections_to_clear["admins"] = "Admins"
    
    for collection_name, display_name in collections_to_clear.items():
        count = db[collection_name].count_documents({})
        if count > 0:
            result = db[collection_name].delete_many({})
            print(f"✓ Deleted {result.deleted_count} {display_name.lower()}")
        else:
            print(f"✓ {display_name} collection already empty")
    
    print("\n✓ Database cleared successfully!")


def main() -> None:
    """
    Main seed function.

    Default: players from Excel "Base de dados"; championships/stats from the same sheet
    (long format) when present, else data/BASE_DADOS.csv. Other Excel sheets and data CSVs
    are ignored unless legacy flags are set.

    By default, the script is idempotent. Use --reset to clear existing data before seeding.

    Raises:
        SystemExit: If critical errors occur
    """
    parser = argparse.ArgumentParser(
        description=(
            "Seed MongoDB from Excel (and optional BASE_DADOS.csv). "
            "Canonical per-championship stats: Excel 'Base de dados' long format first, "
            f"then data/{BASE_DADOS_FILENAME}. Other sheets/CSVs ignored unless legacy flags."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Normal seed (idempotent - won't create duplicates)
  python seed_mongodb.py
  
  # Clear all data and reseed
  python seed_mongodb.py --reset
  
  # Clear data but keep admin users
  python seed_mongodb.py --reset --keep-admins

  # Custom Excel path (overrides env EXCEL_FILE)
  python seed_mongodb.py --excel /path/to/galaticos.xlsm

  # Re-enable old imports from Excel championship tabs and/or data/*.csv
  python seed_mongodb.py --import-excel-championships --import-data-csv

  # Import matches from tournament sheets + compute season performance
  python seed_mongodb.py --import-tournament-matches

  # Import ASBAC standings and records
  python seed_mongodb.py --import-asbac-data

  # Full enrichment (all new features)
  python seed_mongodb.py --reset --import-tournament-matches --import-asbac-data
        """
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Clear all existing data before seeding (WARNING: This will delete all teams, players, championships, and matches)"
    )
    parser.add_argument(
        "--keep-admins",
        action="store_true",
        help="When using --reset, keep existing admin users (only works with --reset)"
    )
    parser.add_argument(
        "--excel",
        metavar="PATH",
        help="Path to .xlsm (default: env EXCEL_FILE or data/galaticos.xlsm)",
    )
    parser.add_argument(
        "--ignore-smoke-dataset",
        action="store_true",
        help="Allow official seed without --reset even if smoke/E2E data is present (risk of mixed datasets)",
    )
    parser.add_argument(
        "--import-excel-championships",
        action="store_true",
        help="Legacy: import championship data from all Excel sheets except Base de dados / PLACAS",
    )
    parser.add_argument(
        "--import-data-csv",
        action="store_true",
        help="Legacy: import championships/matches from data/*.csv (excl. galaticos, BASE_DADOS)",
    )
    parser.add_argument(
        "--import-tournament-matches",
        action="store_true",
        help="Parse match results from Excel tournament sheets into matches collection",
    )
    parser.add_argument(
        "--import-asbac-data",
        action="store_true",
        help="Parse ASBAC standings (SIMULADOR) and records (CURIOSIDADES)",
    )

    args = parser.parse_args()

    if args.keep_admins and not args.reset:
        print("✗ Error: --keep-admins can only be used with --reset", file=sys.stderr)
        sys.exit(1)
    
    print("=" * 60)
    print("MongoDB Seed Script for Galáticos")
    print("=" * 60)
    
    if args.reset:
        print("\n⚠ WARNING: --reset flag is set. This will DELETE all existing data!")
        if not args.keep_admins:
            print("⚠ All teams, players, championships, matches, and admins will be deleted.")
        else:
            print("⚠ All teams, players, championships, and matches will be deleted.")
            print("⚠ Admin users will be preserved.")
        print()

    if (
        args.reset
        and _galaticos_env_is_production()
        and not _destructive_seed_explicitly_allowed()
    ):
        print(
            "\n✗ Refusing --reset: GALATICOS_ENV is set to production.",
            file=sys.stderr,
        )
        print(
            "  Set ALLOW_DESTRUCTIVE_SEED=1 only if you intentionally want to wipe this database.",
            file=sys.stderr,
        )
        sys.exit(1)

    # Resolve Excel path: --excel, env EXCEL_FILE, default; try data/<basename>
    excel_path = resolve_excel_path(args.excel)
    if not excel_path.exists():
        print(f"✗ Error: Excel file not found: {excel_path}", file=sys.stderr)
        print("  Set --excel, env EXCEL_FILE, or place data/galaticos.xlsm", file=sys.stderr)
        sys.exit(1)
    
    # Connect to MongoDB
    client = get_mongo_client()
    db = client[DB_NAME]
    
    print(f"\n✓ Using database: {DB_NAME}")

    if (
        not args.reset
        and not args.ignore_smoke_dataset
        and database_contains_smoke_dataset(db)
    ):
        print(
            "\n✗ Refusing official seed: this database still contains smoke/E2E test data "
            "(seed-smoke).",
            file=sys.stderr,
        )
        print(
            "  Run with --reset to load only official Excel data, or use a separate DB_NAME "
            "for tests.",
            file=sys.stderr,
        )
        print(
            "  Override only if you accept mixed datasets: --ignore-smoke-dataset",
            file=sys.stderr,
        )
        client.close()
        sys.exit(1)
    
    # Clear database if --reset flag is set
    if args.reset:
        clear_database(db, keep_admins=args.keep_admins)
        print()
    
    # Load Excel file
    print(f"\n📖 Reading Excel file: {excel_path}")
    wb = openpyxl.load_workbook(excel_path, data_only=True)
    sheet_names = wb.sheetnames
    print(f"✓ Found {len(sheet_names)} sheets")
    
    # Step 1: Create team
    print("\n" + "=" * 60)
    print("Step 1: Creating team")
    print("=" * 60)
    team_id = create_team(db)
    
    # Step 1.5: Create admin user
    print("\n" + "=" * 60)
    print("Step 1.5: Creating admin user")
    print("=" * 60)
    create_admin(db, username="admin", password="admin")
    
    # Step 2: Read and create players from "Base de dados"
    print("\n" + "=" * 60)
    print("Step 2: Creating players from 'Base de dados'")
    print("=" * 60)
    
    if "Base de dados" not in sheet_names:
        print("✗ Error: 'Base de dados' sheet not found!")
        sys.exit(1)
    
    players_df = pd.read_excel(excel_path, sheet_name="Base de dados", engine="openpyxl")
    df_norm = _normalize_base_dados_columns(players_df.copy())
    long_format = is_long_format_base_dados_sheet(df_norm)
    if long_format:
        print("  Detected long-format sheet (atleta + campeonato); stats applied in Step 3.7.")
        player_map = create_players_from_long_base_dados_sheet(
            db, players_df, df_norm, team_id
        )
        canonical_df = df_norm.copy()
        canonical_source = "Excel sheet 'Base de dados'"
    else:
        player_map = create_players(db, players_df, team_id)
        base_csv_path = Path("data") / BASE_DADOS_FILENAME
        canonical_df, canonical_source = load_canonical_base_dados(excel_path, base_csv_path)

    print("\n" + "=" * 60)
    print("Step 2.5: Ensure players from canonical BASE_DADOS (Excel first, else CSV)")
    print("=" * 60)
    ensure_players_from_base_dados(db, team_id, player_map, canonical_df)

    # Step 3: Excel championship sheets (skipped by default)
    print("\n" + "=" * 60)
    print("Step 3: Excel championship sheets")
    print("=" * 60)

    championship_sheets = [s for s in sheet_names if s not in SKIP_SHEETS]
    championships_created = 0
    total_players_processed = 0

    if args.import_excel_championships:
        for sheet_name in championship_sheets:
            print(f"\n📊 Processing sheet: {sheet_name}")
            try:
                sheet_df = pd.read_excel(
                    excel_path, sheet_name=sheet_name, engine="openpyxl"
                )
                if sheet_df.empty:
                    print(f"  ⚠ Skipping empty sheet: {sheet_name}")
                    continue

                champ_id, players_count = process_championship_sheet(
                    db, sheet_name, sheet_df, player_map
                )
                championships_created += 1
                total_players_processed += players_count
                print(f"  ✓ Processed {players_count} players")
            except Exception as e:
                print(f"  ✗ Error processing sheet '{sheet_name}': {e}")
    else:
        print(
            "  Skipped (not writing to Mongo). Use --import-excel-championships for legacy import."
        )
        if championship_sheets:
            print(f"  Sheets present but ignored: {', '.join(championship_sheets)}")

    # Step 3.5: CSV row-layout imports (skipped by default)
    print("\n" + "=" * 60)
    print("Step 3.5: Championship CSV import from data/")
    print("=" * 60)
    if args.import_data_csv:
        process_all_csv_championships(db, player_map, team_id)
    else:
        print(
            "  Skipped (not writing to Mongo). Default championship stats use Excel "
            f"'Base de dados' (long format) or data/{BASE_DADOS_FILENAME}."
        )
        print("  Use --import-data-csv for legacy import.")

    # Step 3.6: Rebuild aggregated stats from matches (no-op if there are no matches)
    rebuild_aggregated_stats_from_matches(db)
    match_count = db.matches.count_documents({})
    if match_count == 0:
        print(
            "  Note: no documents in matches; rebuild only affects players that already had match-derived stats."
        )

    # Step 3.7: canonical BASE_DADOS table stats (overwrites merged table fields)
    apply_base_dados_to_db(
        db, player_map, team_id, DEFAULT_SEASON, canonical_df, canonical_source
    )

    # Step 5: Process tournament sheets for matches (if flag set)
    matches_from_sheets = 0
    if args.import_tournament_matches:
        matches_created, matches_updated = process_excel_tournament_sheets(
            db, excel_path, sheet_names, player_map, team_id
        )
        matches_from_sheets = matches_created + matches_updated

        # Step 6: Compute season performance stats
        compute_season_performance(db)

        # Step 9: Update season winners from Excel
        update_season_winners_from_excel(db, excel_path, sheet_names, player_map)
    else:
        print("\n" + "=" * 60)
        print("Step 5: Tournament sheet match import")
        print("=" * 60)
        print("  Skipped. Use --import-tournament-matches to parse matches from Excel sheets.")

    # Step 7-8: Parse ASBAC special data (if flag set)
    standings_count = 0
    records_count = 0
    if args.import_asbac_data:
        standings_count = parse_asbac_standings(db, excel_path)
        records_count = parse_asbac_records(db, excel_path, player_map)
    else:
        print("\n" + "=" * 60)
        print("Step 7-8: ASBAC special data import")
        print("=" * 60)
        print("  Skipped. Use --import-asbac-data to parse standings and records.")

    # Step 4: Update team with active player IDs
    print("\n" + "=" * 60)
    print("Step 4: Updating team with active players")
    print("=" * 60)
    
    active_player_ids = list(player_map.values())
    db.teams.update_one(
        {"_id": team_id},
        {
            "$set": {
                "active-player-ids": active_player_ids,
                "updated-at": datetime.now(timezone.utc)
            }
        }
    )
    print(f"✓ Updated team with {len(active_player_ids)} active players")
    
    # Summary
    admin_user = "admin"
    admin_pass = "admin"
    print("\n" + "=" * 60)
    print("Seed Summary")
    print("=" * 60)
    print(f"✓ Admin user created/verified: 1")
    print(f"✓ Team created/updated: 1")
    print(f"✓ Players processed: {len(player_map)}")
    print(f"✓ Championships processed: {championships_created}")
    print(f"✓ Total player-championship records: {total_players_processed}")
    if args.import_tournament_matches:
        print(f"✓ Matches from tournament sheets: {matches_from_sheets}")
    if args.import_asbac_data:
        print(f"✓ Standings documents: {standings_count}")
        print(f"✓ Records documents: {records_count}")
    print("\n--- Admin credentials (dev) ---")
    print(f"  Username: {admin_user}")
    print(f"  Password: {admin_pass}")
    print("--------------------------------")
    print("\n✓ Seed completed successfully!")
    
    client.close()


if __name__ == "__main__":
    main()

