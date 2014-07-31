(ns lazybot.plugins.whatis
  (:use [lazybot registry]
        [lazybot.plugins.login :only [when-privs]]
        [somnium.congomongo :only [fetch fetch-one insert! destroy! update!]]))

(defn tell-about
  ([what who com-m]
   (if-let [result (fetch-one :whatis :where {:subject what})]
     (send-message com-m (str (if who (str who ": ") "") what " is " (:is result)))
     (send-message com-m (str what " does not exist in my database."))))
  ([what com-m]
   (tell-about what nil com-m)))

(defplugin
  (:cmd
   "Teaches the bot a new thing. It takes a name and whatever you want to assign the name
   to. For example: $learn me a human being."
   #{"learn"}
   (fn [{:keys [args] :as com-m}]
     (let [[subject & is] args
           is-s (apply str (interpose " " is))
           locked? (:locked (fetch-one :whatis :where {:subject subject}) false)]
       (if-not locked?
         (do
          (destroy! :whatis {:subject subject})
          (insert! :whatis {:subject subject :is is-s})
          #_(send-message com-m "My memory is more powerful than M-x butterfly. I won't forget it.")
          (send-message com-m "Memorised."))
         (send-message com-m (str "Topic " subject " is locked."))))))

  (:cmd
   "Lock a topic"
   #{"lock"}
   (fn [{:keys [args] :as com-m}]
     (if-let [result (fetch-one :whatis :where {:subject (first args)})]
       (when-privs com-m :admin
                   (update! :whatis result (merge result {:locked true}))
                   (send-message com-m (str "Topic " (first args) " locked.")))
       (send-message com-m (str "Topic " (first args) " not found.")))))

   (:cmd
    "Pass it a key, and it will tell you what is at the key in the database."
    #{"whatis"}
    (fn [{[what] :args :as com-m}]
      (tell-about what com-m)))

   (:cmd
    "Pass it a key, and it will tell the recipient what is at the key in the database via PM
     Example - $tell G0SUB about clojure"
    #{"tell"}
    (fn [{[who _ what] :args :as com-m}]
      (when what
        (tell-about what who com-m))))

   (:cmd
    "Forgets the value of a key."
    #{"forget"}
    (fn [{[what] :args :as com-m}]
      (do (destroy! :whatis {:subject what})
          (send-message com-m (str "If " what " was there before, it isn't anymore. R.I.P.")))))

   (:cmd
    "Gets a random value from the database."
    #{"rwhatis"}
    (fn [com-m]
      (let [what (-> :whatis fetch rand-nth :subject)]
        (tell-about what com-m))))
   (:indexes [[:subject]]))
