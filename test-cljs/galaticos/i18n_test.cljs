(ns galaticos.i18n-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [galaticos.i18n :as i18n]))

(deftest exact-matches
  (testing "mensagens conhecidas do backend são traduzidas"
    (is (= "Erro desconhecido" (i18n/t "Unknown error")))
    (is (= "Falha na requisição" (i18n/t "Request failed")))
    (is (= "Partida não encontrada" (i18n/t "Match not found")))
    (is (= "Campeonato não encontrado" (i18n/t "Championship not found")))
    (is (= "Jogador não encontrado" (i18n/t "Player not found")))
    (is (= "Time não encontrado" (i18n/t "Team not found")))
    (is (= "Temporada não encontrada" (i18n/t "Season not found")))
    (is (= "Autenticação obrigatória" (i18n/t "Authentication required")))
    (is (= "Usuário e senha são obrigatórios" (i18n/t "Username and password required")))
    (is (= "ID do campeonato obrigatório" (i18n/t "Championship ID required")))
    (is (= "ID do jogador obrigatório" (i18n/t "Player ID required")))
    (is (= "ID do campeonato e do jogador são obrigatórios"
           (i18n/t "Championship ID and player ID are required")))
    (is (= "ID da temporada e do jogador são obrigatórios"
           (i18n/t "Season ID and player ID are required")))
    (is (= "Tipo de mídia não suportado: application/json obrigatório"
           (i18n/t "Unsupported Media Type: application/json required")))))

(deftest prefix-patterns
  (testing "mensagens com prefixo variável preservam o sufixo"
    (is (= "Erro de rede: ECONNRESET"
           (i18n/t "Network error: ECONNRESET")))
    (is (= "Falha na requisição: 500"
           (i18n/t "Request failed: 500")))))

(deftest fallback
  (testing "mensagens desconhecidas retornam o texto original"
    (is (= "Algum erro novo" (i18n/t "Algum erro novo")))
    (is (= "Custom backend error" (i18n/t "Custom backend error"))))
  (testing "nil e valores não-string"
    (is (nil? (i18n/t nil)))
    (is (= "42" (i18n/t 42)))))
