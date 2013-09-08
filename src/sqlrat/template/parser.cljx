(ns sqlrat.template.parser
  (:require [clojure.string :as str]
            [sqlrat.util :as u]
            [sqlrat.template.internal :as it])
  (#+clj :require #+cljs :require-macros [sqlrat.util-macros :as um]))


#+cljs (defn char?
         [x]
         (and (string? x) (= 1 (count x))))


(defn parse-tokens
  "Parse a SQL string into tokens, extracting embedded :keywords as keywords.
  Embedded comments and embedded escape characters in literals are not supported."
  ([sql opts] {:pre [(string? sql) (map? opts)]}
     (let [{:keys [markers kdelims]} opts
           sv-conj #(it/sqlvec-conj %1 %2 "")]
       (um/when-assert
        (assert (set? markers)) (assert (every? char? markers))
        (assert (set? kdelims)) (assert (every? char? kdelims)))
       (loop [schars (seq sql)
              sqlvec [""]
              litc nil  ; literal marker char
              kbuf []]
         ;; #_Comment/uncomment the line below for debugging
         #_(println (apply format "schars %s, sqlvec %s, litc %s, kbuf %s"
                         (map pr-str [schars sqlvec litc kbuf])))
         (if (empty? schars)
           (vec (map #(if (string? %) (str/trim %) %) sqlvec))
           (let [each (first schars)
                 push #(sv-conj sqlvec (str each))
                 [schars sqlvec litc kbuf]
                 (cond
                  ;; literal in progress
                  litc [schars (push) (and (not= each litc) litc) kbuf]
                  ;; non-empty keyword buffer implies keyword parsing in progress
                  (seq kbuf) (let [eos? (not (next schars))
                                   del? (kdelims each)
                                   eok? (or eos? del?)]
                               (if (= kbuf [\:])
                                 (if eok?
                                   [(cons each schars) (sv-conj sqlvec ":") nil []]
                                   [schars sqlvec nil (conj kbuf each)])
                                 (if eok?
                                   [(if (and eos? (not del?)) schars (cons each schars))
                                    (conj sqlvec (->> (if del? kbuf (conj kbuf each))
                                                      rest
                                                      str/join
                                                      keyword))
                                    nil []]
                                   [schars sqlvec nil (conj kbuf each)])))
                  ;; literal request?
                  (markers each) [schars (push) each kbuf]
                  ;; keyword request?
                  (and (= each \:)
                       (let [lt (last sqlvec)]
                         (and (string? lt)
                              (not= \: (last lt))
                              (kdelims (last lt))))) [schars sqlvec nil [each]]
                  ;; catch-all
                  :otherwise [schars (push) litc []])]
             (recur (rest schars) sqlvec litc kbuf))))))
  ([sql]
     (parse-tokens sql {:markers #{\' \"}
                        :kdelims #{\space \! \# \$ \% \& \( \) \* \+ \,
                                   \/ \: \; \< \= \> \@ \[ \] \^ \`}})))
