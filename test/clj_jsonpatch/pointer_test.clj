(ns clj-jsonpatch.pointer-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-jsonpatch.pointer :as ptr]))

(deftest test-escape-segment
  (testing "Basic escaping"
    (is (= "~0" (ptr/escape-segment "~")))
    (is (= "~1" (ptr/escape-segment "/"))))
  (testing "Multiple escapes"
    (is (= "~0~1~0" (ptr/escape-segment "~/~")))
    (is (= "normal" (ptr/escape-segment "normal")))))

(deftest test-unescape-segment
  (testing "Basic unescaping"
    (is (= "~" (ptr/unescape-segment "~0")))
    (is (= "/" (ptr/unescape-segment "~1"))))
  (testing "Multiple unescapes"
    (is (= "~/" (ptr/unescape-segment "~0~1")))
    (is (= "normal" (ptr/unescape-segment "normal")))))

(deftest test-pointer->segments
  (testing "Simple pointers"
    (is (= ["a" "b" "c"] (ptr/pointer->segments "/a/b/c")))
    (is (= ["single"] (ptr/pointer->segments "/single"))))
  (testing "Root pointer"
    (is (= [] (ptr/pointer->segments ""))))
  (testing "Empty segments"
    (is (= ["a" "" "c"] (ptr/pointer->segments "/a//c")))
    (is (= [""] (ptr/pointer->segments "/"))))
  (testing "Escaped segments"
    (is (= ["a/b" "c~d"] (ptr/pointer->segments "/a~1b/c~0d"))))
  (testing "Invalid pointers"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid JSON Pointer"
                          (ptr/pointer->segments "invalid")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid JSON Pointer"
                          (ptr/pointer->segments "a/b/c")))))

(deftest test-segments->pointer
  (testing "Simple segments"
    (is (= "/a/b/c" (ptr/segments->pointer ["a" "b" "c"])))
    (is (= "/single" (ptr/segments->pointer ["single"]))))
  (testing "Empty segments"
    (is (= "" (ptr/segments->pointer [])))
    (is (= "/" (ptr/segments->pointer [""])))
    (is (= "/a//c" (ptr/segments->pointer ["a" "" "c"]))))
  (testing "Escaped segments"
    (is (= "/a~1b/c~0d" (ptr/segments->pointer ["a/b" "c~d"])))))

(deftest test-parse-array-index
  (testing "Valid indices"
    (is (= 0 (ptr/parse-array-index "0")))
    (is (= 42 (ptr/parse-array-index "42")))
    (is (= :append (ptr/parse-array-index "-"))))
  (testing "Invalid indices"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid array index"
                          (ptr/parse-array-index "abc")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Array index cannot be negative"
                          (ptr/parse-array-index "-1")))))

(deftest test-resolve-pointer
  (testing "Map navigation"
    (let [doc {"a" {"b" {"c" "value"}}}]
      (is (= "value" (ptr/resolve-pointer doc "/a/b/c")))
      (is (= {"b" {"c" "value"}} (ptr/resolve-pointer doc "/a")))))

  (testing "Vector navigation"
    (let [doc [1 2 3]]
      (is (= 1 (ptr/resolve-pointer doc "/0")))
      (is (= 3 (ptr/resolve-pointer doc "/2")))))

  (testing "Mixed navigation"
    (let [doc {"users" [{"name" "Alice"} {"name" "Bob"}]}]
      (is (= "Alice" (ptr/resolve-pointer doc "/users/0/name")))
      (is (= "Bob" (ptr/resolve-pointer doc "/users/1/name")))))

  (testing "Root pointer"
    (let [doc {"key" "value"}]
      (is (= doc (ptr/resolve-pointer doc "")))))

  (testing "Error cases"
    (let [doc {"a" {"b" "value"}}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Path not found"
                            (ptr/resolve-pointer doc "/a/c")))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Path not found"
                            (ptr/resolve-pointer doc "/nonexistent"))))

    (let [doc [1 2 3]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Array index out of bounds"
                            (ptr/resolve-pointer doc "/3")))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"append index"
                            (ptr/resolve-pointer doc "/-"))))

    (let [doc "string"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot navigate into primitive"
                            (ptr/resolve-pointer doc "/0"))))))

(deftest test-set-pointer
  (testing "Setting in maps"
    (let [doc {"a" {"b" "old"}}]
      (is (= {"a" {"b" "new"}}
             (ptr/set-pointer doc "/a/b" "new")))
      (is (= {"a" {"b" "old"} "new" "value"}
             (ptr/set-pointer doc "/new" "value")))))

  (testing "Setting in vectors"
    (let [doc [1 2 3]]
      (is (= [1 2 "new"] (ptr/set-pointer doc "/2" "new")))
      (is (= [1 2 3 "new"] (ptr/set-pointer doc "/3" "new")))
      (is (= [1 2 3 "new"] (ptr/set-pointer doc "/-" "new")))))

  (testing "Setting root"
    (let [doc {"old" "value"}]
      (is (= {"new" "value"} (ptr/set-pointer doc "" {"new" "value"})))))

  (testing "Creating nested structures"
    (let [doc {}]
      (is (= {"a" {"b" {"c" "value"}}}
             (ptr/set-pointer doc "/a/b/c" "value")))))

  (testing "Error cases"
    (let [doc [1 2 3]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot insert at array index beyond length"
                            (ptr/set-pointer doc "/5" "value"))))

    (let [doc "string"]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot set property on primitive"
                            (ptr/set-pointer doc "/0" "value"))))))

(deftest test-remove-pointer
  (testing "Removing from maps"
    (let [doc {"a" {"b" "value" "c" "keep"}}]
      (is (= {"a" {"c" "keep"}} 
             (ptr/remove-pointer doc "/a/b")))
      (is (= {} (ptr/remove-pointer doc "/a")))))

  (testing "Removing from vectors"
    (let [doc [1 2 3 4]]
      (is (= [1 3 4] (ptr/remove-pointer doc "/1")))
      (is (= [1 2 3] (ptr/remove-pointer doc "/3")))))

  (testing "Error cases"
    (let [doc {"a" "value"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Cannot remove root document"
                            (ptr/remove-pointer doc ""))))

    (let [doc {"a" "value"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Path not found"
                            (ptr/remove-pointer doc "/nonexistent"))))

    (let [doc [1 2 3]]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Array index out of bounds"
                            (ptr/remove-pointer doc "/3")))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"append index"
                            (ptr/remove-pointer doc "/-"))))))

(deftest test-rfc-examples
  (testing "RFC 6901 examples"
    ;; Example from RFC: {"foo": ["bar", "baz"]}
    (let [doc {"foo" ["bar" "baz"]}]
      (is (= "bar" (ptr/resolve-pointer doc "/foo/0")))
      (is (= "baz" (ptr/resolve-pointer doc "/foo/1")))
      (is (= ["bar" "baz"] (ptr/resolve-pointer doc "/foo"))))))
