# Sección 07 — Schedulers y Manejo de Hilos

## Objetivo

Esta sección aborda uno de los temas más importantes de la programación reactiva: **cómo controlar en qué hilo se ejecuta cada parte del pipeline**. Se exploran los Schedulers disponibles en Reactor, los operadores `subscribeOn()` y `publishOn()`, el uso de Virtual Threads (Java 21+), estrategias de backpressure y el procesamiento paralelo con `parallel()`.

---

## Conceptos Clave

### Schedulers disponibles

| Scheduler | Uso recomendado |
|-----------|----------------|
| `Schedulers.boundedElastic()` | Operaciones I/O bloqueantes (BD, APIs, archivos) |
| `Schedulers.parallel()` | Operaciones CPU-bound paralelas |
| `Schedulers.single()` | Un solo hilo dedicado |
| `Schedulers.immediate()` | Ejecuta en el hilo actual (por defecto) |
| `Schedulers.fromExecutorService(...)` | Scheduler personalizado |

---

## Lecciones

### Lec01 — Comportamiento por Defecto

```java
var flux = Flux.create(sink -> { ... }).doOnNext(v -> log.info("value: {}", v));
Runnable runnable = () -> flux.subscribe(Util.subscriber("sub1"));
Thread.ofPlatform().start(runnable);
```

**Sin ningún Scheduler:** todo el pipeline se ejecuta en el **hilo que llama a `subscribe()`**. Si ese hilo se bloquea, todo el pipeline se bloquea.

- Execution síncrona y secuencial.
- Sin overhead de cambio de contexto.
- No adecuado para operaciones I/O bloqueantes.

---

### Lec02 — `subscribeOn()`

```java
Flux.create(sink -> { ... })
    .doOnNext(v -> log.info("value: {}", v))
    .doFirst(() -> log.info("first1"))
    .subscribeOn(Schedulers.boundedElastic())  // <-- afecta todo el pipeline
    .doFirst(() -> log.info("first2"))
    .subscribe(Util.subscriber("sub1"));
```

`subscribeOn()` cambia el hilo donde se ejecuta **todo el pipeline**, incluyendo:
- La fuente (Flux.create, Flux.generate, etc.)
- Todos los operadores upstream y downstream
- El Subscriber final

**Regla:** si hay múltiples `subscribeOn()`, solo el **más cercano a la fuente** tiene efecto.

```
Pipeline:   [Flux.create] → [operadores] → [subscribeOn(A)] → [operadores] → [subscribeOn(B)] → [Subscriber]
Resultado:  Todo se ejecuta en Scheduler A (el más cercano a la fuente)
```

**Cuándo usar:** cuando la fuente es bloqueante (llamadas a BD, APIs, lectura de archivos) y necesitas no bloquear el hilo principal.

---

### Lec03 — Múltiples `subscribeOn()`

Demostración visual de que solo el `subscribeOn()` más cercano a la fuente tiene efecto cuando hay varios en el mismo pipeline. El segundo es ignorado.

---

### Lec04 — Virtual Threads (Java 21+)

```java
Schedulers.fromExecutorService(
    Executors.newVirtualThreadPerTaskExecutor()
)
```

Los **Virtual Threads** (Project Loom) son hilos de peso liviano gestionados por la JVM. Permiten tener miles o millones de hilos concurrentes sin el overhead de los Platform Threads. Pueden combinarse con Reactor para operaciones bloqueantes de forma más eficiente.

---

### Lec05 — `publishOn()` vs `subscribeOn()`

```java
Flux.create(sink -> { ... })                          // hilo del que llama
    .publishOn(Schedulers.parallel())                  // cambia a parallel
    .doOnNext(v -> log.info("value: {}", v))          // se ejecuta en parallel
    .doFirst(() -> log.info("first1"))                // se ejecuta en parallel
    .publishOn(Schedulers.boundedElastic())            // cambia a boundedElastic
    .doFirst(() -> log.info("first2"))                // se ejecuta en boundedElastic
```

`publishOn()` cambia el hilo **solo para los operadores que están después** de él en el pipeline:

