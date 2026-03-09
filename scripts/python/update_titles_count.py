#!/usr/bin/env python3
"""
Update titles-count for championships based on player aggregated stats.
Reads MONGO_URI and DB_NAME from environment. Used by db:setup (local or Docker).
"""
import os
import sys
from datetime import datetime

from pymongo import MongoClient


def main():
    mongo_uri = os.environ.get("MONGO_URI", "mongodb://localhost:27017")
    db_name = os.environ.get("DB_NAME", "galaticos")

    try:
        client = MongoClient(mongo_uri, serverSelectionTimeoutMS=5000)
        db = client[db_name]

        championships = db.championships.find({})
        updated_count = 0

        for championship in championships:
            championship_id = championship["_id"]
            max_titles = 0

            players = db.players.find({
                "aggregated-stats.by-championship.championship-id": championship_id
            })

            for player in players:
                aggregated_stats = player.get("aggregated-stats", {})
                by_championship = aggregated_stats.get("by-championship", [])

                for champ_stats in by_championship:
                    champ_id = champ_stats.get("championship-id")
                    if str(champ_id) == str(championship_id):
                        titles = champ_stats.get("titles", 0)
                        if isinstance(titles, (int, float)) and titles > max_titles:
                            max_titles = int(titles)
                        break

            result = db.championships.update_one(
                {"_id": championship_id},
                {
                    "$set": {
                        "titles-count": max_titles,
                        "updated-at": datetime.utcnow(),
                    }
                },
            )

            if result.modified_count > 0:
                updated_count += 1
                print(f"  Updated {championship.get('name', 'Unknown')}: titles-count = {max_titles}")

        print(f"✓ Updated {updated_count} championship(s)")
        sys.exit(0)
    except Exception as e:
        print(f"✗ Error updating titles-count: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
