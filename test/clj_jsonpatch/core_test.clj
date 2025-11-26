(ns clj-jsonpatch.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clj-jsonpatch.core :as core]))

(deftest test-validate-operation
  (testing "Valid operations"
    (is (= {:op "add" :path "/a" :value "test"}
           (core/validate-operation {:op "add" :path "/a" :value "test"})))
    (is (= {:op "remove" :path "/a"}
           (core/validate-operation {:op "remove" :path "/a"}))))

  (testing "Invalid operations"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Invalid operation type"
                          (core/validate-operation {:op "invalid" :path "/a"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing required 'path'"
                          (core/validate-operation {:op "add"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing required 'value'"
                          (core/validate-operation {:op "add" :path "/a"})))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing required 'from'"
                          (core/validate-operation {:op "move" :path "/a"})))))

(deftest test-add-operation
  (testing "Adding to maps"
    (let [doc {"a" "old"}]
      (is (= {"a" "old" "new" "value"}
             (core/add-operation doc {:op "add" :path "/new" :value "value"})))
      (is (= {"a" "new"}
             (core/add-operation doc {:op "add" :path "/a" :value "new"})))))

  (testing "Adding to vectors"
    (let [doc [1 2 3]]
      (is (= [1 2 "new" 3] 
             (core/add-operation doc {:op "add" :path "/2" :value "new"})))
      (is (= [1 2 3 "new"] 
             (core/add-operation doc {:op "add" :path "/3" :value "new"})))
      (is (= [1 2 3 "new"] 
             (core/add-operation doc {:op "add" :path "/-" :value "new"})))))

  (testing "Adding nested structures"
    (let [doc {}]
      (is (= {"a" {"b" {"c" "value"}}}
             (core/add-operation doc {:op "add" :path "/a/b/c" :value "value"}))))))

(deftest test-remove-operation
  (testing "Removing from maps"
    (let [doc {"a" "value" "b" "keep"}]
      (is (= {"b" "keep"}
             (core/remove-operation doc {:op "remove" :path "/a"})))))

  (testing "Removing from vectors"
    (let [doc [1 2 3 4]]
      (is (= [1 3 4]
             (core/remove-operation doc {:op "remove" :path "/1"}))))))

(deftest test-replace-operation
  (testing "Replacing in maps"
    (let [doc {"a" "old"}]
      (is (= {"a" "new"}
             (core/replace-operation doc {:op "replace" :path "/a" :value "new"})))))

  (testing "Replacing in vectors"
    (let [doc [1 2 3]]
      (is (= [1 2 "new"]
             (core/replace-operation doc {:op "replace" :path "/2" :value "new"}))))))

(deftest test-move-operation
  (testing "Moving in maps"
    (let [doc {"a" "value" "b" "keep"}]
      (is (= {"b" "keep" "c" "value"}
             (core/move-operation doc {:op "move" :from "/a" :path "/c"})))))

  (testing "Moving from vector to map"
    (let [doc {"items" [1 2 3]}]
      (is (= {"items" [1 3] "moved" 2}
             (core/move-operation doc {:op "move" :from "/items/1" :path "/moved"}))))))

(deftest test-copy-operation
  (testing "Copying in maps"
    (let [doc {"a" "value" "b" "keep"}]
      (is (= {"a" "value" "b" "keep" "c" "value"}
             (core/copy-operation doc {:op "copy" :from "/a" :path "/c"})))))

  (testing "Copying from vector to map"
    (let [doc {"items" [1 2 3]}]
      (is (= {"items" [1 2 3] "copied" 2}
             (core/copy-operation doc {:op "copy" :from "/items/1" :path "/copied"}))))))

(deftest test-test-operation
  (testing "Successful test"
    (let [doc {"a" "value"}]
      (is (= doc
             (core/test-operation doc {:op "test" :path "/a" :value "value"})))))

  (testing "Failed test"
    (let [doc {"a" "value"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Test operation failed"
                            (core/test-operation doc {:op "test" :path "/a" :value "wrong"}))))))

(deftest test-apply-operation
  (testing "All operation types"
    (let [doc {"users" [{"name" "Alice"} {"name" "Bob"}]}]
      (is (= {"users" [{"name" "Alice"} {"name" "Charlie"}]}
             (core/apply-operation doc {:op "replace" :path "/users/1/name" :value "Charlie"})))
      (is (= {"users" [{"name" "Alice"} {"name" "Bob"} {"name" "Charlie"}]}
             (core/apply-operation doc {:op "add" :path "/users/-" :value {"name" "Charlie"}}))))))

