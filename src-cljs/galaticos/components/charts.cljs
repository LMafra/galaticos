(ns galaticos.components.charts
  "Chart wrappers for Recharts components"
  (:require ["recharts" :refer [ResponsiveContainer LineChart Line CartesianGrid XAxis YAxis Tooltip BarChart Bar PieChart Pie Cell AreaChart Area Legend]]))

(defn line-chart [{:keys [data x-key y-key stroke]}]
  [:> ResponsiveContainer {:width "100%" :height 260}
   [:> LineChart {:data data}
    [:> CartesianGrid {:strokeDasharray "3 3" :stroke "#E2E8F0"}]
    [:> XAxis {:dataKey x-key :stroke "#94A3B8"}]
    [:> YAxis {:stroke "#94A3B8"}]
    [:> Tooltip]
    [:> Line {:type "monotone"
              :dataKey y-key
              :stroke (or stroke "#820000")
              :strokeWidth 2
              :dot false}]]])

(defn bar-chart [{:keys [data x-key y-key fill]}]
  [:> ResponsiveContainer {:width "100%" :height 260}
   [:> BarChart {:data data}
    [:> CartesianGrid {:strokeDasharray "3 3" :stroke "#E2E8F0"}]
    [:> XAxis {:dataKey x-key :stroke "#94A3B8"}]
    [:> YAxis {:stroke "#94A3B8"}]
    [:> Tooltip]
    [:> Bar {:dataKey y-key :fill (or fill "#820000")}]]])

(defn pie-chart [{:keys [data name-key value-key colors]}]
  (let [colors (or colors ["#820000" "#FFD500" "#3B82F6" "#10B981" "#F97316"])]
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
         (fn [idx _]
           ^{:key idx}
           [:> Cell {:fill (nth colors (mod idx (count colors)))}])
         data))]
      [:> Tooltip]
      [:> Legend]]]))

(defn area-chart [{:keys [data x-key y-key stroke fill]}]
  [:> ResponsiveContainer {:width "100%" :height 260}
   [:> AreaChart {:data data}
    [:> CartesianGrid {:strokeDasharray "3 3" :stroke "#E2E8F0"}]
    [:> XAxis {:dataKey x-key :stroke "#94A3B8"}]
    [:> YAxis {:stroke "#94A3B8"}]
    [:> Tooltip]
    [:> Area {:type "monotone"
              :dataKey y-key
              :stroke (or stroke "#820000")
              :fill (or fill "rgba(130,0,0,0.15)")}]]])
