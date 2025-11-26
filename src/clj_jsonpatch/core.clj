(ns clj-jsonpatch.core
  (:require [clj-jsonpatch.pointer :as ptr]))

(defn validate-operation
  "Validate a single JSON Patch operation according to RFC 6902"
  [op]
  (let [op-type (:op op)]
    (when-not (contains? #{"add" "remove" "replace" "move" "copy" "test"} op-type)
      (throw (ex-info "Invalid operation type" 
                      {:op op :reason :invalid-operation})))
    
    (when-not (:path op)
      (throw (ex-info "Operation missing required 'path' field" 
                      {:op op :reason :missing-path})))
    
    (case op-type
      "remove" nil
      "add" (when-not (contains? op :value)
              (throw (ex-info "Add operation missing required 'value' field" 
                              {:op op :reason :missing-value})))
      "replace" (when-not (contains? op :value)
                  (throw (ex-info "Replace operation missing required 'value' field" 
                                  {:op op :reason :missing-value})))
      "move" (when-not (:from op)
               (throw (ex-info "Move operation missing required 'from' field" 
                               {:op op :reason :missing-from})))
      "copy" (when-not (:from op)
               (throw (ex-info "Copy operation missing required 'from' field" 
                               {:op op :reason :missing-from})))
      "test" (when-not (contains? op :value)
               (throw (ex-info "Test operation missing required 'value' field" 
                               {:op op :reason :missing-value}))))
    op))

(defn- insert-into-vector
  "Pure insertion into vector at index or append when index is :append or == count."
  [v segment value path]
  (let [index (ptr/parse-array-index segment)
        length (count v)]
    (cond
      (= index :append) (conj v value)
      (<= 0 index length) (into [] (concat (subvec v 0 index) [value] (subvec v index)))
      :else (throw (ex-info "Cannot insert at array index beyond length"
                            {:path path :segment segment :index index
                             :array-length length :reason :index-out-of-bounds})))))

(defn add-operation
  "Apply JSON Patch 'add' operation to document"
  [doc op]
  (let [path (:path op)
        value (:value op)
        segments (ptr/pointer->segments path)]
    (letfn [(step [current [segment & tail]]
              (if (nil? segment)
                value
                (cond
                  (map? current)
                  (assoc current segment (step (get current segment {}) tail))

                  (vector? current)
                  (if (empty? tail)
                    (insert-into-vector current segment value path)
                    (let [index (ptr/parse-array-index segment)
                          length (count current)]
                      (cond
                        (= index :append) (conj current (step {} tail))
                        (< index length) (assoc current index (step (nth current index) tail))
                        (= index length) (conj current (step {} tail))
                        :else (throw (ex-info "Cannot insert at array index beyond length"
                                              {:path path :segment segment :index index
                                               :array-length length :reason :index-out-of-bounds})))))

                  :else
                  (throw (ex-info "Cannot set property on primitive value"
                                  {:path path :segment segment :reason :primitive-set})))))]
      (step doc segments))))

(defn remove-operation
  "Apply JSON Patch 'remove' operation to document"
  [doc op]
  (let [path (:path op)]
    (ptr/remove-pointer doc path)))

(defn replace-operation
  "Apply JSON Patch 'replace' operation to document"
  [doc op]
  (let [path (:path op)
        value (:value op)]
    (ptr/resolve-pointer doc path)
    (ptr/set-pointer doc path value)))

(defn move-operation
  "Apply JSON Patch 'move' operation to document"
  [doc op]
  (let [from (:from op)
        to (:path op)
        value (ptr/resolve-pointer doc from)
        doc-without-source (ptr/remove-pointer doc from)]
    (ptr/set-pointer doc-without-source to value)))

(defn copy-operation
  "Apply JSON Patch 'copy' operation to document"
  [doc op]
  (let [from (:from op)
        to (:path op)
        value (ptr/resolve-pointer doc from)]
    (ptr/set-pointer doc to value)))

(defn test-operation
  "Apply JSON Patch 'test' operation to document"
  [doc op]
  (let [path (:path op)
        expected-value (:value op)
        actual-value (ptr/resolve-pointer doc path)]
    (if (= actual-value expected-value)
      doc
      (throw (ex-info "Test operation failed: values do not match" 
                      {:op op :expected expected-value :actual actual-value 
                       :reason :test-failed})))))

(defn apply-operation
  "Apply a single JSON Patch operation to document"
  [doc op]
  (let [validated-op (validate-operation op)]
    (case (:op validated-op)
      "add" (add-operation doc validated-op)
      "remove" (remove-operation doc validated-op)
      "replace" (replace-operation doc validated-op)
      "move" (move-operation doc validated-op)
      "copy" (copy-operation doc validated-op)
      "test" (test-operation doc validated-op)
      (throw (ex-info "Unsupported operation" 
                      {:op validated-op :reason :unsupported-operation})))))

(defn apply-patch
  "Apply a sequence of JSON Patch operations to document
   Returns new document with all operations applied"
  [doc patch]
  (loop [current-doc doc
         remaining-patch patch
         applied-ops []]
    (if (empty? remaining-patch)
      current-doc
      (let [op (first remaining-patch)
            new-doc (try
                      (apply-operation current-doc op)
                      (catch Exception e
                        (throw (ex-info "JSON Patch operation failed"
                                        {:failed-op op
                                         :applied-ops applied-ops
                                         :remaining-ops (rest remaining-patch)
                                         :original-error e}))))]
        (recur new-doc (rest remaining-patch) (conj applied-ops op))))))


(defn add-op
  "Create an 'add' operation"
  [path value]
  {:op "add" :path path :value value})

(defn remove-op
  "Create a 'remove' operation"
  [path]
  {:op "remove" :path path})

(defn replace-op
  "Create a 'replace' operation"
  [path value]
  {:op "replace" :path path :value value})

(defn move-op
  "Create a 'move' operation"
  [from path]
  {:op "move" :from from :path path})

(defn copy-op
  "Create a 'copy' operation"
  [from path]
  {:op "copy" :from from :path path})

(defn test-op
  "Create a 'test' operation"
  [path value]
  {:op "test" :path path :value value})


(def resolve-pointer ptr/resolve-pointer)
(def set-pointer ptr/set-pointer)
(def remove-pointer ptr/remove-pointer)
(def pointer->segments ptr/pointer->segments)
(def segments->pointer ptr/segments->pointer)
