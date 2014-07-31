(ns lazybot.plugins.raffle
  (:require [lazybot.registry :refer [send-message defplugin]]
            [lazybot.utilities :refer [format-time prefix]]
            [lazybot.plugins.login :refer [when-privs]]
            [clojure.string :refer [join]]))

(defonce raffles (ref {}))

(defmulti raffle second)

(defmethod raffle "start" [_ item channel nick com-m]
  (cond
   (nil? item)                             (send-message com-m (format "%s: please provide a name for your giveaway! (@raffle start NAME)" nick))
   (set? (get-in @raffles [channel item])) (send-message com-m (format "%s: A raffle for %s already exists, please choose another name or stop the raffle." nick item))
   :else                                   (when-privs com-m :op
                                                       (dosync (alter raffles assoc-in [channel item] #{}))
                                                       (send-message com-m
                                                                     (format "%s has just started a giveaway for %s, join in with '@raffle %s'!"
                                                                             nick item item)))))

(defmethod raffle "winner" [_ item channel nick com-m]
  (when-privs com-m :op
              (let [players (get-in @raffles [channel item])]
                (send-message com-m
                              (condp = players
                                nil (format "%s: Giveaway '%s' not found." nick item)
                                #{} (format "%s: Nobody wanted your giveaway of %s. :(" nick item)
                                (format "AND THE WINNER IS... %s! Congratulations, you won %s!" (rand-nth (seq players)) item))))))

(defmethod raffle "stop" [_ item channel nick com-m]
  (when-privs com-m :op
              (dosync (alter raffles update-in [channel] dissoc item))
              (send-message com-m (format "%s: Giveaway '%s' removed." nick item))))

(defmethod raffle "players" [_ item channel nick com-m]
  (when-privs com-m :op
              (if-let [players (get-in @raffles [channel item])]
               (send-message com-m (format "Viewers joined in raffle for %s: %s" item (join ", " players))))))

(defmethod raffle "list" [_ _ channel nick com-m]
  (if-let [rfls (keys (@raffles channel))]
    (send-message com-m (format "%s: The following giveaways are currently running: %s"
                                nick (join ", " rfls)))
    (send-message com-m (format "%s: No giveaways are currently running." nick))))

(defmethod raffle nil [_ _ channel nick com-m]
  (send-message com-m "I can manage giveaways for you. Please use '@raffle help' for help on the commands."))

(defmethod raffle "help" [_ _ channel nick com-m]
  (send-message com-m "Moderators can start giveaways with '@raffle start [name]', stop them with '@raffle stop [name]' or select a random winner with '@raffle winner [name]'.")
  (send-message com-m "All viewers can check on ongoing giveaways with '@raffle list' and join them with '@raffle [name]."))

(defmethod raffle :default [item _ channel nick com-m]
  (dosync (if-let [players (get-in @raffles [channel item])]
            (do (alter raffles update-in [channel item] conj nick)
                (send-message com-m (format "%s has joined the giveaway for %s, %d viewers playing."
                                            nick item (count (get-in @raffles [channel item])))))
            (send-message com-m (format "%s: Giveaway '%s' not found." nick item)))))

(defplugin
  (:cmd
   "The raffle/giveaway system. Use '@raffle help' for more help."
   #{"raffle"}
   (fn [{:keys [channel nick] [what item] :args :as com-m}]
     (println "RAFFLE" what item "in" channel "by" nick)
     (raffle what item channel nick com-m))))
