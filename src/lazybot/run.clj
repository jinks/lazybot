(ns lazybot.run
  (:use [lazybot core irc info]
        [clojure.tools.cli :only [parse-opts]]
        [clojure.java.io :only [writer file]])
  (:gen-class))

(defn -main [& args]
  (let [{{:keys [logpath background config-dir help]} :options :as opts}
        (parse-opts args
             [["-b" "--background"
               "Start lazybot in the background. Should only be used along with --logpath."]
              ["-l" "--logpath PATH" "A file for lazybot to direct output to."
               ]
              ["-c" "--config-dir PATH" "Directory to look for config.clj and other configuraiton."]
              ["-h" "--help" nil]])]
    (when help
      (println (:summary opts))
      (System/exit 0))
    (when config-dir
      (alter-var-root #'*lazybot-dir* (constantly (file config-dir))))
    (if background
      (.exec (Runtime/getRuntime)
             (str "java -jar lazybot.jar --logpath " logpath))
      (let [write (if logpath (writer logpath) *out*)
            config (read-config)]
        (doseq [stream [#'*out* #'*err*]]
          (alter-var-root stream (constantly write)))
        (start-server (:servers-port config 8080))
        (initiate-mongo)
        (start-bots (:servers config))))))
