(ns lazybot.core
  (:use [lazybot registry info]
        [clojure.stacktrace :only [root-cause]]
        [somnium.congomongo :only [mongo!]]
        [clojure.set :only [intersection]]
        [compojure.core :only [routes]]
        [ring.middleware.params :only [wrap-params]]
        [ring.adapter.jetty :only [run-jetty]]
        [irclj.events :only [stdout-callback]])
  (:require [irclj.core :as irclj])
  (:import [java.io File FileReader]))

;; This is pretty much the only global mutable state in the entire bot, and it
;; is entirely necessary. We pass the bot refs to commands and hooks, but
;; sometimes it can't be helped and you need to get at the bots outside of
;; a defplugin. Very little code uses this atom.
(def bots
  "All of the bots that are currently running."
  (atom {}))

(defn initiate-mongo
  "Initiate the mongodb connection and set it globally."
  []
  (try
    (mongo! :db (or (:db (read-config)) "lazybot"))
    (catch Throwable e
      (println "Error starting mongo (see below), carrying on without it")
      (.printStackTrace e))))

(defn call-all
  "Call all hooks of a specific type."
  [{bot :bot :as ircm} hook-key]
  (when-not (ignore-message? ircm)
    (doseq [hook (pull-hooks bot hook-key)]
      (hook ircm))))

(defn- augment [type ircm]
  (condp = type
    :on-message (assoc ircm :channel (first (:params ircm)) :message (:text ircm))
    :on-join (assoc ircm :channel (first (:params ircm)))
    :on-part (assoc ircm :channel (first (:params ircm)))
    ircm))

(defn dispatch
  "Dispatch on type of message."
  [irc type m]
  (let [bot  (@bots (:network @irc))
        ircm (merge m bot)]
    (call-all (augment type ircm) type)))

;; Note that even the actual handling of commands is done via a hook.
(def initial-hooks
  "The hooks that every bot, even without plugins, needs to have."
  {:privmsg (fn [irc m] (dispatch irc :on-message m))
   :join    (fn [irc m] (dispatch irc :on-join m))
   :part    (fn [irc m] (dispatch irc :on-part m))
   :mode    (fn [irc m] (dispatch irc :on-mode m))
   :kick    (fn [irc m] (dispatch irc :on-kick m))
   :nick    (fn [irc m] (dispatch irc :on-nick m))
   :on-quit (fn [irc m] (dispatch irc :on-quit m))
   :raw-log stdout-callback})

(defn reload-config
  "Reloads and sets the configuration in a bot."
  [bot]
  (alter bot assoc :config (read-config)))

;; A plugin is just a file on the classpath with a namespace of
;; `lazybot.plugins.<x>` that contains a call to defplugin.
(defn load-plugin
  "Load a plugin (a Clojure source file)."
  [irc refzors plugin]
  (let [ns (symbol (str "lazybot.plugins." plugin))]
    (require ns :reload)
    ((resolve (symbol (str ns "/load-this-plugin"))) irc refzors)))

(defn safe-load-plugin
  "Load a plugin. Returns true if loading it was successful, false if
   otherwise."
  [irc refzors plugin]
  (try
    (load-plugin irc refzors plugin)
    true
    (catch Exception e false)))

(defn load-plugins
  "Load all plugins specified in the bot's configuration."
  [irc refzors]
  (let [info (:config @refzors)]
    (doseq [plug (:plugins (info (:network @irc)))]
      (load-plugin irc refzors plug))))

(defn reload-configs
  "Reloads the bot's configs. Must be ran in a transaction."
  [& bots]
  (doseq [[_ bot] bots]
    (reload-config bot)))

(defn extract-routes
  "Extracts the routes from bots."
  [bots]
  (->> bots
       (mapcat #(->> % :bot deref :modules vals (map :routes)))
       (filter identity)))

(def sroutes nil)

(defn route [rs]
  (alter-var-root #'lazybot.core/sroutes (constantly (wrap-params (apply routes rs)))))

(defn start-server [port]
  (defonce server (run-jetty #'lazybot.core/sroutes
                             {:port port :join? false})))

(defn reload-all
  "A clever function to reload everything when running lazybot from SLIME.
  Do not try to reload anything individually. It doesn't work because of the nature
  of plugins. This makes sure everything is reset to the way it was
  when the bot was first loaded."
  [& bots]
  (require 'lazybot.registry :reload)
  (require 'lazybot.utilities :reload)
  (require 'lazybot.paste :reload)
  (route (extract-routes (vals @bots)))
  (doseq [{:keys [com bot]} @bots]
    (doseq [{:keys [cleanup]} (vals (:modules @bot))]
      (when cleanup (cleanup)))
    (dosync
     (alter bot dissoc :modules)
     (alter bot assoc-in [:modules :internal :hooks] {})
     (reload-config bot))
    (load-plugins com bot)))

(defn reconnect
  "Reconnect a bot's or all bots' irc connection"
  ([] (->> @bots keys (map reconnect)))
  ([server] (let [bot (-> @bots (get server) :bot)
                  com (-> @bots (get server) :com)
                  [port nick pass channels] ((juxt :port :bot-name :bot-password :channels)
                                    (get-in @bot [:config server]))
                  fnmap (:callbacks @com)
                  _ (irclj/kill com)
                  irc (irclj/connect server port nick :pass pass :callbacks fnmap)]
              (apply (partial irclj/join irc) channels)
              (dosync (swap! bots assoc-in [server :com] irc) nil))))
