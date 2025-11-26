<<<<<<< HEAD
# clj-jsonpatch

[![Clojure](https://img.shields.io/badge/Clojure-1.11+-green.svg)](https://clojure.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A pure, zero-dependency, RFC-compliant implementation of **JSON Pointer** ([RFC 6901](https://tools.ietf.org/html/rfc6901)) and **JSON Patch** ([RFC 6902](https://tools.ietf.org/html/rfc6902)) for Clojure.

## Features

- ✅ **RFC 6901/6902 compliant** - Full specification support
- ✅ **Pure functions** - No side effects, immutable data structures
- ✅ **Zero dependencies** - Only Clojure core
- ✅ **Rich error handling** - Detailed `ex-info` with `:reason`, `:path`, `:segment`, `:index`
- ✅ **Array operations** - Insert at index, append with `-`
- ✅ **Deep path support** - Nested maps, vectors, and mixed structures
- ✅ **Escape handling** - Proper `~0` and `~1` encoding/decoding

## Installation

### deps.edn (after first release)

```clojure
{:deps {io.github.ibrahimypr/clj-jsonpatch {:git/tag "v0.1.0" :git/sha "SHORT_SHA"}}}
```

### Local Installation

```bash
git clone https://github.com/ibrahimypr/clj-jsonpatch.git
cd clj-jsonpatch
```

### Requirements
- Clojure 1.11+
- JDK 17+

## Quick Start

```clojure
(require '[clj-jsonpatch.core :as jp])

;; Apply a JSON Patch
(jp/apply-patch
  {:user {:name "Alice" :age 30}}
  [(jp/replace-op "/user/name" "Bob")
   (jp/add-op "/user/email" "bob@example.com")
   (jp/remove-op "/user/age")])
;; => {:user {:name "Bob" :email "bob@example.com"}}

;; Resolve a JSON Pointer
(jp/resolve-pointer {:users [{:name "Alice"} {:name "Bob"}]} "/users/1/name")
;; => "Bob"
```

## API Reference

### JSON Patch Operations

All patch operations follow RFC 6902 specification.

#### `apply-patch`z
Apply a sequence of patch operations to a document.

```clojure
(jp/apply-patch document patch-operations)
```

#### `apply-operation`
Apply a single patch operation.

```clojure
(jp/apply-operation document {:op "add" :path "/foo" :value "bar"})
```

#### Operation Helpers

| Function | Description | Example |
|----------|-------------|---------|
| `add-op` | Add a value | `(jp/add-op "/path" value)` |
| `remove-op` | Remove a value | `(jp/remove-op "/path")` |
| `replace-op` | Replace a value | `(jp/replace-op "/path" value)` |
| `move-op` | Move a value | `(jp/move-op "/from" "/to")` |
| `copy-op` | Copy a value | `(jp/copy-op "/from" "/to")` |
| `test-op` | Test a value | `(jp/test-op "/path" expected)` |

### JSON Pointer Functions

| Function | Description |
|----------|-------------|
| `resolve-pointer` | Get value at pointer path |
| `set-pointer` | Set value at pointer path |
| `remove-pointer` | Remove value at pointer path |
| `pointer->segments` | Convert pointer string to segments |
| `segments->pointer` | Convert segments to pointer string |

## Examples

### Working with Arrays

```clojure
;; Append to array using "-"
(jp/apply-patch
  {:items ["a" "b"]}
  [(jp/add-op "/items/-" "c")])
;; => {:items ["a" "b" "c"]}

;; Insert at specific index
(jp/apply-patch
  {:items ["a" "c"]}
  [(jp/add-op "/items/1" "b")])
;; => {:items ["a" "b" "c"]}
```

### Move and Copy

```clojure
;; Move a value
(jp/apply-patch
  {:source "value" :target nil}
  [(jp/move-op "/source" "/target")])
;; => {:target "value"}

;; Copy a value
(jp/apply-patch
  {:original "data"}
  [(jp/copy-op "/original" "/backup")])
;; => {:original "data" :backup "data"}
```

### Test Operation

```clojure
;; Test passes - returns document
(jp/apply-patch
  {:version 1}
  [(jp/test-op "/version" 1)
   (jp/replace-op "/version" 2)])
;; => {:version 2}

;; Test fails - throws exception
(jp/apply-patch
  {:version 1}
  [(jp/test-op "/version" 2)])
;; => throws ex-info with :reason :test-failed
```

### Escaped Paths

```clojure
;; Handle special characters in keys
(jp/resolve-pointer {"a/b" {"~c" "value"}} "/a~1b/~0c")
;; => "value"
```

## Error Handling

All errors are thrown as `ex-info` with structured data:

```clojure
(try
  (jp/resolve-pointer {:a 1} "/b")
  (catch Exception e
    (ex-data e)))
;; => {:path "/b" :segment "b" :reason :path-not-found ...}
```

### Error Reasons

| Reason | Description |
|--------|-------------|
| `:path-not-found` | Path does not exist in document |
| `:index-out-of-bounds` | Array index exceeds length |
| `:invalid-index` | Invalid array index format |
| `:test-failed` | Test operation value mismatch |
| `:missing-path` | Operation missing required path |
| `:missing-value` | Operation missing required value |
| `:missing-from` | Move/copy missing from field |

## Running Tests

```bash
clojure -M -e "(require 'clj-jsonpatch.core-test 'clj-jsonpatch.pointer-test) (clojure.test/run-all-tests)"
```

## Project Structure

```
clj-jsonpatch/
├── deps.edn
├── src/clj_jsonpatch/
│   ├── core.clj          # JSON Patch implementation
│   └── pointer.clj       # JSON Pointer implementation
└── test/clj_jsonpatch/
    ├── core_test.clj     # Patch tests
    └── pointer_test.clj  # Pointer tests
```

## Design Principles

- **Pure functions** - No mutable state, all functions return new values
- **Zero dependencies** - Only Clojure core library
- **Consistent errors** - All errors use `ex-info` with `:reason` key
- **Modular design** - Pointer engine is independent from patch engine

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
=======
# clj-jsonpatch
>>>>>>> 3c4f25d441de88d33df4d13b3f1dc55af69f989b
