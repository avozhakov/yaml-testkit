# YAML Testkit

The YAML Testkit runs integration and unit tests that are written as YAML files. You write no Java for a test case. One small entry class finds the YAML files, and the engine turns every case in them into a JUnit test.

A case calls the code in one of three ways:

1. `http` sends an HTTP request to the running Spring Boot application on a real port.
2. `bean` calls a method of a bean in the running application context.
3. `class` creates an object of a class without Spring and calls one of its methods.

The engine code is in `com.yamltestkit` (`src/main/java`): the public API (`YamlCaseSuite`, `@YamlCases`) in the root package, the converter SPI in `convert`, and the engine itself in the `engine`, `domain` and `infrastructure` packages. The JSON Schema for the files is `src/main/resources/com/yamltestkit/yaml-testkit.schema.json`; it is packaged into the JAR. The engine's own tests are in `com.yamltestkit.selftest` (`src/test/java`), and their case files under `src/test/resources/com/yamltestkit/selftest/cases/` and `.../selftest/unit-cases/` are working examples of every feature.

Add the library to the test classpath of the service:

```xml
<dependency>
    <groupId>com.yamltestkit</groupId>
    <artifactId>yaml-testkit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

Build the library with `mvn clean verify`: Surefire runs the `*Test` classes, Failsafe runs the `*IT` classes, and JaCoCo checks 80 % line coverage over both.

## Contents

1. [Quick start](#quick-start)
2. [Case files](#case-files)
3. [Call kinds](#call-kinds)
4. [Steps and references](#steps-and-references)
5. [`_context.yaml`](#_contextyaml)
6. [`_caseDefaults.yaml` and stub layers](#_casedefaultsyaml-and-stub-layers)
7. [Variants and the variant-wide mock rule](#variants-and-the-variant-wide-mock-rule)
8. [Stubs on mocks and spies](#stubs-on-mocks-and-spies)
9. [WireMock services](#wiremock-services)
10. [Expectations](#expectations)
11. [Verification](#verification)
12. [Logs and context checks](#logs-and-context-checks)
13. [Polling, fresh contexts and `settleMs`](#polling-fresh-contexts-and-settlems)
14. [Running a subset of cases](#running-a-subset-of-cases)
15. [Schema mapping in IntelliJ IDEA](#schema-mapping-in-intellij-idea)
16. [Adding a value converter](#adding-a-value-converter)
17. [Things to know](#things-to-know)
18. [What the testkit does not support](#what-the-testkit-does-not-support)

## Quick start

1. Create an entry class. Failsafe runs `*IT` classes, so an entry class for `http` and `bean` cases ends with `IT`. Surefire runs `*Test` classes, so an entry class for `class` cases ends with `Test`.

   ```java
   import com.yamltestkit.YamlCaseSuite;
   import com.yamltestkit.YamlCases;
   import com.yamltestkit.domain.model.CallKind;

   @YamlCases(value = "com/example/yaml/cases/**", kinds = {CallKind.HTTP, CallKind.BEAN})
   class ExampleServiceYamlIT extends YamlCaseSuite {
   }

   @YamlCases(value = "com/example/yaml/unit-cases/**", kinds = CallKind.CLASS)
   class ExampleServiceYamlTest extends YamlCaseSuite {
   }
   ```

   A glob is relative to the classpath root. `*` matches inside one folder, `**` matches across folders. `kinds` keeps only the cases whose calls are all of these kinds; without `kinds`, all kinds run.

2. Put the files under `src/test/resources`:

   ```
   com/example/yaml/cases/
       _context.yaml        how to start the application (needed for http and bean cases)
       _caseDefaults.yaml   stubs applied before every http / bean case (optional)
       discover/ebay-off.yaml
       ads/table.yaml
   ```

   The folder before the first wildcard of a glob is its root (`com/example/yaml/cases/`). The engine reads `_context.yaml` and `_caseDefaults.yaml` from that root. A file whose name starts with `_` is never a case file.

3. Write a case and run the entry class. The IDE shows the cases as a tree: folder, then file, then case name. Cases of one file run in file order.

   ```yaml
   name: an ad request returns two ads
   call: { http: GET /ads, query: { plid: 14019, qt: shoes } }
   expect:
     status: 200
     assert:
       - { path: "$.ads", size: 2 }
   ```

## Case files

A file holds one case, or a list of cases with optional shared `caseDefaults`. Top-level keys of a list file are `define` (optional), `caseDefaults` (optional) and `cases`. A single-case file has the case's own keys at the top level, plus an optional `define`.

```yaml
caseDefaults:
  mocks:
    - bean: dspClient
      method: requestAds
      returns:
        ads: [ { id: t1, title: Term ad, qt: term, section: top, price: 10, landingUrl: "https://shop.example/t1" } ]
  call: { http: GET /ads }
  expect: { status: 200 }
cases:
  - name: "v2.0 plain term"
    call: { query: { plid: 14019, v: "2.0", qt: "nad+1600" } }
    expect:
      assert: [ { path: "$.ads[0].qt", absent: true } ]

  - name: "v2.1 JSON array term"
    call: { query: { plid: 14019, v: "2.1", qt: '["shoes","socks"]' } }
    expect:
      assert: [ { path: "$.ads[*].qt", containsExactly: [ term ] } ]
