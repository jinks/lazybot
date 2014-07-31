(ns lazybot.plugins.4kb
  (:use [lazybot utilities info registry]
        [clj-time.core :only [now interval to-time-zone time-zone-for-id]]
        [clj-time.format :only [unparse formatter-local]])
  (:import java.net.InetAddress))

(defplugin
  (:cmd
   "Tell the Time in US/Pacific (4kbshort's timezone)."
   #{"kbtime"}
   (fn [{:keys [nick bot args] :as com-m}]
     (let [time (unparse (formatter-local "EEE, dd MMM hh:mm aa")
                         (to-time-zone (now) (time-zone-for-id "US/Pacific")))]
       (send-message com-m (str "The local time for 4kbshort is " time))))))
