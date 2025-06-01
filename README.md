# HTMX Async Rendering

This is a playground project where I'm experimenting with HTMX and async
rendering using Clojure.

Start a [REPL](#repls) in your editor or terminal of choice.

Start the server with:

```clojure
(ir/go)
```

And visit http://localhost:3000 in your browser.

System configuration is available under `resources/system.edn`.

To reload changes:

```clojure
(ir/reset)
```

## REPLs

### Cursive

Configure a [REPL following the Cursive documentation](https://cursive-ide.com/userguide/repl.html). Using the default "Run with IntelliJ project classpath" option will let you select an alias from the ["Clojure deps" aliases selection](https://cursive-ide.com/userguide/deps.html#refreshing-deps-dependencies).

### CIDER

Use the `cider` alias for CIDER nREPL support (run `clj -M:dev:cider`). See the [CIDER docs](https://docs.cider.mx/cider/basics/up_and_running.html) for more help.

### Command Line

Run `clj -M:dev:nrepl` or `make repl`.
