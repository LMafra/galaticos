#!/usr/bin/env python3
"""
MongoDB Seed Script for Galáticos
Reads data from Excel .xlsm file and seeds MongoDB database

This script processes an Excel file containing player statistics and championship
data, creating teams, players, and championships in the MongoDB database.
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

# Championship sheet names to skip (summary/aggregate sheets)
SKIP_SHEETS = ["Base de dados", "PLACAS"]

# Position mapping (if needed)
POSITION_OPTIONS = ["Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"]


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

    if "gk" in str(name).upper():
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


def create_championship(db, name: str, season: str = "2025", titles_count: int = 0) -> ObjectId:
    """Create or get championship"""
    championships_collection = db.championships
    
    # Check if championship exists
    championship = championships_collection.find_one({
        "name": name,
        "season": season
    })
    
    if championship:
        return championship["_id"]
    
    # Create championship
    now = datetime.now(timezone.utc)
    format_type = infer_championship_format(name)
    
    championship_doc = {
        "name": name,
        "season": season,
        "format": format_type,
        "start-date": datetime(2025, 1, 1),
        "end-date": datetime(2025, 12, 31),
        "status": "active",
        "titles-count": titles_count,
        "created-at": now,
        "updated-at": now
    }
    
    result = championships_collection.insert_one(championship_doc)
    print(f"  ✓ Created championship: {name} ({format_type})")
    return result.inserted_id


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
    m = re.search(r"(20\\d{2})", text)
    if m:
        season = m.group(1)
        name = text.replace(season, "").strip(" -")
        if not name:
            name = fallback_name
    else:
        season = "2025"
        name = text or fallback_name

    return name, season


def normalize_text_basic(value: str) -> str:
    """
    Basic text normalization: strip, collapse spaces, lowercase.
    (We intentionally keep accents here; player matching already does fuzzy logic.)
    """
    if not value:
        return ""
    text = str(value).strip()
    text = re.sub(r"\\s+", " ", text)
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
) -> None:
    """
    Update a player's aggregated-stats with table (initial columns) values for one championship.
    Used so that abas iniciais / tabela are the baseline; matches will validate later.
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
        if entry.get("championship-id") == championship_id:
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


def process_csv_championship_file(
    db,
    csv_path: Path,
    player_map: Dict[str, ObjectId],
    team_id: ObjectId,
) -> Tuple[int, int, int]:
    """
    Process a single CSV file representing one or more championships.

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

    # Helper to get or create championship for a given label
    def get_or_create_championship_for_label(label: Optional[str]) -> Optional[ObjectId]:
        nonlocal championships_touched

        base_name = csv_path.stem.upper()
        effective_label = label or base_name

        if effective_label in champ_id_by_label:
            return champ_id_by_label[effective_label]

        name, season = parse_championship_label(effective_label, base_name)

        # Try to find existing championship
        existing = championships_collection.find_one({"name": name, "season": season})
        if existing:
            champ_id = existing["_id"]
            champ_name_by_id[champ_id] = existing.get("name", name)
        else:
            champ_id = create_championship(db, name, season, titles_count=0)
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
    ) -> None:
        nonlocal matches_created_or_updated

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
            update_doc = {
                "$set": {
                    "player-statistics": player_stats,
                    "home-score": home_goals,
                    "away-score": opponent_score,
                    "updated-at": now,
                }
            }
            matches_collection.update_one({"_id": existing["_id"]}, update_doc)
            matches_created_or_updated += 1
        else:
            match_doc = {
                "championship-id": championship_id,
                "home-team-id": team_id,
                "away-team-id": None,
                "date": None,
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
            if re.search(r"20\\d{2}", cell):
                current_champ_label = cell
                break

        # Detect phase lines (e.g. "Quartas de Final", "Semi Final", "Final", "Oitavas de Final")
        joined_text = " ".join(row)
        if re.search(r"(?i)quartas? de final", joined_text):
            current_phase = "Quartas de Final"
        elif re.search(r"(?i)semi\\s*final", joined_text):
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
                                # Use table values as baseline stats for this championship
                                champ_name = champ_name_by_id.get(champ_id, "")
                                _update_player_stats_from_table(
                                    players_collection,
                                    player_id,
                                    champ_id,
                                    champ_name,
                                    games,
                                    goals,
                                    assists,
                                    titles,
                                )

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
    Process all CSV files under data/ as championship + matches sources.
    """
    data_dir = Path("data")
    if not data_dir.exists():
        print("\n⚠ data/ directory not found; skipping CSV import")
        return

    csv_files = sorted(p for p in data_dir.glob("*.csv"))
    # Skip consolidated galaticos.csv if present (it's handled via Excel)
    csv_files = [p for p in csv_files if p.name.lower() != "galaticos.csv"]

    if not csv_files:
        print("\n⚠ No CSV championship files found in data/; skipping CSV import")
        return

    print("\n" + "=" * 60)
    print("Step 3.5: Importing championships and matches from CSV files")
    print("=" * 60)

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
        "matches": "Matches"
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
    Main seed function
    
    Processes Excel file and seeds MongoDB database with:
    - Admin user (if --reset is used or doesn't exist)
    - Team information
    - Player data and statistics
    - Championship data
    
    By default, the script is idempotent - it won't create duplicates.
    Use --reset to clear existing data before seeding.
    
    Raises:
        SystemExit: If critical errors occur
    """
    parser = argparse.ArgumentParser(
        description="Seed MongoDB database with data from Excel file",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Normal seed (idempotent - won't create duplicates)
  python seed_mongodb.py
  
  # Clear all data and reseed
  python seed_mongodb.py --reset
  
  # Clear data but keep admin users
  python seed_mongodb.py --reset --keep-admins
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
    
    # Check if Excel file exists
    excel_path = Path(EXCEL_FILE)
    if not excel_path.exists():
        # Try alternative path
        alt_path = Path("data") / excel_path.name
        if alt_path.exists():
            excel_path = alt_path
        else:
            print(f"✗ Error: Excel file '{EXCEL_FILE}' not found!", file=sys.stderr)
            print(f"  Also checked: {alt_path}", file=sys.stderr)
            sys.exit(1)
    
    # Connect to MongoDB
    client = get_mongo_client()
    db = client[DB_NAME]
    
    print(f"\n✓ Using database: {DB_NAME}")
    
    # Clear database if --reset flag is set
    if args.reset:
        clear_database(db, keep_admins=args.keep_admins)
        print()
    
    # Load Excel file
    print(f"\n📖 Reading Excel file: {EXCEL_FILE}")
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
    player_map = create_players(db, players_df, team_id)
    
    # Step 3: Process championship sheets
    print("\n" + "=" * 60)
    print("Step 3: Processing championship sheets")
    print("=" * 60)
    
    championship_sheets = [s for s in sheet_names if s not in SKIP_SHEETS]
    championships_created = 0
    total_players_processed = 0
    
    for sheet_name in championship_sheets:
        print(f"\n📊 Processing sheet: {sheet_name}")
        try:
            sheet_df = pd.read_excel(excel_path, sheet_name=sheet_name, engine="openpyxl")
            # Skip empty sheets
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

    # Step 3.5: Process CSV files for championships, enrollments and matches
    process_all_csv_championships(db, player_map, team_id)

    # Step 3.6: Rebuild aggregated stats from matches
    rebuild_aggregated_stats_from_matches(db)
    
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
    print("\n--- Admin credentials (dev) ---")
    print(f"  Username: {admin_user}")
    print(f"  Password: {admin_pass}")
    print("--------------------------------")
    print("\n✓ Seed completed successfully!")
    
    client.close()


if __name__ == "__main__":
    main()

