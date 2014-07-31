(ns lazybot.plugins.time
  (:use [lazybot utilities info registry]
        [clj-time.core :only [plus minus now interval in-secs hours to-time-zone time-zone-for-id]]
        [clj-time.format :only [unparse unparse-local formatters formatter-local]])
  (:require [clojure.string :as s])
  (:import java.net.InetAddress))

(defplugin
  (:cmd
   "Gets the current time and date in UTC format."
   #{"time"}
   (fn [{:keys [nick bot args] :as com-m}]
     (let [time (unparse (formatters :date-time-no-ms)
                         (if-let [[[m num]] (seq args)]
                           (let [n (try (Integer/parseInt (str num)) (catch Exception _ 0))]
                             (condp = m
                                 \+ (plus (now) (hours n))
                                 \- (minus (now) (hours n))
                                 (now)))
                           (now)))]
       (send-message com-m (prefix nick "The time is now " time))))))
