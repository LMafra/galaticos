(ns galaticos.i18n
  "Dicionário de traduções EN→PT-BR para mensagens de erro vindas do backend.
   `t` tenta casamento exato; depois aplica padrões de prefixo para mensagens
   com partes variáveis; por fim devolve o texto original como fallback."
  (:require [clojure.string :as str]))

(def ^:private dictionary
  {"Unknown error"                                    "Erro desconhecido"
   "Request failed"                                   "Falha na requisição"
   "Network error"                                    "Erro de rede"
   "Match not found"                                  "Partida não encontrada"
   "Championship not found"                           "Campeonato não encontrado"
   "Player not found"                                 "Jogador não encontrado"
   "Team not found"                                   "Time não encontrado"
   "Season not found"                                 "Temporada não encontrada"
   "Authentication required"                          "Autenticação obrigatória"
   "Username and password required"                   "Usuário e senha são obrigatórios"
   "Championship ID required"                         "ID do campeonato obrigatório"
   "Player ID required"                               "ID do jogador obrigatório"
   "Team ID required"                                 "ID do time obrigatório"
   "Season ID required"                               "ID da temporada obrigatório"
   "Championship ID and player ID are required"       "ID do campeonato e do jogador são obrigatórios"
   "Season ID and player ID are required"             "ID da temporada e do jogador são obrigatórios"
   "Unsupported Media Type: application/json required" "Tipo de mídia não suportado: application/json obrigatório"
   "Invalid credentials"                              "Credenciais inválidas"
   "Forbidden"                                        "Acesso negado"
   "Not found"                                        "Não encontrado"
   "Bad request"                                      "Requisição inválida"
   "Internal server error"                            "Erro interno do servidor"})

(def ^:private prefix-patterns
  "Padrões EN→PT para mensagens com sufixo variável (ex. 'Network error: ...')."
  [["Network error:"   "Erro de rede:"]
   ["Request failed:"  "Falha na requisição:"]])

(defn t
  "Traduz uma mensagem de erro para PT-BR. Se não houver correspondência,
   devolve a própria mensagem (facilita telemetria e não esconde erros novos)."
  [message]
  (cond
    (nil? message) nil
    (not (string? message)) (str message)
    :else
    (let [s message]
      (or (get dictionary s)
          (some (fn [[en pt]]
                  (when (str/starts-with? s en)
                    (str pt (subs s (count en)))))
                prefix-patterns)
          s))))
