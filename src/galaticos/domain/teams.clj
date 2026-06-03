(ns galaticos.domain.teams
  "Pure team rules.")

(defn- error [type message]
  {:error {:type type :message message}})

(defn- ok [data]
  {:ok data})

(defn can-delete?
  [exists? has-players?]
  (cond
    (not exists?) (error :not-found "Team not found")
    has-players? (error :conflict
                        "Cannot delete team: it has associated players. Please remove players from team first.")
    :else (ok true)))
