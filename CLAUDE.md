# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Starting the Application
```bash
# Start REPL and server (recommended)
clj -M:dev
# Then in REPL: (ir/go) to start, (ir/reset) to reload

# Alternative using Make/Babashka
make repl        # Start nREPL
bb run           # Start application
bb nrepl         # Start nREPL
bb cider         # Start CIDER nREPL
```

### Testing
```bash
clj -M:test      # Run all tests
make test        # Alternative via Make
bb test          # Alternative via Babashka
```

### Building
```bash
clj -T:build all  # Build uberjar
make uberjar      # Alternative via Make
bb uberjar        # Alternative via Babashka
```

### CSS Development
```bash
make css         # Watch and compile Tailwind CSS
```

## Architecture Overview

This is a **Clojure web application** demonstrating HTMX async rendering patterns with real-time capabilities.

### Core Technologies
- **Clojure** with **Integrant** for system management
- **HTMX** for frontend interactivity
- **Ring/Reitit** for HTTP routing
- **Server-Sent Events (SSE)** for real-time updates
- **XTDB** as the database
- **Apache Kafka** for event streaming/ETL
- **Tailwind CSS** for styling

### Key Architecture Patterns

#### Async Rendering System
- `my-app.web.async-render` provides `suspense` and `stream` functions
- Components can render placeholders while waiting for async data
- Uses SSE to push rendered content when ready
- Automatic cleanup when clients disconnect

#### ETL/Event Processing
- `my-app.tools.etl-topology` defines Kafka-based ETL pipelines
- Declarative topology with sources, connectors, and sinks
- Flight data processing pipeline as example implementation
- Uses EDN serialization for all Kafka messages

#### System Configuration
- `resources/system.edn` contains Integrant configuration
- Environment-aware configuration with `#env` readers
- Modular component dependencies via `#ig/ref`

### Directory Structure
- `src/my_app/` - Main application code
  - `web/` - HTTP handlers, routing, SSE, middleware
  - `tools/` - Utilities (async, ETL, date handling, etc.)
- `resources/` - Configuration and static assets
- `env/` - Environment-specific configurations
- `test/` - Test files
- `modules/` - Kit framework modules

### Development Workflow
1. Start REPL with `clj -M:dev`
2. In REPL: `(ir/go)` to start system
3. Visit http://localhost:3000
4. Use `(ir/reset)` to reload changes
5. CSS changes require `make css` in separate terminal

### Database
- XTDB stores data in `data/dev/` (development)
- Transaction log, document store, and index store separation
- Configured via `:my-app.db/db-node` in system.edn
