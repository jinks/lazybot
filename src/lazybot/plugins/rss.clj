(ns lazybot.plugins.rss
  (:use lazybot.registry
	[lazybot.utilities :only [shorten-url]])
  (:require [clojure.xml :as xml]
	    [clojure.zip :as zip]
	    [clojure.data.zip.xml :as zf]))

(defn cull [zipper]
  (let [items (take 3 (zf/xml-> zipper :channel :item))
	items2 (take 3 (zf/xml-> zipper :item))
	items3 (take 3 (zf/xml-> zipper :entry))]
    (map (fn [item]
	   [(first (zf/xml-> item :title zf/text))
	    (shorten-url (first (if-let [atom-link (seq (zf/xml-> item :link (zf/attr :href)))]
				  atom-link
				  (zf/xml-> item :link zf/text))) "isgd")])
	 (cond (seq items)  items
	       (seq items2) items2
	       (seq items3) items3))))

(defn pull-feed [url]
  (-> url xml/parse zip/xml-zip cull))

(defplugin
  (:cmd
   "Get's the first three results from an RSS or Atom feed."
   #{"rss" "atom"}
   (fn [{:keys [bot channel args] :as com-m}]
     (try
       (doseq [[title link] (pull-feed (first args))]
         (send-message com-m (str title " -- " link)))
       (catch Exception _ (send-message com-m "Feed is unreadable."))))))
