(ns dapper.filters
  (:use
   [clj-etl-utils.lang-utils :only [raise]])
  (:import
   [com.unboundid.ldap.sdk Filter]))

(defn create [search-str]
  (Filter/create search-str))

(defn equals [attr-name attr-value]
  (Filter/createEqualityFilter (name attr-name) (name attr-value)))
