#!/usr/bin/env python3
"""
MongoDB Seed Script for Galáticos
Reads data from Excel .xlsm file and seeds MongoDB database

This script processes an Excel file containing player statistics and championship
data, creating teams, players, and championships in the MongoDB database.
"""

import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

try:
    import pandas as pd
    import openpyxl
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
EXCEL_FILE = "data/raw/Galáticos 2025 Automatizada 1.12.xlsm"

# Championship sheet names to skip (summary/aggregate sheets)
SKIP_SHEETS = ["Base de dados", "PLACAS"]

# Position mapping (if needed)
POSITION_OPTIONS = ["Goleiro", "Zagueiro", "Lateral", "Volante", "Meia", "Atacante"]


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


def create_players(db, players_df: pd.DataFrame, team_id: ObjectId) -> Dict[str, ObjectId]:
    """
    Create players from DataFrame
    Returns dict mapping player name -> player ObjectId
    """
    players_collection = db.players
    player_map = {}
    now = datetime.now(timezone.utc)
    
    # Get existing players
    existing_players = {p["name"]: p["_id"] for p in players_collection.find({}, {"name": 1})}
    
    for _, row in players_df.iterrows():
        player_name = normalize_player_name(row.get("Atleta", ""))
        if not player_name or player_name in player_map:
            continue
        
        # Check if player already exists
        if player_name in existing_players:
            player_map[player_name] = existing_players[player_name]
            continue
        
        # Create new player
        player_doc = {
            "name": player_name,
            "nickname": None,
            "position": "Atacante",  # Default position, can be updated later
            "team-id": team_id,
            "active": True,
            "aggregated-stats": {
                "total": {
                    "games": safe_int(row.get("Jogos", 0)),
                    "goals": safe_int(row.get("Gols", 0)),
                    "assists": safe_int(row.get("Assistências", 0)),
                    "titles": safe_int(row.get("Títulos", 0))
                },
                "by-championship": []
            },
            "created-at": now,
            "updated-at": now
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
        
        players_processed += 1
    
    return championship_id, players_processed


def main() -> None:
    """
    Main seed function
    
    Processes Excel file and seeds MongoDB database with:
    - Team information
    - Player data and statistics
    - Championship data
    
    Raises:
        SystemExit: If critical errors occur
    """
    print("=" * 60)
    print("MongoDB Seed Script for Galáticos")
    print("=" * 60)
    
    # Check if Excel file exists
    excel_path = Path(EXCEL_FILE)
    if not excel_path.exists():
        # Try alternative path
        alt_path = Path("data/raw") / excel_path.name
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
    print("\n" + "=" * 60)
    print("Seed Summary")
    print("=" * 60)
    print(f"✓ Team created/updated: 1")
    print(f"✓ Players processed: {len(player_map)}")
    print(f"✓ Championships processed: {championships_created}")
    print(f"✓ Total player-championship records: {total_players_processed}")
    print("\n✓ Seed completed successfully!")
    
    client.close()


if __name__ == "__main__":
    main()