| | `subscribeOn()` | `publishOn()` |
|-|----------------|---------------|
| Afecta | Todo el pipeline | Solo downstream (operadores después) |
| Múltiples usos | Solo el primero tiene efecto | Cada uno tiene efecto independiente |
| Uso principal | Fuente bloqueante | Optimizar partes específicas del pipeline |

**Patrón común:**
```java
Flux.create(...)                        // fuente bloqueante
    .subscribeOn(Schedulers.boundedElastic())  // fuente en boundedElastic
    .map(...)                           // en boundedElastic
    .publishOn(Schedulers.parallel())   // cambia a parallel
    .map(...)                           // en parallel
```

---

### Lec06 — Problema con Event Loop y su Solución

#### ¿Qué es el Event Loop?

En aplicaciones reactivas construidas sobre Netty (como Spring WebFlux), existe un **Event Loop**: un hilo especializado y extremadamente valioso cuyo único propósito es gestionar operaciones I/O no bloqueantes. Puede manejar miles de conexiones simultáneas precisamente porque **nunca se bloquea**.

La regla de oro es: **nunca bloquees el Event Loop**.

---

#### El problema: bloquear el Event Loop con una operación lenta

```java
var client = new ExternalServiceClient(); // cliente HTTP reactivo (usa Netty)

for (int i = 1; i <= 5; i++) {
    client.getProductName(i)                   // I/O no bloqueante → retorna rápido
          .map(Lec06EventLoopIssueFix::process) // ❌ process() bloquea 1 segundo
          .subscribe(Util.subscriber());
}
```

`getProductName()` es una llamada HTTP no bloqueante: el Event Loop la despacha y puede seguir haciendo otras cosas mientras espera la respuesta. Hasta ahí todo bien.

El problema ocurre **después**: cuando llega la respuesta, el Event Loop la entrega a `map()`. El método `process()` dentro de ese `map()` hace un `Thread.sleep(1)`. Eso **bloquea al Event Loop por un segundo entero**.

```
Timeline sin publishOn():

  t=0s   Event Loop → dispara las 5 llamadas HTTP (no bloqueante ✅)
  t=1s   Llega respuesta 1 → Event Loop llama a process() → se bloquea 1s ❌
  t=2s   Event Loop queda libre → llega respuesta 2 → se bloquea 1s ❌
  t=3s   ...
  t=5s   Termina la última. Tiempo total: ~5 segundos (secuencial)
```

Durante esos segundos el Event Loop no puede atender ninguna otra petición: toda la aplicación se degrada.

---

#### La solución: `publishOn()` antes del operador bloqueante

```java
client.getProductName(i)
      .publishOn(Schedulers.boundedElastic()) // ← descarga el trabajo aquí
      .map(Lec06EventLoopIssueFix::process)   // process() ya no corre en el Event Loop
      .subscribe(Util.subscriber());
```

Lo que ocurre ahora:

1. `getProductName()` → el Event Loop gestiona el I/O y recibe la respuesta.
2. `publishOn(boundedElastic)` → el dato se pasa a un hilo del pool `boundedElastic`. El Event Loop queda libre **de inmediato**.
3. `process()` → se ejecuta en el hilo de `boundedElastic`. Puede bloquearse sin problema, ese pool está diseñado para eso.
4. Las 5 llamadas HTTP llegan casi al mismo tiempo (~1 segundo) y se procesan **en paralelo**, cada una en su propio hilo de `boundedElastic`.

```
Timeline con publishOn():

  t=0s   Event Loop → dispara las 5 llamadas HTTP (no bloqueante ✅)
  t=1s   Llegan las 5 respuestas → Event Loop las entrega a publishOn() y queda libre ✅
         bounded-1 → process(prod1) [1s]  ┐
         bounded-2 → process(prod2) [1s]  │ en paralelo
         bounded-3 → process(prod3) [1s]  │
         bounded-4 → process(prod4) [1s]  │
         bounded-5 → process(prod5) [1s]  ┘
  t=2s   Todos terminaron. Tiempo total: ~1 segundo (paralelo)
```

---

#### Regla práctica

