#!/usr/bin/env python3
"""
Print collection counts after seed. Used by db:seed / db:seed-full for verification.
Reads MONGO_URI and DB_NAME from environment.
"""
import os
import sys

from pymongo import MongoClient


def main():
    mongo_uri = os.environ.get("MONGO_URI", "mongodb://localhost:27017")
    db_name = os.environ.get("DB_NAME", "galaticos")

    try:
        client = MongoClient(mongo_uri, serverSelectionTimeoutMS=5000)
        db = client[db_name]

        admins_count = db.admins.count_documents({})
        teams_count = db.teams.count_documents({})
        players_count = db.players.count_documents({})
        championships_count = db.championships.count_documents({})
        seasons_count = db.seasons.count_documents({})
        matches_count = db.matches.count_documents({})
        standings_count = db.standings.count_documents({})
        records_count = db.records.count_documents({})

        players_with_pm = db.players.count_documents(
            {"aggregated-stats.by-championship.pre-match-stats": {"$exists": True}}
        )
        players_with_frozen = db.players.count_documents(
            {"aggregated-stats.by-championship.baseline-match-rollup": {"$exists": True}}
        )

        print(f"  Admins: {admins_count}")
        print(f"  Teams: {teams_count}")
        print(f"  Players: {players_count}")
        print(f"  Championships: {championships_count}")
        print(f"  Seasons: {seasons_count}")
        print(f"  Matches: {matches_count}")
        if standings_count or records_count:
            print(f"  Standings: {standings_count}")
            print(f"  Records: {records_count}")
        print(f"  Players with pre-match-stats rows: {players_with_pm}")
        print(f"  Players with baseline-match-rollup rows: {players_with_frozen}")

        if admins_count > 0 and teams_count > 0 and players_count > 0:
            print("✓ Data verification successful")
            if matches_count == 0:
                print(
                    "  Note: no matches — use db:seed --full or db:seed-full for tournament/CSV matches",
                    file=sys.stderr,
                )
            sys.exit(0)
        else:
            print("⚠ Warning: Some collections appear to be empty", file=sys.stderr)
            sys.exit(0)
    except Exception as e:
        print(f"⚠ Could not verify data: {e}", file=sys.stderr)
        sys.exit(0)


if __name__ == "__main__":
    main()
