(ns galaticos.components.common
  "Shared/common UI components"
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defn loading-spinner []
  [:div.loading "Loading..."])

(defn error-message [message]
  [:div.error {:style {:color "red" :padding "10px"}} message])

(defn- parse-number
  "Try to parse a string as a number, return nil if not a number"
  [s]
  (let [parsed (js/parseFloat s)]
    (when (and (not (js/isNaN parsed))
               (not (js/isNaN (js/parseInt s 10))))
      parsed)))

(defn- compare-values
  "Compare two values for sorting, handling strings and numbers"
  [a b]
  (let [a-str (str a)
        b-str (str b)
        a-num (parse-number a-str)
        b-num (parse-number b-str)]
    (cond
      (and a-num b-num) (compare a-num b-num)
      a-num -1
      b-num 1
      :else (compare (str/lower-case a-str) (str/lower-case b-str)))))

(defn table [_headers _rows & _opts]
  (let [search-query (r/atom "")
        sort-column (r/atom nil)
        sort-direction (r/atom :asc)]
    (fn [headers rows & {:keys [on-row-click row-data sortable? sortable-columns]}]
      (let [headers-vec (vec headers)
            rows-vec (vec rows)
            row-data-vec (if row-data (vec row-data) (repeat nil))
            sortable-cols (if sortable-columns
                            (set sortable-columns)
                            (set (range (count headers-vec))))
            handle-header-click (fn [col-idx]
                                  (if (and (= @sort-column col-idx)
                                           (contains? sortable-cols col-idx))
                                    (swap! sort-direction #(if (= % :asc) :desc :asc))
                                    (do
                                      (reset! sort-column col-idx)
                                      (reset! sort-direction :asc))))
            row-pairs (map vector rows-vec row-data-vec)
            filtered-pairs (if (or (nil? @search-query) (str/blank? @search-query))
                             row-pairs
                             (let [query-lower (str/lower-case @search-query)]
                               (filter (fn [[row _]]
                                         (some (fn [cell]
                                                 (str/includes? (str/lower-case (str cell)) query-lower))
                                               row))
                                       row-pairs)))
            sorted-pairs (if (and sortable?
                                  (some? @sort-column)
                                  (contains? sortable-cols @sort-column))
                           (let [sorted (sort-by (fn [[row _]]
                                                   (nth row @sort-column))
                                                 compare-values
                                                 filtered-pairs)]
                             (if (= @sort-direction :desc)
                               (reverse sorted)
                               sorted))
                           filtered-pairs)
            final-rows (mapv first sorted-pairs)
            final-row-data (mapv second sorted-pairs)]
        [:div
         [:div {:style {:margin-bottom "12px"}}
          [:input {:type "text"
                   :value @search-query
                   :on-change #(reset! search-query (-> % .-target .-value))
                   :placeholder "Buscar..."
                   :style {:width "100%"
                          :max-width "400px"
                          :padding "8px"
                          :border "1px solid #ccc"
                          :border-radius "4px"
                          :font-size "14px"}}]]
         [:table {:style {:width "100%" :border-collapse "collapse"}}
          [:thead
           [:tr
            (doall
             (map-indexed
              (fn [idx header]
                (let [is-sortable (and sortable? (contains? sortable-cols idx))
                      is-sorted (= @sort-column idx)
                      sort-indicator (when is-sorted
                                       (if (= @sort-direction :asc) " ↑" " ↓"))]
                  ^{:key idx}
                  [:th {:on-click (when is-sortable #(handle-header-click idx))
                        :style (merge {:border "1px solid #ddd"
                                       :padding "8px"
                                       :text-align "left"}
                                      (when is-sortable {:cursor "pointer"
                                                         :user-select "none"
                                                         :background-color (when is-sorted "#e8f4f8")}))}
                   (str header sort-indicator)]))
              headers-vec))]]
          [:tbody
           (doall
            (map-indexed
             (fn [idx row]
               ^{:key idx}
               [:tr {:on-click (when on-row-click #(on-row-click (nth final-row-data idx)))
                     :style (merge {:border "1px solid #ddd"}
                                   (when on-row-click {:cursor "pointer"}))}
                (doall
                 (map-indexed
                  (fn [idx2 cell]
                    ^{:key idx2}
                    [:td {:style {:border "1px solid #ddd" :padding "8px"}} (str cell)])
                  row))])
             final-rows))]]]))))

(defn button [text on-click & {:keys [class style disabled type]}]
  [:button {:on-click on-click
            :class class
            :type (or type "button")
            :disabled disabled
            :style (merge {:padding "8px 16px"
                           :cursor (if disabled "not-allowed" "pointer")
                           :opacity (when disabled 0.7)
                           :border "1px solid #ccc"
                           :border-radius "4px"
                           :background-color "#f0f0f0"}
                          style)}
   text])

(defn input-field [label value on-change & {:keys [type placeholder]}]
  [:div {:style {:margin-bottom "10px"}}
   [:label {:style {:display "block" :margin-bottom "5px"}} label]
   [:input {:type (or type "text")
            :value value
            :on-change #(on-change (-> % .-target .-value))
            :placeholder placeholder
            :style {:width "100%"
                   :padding "8px"
                   :border "1px solid #ccc"
                   :border-radius "4px"}}]])

(defn select-field [label value options on-change]
  [:div {:style {:margin-bottom "10px"}}
   [:label {:style {:display "block" :margin-bottom "5px"}} label]
   (into [:select {:value value
                   :on-change #(on-change (-> % .-target .-value))
                   :style {:width "100%"
                           :padding "8px"
                           :border "1px solid #ccc"
                           :border-radius "4px"}}]
         (map (fn [[opt-value opt-label]]
                [:option {:key opt-value :value opt-value} opt-label])
              options))])