(deftest test-apply-patch
  (testing "Multiple operations"
    (let [doc {"users" [{"name" "Alice"}]}
          patch [{:op "add" :path "/users/-" :value {"name" "Bob"}}
                 {:op "replace" :path "/users/0/name" :value "Alice Smith"}]]
      (is (= {"users" [{"name" "Alice Smith"} {"name" "Bob"}]}
             (core/apply-patch doc patch)))))

  (testing "Complex patch sequence"
    (let [doc {"data" {"values" [1 2 3] "count" 3}}
          patch [{:op "add" :path "/data/values/-" :value 4}
                 {:op "replace" :path "/data/count" :value 4}
                 {:op "copy" :from "/data/values/0" :path "/data/first"}
                 {:op "test" :path "/data/count" :value 4}]]
      (is (= {"data" {"values" [1 2 3 4] "count" 4 "first" 1}}
             (core/apply-patch doc patch))))))

(deftest test-convenience-functions
  (testing "Operation builders"
    (is (= {:op "add" :path "/a" :value "test"}
           (core/add-op "/a" "test")))
    (is (= {:op "remove" :path "/a"}
           (core/remove-op "/a")))
    (is (= {:op "replace" :path "/a" :value "test"}
           (core/replace-op "/a" "test")))
    (is (= {:op "move" :from "/a" :path "/b"}
           (core/move-op "/a" "/b")))
    (is (= {:op "copy" :from "/a" :path "/b"}
           (core/copy-op "/a" "/b")))
    (is (= {:op "test" :path "/a" :value "test"}
           (core/test-op "/a" "test")))))

(deftest test-rfc-examples
  (testing "RFC 6902 examples"
    ;; Example: Adding an object member
    (let [doc {"foo" "bar"}
          patch [{:op "add" :path "/baz" :value "qux"}]]
      (is (= {"foo" "bar" "baz" "qux"}
             (core/apply-patch doc patch))))

    ;; Example: Adding an array element
    (let [doc {"foo" ["bar" "baz"]}
          patch [{:op "add" :path "/foo/1" :value "qux"}]]
      (is (= {"foo" ["bar" "qux" "baz"]}
             (core/apply-patch doc patch))))

    ;; Example: Removing an object member
    (let [doc {"foo" "bar" "baz" "qux"}
          patch [{:op "remove" :path "/baz"}]]
      (is (= {"foo" "bar"}
             (core/apply-patch doc patch))))

    ;; Example: Replacing a value
    (let [doc {"baz" "qux" "foo" "bar"}
          patch [{:op "replace" :path "/baz" :value "boo"}]]
      (is (= {"baz" "boo" "foo" "bar"}
             (core/apply-patch doc patch))))

    ;; Example: Moving a value
    (let [doc {"foo" "bar"}
          patch [{:op "move" :from "/foo" :path "/baz"}]]
      (is (= {"baz" "bar"}
             (core/apply-patch doc patch))))

    ;; Example: Copying a value
    (let [doc {"foo" "bar"}
          patch [{:op "copy" :from "/foo" :path "/baz"}]]
      (is (= {"foo" "bar" "baz" "bar"}
             (core/apply-patch doc patch))))

    ;; Example: Testing a value
    (let [doc {"baz" "qux" "foo" ["a" "b" "c"]}
          patch [{:op "test" :path "/baz" :value "qux"}]]
      (is (= doc
             (core/apply-patch doc patch))))))

(deftest test-error-handling
  (testing "Path not found errors"
    (let [doc {"a" "value"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Path not found"
                            (core/apply-operation doc {:op "remove" :path "/nonexistent"})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Path not found"
                            (core/apply-operation doc {:op "replace" :path "/nonexistent" :value "test"})))))

  (testing "Test operation failures"
    (let [doc {"a" "value"}]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Test operation failed"
                            (core/apply-operation doc {:op "test" :path "/a" :value "wrong"}))))))

(deftest test-re-exported-pointer-functions
  (testing "Pointer functions are re-exported"
    (let [doc {"a" {"b" "value"}}]
      (is (= "value" (core/resolve-pointer doc "/a/b")))
      (is (= {"a" {"b" "new"}}
             (core/set-pointer doc "/a/b" "new")))
      (is (= {"a" {}}
             (core/remove-pointer doc "/a/b")))
      (is (= ["a" "b"] (core/pointer->segments "/a/b")))
      (is (= "/a/b" (core/segments->pointer ["a" "b"]))))))
