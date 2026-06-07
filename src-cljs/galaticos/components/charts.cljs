(ns galaticos.components.charts
  "Chart wrappers for Recharts components"
  (:require [galaticos.state :as state]
            ["recharts" :refer [ResponsiveContainer LineChart Line CartesianGrid XAxis YAxis Tooltip BarChart Bar PieChart Pie Cell AreaChart Area LabelList Legend]]))

(defn- chart-theme []
  (let [dark? (= "dark" (get-in @state/app-state [:ui :theme]))]
    {:grid (if dark? "#334155" "#E2E8F0")
     :axis (if dark? "#94A3B8" "#64748B")
     :label (if dark? "#E2E8F0" "#334155")
     :tooltip-bg (if dark? "#0f172a" "#ffffff")
     :tooltip-border (if dark? "#334155" "#E2E8F0")}))

(defn- value-label [v]
  (when (number? v) (str v)))

(defn line-chart [{:keys [data x-key y-key stroke label]}]
  (let [{:keys [grid axis label tooltip-bg tooltip-border]} (chart-theme)
        stroke-color (or stroke "#820000")]
    [:> ResponsiveContainer {:width "100%" :height 260}
     [:> LineChart {:data data}
      [:> CartesianGrid {:strokeDasharray "3 3" :stroke grid}]
      [:> XAxis {:dataKey x-key :stroke axis :tick {:fill axis :fontSize 11}}]
      [:> YAxis {:stroke axis :tick {:fill axis :fontSize 11}}]
      [:> Tooltip {:contentStyle {:backgroundColor tooltip-bg
                                 :border (str "1px solid " tooltip-border)
                                 :borderRadius "8px"}}]
      [:> Line {:type "monotone"
                :dataKey y-key
                :name (or label "Valor")
                :stroke stroke-color
                :strokeWidth 2
                :dot {:r 3 :fill stroke-color}
                :activeDot {:r 5}}]
      [:> LabelList {:dataKey y-key :position "top" :fill label :fontSize 11 :formatter value-label}]]]))

(defn bar-chart [{:keys [data x-key y-key fill label]}]
  (let [{:keys [grid axis label tooltip-bg tooltip-border]} (chart-theme)
        fill-color (or fill "#820000")]
    [:> ResponsiveContainer {:width "100%" :height 260}
     [:> BarChart {:data data}
      [:> CartesianGrid {:strokeDasharray "3 3" :stroke grid}]
      [:> XAxis {:dataKey x-key :stroke axis :tick {:fill axis :fontSize 11}}]
      [:> YAxis {:stroke axis :tick {:fill axis :fontSize 11}}]
      [:> Tooltip {:contentStyle {:backgroundColor tooltip-bg
                                 :border (str "1px solid " tooltip-border)
                                 :borderRadius "8px"}}]
      [:> Legend {:wrapperStyle {:fontSize "12px"}}]
      [:> Bar {:dataKey y-key :name (or label "Valor") :fill fill-color :radius [4 4 0 0]}
       [:> LabelList {:dataKey y-key :position "top" :fill label :fontSize 11 :formatter value-label}]]]]))

(defn pie-chart [{:keys [data name-key value-key colors]}]
  (let [colors (or colors ["#820000" "#FFD500" "#3B82F6" "#10B981" "#F97316"])
        {:keys [grid axis tooltip-bg tooltip-border]} (chart-theme)]
    [:> ResponsiveContainer {:width "100%" :height 260}
     [:> PieChart
      [:> Pie {:data data
               :dataKey value-key
               :nameKey name-key
               :innerRadius 50
               :outerRadius 90
               :paddingAngle 3}
       (doall
        (map-indexed
         (fn [idx _entry]
           ^{:key idx}
           [:> Cell {:fill (nth colors (mod idx (count colors)))
                     :stroke (if (= "dark" (get-in @state/app-state [:ui :theme])) "#0f172a" "#fff")
                     :strokeWidth 2}])
         data))]
      [:> Tooltip {:contentStyle {:backgroundColor tooltip-bg
                                 :border (str "1px solid " tooltip-border)}}]
      [:> Legend {:formatter (fn [v _entry] v) :wrapperStyle {:fontSize "12px"}}]]]))

(defn area-chart [{:keys [data x-key y-key stroke fill label]}]
  (let [{:keys [grid axis label tooltip-bg tooltip-border]} (chart-theme)
        stroke-color (or stroke "#820000")]
    [:> ResponsiveContainer {:width "100%" :height 260}
     [:> AreaChart {:data data}
      [:> CartesianGrid {:strokeDasharray "3 3" :stroke grid}]
      [:> XAxis {:dataKey x-key :stroke axis :tick {:fill axis :fontSize 11}}]
      [:> YAxis {:stroke axis :tick {:fill axis :fontSize 11}}]
      [:> Tooltip {:contentStyle {:backgroundColor tooltip-bg
                                 :border (str "1px solid " tooltip-border)}}]
      [:> Area {:type "monotone"
                :dataKey y-key
                :name (or label "Valor")
                :stroke stroke-color
                :fill (or fill "rgba(130,0,0,0.15)")}]
      [:> LabelList {:dataKey y-key :position "top" :fill label :fontSize 11 :formatter value-label}]]]))
