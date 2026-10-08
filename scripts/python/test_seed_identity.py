"""Identity rules for the official seed. No MongoDB."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import pandas as pd

import seed_mongodb as seed


class PlayerIdentityTest(unittest.TestCase):
    def test_patrick_gk_is_a_different_person_and_gk_suffix_collapses(self):
        self.assertNotEqual(
            seed.player_identity_key("Patrick"),
            seed.player_identity_key("Patrick GK"),
        )
        self.assertEqual(
            seed.player_identity_key("Cantarelle GK"),
            seed.player_identity_key("Cantarelle"),
        )

        frame = seed.canonicalize_base_dados_frame(
            pd.DataFrame(
                [
                    {"atleta": "Patrick", "campeonato": "Liga", "jogos": 1, "gols": 0, "assistencias": 0, "titulos": 0},
                    {"atleta": "Patrick GK", "campeonato": "Liga", "jogos": 4, "gols": 0, "assistencias": 0, "titulos": 0},
                    {"atleta": "Cantarelle GK", "campeonato": "Liga", "jogos": 2, "gols": 0, "assistencias": 0, "titulos": 0},
                    {"atleta": "Cantarelle", "campeonato": "Liga", "jogos": 3, "gols": 1, "assistencias": 0, "titulos": 1},
                ]
            )
        )
        names = set(frame["atleta"])
        self.assertEqual(names, {"Patrick", "Patrick GK", "Cantarelle"})
        cantarelle = frame[frame["atleta"] == "Cantarelle"].iloc[0]
        self.assertEqual(int(cantarelle["jogos"]), 5)
        self.assertEqual(int(cantarelle["titulos"]), 1)

    def test_alias_sums_stats_and_keeps_max_titles(self):
        self.assertNotEqual(
            seed.player_identity_key("Queiroz"),
            seed.player_identity_key("Romario Queiroz"),
        )
        frame = seed.canonicalize_base_dados_frame(
            pd.DataFrame(
                [
                    {
                        "atleta": "Caio",
                        "campeonato": "ASTCU (campo)",
                        "jogos": 4,
                        "gols": 1,
                        "assistencias": 2,
                        "titulos": 1,
                        "Posição": "Meia",
                    },
                    {
                        "atleta": "Caio Nacfur",
                        "campeonato": "ASTCU",
                        "jogos": 2,
                        "gols": 3,
                        "assistencias": 1,
                        "titulos": 1,
                        "Posição": "",
                    },
                    {
                        "atleta": "Queiroz",
                        "campeonato": "ASTCU",
                        "jogos": 1,
                        "gols": 0,
                        "assistencias": 0,
                        "titulos": 0,
                    },
                    {
                        "atleta": "Romario Queiroz",
                        "campeonato": "ASTCU",
                        "jogos": 8,
                        "gols": 4,
                        "assistencias": 0,
                        "titulos": 0,
                    },
                ]
            )
        )
        caio_rows = frame[frame["atleta"] == "Caio Nacfur"]
        self.assertEqual(len(caio_rows), 1)
        caio = caio_rows.iloc[0]
        self.assertEqual(caio["campeonato"], "ASTCU")
        self.assertEqual(int(caio["jogos"]), 6)
        self.assertEqual(int(caio["gols"]), 4)
        self.assertEqual(int(caio["assistencias"]), 3)
        self.assertEqual(int(caio["titulos"]), 1)
        self.assertEqual(seed._explicit_position_for_player(frame, "Caio Nacfur"), "Meia")
        self.assertEqual(set(frame["atleta"]), {"Caio Nacfur", "Queiroz", "Romario Queiroz"})

    def test_find_player_by_name_uses_identity_only(self):
        romario = object()
        caio = object()
        patrick = object()
        patrick_gk = object()
        player_map = {
            "Romario Queiroz": romario,
            "Caio Nacfur": caio,
            "Patrick": patrick,
            "Patrick GK": patrick_gk,
        }
        self.assertIs(seed.find_player_by_name(player_map, "Caio"), caio)
        self.assertIs(seed.find_player_by_name(player_map, "Romario Queiroz"), romario)
        self.assertIsNone(seed.find_player_by_name(player_map, "Queiroz"))
        self.assertIs(seed.find_player_by_name(player_map, "Patrick GK"), patrick_gk)
        self.assertIs(seed.find_player_by_name(player_map, "Patrick"), patrick)


if __name__ == "__main__":
    unittest.main()
