(ns clj-jsonpatch.pointer
  (:require [clojure.string :as str]))

(defn escape-segment
  "Escape a JSON Pointer segment according to RFC 6901
   ~ -> ~0
   / -> ~1"
  [segment]
  (-> segment
      (str/replace "~" "~0")
      (str/replace "/" "~1")))

(defn unescape-segment
  "Unescape a JSON Pointer segment according to RFC 6901
   ~0 -> ~
   ~1 -> /"
  [segment]
  (-> segment
      (str/replace "~1" "/")
      (str/replace "~0" "~")))

(defn pointer->segments
  "Convert RFC 6901 pointer string to segment vector
   '/a/b/c' -> ['a' 'b' 'c']
   '' -> [] (root pointer)"
  [pointer]
  (cond
    (= pointer "") []
    (not (str/starts-with? pointer "/"))
    (throw (ex-info "Invalid JSON Pointer: must start with /"
                    {:pointer pointer :reason :invalid-format}))
    :else
    (->> (str/split (subs pointer 1) #"/" -1)
         (map unescape-segment)
         vec)))

(defn segments->pointer
  "Convert segment vector to RFC 6901 pointer string
   ['a' 'b' 'c'] -> '/a/b/c'
   [] -> ''"
  [segments]
  (if (empty? segments)
    ""
    (str "/" (str/join "/" (map escape-segment segments)))))

(defn parse-array-index
  "Parse array index from segment string
   Returns integer index if valid, throws if invalid.
   Returns :append for '-' character (RFC 6902 array append)"
  [segment]
  (cond
    (= segment "-") :append
    (re-matches #"-\d+" segment)
    (throw (ex-info "Array index cannot be negative"
                    {:segment segment :reason :negative-index}))
    (re-matches #"\d+" segment)
    (Long/parseLong segment)
    :else
    (throw (ex-info "Invalid array index"
                    {:segment segment :reason :invalid-index}))))

(defn resolve-pointer
  "Resolve JSON Pointer against document and return value at path
   Throws ex-info if path not found or invalid"
  [doc pointer]
  (letfn [(step [current [segment & tail] path-so-far]
            (if (nil? segment)
              current
              (let [new-path (conj path-so-far segment)]
                (cond
                  (map? current)
                  (if (contains? current segment)
                    (step (get current segment) tail new-path)
                    (throw (ex-info "Path not found in map"
                                    {:path pointer :segment segment :current-path new-path
                                     :reason :path-not-found})))

                  (vector? current)
                  (let [index (parse-array-index segment)]
                    (cond
                      (= index :append)
                      (throw (ex-info "Cannot navigate using append index '-'"
                                      {:path pointer :segment segment :current-path new-path
                                       :reason :append-navigation}))

                      (< index (count current))
                      (step (nth current index) tail new-path)

                      :else
                      (throw (ex-info "Array index out of bounds"
                                      {:path pointer :segment segment :index index
                                       :array-length (count current) :current-path new-path
                                       :reason :index-out-of-bounds}))))

                  :else
                  (throw (ex-info "Cannot navigate into primitive value"
                                  {:path pointer :segment segment :current-path new-path
                                   :reason :primitive-navigation}))))))]
    (step doc (pointer->segments pointer) [])))

(defn set-pointer
  "Set value at JSON Pointer path in document
   Returns new immutable document with value set"
  [doc pointer value]
  (letfn [(step [current [segment & tail]]
            (if (nil? segment)
              value
              (cond
                (map? current)
                (let [next-value (get current segment {})]
                  (assoc current segment (step next-value tail)))

                (vector? current)
                (let [index (parse-array-index segment)]
                  (cond
                    (= index :append)
                    (conj current (step {} tail))

                    (< index (count current))
                    (assoc current index (step (nth current index) tail))

                    (= index (count current))
                    (conj current (step {} tail))

                    :else
                    (throw (ex-info "Cannot insert at array index beyond length"
                                    {:path pointer :segment segment :index index
                                     :array-length (count current) :reason :index-out-of-bounds}))))

                :else
                (throw (ex-info "Cannot set property on primitive value"
                                {:path pointer :segment segment :reason :primitive-set})))))]
    (step doc (pointer->segments pointer))))

(defn remove-pointer
  "Remove value at JSON Pointer path from document
   Returns new immutable document with value removed"
  [doc pointer]
  (let [step (fn step [current segments path-so-far]
               (if (empty? segments)
                 (throw (ex-info "Cannot remove root document"
                                 {:path pointer :reason :root-remove}))
                 (let [[segment & tail] segments
                       new-path (conj path-so-far segment)]
                   (cond
                     (map? current)
                     (if (empty? tail)
                       (if (contains? current segment)
                         (dissoc current segment)
                         (throw (ex-info "Path not found in map for removal"
                                         {:path pointer :segment segment :current-path new-path
                                          :reason :path-not-found})))
                       (if (contains? current segment)
                         (assoc current segment (step (get current segment) tail new-path))
                         (throw (ex-info "Path not found in map for removal"
                                         {:path pointer :segment segment :current-path new-path
                                          :reason :path-not-found}))))

                     (vector? current)
                     (let [index (parse-array-index segment)]
                       (cond
                         (= index :append)
                         (throw (ex-info "Cannot remove append index '-'"
                                         {:path pointer :segment segment :current-path new-path
                                          :reason :append-remove}))

                         (empty? tail)
                         (if (< index (count current))
                           (into [] (concat (subvec current 0 index)
                                            (subvec current (inc index))))
                           (throw (ex-info "Array index out of bounds for removal"
                                           {:path pointer :segment segment :index index
                                            :array-length (count current) :reason :index-out-of-bounds})))

                         (< index (count current))
                         (assoc current index (step (nth current index) tail new-path))

                         :else
                         (throw (ex-info "Array index out of bounds for navigation"
                                         {:path pointer :segment segment :index index
                                          :array-length (count current) :reason :index-out-of-bounds}))))

                     :else
                     (throw (ex-info "Cannot remove from primitive value"
                                     {:path pointer :segment segment :reason :primitive-remove}))))))]
    (step doc (pointer->segments pointer) [])))