| ¿Quién ejecuta la operación? | ¿Está bien? |
|------------------------------|-------------|
| Event Loop ejecuta I/O no bloqueante | ✅ Es su trabajo |
| Event Loop ejecuta `Thread.sleep` / JDBC / archivo | ❌ Nunca hagas esto |
| `boundedElastic` ejecuta operaciones bloqueantes | ✅ Correcto |

> La solución siempre es la misma: coloca `publishOn(Schedulers.boundedElastic())` **justo antes** del operador que contiene la operación bloqueante.

---

### Lec07 — Combinación `publishOn()` + `subscribeOn()`

#### El código demostrado

```java
var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);  // ¿en qué hilo?
                       sink.next(i);
                   }
                   sink.complete();
               })
               .publishOn(Schedulers.parallel())           // ← punto de cambio
               .doOnNext(v -> log.info("value: {}", v))   // ¿en qué hilo?
               .doFirst(() -> log.info("first1"))          // ¿en qué hilo?
               .subscribeOn(Schedulers.boundedElastic())   // ← ¿tiene efecto?
               .doFirst(() -> log.info("first2"));         // ¿en qué hilo?

Thread.ofPlatform().start(() -> flux.subscribe(Util.subscriber("sub1")));
```

#### Cómo Reactor evalúa el pipeline — de afuera hacia adentro

La suscripción viaja de **downstream a upstream** (del `subscribe()` hacia `Flux.create()`). Los operadores `doFirst` se ejecutan en ese orden de propagación hacia la fuente.

```
Orden de ejecución en la fase de suscripción (upstream):
  sub1 subscribe → doFirst("first2") → subscribeOn(boundedElastic) → doFirst("first1") → publishOn(parallel) → Flux.create
```

Luego, los datos fluyen de **upstream a downstream**:

```
Fase de emisión (downstream):
  Flux.create [hilo del thread Platform] → publishOn [cambia a parallel] → doOnNext [parallel] → sub1 [parallel]
```

#### Análisis hilo por hilo

| Operador | Hilo real |
|----------|-----------|
| `Flux.create` (genera datos) | El hilo Platform que llamó a `subscribe()` |
| `publishOn(parallel)` | Transfiere el trabajo aquí → |
| `doOnNext` | `parallel-N` |
| `doFirst("first1")` | `parallel-N` (se ejecuta al suscribirse, después del publishOn) |
| `subscribeOn(boundedElastic)` | **No tiene efecto** — está después de `publishOn()` |
| `doFirst("first2")` | El hilo Platform (antes del `publishOn`) |
| `subscribe` / Subscriber | `parallel-N` |

#### ¿Por qué `subscribeOn()` no tiene efecto aquí?

`subscribeOn()` intenta mover la fuente a otro hilo. Sin embargo, en este pipeline ya hay un `publishOn(parallel)` **más cercano a la fuente**. `publishOn` intercepta primero la señal de suscripción y cambia el contexto antes de que `subscribeOn` pueda actuar sobre la fuente. Por eso `Flux.create` sigue corriendo en el hilo Platform.

> **Regla:** para que `subscribeOn()` tenga efecto sobre la fuente, debe estar **más cerca de la fuente** que cualquier `publishOn()`.

#### El patrón correcto cuando necesitas ambos

```java
Flux.create(sink -> { /* fuente bloqueante */ })
    .subscribeOn(Schedulers.boundedElastic())  // ← fuente en boundedElastic
    .map(item -> transform(item))              //   sigue en boundedElastic
    .publishOn(Schedulers.parallel())          // ← cambia a parallel aquí
    .map(item -> heavyCompute(item))           //   en parallel (CPU-bound)
    .subscribe(Util.subscriber());
```

```
Fuente (I/O) → [boundedElastic] → map → [publishOn] → [parallel] → map → Subscriber
```

---

### Lec08 — Procesamiento Paralelo con `parallel()` + `runOn()`

#### El código

```java
Flux.range(1, 10)
    .parallel(3)                    // divide el stream en 3 rails
    .runOn(Schedulers.parallel())   // cada rail corre en su propio hilo
    .map(Lec08Parallel::process)    // process() tarda 1 segundo (CPU-bound)
 // .sequential()                   // reagruparía los rails (comentado aquí)
    .map(i -> i + "a")
    .subscribe(Util.subscriber());
```

#### ¿Qué son los "rails"?

