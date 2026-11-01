# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`yaml-testkit` (`com.yamltestkit:yaml-testkit`) is a library, not a service: a JUnit 5 engine that turns YAML case files into dynamic tests. Consumers put it on their test classpath and write one empty entry class per suite (`@YamlCases(...) class XxxYamlIT extends YamlCaseSuite {}`). `README.md` is the full user-facing reference for the YAML format (call kinds, stub layers, variants, expectations, converters) — read it before changing behaviour, and keep it in sync with any behaviour change.

Java 25, Spring Boot 4 parent (used only for plugin/dependency management), Lombok. The engine is written against **Jackson 2** (`com.fasterxml.jackson`), not the Jackson 3 that Spring Boot 4 manages — don't import `tools.jackson`.

## Commands

```bash
mvn clean verify                 # full build: Surefire (*Test), Failsafe (*IT), JaCoCo 80% line-coverage gate over both
mvn test                         # unit phase only (*Test), incl. YAML `class` cases
mvn test -Dtest=AssertionEvaluatorTest#methodName

# One IT only; skip Surefire tests and the coverage gate, which fails on a partial run
mvn verify -Dit.test=YamlTestkitSelfIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true

# A subset of YAML cases: Ant pattern over <file path relative to glob root>/<case name>
mvn verify -Dit.test=YamlTestkitSelfIT -Dyamlit.filter='verify/no-leak.yaml/*' -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

Surefire and Failsafe load Mockito as a `-javaagent` from the local Maven repo (see `argLine` in `pom.xml`); IDE run configurations need the same agent. JaCoCo data and reports go to `.ci-reports/`.

## Architecture

Package root: `com.yamltestkit` (`src/main/java`), laid out in DDD-style layers. Dependencies point inward only: `api → junit → engine → infrastructure / convert → domain`. No package cycles; keep it that way when adding code.

**Pipeline.** `YamlCaseSuite` (a `@TestFactory`) gets a `YamlCaseEngine` from the JUnit class-level store (closed when the class finishes) and calls `engine.load()` → `engine.plan(files)` → `CaseTreeBuilder` builds folder/file/case dynamic nodes, each calling `engine.run(entry)`. Negative tests and `YamlCaseEngineIT` drive the engine API directly.

- **api** (`com.yamltestkit`) — what consumers touch: `YamlCaseSuite`, `@YamlCases`, and package-private `YamlCasesReader` (annotation + `yamlit.filter` → `SuiteSettings`, kept here so the engine never depends on the API package). `CallKind` for `@YamlCases(kinds = …)` lives in `domain.model`.
- **junit** — inbound JUnit adapter: `CaseTreeBuilder`.
- **engine** (application layer) — orchestration and composition root: `YamlCaseEngine` (wires loaders, executors, Spring lifecycle), `SuiteSettings`, `CasePlan`, `CaseResult`; one `StepExecutor` per `CallKind` (`HttpStepExecutor`, `BeanStepExecutor`, `UnitStepExecutor`); `CaseRun` (per-case state: context, dep mocks, step results/captures for `{ ref }`, resolved by `References`); `StepPolling` (step-level `within`); `EffectChecks` (`verify` → `logs` → `context`, run before the executor's own expectations so asserts can reference `verify[].as` captures); `ReturnAssertion`.
- **domain** — pure logic, no Spring/Mockito/WireMock/Logback/JUnit and no package outside `domain`:
  - `domain.model` — immutable records for everything bound from YAML (`YamlCase`, `Step`, `Call`, `Expect`, `Stub`, `HttpStub`, `ContextFile`, …), `CallKind`, `CaseDefinitionException` (thrown everywhere for an invalid case). `CaseEntry` is sealed: `YamlCase | BrokenCase`.
  - `domain.rules` — `CaseRules`, `ReferenceOrderCheck`: case invariants checked after binding.
  - `domain.plan` — `ContextPlanner` plans every variant from **all loaded cases** (not just filter-selected ones) before any context starts — the "variant-wide mock rule" and the mock/spy/value-bean conflict checks — into `ContextPlans` / `VariantPlan` / `VariantKey`; `StubLayers` merges stub layers (boot-time → `_defaults.yaml` → file `defaults` → case → step; same-key stubs in a higher layer replace lower ones).
  - `domain.assertion` — `JsonComparator` (body modes), `AssertionEvaluator` / `OperatorCheck` (`assert` operators over JsonPath), `BodyAssertion`, `TextAssertion`, `CallCounts`, `CheckResult`.
  - `domain.json` — shared Jackson `JsonMappers` and `Abbreviations` (message shortening).
- **infrastructure** — adapters to frameworks, depend on `domain` (and `convert`), never on `engine`:
  - `yaml` — `CaseResourceLocator` resolves classpath globs; `RootConfigurationLoader` reads `_context.yaml` / `_defaults.yaml` of each glob root (folder before the first wildcard); `CaseFileLoader` does YAML → `SchemaValidator` (`src/main/resources/com/yamltestkit/yaml-testkit.schema.json`) → `DefaultsMerger` → `*Binder` → `domain.rules`. Load failures never throw out of the loader: a file that fails before splitting becomes one `BrokenCase` named `CaseFileLoader.INVALID_FILE`; a single bad case becomes a `BrokenCase` with its own name so siblings still run.
  - `spring` — context lifecycle for `http`/`bean` cases, driven by `domain.plan`: `ContextRegistry` (one shared context per variant, plus fresh ones), `BeanOverridePostProcessor` / `SpyPostProcessor` (mocks, spies, value beans), `SpringCaseLifecycle` (per-case reset: settleMs, mocks, WireMock, logs; registers layered stubs), `ContextAssertion`.
  - `wiremock` — `WireMockServices`, `WireMockStubMapper`, `JournalVerifier`.
  - `mockito` — stubs (`StubApplier`, `AnswerFactory`, `ArgMatcherFactory`, `ExceptionFactory`), `MockVerifier`, and `UnitInstanceFactory` / `DependencyMatcher` building `class`-call instances with mocked constructor deps.
  - `logback` — log capture attached directly to named loggers (independent of additivity/appenders).
  - `reflect` (type and method lookup), `invocation` (`Invocations`, `CallOutcome`), `http` (`HttpRequests`).
- **convert** — public SPI `ValueConverter` and the `ValueConverters` chain (user → protobuf → vavr → CompletableFuture → Optional → builder → Jackson); optional converters are loaded by class name. Depends on `domain.json` and `infrastructure.reflect` only.

Classes are public where another layer uses them; keep everything else package-private.

**Package isolation rule** (enforced by `PackageIsolationTest`): main sources must not reference the self-test fixtures (`com.yamltestkit.selftest`), and may reference `com.google.protobuf` / `io.vavr` only under `convert/protobuf` / `convert/vavr` (those libraries are `<optional>` dependencies). `domain/` must not reference Spring, Mockito, WireMock, Logback or JUnit, nor any engine package outside `domain`.

**Failure messages are part of the contract.** `NegativeCasesIT` runs every case under `selftest/negative/` and asserts exact message substrings (file, case, path, operator, expected/actual). Changing a message means updating that test; adding a check or operator should add a negative case there. `load_whenTheNegativeFolderIsRead_...` fails if a negative case has no expected message.

## Tests

- `src/test/java/.../selftest/app` — a fixture Spring Boot app (`SelftestApplication`, config `selftest/app/selftest.yml`) that `http`/`bean` self-test cases run against. `selftest/unit` — plain classes for `class` cases.
- `src/test/resources/.../selftest/cases/` (run by `YamlTestkitSelfIT`) and `.../selftest/unit-cases/` (run by `YamlTestkitSelfTest`) are the positive, end-to-end examples of every feature — add a YAML case here for new features.
- `src/test/resources/.../loader/` — fixtures for loader unit tests.
- Test method naming: `method_whenCondition_shouldBehavior`.
- Javadoc in the code cites "spec: …" and "design Dn" — these refer to design documents that are not in this repository.
