(ns lazybot.plugins.twitch
  (:use lazybot.registry)
  (:require [clojure.string :as s]
            [clj-twitch.channel :as twitch]
            [clj-twitch.examples :as ex]))

(defplugin
  (:cmd
   "Lookup the running game of the current twitch channel."
   #{"game"}
   (fn [{:keys [channel args] :as com-m}]
     (let [channel (if-let [chan (first args)] chan (apply str (rest channel)))
           game    (twitch/game channel)
           channel (twitch/pretty channel)]
       (send-message com-m (str "Currently playing " game " on " channel ".")))))
  (:cmd
   "Lookup channel status."
   #{"stats"}
   (fn [{:keys [channel args] :as com-m}]
     (let [channel (if-let [chan (first args)] chan (apply str (rest channel)))
           status  (ex/summary channel)
           channel (twitch/pretty channel)]
       (send-message com-m status)))))