```

Rules:

1. Every case has a `name`, unique in its file.
2. Every case has exactly one `call` or a list of `steps`.
3. The file's `caseDefaults` are merged into each case before the case's own values. A case value wins. Objects (`call`, its `query`, `headers` and `deps`, `expect`, `expect.headers`, `bodyText`) merge key by key. A list or a payload in the case replaces the default one. `mocks`, `spies` and `http` in `caseDefaults` are a separate stub layer (see [stub layers](#_casedefaultsyaml-and-stub-layers)).
4. A payload (`args`, `returns`, `body`, an expected value) is written inline or as `{ file: <path> }`. The path is relative to the case file. A file that is not JSON gives its text.
5. YAML `null` is Java `null`. For an `Optional` target it is `Optional.empty()`.
6. `"#any"` must be in quotes, because `#` starts a YAML comment.
7. `context: <variant>` selects a context variant, and `freshContext: true` asks for a new context (see [variants](#variants-and-the-variant-wide-mock-rule) and [fresh contexts](#polling-fresh-contexts-and-settlems)).
8. YAML anchors, aliases and `<<` merge keys work in every YAML file the engine reads (see [anchors and merge keys](#anchors-aliases-and-merge-keys)).
9. `define` is an optional top-level block of fragments for anchors. The engine applies it nowhere (see [`define`](#a-place-for-anchors-define)). It is not `caseDefaults`: `caseDefaults` is merged into every case, `define` into none.

### Anchors, aliases and merge keys

An anchor (`&name`) marks a value, and an alias (`*name`) later in the same file inserts a copy of it. A merge key (`<<`) copies the keys of one mapping, or of a list of mappings, into another mapping, where keys can be overridden:

```yaml
define:
  request: &request
    intent: &intent { prompt: running shoes, ad_count: 2 }
    meta: { request_id: req-1 }
caseDefaults:
  call: { http: POST /discover }
cases:
  - name: full request
    call: { body: *request }

  - name: one ad
    call:
      body:
        <<: *request                          # intent and meta of the defined request
        intent: { <<: *intent, ad_count: 1 }  # prompt stays, ad_count changes
```

1. Anchors and merge keys are resolved when the file is read, before schema validation and before the `caseDefaults` merge. The schema, the binders and the failure messages see only the resolved values.
2. An alias inserts an independent copy of an object, a list or a scalar. A scalar keeps its type: an alias to `42` is the number 42.
3. An anchor works only after its definition, in the same file. Define it in the top-level `define` block, or at its first use. The engine reads `_context.yaml`, `_caseDefaults.yaml`, every case file and every YAML payload file separately, so an anchor does not reach another file.
4. A merge is shallow, as in the YAML merge-key specification. A key written in the mapping wins over a merged key. In `<<: [*a, *b]`, a key of `*a` wins over the same key of `*b`. A merged value that is an object is replaced as a whole, not merged: to change one key of a nested object, merge at that level, like `intent` above.
5. An alias to an unknown anchor, and a `<<` whose value is not a mapping or a list of mappings, fail the file when it loads. The message gives the line and the column.
6. Aliases may add at most 100 000 values to one file. A file with more (nested aliases multiply) fails when it loads.

#### A place for anchors: `define`

`define` is an optional top-level key of a case file, `_context.yaml` and `_caseDefaults.yaml`. Its value is any YAML, usually a mapping of named fragments that carry anchors. It exists only so that aliases and merge keys can refer to it.

- The engine applies `define` nowhere. It does not bind it, does not merge it, and does not check it against the case structure: a fragment may be a part of a request, a stub response or anything else. After schema validation, the engine drops the block.
- `define` is not `caseDefaults`. `caseDefaults` is applied to every case of the file: a call, an expectation or a stub there changes each case. `define` is applied to no case: a fragment in it changes a case only where the case uses an alias or a merge key.
- Write `define` before the aliases that use it, so first in the file: an anchor works only after its definition.
- `define` is allowed only at the top level. Inside a case, a step or `caseDefaults` it fails schema validation.

## Call kinds

A `call` holds exactly one of the keys `http`, `bean` and `class`. That key gives the call kind, and its value names the target. The other keys of the `call` are the fields of that kind:

| Kind | Target | Fields |
|---|---|---|
| `http` | `"<METHOD> <path>"`, for example `GET /ads` | `query`, `rawQuery`, `headers`, `body` |
| `bean` | a bean name | `method` (required), `paramTypes`, `args` |
| `class` | a class name | `method` (required), `paramTypes`, `args`, `constructor`, `deps` |

The engine checks the call after it merges the file's `caseDefaults`, so `caseDefaults.call` may give the kind and a case only some fields, or the other way round. The case fails when the file loads if the merged call has no kind key, more than one kind key, or a field of another kind (for example `deps` in a `bean` call). The other cases of the file still run. A `caseDefaults.call` with such a problem fails every case of the file.

### `http`

```yaml
name: async discover answers with the partner's items
call:
  http: POST /discover
  headers: { x-discover-version: "3.0" }
  body: { intent: { prompt: running shoes, ad_count: 2 }, meta: { request_id: req-1 } }
expect:
  status: 200
  body: { request_id: req-1, items: [ { id: i1, title: Pegasus 41 } ] }
```

1. `http` is the HTTP method (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD` or `OPTIONS`), one space and a path that starts with `/`. The path has no spaces, `?` or `#`: the query goes in `query` or `rawQuery`, not in the path. A case that changes the method or the path writes the whole `http` value again.
2. `query` is a map. The engine encodes each value (a space becomes `%20`). A list value sends the parameter once for each item.
3. `rawQuery` is sent exactly as written, without encoding: `rawQuery: "plid=123&qt=%5B%22a%22%5D"`. A call can't have both `query` and `rawQuery`. This also applies after the merge: if a file's `caseDefaults.call` has a `query` and a case has a `rawQuery`, the case fails when the file loads.
4. A `body` that is not a string is sent as JSON with `Content-Type: application/json`, unless the case sets that header.
5. The request goes to `http://localhost:<port>` over HTTP/1.1, with a 5 s connect timeout and a 60 s request timeout. Async controllers need nothing special.

### `bean`

```yaml
name: reload reads the feed table again
mocks:
  - { bean: feedRepository, method: findAll, returns: [ { id: 1 }, { id: 2 }, { id: 3 } ] }
steps:
  - name: reload
    call: { bean: feedCache, method: reload }
  - name: size
    call: { bean: feedCache, method: size }
    expect: { returns: 3 }
```

The value of `bean` is the bean name. The engine converts each of `args` to the method's parameter type. An overloaded method needs `paramTypes`, for example `paramTypes: [ java.lang.String ]` or `paramTypes: [ String ]`.

### `class`

```yaml
name: dependency matched by simple type name
call:
  class: com.example.AdTitleFormatter
  method: format
  args: [ shop, [ a, b, c ] ]
  deps:
    CtaIdConverter: { stubs: [ { method: convert, returns: 42 } ] }
    limits: { value: { max: 3 } }
expect:
  returns: { ctaId: 42, titles: [ a, b, c ] }
```

The value of `class` is the full class name. No Spring context starts for a `class` case. The engine creates the object itself:

1. With one constructor, it uses that constructor. With several, it uses the one listed in `constructor` (parameter types, `[]` for the no-argument one).
2. A `deps` key matches a constructor parameter by its name. Otherwise it matches by type (simple or full class name), if exactly one parameter has that type.
3. Each `deps` entry is one of:
   1. `stubs: [ … ]`: a Mockito mock with these stubs.
   2. `value: …`: a real object converted from YAML.
   3. `real: true`: a real object of the parameter's type, built by the same rules.
4. A parameter without a `deps` entry is a plain Mockito mock. A primitive parameter can't be a mock, so it needs `value` or `real: true`.
5. For a static method, the engine creates no object.

Parameter names come from the `-parameters` compiler flag, which `spring-boot-starter-parent` enables. A project without that parent must enable the flag itself, for example with `<parameters>true</parameters>` in the configuration of `maven-compiler-plugin`. A `class` case can't use `mocks`, `spies`, `http`, `context` or `freshContext`. It can verify its dependency mocks with `verify: [ { dep: CtaIdConverter, method: convert, times: 1 } ]`.

## Steps and references

A case without `steps` has one step named `main`. With `steps`, each step has a `name`, a `call`, optional `mocks` / `spies` / `http` stubs (added for this step and later ones) and an optional `expect`.

An expected value can be a reference `{ ref: <name>, path?: <JSONPath>, parseJson?: true }`. The name is an earlier step (its response body, or the JSON of its returned value) or a capture made with `verify[].as`.

```yaml
name: second response equals the first one
steps:
  - name: first
    call: { http: GET /echo, query: { plid: 123, qt: shoes } }
  - name: second
    call: { http: GET /echo, query: { plid: 123, qt: shoes } }
    expect:
      assert:
        - { path: "$.params", equals: { ref: first, path: "$.params" } }
      body: { ref: first }
```

1. A reference can only point to something made earlier in the same case. A forward or unknown reference fails when the file loads.
2. Inside a step, `verify` checks run first, in list order. So `assert`, `body` and later checks of the same step can use their captures.
3. A step's expectations may refer to its own result.
4. A step whose call threw an exception has no result.
5. A `body` with a reference and a `mode` is written `{ value: { ref: …, path: … }, mode: strict }`.

## `_context.yaml`

`_context.yaml` says how to start the application for `http` and `bean` cases.

```yaml
application: com.example.ExampleApplication
profiles: [ test ]
properties:
  spring.config.name: example-test
  logging.level.com.example: INFO
  partner.base-url: ${wiremock.partner.url}
  tracking.host: ${wiremock.tracking.host}
  tracking.port: ${wiremock.tracking.port}
services: [ partner, tracking ]
settleMs: 150
mockBeans:
  - eventLogger
  - name: feedRepository
    stubs:
      - { method: findAll, returns: { file: data/rows.json } }
  - name: reportStore
    type: com.example.ReportStore
    primary: true
spyBeans: [ auditService ]
valueBeans:
  - name: exchangeSources
    type: com.example.ExchangeSources
    value: { sources: [] }
    primary: true
variants:
  eco:
    properties: { feature.optional: "off" }
converters: [ com.example.yaml.MoneyConverter ]
```

| Key | Meaning |
|---|---|
| `application` | The `@SpringBootApplication` (or `@SpringBootConfiguration`) class. Required for `http` / `bean` cases. |
| `profiles` | Active Spring profiles. |
| `properties` | Properties of every context. They win over the application's own configuration files. Spring Boot sees them before the context exists, so `spring.config.name`, `spring.profiles.*` and `logging.level.*` work. The server port is always random. |
| `services` | WireMock services (see [WireMock services](#wiremock-services)). |
| `mockBeans` | Beans replaced by Mockito mocks. An entry is a bean name or `{ name, type?, primary?, stubs? }`. |
| `spyBeans` | Beans wrapped in Mockito spies. An entry is a bean name or `{ name, stubs? }`. |
| `valueBeans` | Real objects converted from YAML: `{ name, type, value, primary? }`. A bean of that name is replaced, or a new one is added. |
| `variants` | Named variants. Each one can set `properties`, `mockBeans`, `spyBeans` and `valueBeans`. |
| `converters` | Extra value converters, by class name (see [Adding a value converter](#adding-a-value-converter)). |
| `settleMs` | A wait between cases for late asynchronous calls (see [`settleMs`](#polling-fresh-contexts-and-settlems)). |
| `define` | Fragments for YAML anchors, for example stubs shared by several variants. The engine applies it nowhere (see [`define`](#a-place-for-anchors-define)). |

Mocks, spies and value beans:

1. A **mock** replaces the bean definition before the context starts. The real bean is never created. The mock keeps the bean's generic type, so `Supplier<Map<String, byte[]>>` consumers still receive it. With `type`, the engine creates the mock when no bean of that name exists. `primary: true` makes it the primary bean.
2. A **spy** wraps the real bean after Spring initialises it. Real methods run unless a stub says otherwise.
3. `stubs` on a `mockBeans` / `spyBeans` entry are active from the moment the mock or spy exists. So they answer calls made while the context starts, for example a cache loaded at startup.
4. A bean name that the context does not have, and that has no `type`, stops the context start. The message names the bean and every file that refers to it.

## `_caseDefaults.yaml` and stub layers

`_caseDefaults.yaml` holds `mocks`, `spies` and `http` stubs that apply before every `http` / `bean` case of its root, and an optional [`define`](#a-place-for-anchors-define) block of fragments for anchors, which the engine applies nowhere:

```yaml
mocks:
  - { bean: placementAccess, method: getPlacement, returns: { valid: { id: 14019, availableSlots: 4 } } }
  - { bean: pricingEnricher, method: enrich, returnsArg: 0 }
  - { bean: providerRegistry, method: find, args: [ dsp ], returns: { name: dsp, maxAds: 4 } }
http:
  - service: tracking
    method: POST
    path: /urls
    response: { status: 200, body: { urls: [] } }
```

The engine registers stubs in layers, lowest first:

1. boot-time `stubs` of the variant's `mockBeans` / `spyBeans`;
2. `_caseDefaults.yaml`;
3. the file's `caseDefaults`;
4. the case;
5. the step.

A later layer's stub with the same key replaces the earlier one, and the earlier answer never runs. The key of a bean stub is bean + method + `paramTypes` + `args`. The key of a WireMock stub is service + method + path / path pattern + query + headers + body matcher. Stubs of one layer are all registered, even when they share a key.

**Which stub answers.** Mockito and WireMock answer a call with the stub that was registered last among the stubs that match it. The engine registers the layers lowest first, and the stubs of one layer in list order. This has three effects:

1. Bean stubs in one layer: put a `"#any"` stub, or a stub without `args`, before the stubs with specific `args`. If it comes after them, it answers every call, and the specific stubs never answer.
2. Bean stubs in different layers: a catch-all stub of a higher layer has a different key from a specific stub of a lower layer. So it does not replace that stub, but the engine registers it later, and it answers all calls. For example, a case stub `{ bean: providerRegistry, method: find, returns: … }` without `args` hides the `args: [ dsp ]` stub of `_caseDefaults.yaml`.
3. WireMock stubs follow the same rule. A stub with a wider match (a `pathPattern`, or no `method`) that the engine registers after a more specific stub answers the requests of both. In one layer, put the wider stub first. In a higher layer, a wider stub hides the more specific stubs of the lower layers.

Before each case the engine resets every mock and spy of the variant's context and every WireMock server, and then registers the layers again. So a stub or a recorded call of one case never reaches the next case.

## Variants and the variant-wide mock rule

A case selects a variant with `context: <variant>`. A variant adds properties and bean entries to the base ones, or replaces them by name. The engine starts at most one shared context per variant, when the first case that needs it runs, and keeps it until the entry class finishes.

```yaml
# _context.yaml
variants:
  eco:
    properties: { fixture.mode: eco, fixture.optional-feature: "off" }
  unmappable-feed:
    mockBeans:
      - name: feedRepository
        stubs:
          - { method: findAll, returns: [ { id: 1 }, { name: feed-77 } ] }
```

```yaml
# a case file
name: a feature switched off removes its bean
context: eco
call: { bean: featureRegistry, method: keys }
expect:
  returns: [ coreFeature ]
  context:
    beanTypes: { absent: [ com.example.OptionalFeature ] }
```

**The variant-wide mock rule.** Mocks and spies are fixed when a context starts. So the engine collects every bean that `_context.yaml`, `_caseDefaults.yaml` or any case of a variant names under `mocks` / `spies`. If one case of a variant mocks a bean, that bean is a mock in every case of the variant. In the other cases it answers with the lower stub layers or with Mockito defaults. All loaded cases count, also the ones that `yamlit.filter` does not select, so a case behaves the same alone and with the others.

Add a new variant when a case needs a bean to be real and another case of the same variant needs it mocked. The engine also rejects these setups before the context starts:

1. A bean that is mocked in one place and spied in another, in one variant. The message names the bean and both files.
2. A bean that is a value bean and is also mocked or spied, in one variant.
3. A `verify` on a bean that the variant neither mocks nor spies. Without this check, the verification would turn a real bean into a mock for the whole variant. Add the bean to `spies` (or `spyBeans`) to verify the real bean.

## Stubs on mocks and spies

A stub has the same shape in `mocks`, `spies`, boot-time `stubs` and `deps.<name>.stubs`. In `mocks` / `spies` it also names its `bean`. A bare bean name in `mocks` / `spies` mocks or spies the bean without a stub.

```yaml
mocks:
  - feedRepository
  - { bean: providerRegistry, method: find, args: [ amazon ], returns: { name: amazon, maxAds: 2 } }
  - { bean: providerRegistry, method: find, args: [ ebay ], returns: null }
  - { bean: pricingEnricher, method: enrich, args: [ "#any" ], returnsArg: 0 }
  - bean: dspClient
    method: requestAds
    answers:
      - throws: { type: com.example.ApiException, args: [ 503, transient ] }
      - throws:
          type: com.example.ApiException
          cause: { type: java.net.http.HttpTimeoutException, message: read timed out }
      - returns: { file: data/dsp-two-ads.json }
spies:
  - { bean: auditService, method: label, args: [ x ], returns: stubbed }
```

| Key | Meaning |
|---|---|
| `method` | The method name. An overloaded method also needs `paramTypes`. Private and static methods can't be stubbed. |
| `args` | Per argument: a literal (compared by JSON equality after conversion), `"#any"`, or `{ anyOf: <class name> }` (matches by runtime type). Without `args`, the stub answers every call. Stubs with different `args` live side by side. |
| `returns` | The value to return, converted to the method's return type. |
| `returnsArg: <index>` | Returns that argument itself. If the return type is a wrapper (`Optional`, `CompletableFuture`, vavr `Validation`), the argument is wrapped. The argument is never copied through JSON. |
| `throws` | `{ type, message?, args?, cause? }`. The engine picks the constructor that fits `args` (or `message`). `cause` is built the same way. |
| `answers` | A list of `{ returns }` / `{ throws }` for consecutive calls. The last answer repeats. |

The engine registers stubs in the `doAnswer(…).when(mock)` form, so stubbing a spy never runs the real method.

## WireMock services

Declare a logical name for each external HTTP service in `_context.yaml`. The engine starts one WireMock server per service, on a random port, before the first context starts. Give the application the server's address with a placeholder in `properties`:

| Placeholder | Value |
|---|---|
| `${wiremock.<service>.url}` | `http://localhost:<port>` |
| `${wiremock.<service>.host}` | `localhost` |
| `${wiremock.<service>.port}` | the port |

Stubs go into an `http` list in `_caseDefaults.yaml`, a file's `caseDefaults`, a case or a step:

```yaml
http:
  - service: partner
    method: POST
    path: /search
    body: { query: tents, limit: 3 }
    response:
      status: 200
      headers: { Content-Type: application/json }
      body: { items: [ { id: t1, title: Dome tent } ] }
```

| Key | Meaning |
|---|---|
| `service` | A service from `_context.yaml`. An unknown service fails the case and lists the declared ones. |
| `method` | HTTP method, or `ANY`. Without it, any method matches. |
| `path` / `pathPattern` | Exact path, or a regular expression over the path. |
| `query`, `headers` | Exact values. Header names match in any letter case. |
| `body` | The request body must be equal to this JSON. |
| `response` | `status` (default 200), `headers`, `body` (inline or `{ file }`), `delayMs`, `fault`, `template`. |
| `responses` + `sequence: true` | Consecutive matching requests get these responses in order. The last one repeats. |

`fault` is a WireMock fault name such as `CONNECTION_RESET_BY_PEER`. `delayMs: 3000` delays the response, so a case can check a timeout path.

**Response templating.** With `template: true`, WireMock renders the response from the request with its Handlebars helpers. This is how a stub answers values that the application generates at run time:

```yaml
http:
  - service: tracking
    method: POST
    path: /urls
    response:
      status: 200
      headers: { Content-Type: application/json }
      template: true
      body: >-
        {"urls": [{{#each (jsonPath request.body '$.items') as |item|}}{{#unless @first}}, {{/unless}}{"id":
        "{{item.id}}", "clickUrl": "https://click.example/{{item.id}}"}{{/each}}]}
```

The servers bind to `localhost` only. The engine resets their stubs and request journals before each case.

## Expectations

| Call kind | Keys of `expect` |
|---|---|
| `http` | `status`, `headers`, `body`, `bodyText`, `assert`, `verify`, `logs`, `context` |
| `bean` | `returns` or `throws`, `assert`, `verify`, `logs`, `context` |
| `class` | `returns` or `throws`, `assert`, `verify` (on `dep`), `logs` |

`within` and `every` make a step repeat (see [polling](#polling-fresh-contexts-and-settlems)).

### `status`, `headers`

`status` is the HTTP status code. Each entry of `headers` must be equal to the response header as a string. A header with several values is compared as its values joined with `,`.

### `body`

```yaml
expect:
  body: { request_id: req-1, items: [ { id: i1 } ] }
```

```yaml
expect:
  body:
    file: data/golden-ads.json
    mode: strict
    ignore: [ "$.requestId", "$.ads[*].adSelectionId" ]
```

| Mode | Meaning |
|---|---|
| (default) | JSON compare. The actual body may have extra fields. Array order matters. |
| `strict` | JSON compare. Extra fields fail. Array order matters. |
| `exact` | The raw text must be equal, including whitespace. |

`ignore` lists JSONPaths that the engine removes from both sides first. Numbers are compared exactly, so two different 19-digit ids never count as equal. A failure shows the path of each difference.

### `bodyText`

String checks on the raw body, for any content type: `equals`, `contains`, `notContains`, `startsWith` and `matches`. Each key takes one value or a list, and every value in a list must hold.

```yaml
bodyText:
  startsWith: "<ads>"
  contains: "<url>"
  notContains: [ asin, gtin ]
  matches: '^<ads>.*</ads>$'
```

### `returns`, `throws`

`returns` compares the returned value's JSON like the default `body` compare: extra actual fields are allowed, and array order matters. The engine converts the value to JSON with the [value converters](#adding-a-value-converter), so a protobuf message is compared as its JSON. `throws: { type, message? }` expects an exception. If the method returns normally, the case fails and says what it returned. A returned `CompletableFuture` that completed with an exception counts as the call throwing that exception.

### `assert`

Each entry is `{ path: <JSONPath>, <operator>: <value>, … }`. The path is evaluated on the response body (`http`) or on the returned value's JSON (`bean`, `class`). To check a scalar return value, use `path: "$"`.

The rows for `isNull`, `notNull`, `exists` and `absent` describe a path to one value. Rule 3 under the table gives their meaning on a path with a wildcard or a filter.

| Operator | Passes when |
|---|---|
| `equals` | the value is equal (objects use the default `body` compare; add `mode: strict` to the entry for a strict one). The expected value may be a `{ ref }`. |
| `notEquals` | the value is not equal under the same compare. |
| `isNull: true` | the key is missing, or its value is `null`. |
| `notNull: true` | the key is there and its value is not `null`. |
| `exists: true` | the key is there, whatever its value. |
| `absent: true` | the key is missing. A key with the value `null` fails. |
| `size` | an array (or the list that a wildcard / filter path selects) has this many elements, an object has this many fields, or a string has this many characters. |
| `gte`, `lte` | the number is greater / less than or equal to the value. |
| `contains` | an array has the item(s), or a string has the substring(s). |
| `containsExactly` | an array has exactly these items, in this order. |
| `containsInAnyOrder` | an array has exactly these items, in any order. |
| `unique: true` | the array has no duplicate values. |
| `startsWith`, `matches` | the string starts with the value / matches the regular expression. |
| `all` / `none` | a nested assertion holds for every element / for no element of the array. |
| `parseJson: true` | not an operator: the string value is parsed as JSON before the operators run. |

Example:

```yaml
assert:
  - { path: "$.ads[?(@.qt == 'shoes')]", size: 2 }
  - { path: "$.ads", none: { path: "$.asin", exists: true } }
  - { path: "$.ads[0].gtin", exists: true }
  - { path: "$.ads[0].gtin", isNull: true }
  - { path: "$.ads[*].id", containsExactly: [ p1, p2 ] }
  - { path: "$.requestId", matches: 'req-\d+' }
  - { path: "$.diag[0].payload", parseJson: true, equals: { dropped: [ ] } }
```

Rules for paths:

1. A missing key and a `null` value are different. "Present and null" is written as `exists: true` plus `isNull: true`. `isNull` also passes on a missing key, because many serializers do not write `null` fields.
2. A path with a wildcard or a filter selects a list. On such a path, the part before the first wildcard or filter must exist. Otherwise every operator fails with "path prefix missing", so a misspelled key never passes by accident. For example, `{ path: "$.adz[*].qt", size: 0 }` fails when the body has no `adz` key.
3. On a path with a wildcard or a filter, the presence operators work on the matches. A match is a value that the path selects. An element that does not have the key gives no match.
   1. `exists: true` passes when there is at least one match.
   2. `absent: true` passes when there is no match.
   3. `isNull: true` and `notNull: true` need at least one match, and the check must hold for every match.

   To check that a filter selects no element, use `size: 0`.
4. `all` and `none` need an array. To check a parsed object, use `equals` or `size`.

A failure message names the file, case, step, path, operator, and the expected and actual values.

## Verification

`expect.verify` is a list of checks. A check is on a mock or spy (`bean`), on a `class` case's dependency mock (`dep`), or on a WireMock service (`service` with the request matching keys of an `http` stub).

```yaml
verify:
  - { bean: dspClient, method: requestAds, times: 1, as: dsp }
  - { bean: placementAccess, method: getPlacement, args: [ 14019 ], times: 1 }
  - { bean: amazonConnector, method: fetch, never: true }
  - { bean: trackingProvider, noInteractions: true }
  - bean: eventLogger
    method: send
    argType: { index: 1, type: com.example.events.ResponseEvent }
    times: 1
    within: 3s
    assert:
      - { path: "$[0].args[1].requestId", equals: { ref: dsp, path: "$[0].args[0].requestId" } }
  - service: partner
    method: POST
    path: /search
    times: 1
    assert:
      - { path: "$[0].body.query", equals: tents }
      - { path: "$[0].headers.content-type", equals: application/json }
```

| Key | Meaning |
|---|---|
| `times`, `atLeast` | Exact or minimum number of matching calls. A check without a count means "at least once". |
| `never: true` | No matching call. |
| `noInteractions: true` | No call to the mock at all (`bean` or `dep`). |
| `method`, `paramTypes` | The method to count. An overloaded method needs `paramTypes`. |
| `args` | Argument matchers, the same as in stubs. |
| `argType: { index, type }` | Keeps only calls whose argument at `index` is an instance of `type`. |
| `within: <duration>` | Waits until the check holds, checking every 100 ms (for calls made on other threads). |
| `as: <name>` | Keeps the captured calls for references. |
| `assert` | Assertions on the captured calls' JSON. |

The captured calls are JSON in call order:

1. Mock or spy: `[ { "args": [ … ] }, … ]`. The engine converts each argument with the value converters.
2. WireMock: `[ { "method", "path", "query", "headers", "body" }, … ]`. `path` has no query string. `query` and `headers` map a name to a string, or to an array when it was sent several times. Header names are always in lowercase, whatever case the client sent. `body` is the parsed JSON, else the text, else `null` when the body is empty.

Durations are written like `200ms`, `3s` or `1m`.

## Logs and context checks

### `expect.logs`

```yaml
logs:
  - { logger: com.example.FeedCache, level: WARN, contains: "feed-42", times: 1 }
  - { logger: com.example.FeedCache, level: ERROR, absent: true }
```

1. `logger` matches the logger name and its descendants. `level` and `contains` (a substring of the formatted message) select the events. Without `times` the entry needs at least one event; `absent: true` needs none.
2. The engine captures the events from the named logger itself. The logger's additivity and the appenders of the logging configuration don't matter.
3. If the logger's level in the logging configuration is above the expected level, the case fails and names the property to set, for example `logging.level.com.example.FeedCache: WARN`. Put it into the `properties` of `_context.yaml`.
4. `expect.logs` works only with Logback, which is Spring Boot's default. With another logging backend it fails and names that backend.

### `expect.context`

```yaml
context:
  beans: { present: [ feedCache ], absent: [ ebayConnector ] }
  beanTypes: { present: [ com.example.FeedCache ], absent: [ com.example.EbayConnector ] }
```

The engine checks the beans of the case's application context by name and by type.

## Polling, fresh contexts and `settleMs`

**Polling.** A step's `expect.within` repeats the step's call and expectations until they pass or the time is over. `every` sets the pause between attempts (default 200 ms). The failure shows the last attempt.

```yaml
- name: await the new rows
  call: { bean: feedCache, method: size }
  expect: { returns: 3, within: 5s, every: 50ms }
```

Only assertion failures are tried again. A definition error or an I/O error fails at once.

**Fresh contexts.** `freshContext: true` gives the case a newly started context of its variant. The engine closes it after the case. Cases of a file keep their order, so a file can describe "first boot, then reload".

The calls, WireMock requests and log events that a **shared** context makes while it starts are forgotten before the first case uses it. So no case depends on being the one that started the context. A **fresh** context keeps them, so a fresh case can verify startup calls and boot-time log events:

```yaml
name: a fresh case sees the calls its own context made while starting
freshContext: true
call: { bean: feedCache, method: size }
expect:
  returns: 2
  verify:
    - { bean: feedRepository, method: findAll, times: 1 }
```

**`settleMs`.** Application code may still call a mock from another thread after a case ends, for example a delayed event. The next case can then count such a call. `settleMs` in `_context.yaml` makes the engine wait before each `http` / `bean` case (except the first of a root), and also after a shared context starts, before it forgets the startup calls. Choose a value longer than the slowest delayed call.

## Running a subset of cases

Set the system property `yamlit.filter` to an Ant-style pattern over `<file path relative to the glob root>/<case name>`:

```
mvn verify -Dit.test=ExampleServiceYamlIT -Dyamlit.filter='discover/**'
mvn verify -Dit.test=ExampleServiceYamlIT -Dyamlit.filter='verify/no-leak.yaml/*'
```

The pattern supports `*`, `**` and `?`, but not `{a,b}` alternatives. Cases that don't match are not reported.

## Schema mapping in IntelliJ IDEA

The engine checks every file against `yaml-testkit.schema.json` when it loads. To get completion and checks while you type:

1. Open **Settings → Languages & Frameworks → Schemas and DTDs → JSON Schema Mappings**.
2. Add a mapping. Set **Schema file or URL** to the schema file: `src/main/resources/com/yamltestkit/yaml-testkit.schema.json` in this repository, or the same path inside the `yaml-testkit` JAR in a service project. Set **Schema version** to the 2020-12 draft, or to the newest version that the IDE offers.
3. Add each case folder (for example `src/test/resources/com/example/yaml/cases`) as a directory of the mapping.

The schema covers case files, `_context.yaml` and `_caseDefaults.yaml`. It detects the file type from the top-level keys.

## Adding a value converter

The engine converts YAML to Java objects (`args`, `returns`, `deps.value`, `valueBeans`) and Java objects to JSON (`returns` checks, captured calls) through a chain of converters. The first converter that supports a type handles it. The built-in order is:

1. user converters;
2. protobuf messages (`JsonFormat`; only when `protobuf-java` is on the classpath);
3. vavr `Validation`, written `{ valid: … }` or `{ invalid: … }` (only when vavr is on the classpath);
4. `CompletableFuture`;
5. `Optional`;
6. builder-style classes (a static `builder()` with `field(v)` or `setField(v)` methods and `build()`), used only when Jackson can't create the type;
7. Jackson 2, which rejects unknown properties and supports records and `java.time`.

To add a converter, implement `com.yamltestkit.convert.ValueConverter` with a public no-argument constructor:

```java
public class MoneyConverter implements ValueConverter {

    @Override
    public boolean supports(Type type) {
        return type == Money.class;
    }

    @Override
    public Object fromYaml(JsonNode value, Type targetType, ConversionContext ctx) {
        return value.isNull() ? null : Money.parse(value.asText());
    }

    @Override
    public JsonNode toJson(Object value, ConversionContext ctx) {
        return TextNode.valueOf(value.toString());
    }
}
```

Register it under `converters` in `_context.yaml`, or with `@YamlCases(converters = MoneyConverter.class)`. User converters run before the built-in ones. Use `ctx` to convert nested values through the whole chain. For `returnsArg` into your type, also override `wrappedType` and `wrap`. One chain serves the whole entry class: the converters of `@YamlCases` first, then those of every `_context.yaml`.

Conversion notes:

1. proto3 JSON writes `int64` fields as strings, so quote such values in YAML.
2. Jackson rejects a decimal number for an integer type, and a missing or `null` value for a primitive.
3. A value that no converter can handle fails the case. The message names the type, the YAML location and the converters tried.

## Things to know

**Files and `caseDefaults`**

1. A file's `caseDefaults.call` merges into every case of the file. If a file has multi-step cases or cases of another call kind, don't put `call` into its `caseDefaults`; write each call in full. Anchor the repeated parts in [`define`](#a-place-for-anchors-define) and reuse them with an alias or `<<`.
2. YAML anchors (`&name`), aliases (`*name`) and merge keys (`<<: *name`) are resolved when a file is read. An alias is never sent as its anchor name. A merge is shallow: a nested object in the mapping replaces the merged one as a whole.
3. An overloaded method needs `paramTypes`, in stubs, calls and `verify`.

**Contexts and beans**

1. Cases run one after another, because the mocks of a context are shared.
2. A variant may give a `mockBeans` entry other boot-time stubs than the base entry.
3. A variant that only `freshContext` cases use never starts a shared context.
4. Boot-time log events are visible only in `freshContext` cases.
5. Real beans keep their state between the cases of a variant. A case that reads a stateful bean (for example a cache) should prepare it first, for example call `reload`.
6. A bean that only `_caseDefaults.yaml` names is skipped in a variant whose context has no such bean, so its stub there does not stop that context from starting.
7. A bean name that is an alias works: the engine uses the bean it points to. A singleton that the application registers directly, without a bean definition, can't be mocked or spied.
8. A mocked `FactoryBean` is mocked as the type of the object it makes. If that type can't be found without creating the bean, add `type` to the `mockBeans` entry.
9. A spy wraps the bean before the AOP proxies, so the spy sees the real target. A bean that is already a proxy when the spy sees it (for example made by a `FactoryBean`) can't be spied; the start fails with the bean's name. Spy the bean that it delegates to instead.
10. Don't spy a bean that is part of a circular reference. The other bean of the cycle receives the early instance, without the spy, and Spring stops the start ("injected into other beans … in its raw version"). Mock that bean, or spy a bean outside the cycle.
11. When several contexts run, the logging levels of the last context that started are the ones in effect.
12. The engine closes its contexts, WireMock servers and log capture when the entry class finishes. So test classes that run after the entry class don't run with its contexts still open.
13. WireMock servers listen on `localhost` only.

**Timing**

1. The engine resets mocks before each case even while background threads still call them. Only `settleMs` protects against such late calls.
2. `settleMs` costs its value for every case, and once more after each shared context starts. Keep it small.
3. A check with `never: true`, `times: 0` or `noInteractions: true` and a `within` waits the full time. It passes only if the count stays at zero during the whole wait.
4. Before each new attempt of a step-level `within`, the engine forgets the recorded calls of the mocks and spies and the WireMock request journals. Stubs stay. Log events are not forgotten, so `expect.logs` counts grow over the attempts of one step.
5. A step-level `within` checks its time limit only between attempts. A `verify` with its own `within` waits in full on each attempt. So the step can run longer than its `within`, by up to one more attempt.

## What the testkit does not support

These test mechanics stay in Java tests, or a case expresses them another way:

| Mechanic | What to do instead |
|---|---|
| Spring cache seeding and Caffeine native-cache inspection | Seed the data through the mocked data source. |
| A UDP StatsD listener | Keep the test in Java. |
| A readiness-state history recorded inside a stub | Keep the test in Java. |
| File-system checks (OpenAPI golden file and regeneration, scans of `src/main/resources`) | Keep the test in Java. |
| Passing one call's return value into the arguments of the next call | Write the value in the next call's `args`. |
| Replacement beans that need custom Java construction (for example a StatsD client) | `valueBeans` cover data objects; WireMock covers transports. Other cases stay in Java. |
| Bean answers that compute the result from the arguments (parse a field, generate N items) | Stub the boundary over HTTP with WireMock response templating, or key answers by argument value. |
| Stubs that write to a side map that assertions read later | Verify the captured calls instead. |
| Joins between two lists (match every item of one list to an item of another) | Compare equal lists, or check `unique`. |
| Flag-driven conditional stubs and expectations derived from flags | Write one explicit case per combination (a `cases` table). |
| Loops over generated inputs | Write each input in the case file; check each element with `all` / `none`. |
| Deep merge of a seed value (a subclass changes one field of a protobuf seed) | A later stub layer replaces the whole value; write the whole value. Within one file, an anchor and `<<` reuse it and override keys level by level. |
| Dynamic properties from test servers other than WireMock | Use a WireMock service and its `${wiremock.*}` placeholders. |
| Mocking a bean by type or qualifier | Mock it by bean name. |
| `Mono` / reactive return values, vavr `Either` / `Option`, `argThat`-style custom matchers, `InOrder`, a stubbed `Clock` | Not built yet. A custom value converter can add a return type. |
