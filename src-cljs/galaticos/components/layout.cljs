(ns galaticos.components.layout
  "Main layout component with navigation"
  (:require [galaticos.state :as state]
            [galaticos.routes :as routes]
            [galaticos.api :as api]
            [reitit.frontend.easy :as rfe]))

(defn nav-link [route-name text]
  [:a {:href (routes/href route-name)
       :style {:padding "10px"
              :text-decoration "none"
              :color "#333"
              :margin-right "10px"}}
   text])

(defn- user-label [user]
  (cond
    (map? user) (or (:username user) (:name user) (:email user))
    (string? user) user
    :else "Usuário"))

(defn header []
  [:header {:style {:background-color "#f8f8f8"
                   :padding "20px"
                   :border-bottom "1px solid #ddd"
                   :margin-bottom "20px"}}
   [:h1 {:style {:margin "0 0 10px 0"}} "Galáticos - Sistema de Gestão de Elenco"]
   [:nav
    [nav-link :dashboard "Dashboard"]
    [nav-link :players "Jogadores"]
    [nav-link :matches "Partidas"]
    [nav-link :championships "Campeonatos"]
    (let [{:keys [authenticated auth-loading? user]} @state/app-state]
      (cond
        auth-loading? [:span {:style {:float "right" :color "#999"}} "Verificando sessão..."]
        authenticated [:span {:style {:float "right"}}
                      "Logado como: " (user-label user) " | "
                      [:a {:href "#"
                          :on-click (fn [e]
                                     (.preventDefault e)
                                     (api/logout
                                      (fn [_]
                                        (state/clear-auth!)
                                        (api/clear-token!)
                                        (rfe/push-state :login))
                                      (fn [err]
                                        (js/console.error "Logout error:" err)
                                        ;; Still clear local state even if API call fails
                                        (state/clear-auth!)
                                        (api/clear-token!)
                                        (rfe/push-state :login))))
                          :style {:color "#c00"
                                 :text-decoration "none"
                                 :margin-left "5px"}}
                       "Sair"]]
        :else [:span {:style {:float "right" :color "#999"}} "Não autenticado"]))]])

(defn footer []
  [:footer {:style {:margin-top "40px"
                   :padding "20px"
                   :border-top "1px solid #ddd"
                   :text-align "center"
                   :color "#666"}}
   "© 2025 Galáticos - Sistema de Gestão de Elenco Esportivo"])

(defn layout [content]
  [:div {:style {:max-width "1200px"
                :margin "0 auto"
                :padding "20px"}}
   [header]
   (when (:error @state/app-state)
     [:div {:style {:background-color "#fee"
                   :border "1px solid #fcc"
                   :padding "10px"
                   :margin-bottom "20px"
                   :border-radius "4px"}}
      (:error @state/app-state)
      [:button {:on-click #(state/clear-error!)
               :style {:float "right" :margin-left "10px"}} "×"]])
   (if (:loading @state/app-state)
     [:div {:style {:text-align "center" :padding "40px"}} "Carregando..."]
     [:main content])
   [footer]])

