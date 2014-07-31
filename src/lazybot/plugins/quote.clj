(ns lazybot.plugins.quote
  (:use [lazybot registry info]
        [lazybot.utilities :only [format-time prefix]]
        [lazybot.plugins.login :only [when-privs]]
        [clj-time.core :only [now hours to-time-zone time-zone-for-id]]
        [clj-time.format :only [unparse unparse-local formatters formatter-local]]
        [somnium.congomongo :only [fetch fetch-one fetch-and-modify insert! destroy!]]
        [somnium.congomongo.coerce :only [ConvertibleFromMongo ConvertibleToMongo]]
        [clojure.string :only [join]])
  (:require [clj-twitch.channel :as twitch])
  (:import (org.joda.time DateTime)
           (java.util Date)))

(defonce message-map (atom {}))

(extend-protocol ConvertibleFromMongo
  Date
  (mongo->clojure [^Date d keywordize] (new DateTime d)))

(extend-protocol ConvertibleToMongo
  DateTime
  (clojure->mongo [^DateTime dt] (.toDate dt)))

(defn parse-int [s]
   (try
     (Integer. (re-find  #"\d+" s ))
     (catch Exception ex nil)))

(defn next-seq [coll channel]
  (fetch-and-modify :sequences {:_id {:coll coll :channel channel}} {:$inc {:seq 1}} :return-new? true :upsert? true))

(defn insert-with-seq! [coll el]
  (insert! coll (assoc el :seq (:seq (next-seq coll (:channel el))))))

(defn is-nick?
  "Check if a supplied argument is a nick in the channel."
  [nick channel]
  (let [users (map first (:users channel))]
    (some #{nick} users )))

(defn add-quote
  "Adds a quote int the db."
  [quote subject from channel]
  (let [game (or (twitch/game (apply str (rest channel))) "<Unknown>")]
    (insert-with-seq! :quotes {:quote quote, :subject subject,
                               :quoted-by from, :channel channel,
                               :time (now), :game game})))

(defplugin
  (:hook
   :on-message
   (fn [{:keys [com bot nick message channel] :as com-m}]
     (when (not= nick (:nick @com))
         (swap! message-map update-in [(:network @com) channel]
                assoc nick message, :channel-last message))))
  (:cmd
   "Get a random Quote"
   #{"randquote"}
   (fn [{:keys [com bot nick message channel] :as com-m}]
     (let [quotes (fetch :quotes :where {:channel channel})
           total (count quotes), quote (rand-nth quotes)
           at (unparse (formatter-local "dd MMM yyyy")
                         (to-time-zone (:time quote) (time-zone-for-id "US/Pacific")))]
       (send-message com-m (str "[" (:seq quote) "/" total "] \"" (:quote quote) "\" - "
                                  (:subject quote) " @ " at " (Game: " (:game quote) ")")))))
  (:cmd
   "Get a Quote with <id>."
   #{"quote"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (if-let [quotenum (parse-int (first args))]
       (if-let [quote (fetch-one :quotes :where {:channel channel :seq quotenum})]
         (let [at (unparse (formatter-local "dd MMM yyyy")
                         (to-time-zone (:time quote) (time-zone-for-id "US/Pacific")))]
          (send-message com-m (str "\"" (:quote quote) "\" - "
                                   (:subject quote) " @ " at " (Game: " (:game quote) ")")))
         (send-message com-m (prefix nick (str "I couldn't find quote #" quotenum ", sorry. :("))))
       (send-message com-m (prefix nick (str (first args) " doesn't seem to be a number."))))))
  (:cmd
   "Adds a quote."
   #{"addquote"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (if-not args
       (send-message com-m "Quote what?")
       (let [maybe-nick (is-nick? (first args) (get (:channels @com) channel))
             subject (or maybe-nick (apply str (rest channel)))
             msg (join " " (if maybe-nick (rest args) args))
             result (add-quote msg subject nick channel)]
         (send-message com-m (str "Quote #" (:seq result) " added. [" (:quote result) " - "
                                  (:subject result) " (Game: " (:game result) ")]"))))))
  (:cmd
   "Grab a quote of what <nick> said last."
   #{"grab"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (if-let [subject (is-nick? (first args) (get (:channels @com) channel))]
       (let [msg (get-in @message-map [(:network @com) channel subject])
             result (add-quote msg subject nick channel)]
         (if (and msg result)
           (send-message com-m (prefix nick (str "Grabbed quote #" (:seq result) ": " subject " \"" msg "\" ")))
           (send-message com-m (prefix nick (str "I couldn't find anything of <" subject "> to grab. :(")))))
       (send-message com-m (prefix nick "Grab whom?")))))
  (:cmd
   "Adds qoute without sanity checks. ADMIN ONLY."
   #{"raq"}
   (fn [{:keys [com bot channel nick args] :as com-m}]
     (when-privs com-m :admin
       (if-not args
         (send-message com-m "Quote what?")
         (let [subject (first args)
               msg (join " " (rest args))
               result (add-quote msg subject nick channel)]
           (send-message com-m (str "Quote #" (:seq result) " added. [" (:quote result) " - "
                                  (:subject result) " (Game: " (:game result) ")]")))))))
  #_(:index [[:nick :server] :unique true]))