`parallel(n)` transforma un `Flux<T>` en un `ParallelFlux<T>`. Internamente crea `n` sub-flujos independientes (rails). Los elementos del stream se distribuyen entre los rails en modo **round-robin**:

```
Flux.range(1,10):  1  2  3  4  5  6  7  8  9  10

parallel(3):
  Rail 0: 1  4  7  10
  Rail 1: 2  5  8
  Rail 2: 3  6  9
```

Cada rail se ejecuta en su propio hilo (via `runOn()`), por lo que `process()` se ejecuta **en paralelo** para múltiples elementos al mismo tiempo.

#### Timeline de ejecución

```
t=0s  Rail 0 → process(1), Rail 1 → process(2), Rail 2 → process(3)  [paralelo]
t=1s  Rail 0 → process(4), Rail 1 → process(5), Rail 2 → process(6)  [paralelo]
t=2s  Rail 0 → process(7), Rail 1 → process(8), Rail 2 → process(9)  [paralelo]
t=3s  Rail 0 → process(10)

Tiempo total: ~4 segundos   (vs ~10 segundos secuencial)
```

Sin `parallel()`, `process()` correría secuencialmente: 10 elementos × 1 segundo = 10 segundos. Con 3 rails, se reduce a ~4 segundos.

#### `sequential()`: reagrupar los rails

Después de un `ParallelFlux`, los operadores como `collect`, `reduce` o cualquiera que necesite un flujo único no pueden aplicarse directamente. `sequential()` vuelve a unir los rails en un `Flux` normal:

```java
Flux.range(1, 10)
    .parallel(3)
    .runOn(Schedulers.parallel())
    .map(Lec08Parallel::process)
    .sequential()          // ← vuelve a un Flux<Integer> normal
    .collectList()         // ahora sí, operadores secuenciales
    .subscribe(Util.subscriber());
```

> ⚠️ Sin `sequential()`, el orden de salida **no está garantizado** — depende de qué rail termina primero.

#### ¿Por qué `parallel()` no sirve para I/O?

`subscribeOn()` / `publishOn()` mueven el trabajo a otro hilo pero el stream sigue siendo **secuencial**: un elemento a la vez. Para I/O no bloqueante esto es suficiente porque el hilo no se bloquea esperando.

`parallel()` + `runOn()` procesa **múltiples elementos simultáneamente**. Para I/O bloqueante esto ayuda (como vimos en Lec06), pero para I/O **no bloqueante** Reactor ya es eficiente de por sí: el Event Loop puede manejar miles de conexiones con un solo hilo. Agregar `parallel()` solo añade overhead innecesario.

| | `subscribeOn` / `publishOn` | `parallel()` + `runOn()` |
|--|---|---|
| Procesamiento | Un elemento a la vez, en otro hilo | Múltiples elementos simultáneos |
| Ideal para | I/O bloqueante (BD, archivos, APIs bloqueantes) | CPU-bound (cálculo, transformación pesada) |
| Orden de salida | Preservado | No garantizado (sin `sequential()`) |
| Overhead | Bajo | Mayor (coordinar rails) |

> ⚠️ **Regla práctica:** si tu operación es I/O no bloqueante (WebClient, R2DBC, etc.), no necesitas `parallel()`. Si tu operación es un cálculo pesado que consume CPU durante segundos, `parallel()` + `runOn(Schedulers.parallel())` es la herramienta correcta.

---

### Lec05 (también) — Estrategias de Backpressure con Schedulers

Cuando producer y consumer corren en hilos diferentes (via `subscribeOn` + `publishOn`), el producer puede emitir más rápido de lo que el consumer puede procesar. Reactor gestiona automáticamente esta situación con un buffer interno.

---

## Resumen de Operadores

| Operador | Efecto |
|----------|--------|
| `subscribeOn(scheduler)` | Mueve todo el pipeline al scheduler especificado |
| `publishOn(scheduler)` | Mueve los operadores downstream al scheduler especificado |
| `parallel(n)` | Divide el flujo en n rails paralelos |
| `runOn(scheduler)` | Especifica el scheduler para los rails paralelos |
| `sequential()` | Reagrupa rails paralelos en un flujo secuencial |
