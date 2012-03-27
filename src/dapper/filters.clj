(ns dapper.filters
  (:use
   [clj-etl-utils.lang-utils :only [raise]])
  (:import
   [com.unboundid.ldap.sdk Filter]))

(defn create [search-str]
  (Filter/create search-str))

(defn equals [attr-name attr-value]
  (Filter/createEqualityFilter (name attr-name) (name attr-value)))

(defn any [attr-name]
  (Filter/createPresenceFilter (name attr-name)))

(defn and [& filters]
  (Filter/createANDFilter filters))

(defn or [& filters]
  (Filter/createORFilter filters))

(defn not [filter]
  (Filter/createNOTFilter filter))

(defn greater-or-equal [attr-name attr-value]
  (Filter/createGreaterOrEqualFilter (name attr-name) (name attr-value)))

(defn less-or-equal [attr-name attr-value]
  (Filter/createGreaterOrEqualFilter (name attr-name) (name attr-value)))


;; Useful shorthands (maybe?)
(defn = [& args]
  (apply equals args))

(defn >= [& args]
  (apply greater-or-equal args))

(defn <= [& args]
  (apply less-or-equal args))

(defn ! [filter]
  (not filter))