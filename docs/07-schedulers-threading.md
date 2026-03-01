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

#### El código

```java
var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);
                       sink.next(i);
                   }
                   sink.complete();
               })
               .doOnNext(v -> log.info("value: {}", v))
               .doFirst(() -> log.info("first1"))
               .subscribeOn(Schedulers.boundedElastic())  // ← punto de cambio
               .doFirst(() -> log.info("first2"));        // ← está DESPUÉS

Thread.ofPlatform().name("th-virtual-A").start(
    () -> flux.subscribe(Util.subscriber("sub1"))
);
```

#### Los logs reales

```
[   th-virtual-A] first2          → ⚠️ corre en el hilo llamante
[oundedElastic-1] first1          → corre en boundedElastic
[oundedElastic-1] generating: 1   → Flux.create en boundedElastic
[oundedElastic-1] value: 1        → doOnNext en boundedElastic
[oundedElastic-1] sub1 received: 1
[oundedElastic-1] generating: 2
[oundedElastic-1] value: 2
[oundedElastic-1] sub1 received: 2
[oundedElastic-1] sub1 received complete!
```

`first2` corre en `th-virtual-A` (el hilo llamante), y `first1` en `oundedElastic-1`. ¿Por qué?

---

#### La clave: la suscripción viaja de downstream a upstream

Cuando se llama a `subscribe()`, la señal de suscripción no va hacia adelante (hacia el subscriber), sino hacia atrás, **de downstream hacia upstream** — del subscriber hacia la fuente. Los operadores `doFirst()` se ejecutan en ese recorrido de vuelta hacia la fuente.

```
Dirección de la señal de suscripción:
  subscribe() → doFirst("first2") → subscribeOn(boundedElastic) → doFirst("first1") → Flux.create
               [th-virtual-A]       [cambia de hilo aquí ↓]        [oundedElastic-1]   [oundedElastic-1]
```

El recorrido paso a paso sobre `th-virtual-A`:

1. `subscribe()` se llama → la señal empieza a viajar upstream.
2. La señal llega a `doFirst("first2")` — **todavía estamos en `th-virtual-A`**, porque aún no se ha atravesado el `subscribeOn()`.
3. La señal llega a `subscribeOn(boundedElastic())` — **aquí Reactor cambia el hilo**. El resto de la suscripción se despacha a `oundedElastic-1`.
4. La señal llega a `doFirst("first1")` — ya en `oundedElastic-1` ✅
5. La señal llega a `Flux.create` — en `oundedElastic-1`, empieza a emitir.

---

#### Por qué `first2` es la excepción

`doFirst()` se ejecuta **durante el recorrido upstream de la suscripción**, justo en el momento en que la señal pasa por ese operador. Si el operador está **downstream de `subscribeOn()`** (es decir, más cerca del subscriber), la señal lo atraviesa **antes de que `subscribeOn()` cambie el hilo**.

```
POSICIÓN EN EL PIPELINE:

  [Flux.create] ← [doFirst "first1"] ← [subscribeOn()] ← [doFirst "first2"] ← [subscribe()]
       fuente          upstream               ↑                downstream          llamante
                                        punto de cambio
                                        de hilo

  Todo lo que está a la IZQUIERDA del subscribeOn → corre en boundedElastic
  Todo lo que está a la DERECHA del subscribeOn  → corre en el hilo llamante
```

---

#### Resumen hilo por hilo

| Operador | Hilo | Motivo |
|----------|------|--------|
| `doFirst("first2")` | `th-virtual-A` | Está downstream de `subscribeOn()` → se ejecuta antes del cambio de hilo |
| `subscribeOn(boundedElastic)` | — | Punto de cambio |
| `doFirst("first1")` | `oundedElastic-1` | Está upstream de `subscribeOn()` → ya en el nuevo hilo |
| `Flux.create` | `oundedElastic-1` | Fuente — siempre en el hilo del `subscribeOn()` |
| `doOnNext` | `oundedElastic-1` | Operador downstream, los datos fluyen desde `Flux.create` en `oundedElastic` |
| `Subscriber` | `oundedElastic-1` | Mismo hilo que la fuente |

> **Regla corregida:** `subscribeOn()` mueve la **fuente y todos los operadores entre la fuente y el `subscribeOn()`** a un nuevo hilo. Los operadores colocados **después** del `subscribeOn()` (más cerca del subscriber) se ejecutan en el hilo llamante durante la fase de suscripción.

**Cuándo usar:** cuando la fuente es bloqueante (llamadas a BD, APIs, lectura de archivos) y necesitas no bloquear el hilo que llama a `subscribe()`.

**Regla de múltiples `subscribeOn()`:** para la **emisión de datos**, solo el **más cercano a la fuente** determina el hilo. Sin embargo, durante la **fase de suscripción** cada `subscribeOn()` actúa como relay cambiando el hilo para los `doFirst()` que estén entre ellos (ver Lec03 para el análisis detallado).

---

### Lec03 — Múltiples `subscribeOn()`

#### El código

```java
var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);
                       sink.next(i);
                   }
                   sink.complete();
               })
               .doFirst(() -> log.info("first1"))
               .subscribeOn(Schedulers.newParallel("gubathread"))  // ← más cercano a la fuente
               .doOnNext(v -> log.info("value: {}", v))
               .doFirst(() -> log.info("first2"))
               .subscribeOn(Schedulers.boundedElastic())           // ← más lejos de la fuente
               .doFirst(() -> log.info("first3"));

Thread.ofPlatform().start(() -> flux.subscribe(Util.subscriber("sub1")));
```

#### Los logs reales

```
[       Thread-0] first3          → hilo llamante (antes de cualquier subscribeOn)
[oundedElastic-1] first2          → boundedElastic tomó el control aquí
[   gubathread-1] first1          → gubathread tomó el control aquí
[   gubathread-1] generating: 1   → Flux.create en gubathread
[   gubathread-1] value: 1        → doOnNext en gubathread
[   gubathread-1] sub1 received: 1
[   gubathread-1] generating: 2
[   gubathread-1] value: 2
[   gubathread-1] sub1 received: 2
[   gubathread-1] sub1 received complete!
```

---

#### Lo que realmente sucede: ambos `subscribeOn()` actúan durante la suscripción

La frase "el segundo es ignorado" es incorrecta mirando los logs. Lo que ocurre es más preciso:

La señal de suscripción viaja de **downstream a upstream** (del subscriber hacia la fuente), y cada `subscribeOn()` cambia el hilo **en ese momento**, como si fuera un relay de hilos:

```
Direction: ← ← ← ← (suscripción viaja hacia la fuente)

  subscribe()
  [Thread-0]
      │
      ▼
  doFirst("first3")          → corre en Thread-0    (antes del primer subscribeOn)
      │
      ▼
  subscribeOn(boundedElastic) → cambia a oundedElastic-1
      │
      ▼
  doFirst("first2")          → corre en oundedElastic-1
      │
      ▼
  subscribeOn(gubathread)    → cambia a gubathread-1
      │
      ▼
  doFirst("first1")          → corre en gubathread-1
      │
      ▼
  Flux.create                → corre en gubathread-1  ← aquí empiezan a fluir datos
```

Luego los datos fluyen de upstream a downstream (en la dirección normal):

```
Direction: → → → → (datos fluyen hacia el subscriber)

  Flux.create [gubathread-1] → doOnNext [gubathread-1] → subscriber [gubathread-1]
```

---

#### La regla correcta sobre múltiples `subscribeOn()`

| Fase | Comportamiento |
|------|----------------|
| **Suscripción** (upstream ←) | Cada `subscribeOn()` cambia el hilo al pasar por él |
| **Emisión de datos** (downstream →) | Los datos fluyen en el hilo establecido por el `subscribeOn()` **más cercano a la fuente** |

El `subscribeOn(gubathread)` es el más cercano a la fuente, por eso `Flux.create` corre en `gubathread-1`. Los datos emitidos desde ahí siguen en `gubathread-1` a lo largo de todo el pipeline.

El `subscribeOn(boundedElastic)` solo afecta a `doFirst("first2")` durante la propagación de la suscripción — pero **no tiene ningún efecto sobre la emisión de datos**.

---

#### Resumen hilo por hilo

| Operador | Hilo | ¿Por qué? |
|----------|------|-----------|
| `doFirst("first3")` | `Thread-0` | Downstream de ambos `subscribeOn` → hilo llamante |
| `subscribeOn(boundedElastic)` | — | Primer punto de cambio (suscripción viaja upstream) |
| `doFirst("first2")` | `oundedElastic-1` | Entre los dos `subscribeOn` |
| `subscribeOn(gubathread)` | — | Segundo punto de cambio, más cercano a la fuente |
| `doFirst("first1")` | `gubathread-1` | Upstream del `subscribeOn(gubathread)` |
| `Flux.create` | `gubathread-1` | La fuente siempre corre en el scheduler más cercano |
| `doOnNext` | `gubathread-1` | Los datos fluyen desde la fuente → mismo hilo |
| Subscriber | `gubathread-1` | Mismo hilo que toda la emisión |

> **Práctica recomendada:** usa un único `subscribeOn()` por pipeline, ubicado lo más cerca posible de la fuente. Si necesitás cambiar el hilo en partes distintas del pipeline, usá `publishOn()` para cada sección.

---

### Lec04 — Virtual Threads (Java 21+)

#### ¿Qué son los Virtual Threads?

Los **Virtual Threads** (Project Loom, Java 21+) son hilos gestionados por la **JVM** en lugar del sistema operativo. La diferencia clave es cómo se mapean a hilos reales del SO:

```
Platform Threads (tradicionales):
  JVM Thread ──────── OS Thread   (1:1, el SO gestiona cada hilo)
  Límite práctico: ~1.000–10.000 hilos (memoria, cambio de contexto)

Virtual Threads:
  JVM VThread ─┐
  JVM VThread ─┼─── OS Thread 1  (N:M, la JVM multiplexa miles de VThreads
  JVM VThread ─┤                  sobre pocos hilos del SO)
  JVM VThread ─┘
  Límite práctico: millones de VThreads concurrentes
```

Cuando un Virtual Thread se bloquea esperando I/O, la JVM lo **desmonta** del hilo del SO (que queda libre para otro VThread) y lo vuelve a montar cuando la operación I/O termina. El bloqueo es invisible para el SO.

---

#### El código

```java
// ⚠️ Debe establecerse ANTES de que Schedulers.boundedElastic() se inicialice
System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");

var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);
                       sink.next(i);
                   }
                   sink.complete();
               })
               .doOnNext(v -> log.info("value: {}", v))
               .doFirst(() -> log.info("first1-{}", Thread.currentThread().isVirtual()))
               .subscribeOn(Schedulers.boundedElastic())
               .doFirst(() -> log.info("first2-{}", Thread.currentThread().isVirtual()));

Thread.ofPlatform().name("th-platform").start(
    () -> flux.subscribe(Util.subscriber("sub1"))
);
```

`Thread.currentThread().isVirtual()` devuelve `true` si el hilo actual es un Virtual Thread, `false` si es un Platform Thread.

---

#### Análisis de los logs

**Caso 1: `defaultBoundedElasticOnVirtualThreads=false` (valor por defecto)**

```
[    th-platform] first2-false    → doFirst externo: corre en el hilo llamante (Platform) ✅
[oundedElastic-1] first1-false    → boundedElastic usa Platform Threads → isVirtual() = false
[oundedElastic-1] generating: 1   → Flux.create en Platform Thread
[oundedElastic-1] value: 1        → doOnNext en Platform Thread
[oundedElastic-1] sub1 received: 1
```

**Caso 2: `defaultBoundedElasticOnVirtualThreads=true`**

```
[    th-platform] first2-false    → doFirst externo: corre en el hilo llamante (Platform) ✅
[oundedElastic-1] first1-true     → boundedElastic usa Virtual Threads → isVirtual() = true ✅
[oundedElastic-1] generating: 1   → Flux.create en Virtual Thread
[oundedElastic-1] value: 1        → doOnNext en Virtual Thread
[oundedElastic-1] sub1 received: 1
```

---

#### Interpretación de los logs

| Observación | Explicación |
|-------------|-------------|
| `first2` siempre es `false` | `doFirst("first2")` está fuera del alcance de `subscribeOn()`. Se ejecuta en el hilo llamante (`th-platform`), que fue creado con `Thread.ofPlatform()` → nunca es virtual. |
| `first1` cambia entre `false` y `true` | `doFirst("first1")` está dentro del alcance de `subscribeOn(boundedElastic())`. Ese scheduler cambia de tipo según la propiedad del sistema. |
| El nombre del hilo siempre es `oundedElastic-1` | Reactor reutiliza el mismo nombre para el scheduler. Lo que cambia internamente es el **tipo** del hilo, no su nombre. |
| Toda la ejecución (create, doOnNext, subscriber) cambia de tipo | `subscribeOn()` afecta toda la fuente upstream. Al cambiar el scheduler, todos esos operadores pasan a correr en Virtual Threads. |

---

#### ¿Qué cambia internamente con la propiedad?

```
false (default):
  Schedulers.boundedElastic()
    └── ThreadPoolExecutor con Platform Threads
          ├── oundedElastic-1 (Platform)
          ├── oundedElastic-2 (Platform)
          └── ...

true:
  Schedulers.boundedElastic()
    └── Executor con Virtual Threads (uno por tarea)
          ├── oundedElastic-1 (Virtual) ← mismo nombre, distinto tipo
          ├── oundedElastic-2 (Virtual)
          └── ...
```

Con `true`, cada tarea que llega a `boundedElastic()` corre en un Virtual Thread nuevo, creado y destruido automáticamente por la JVM. No hay un pool de tamaño fijo: los Virtual Threads son tan baratos que no necesitan reciclarse.

---

#### ¿Cuándo conviene activarlo?

| Escenario | `false` (Platform Threads) | `true` (Virtual Threads) |
|-----------|---------------------------|--------------------------|
| Pocas operaciones bloqueantes concurrentes | ✅ Suficiente | ✅ También funciona |
| Miles de operaciones bloqueantes simultáneas | ⚠️ El pool se agota | ✅ Escala sin límite |
| Código bloqueante legacy (JDBC, REST síncrono) | ⚠️ Limitado por pool | ✅ Ideal |
| Operaciones CPU-bound | ✅ Ambos iguales | ✅ Ambos iguales |
| I/O no bloqueante (WebClient, R2DBC) | ✅ Prefiere no usar boundedElastic | ✅ Igual, pero innecesario |

> ⚠️ **La propiedad debe establecerse antes de la primera llamada a `Schedulers.boundedElastic()`**, ya que el scheduler se inicializa de forma lazy en la primera invocación. Si se configura después, no tendrá efecto.

---

### Lec05 — `publishOn()` vs `subscribeOn()`

#### El código

```java
var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);
                       sink.next(i);
                   }
                   sink.complete();
               })
               .publishOn(Schedulers.parallel())                   // primer cambio de hilo
               .doOnNext(v -> log.info("value: {}", v))
               .doFirst(() -> log.info("first1"))
               .publishOn(Schedulers.boundedElastic())             // segundo cambio de hilo
               .map(s -> { log.info("map"); return s; })
               .doFirst(() -> log.info("first2"));

Thread.ofPlatform().name("th-platform").start(
    () -> flux.subscribe(Util.subscriber("sub1"))
);
```

#### Los logs reales

```
[    th-platform] first2          → doFirst "fuera" del último publishOn
[    th-platform] first1          → doFirst "fuera" del primer publishOn
[    th-platform] generating: 1   → Flux.create en el hilo llamante
[    th-platform] generating: 2   → Flux.create emite AMBOS antes de que parallel consuma
[     parallel-1] value: 1        → doOnNext en parallel (después del primer publishOn)
[     parallel-1] value: 2
[oundedElastic-1] map             → map en boundedElastic (después del segundo publishOn)
[oundedElastic-1] sub1 received: 1
[oundedElastic-1] map
[oundedElastic-1] sub1 received: 2
[oundedElastic-1] sub1 received complete!
```

---

#### La diferencia clave con `subscribeOn()`

`subscribeOn()` cambia el hilo durante la **propagación de la suscripción** (señal que viaja upstream ←).

`publishOn()` **no hace nada durante la suscripción**. Solo actúa cuando los **datos** fluyen downstream →.

Por eso ambos `doFirst` corren en `th-platform` — la suscripción viaja de vuelta desde el subscriber hacia la fuente completamente en `th-platform`, sin que ningún `publishOn` intervenga:

```
Suscripción (viaja ← upstream):
  subscribe() → doFirst("first2") → publishOn(boundedElastic) → doFirst("first1") → publishOn(parallel) → Flux.create
  [th-platform]   [th-platform]        (sin efecto aquí)          [th-platform]        (sin efecto aquí)    [th-platform]
```

---

#### Las tres zonas de hilos para los datos

Una vez que `Flux.create` empieza a emitir, los datos fluyen downstream → y ahí sí actúan los `publishOn`:

```
Flujo de datos (→ downstream):

  Flux.create        publishOn(parallel)        publishOn(boundedElastic)
  [th-platform]  ──►  [internal queue]  ──►  [parallel-1]  ──►  [internal queue]  ──►  [oundedElastic-1]
  generating: 1       (desacopla hilos)       value: 1           (desacopla hilos)       map
  generating: 2                               value: 2                                   sub1 received
```

| Zona | Hilo | Operadores |
|------|------|-----------|
| Antes del primer `publishOn` | `th-platform` | `Flux.create`, `doFirst` callbacks |
| Entre los dos `publishOn` | `parallel-1` | `doOnNext` |
| Después del segundo `publishOn` | `oundedElastic-1` | `map`, subscriber |

---

#### ¿Por qué `generating: 1` y `generating: 2` aparecen antes de `value: 1`?

`publishOn` introduce una **cola interna** (por defecto 256 elementos) que desacopla al productor del consumidor. El flujo es:

1. `Flux.create` corre en `th-platform` y llena la cola: emite `1` y `2` de forma síncrona.
2. `publishOn(parallel)` toma los ítems de la cola y los despacha a `parallel-1` de forma asíncrona.
3. El productor ya terminó antes de que el consumidor empiece — por eso aparecen los dos `generating` juntos antes de cualquier `value`.

Si el productor fuese más lento (por ejemplo, con `Util.sleepMillis(100)` entre ítems), verías el intercalado: `generating: 1` → `value: 1` → `generating: 2` → `value: 2`.

---

#### `publishOn()` vs `subscribeOn()` — comparación correcta

| | `subscribeOn()` | `publishOn()` |
|--|----------------|---------------|
| ¿Qué mueve? | El hilo de la **fuente** (upstream) | El hilo del **flujo de datos** (downstream) |
| ¿Afecta `doFirst`? | ✅ Sí — cambia hilo durante suscripción | ❌ No — `doFirst` sigue en el hilo llamante |
| ¿Múltiples usos? | Solo el más cercano a la fuente gana | Cada uno tiene efecto independiente |
| ¿Introduce cola? | ❌ No | ✅ Sí (desacopla productor/consumidor) |
| Uso principal | Fuente bloqueante | Seccionar el pipeline en distintos hilos |

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

#### El código

```java
var flux = Flux.create(sink -> {
                   for (int i = 1; i < 3; i++) {
                       log.info("generating: {}", i);
                       sink.next(i);
                   }
                   sink.complete();
               })
               .publishOn(Schedulers.parallel())           // posición 2
               .doOnNext(v -> log.info("value: {}", v))   // posición 3
               .doFirst(() -> log.info("first1"))          // posición 4
               .subscribeOn(Schedulers.boundedElastic())   // posición 5
               .doFirst(() -> log.info("first2"));         // posición 6

Thread.ofPlatform().start(() -> flux.subscribe(Util.subscriber("sub1")));
```

---

#### Paso 1 — suscripción viaja upstream (← derecha a izquierda)

Aplicando lo aprendido en Lec02 y Lec05:
- `subscribeOn` **sí** cambia el hilo durante la propagación de la suscripción.
- `publishOn` **no** cambia el hilo durante la propagación de la suscripción (solo actúa sobre datos).

```
subscribe() en th-platform
    │
    ▼  posición 6
doFirst("first2")          → th-platform   (antes de que subscribeOn cambie el hilo)
    │
    ▼  posición 5
subscribeOn(boundedElastic) → ★ CAMBIA A oundedElastic-1
    │
    ▼  posición 4
doFirst("first1")          → oundedElastic-1
    │
    ▼  posición 3
doOnNext                   → solo registra el callback, no se ejecuta aún
    │
    ▼  posición 2
publishOn(parallel)        → NO cambia hilo en fase de suscripción (Lec05)
    │
    ▼  posición 1
Flux.create                → oundedElastic-1  ← subscribeOn movió la fuente aquí ✅
```

**`subscribeOn(boundedElastic)` sí tiene efecto**: mueve `Flux.create` a `oundedElastic-1` aunque en el código aparezca después de `publishOn`. Lo que importa no es el orden de escritura, sino dónde intercepta la señal de suscripción al viajar upstream.

---

#### Paso 2 — datos fluyen downstream (→ izquierda a derecha)

```
Flux.create emite en oundedElastic-1
    │
    ▼  posición 2
publishOn(parallel)        → ★ CAMBIA A parallel-1 (cola interna, desacopla hilos)
    │
    ▼  posición 3
doOnNext                   → parallel-1
    │
    ▼  posición 4, 5, 6
doFirst / subscribeOn      → no se ejecutan durante la emisión de datos
    │
    ▼
Subscriber                 → parallel-1
```

---

#### Los logs

```
[        Thread-0] first2          → doFirst downstream de subscribeOn → hilo llamante
[oundedElastic-1] first1          → doFirst upstream de subscribeOn → boundedElastic ✅
[oundedElastic-1] generating: 1   → Flux.create en boundedElastic (subscribeOn lo movió aquí) ✅
[oundedElastic-1] generating: 2   → publishOn tiene cola interna → producer adelanta al consumer
[      parallel-1] value: 1       → doOnNext después del publishOn(parallel) ✅
[      parallel-1] value: 2
[      parallel-1] sub1 received: 1
[      parallel-1] sub1 received: 2
[      parallel-1] sub1 received complete!
```

---

#### Resumen hilo por hilo

| Operador | Fase suscripción (←) | Fase datos (→) |
|----------|---------------------|----------------|
| `doFirst("first2")` | `th-platform` (downstream de subscribeOn) | — |
| `subscribeOn(boundedElastic)` | ★ cambia a `oundedElastic-1` | sin efecto |
| `doFirst("first1")` | `oundedElastic-1` (upstream de subscribeOn) | — |
| `publishOn(parallel)` | sin efecto en suscripción | ★ cambia a `parallel-1` |
| `Flux.create` | `oundedElastic-1` ← movido por subscribeOn | emite en `oundedElastic-1` |
| `doOnNext` | — | `parallel-1` |
| Subscriber | — | `parallel-1` |

---

#### El resultado: dos zonas de hilo perfectamente separadas

```
┌──────────────────────────────────┐     ┌────────────────────────────────────┐
│   Fuente (I/O, bloqueante)       │     │   Procesamiento (CPU-bound)        │
│   boundedElastic-1               │ ──► │   parallel-1                       │
│                                  │     │                                    │
│   Flux.create                    │     │   doOnNext                         │
│   doFirst("first1")              │     │   map, filter, ...                 │
│                                  │     │   Subscriber                       │
└──────────────────────────────────┘     └────────────────────────────────────┘
          ▲                                          ▲
          │ subscribeOn(boundedElastic)              │ publishOn(parallel)
          │ mueve la fuente aquí                     │ mueve los datos aquí
```

Esto es exactamente lo que la combinación está diseñada para lograr:
- **`subscribeOn`** se encarga de la **fuente** — la mueve a un scheduler apropiado para I/O.
- **`publishOn`** se encarga del **procesamiento** — cambia el hilo para los operadores que procesan los datos.

Y la clave que demuestra este ejemplo: **no importa dónde escribas `subscribeOn` en el código** — siempre moverá la fuente porque actúa sobre la señal de suscripción, que viaja upstream sin ser afectada por `publishOn`.

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

---

### `subscribeOn(scheduler)`

**Problema que resuelve:** la fuente del stream es bloqueante (JDBC, lectura de archivo, API REST síncrona) y querés que no bloquee el hilo que llama a `subscribe()`.

**Qué hace:** mueve la **fuente** (y todo lo que está entre la fuente y el `subscribeOn`) a un nuevo scheduler. Actúa durante la propagación de la suscripción (← upstream).

**Operadores y métodos afectados:**

Durante la **fase de suscripción** (← upstream), `subscribeOn` cambia el hilo para todo lo que esté entre él y la fuente:

| Operador / Método | ¿Afectado? | Nota |
|-------------------|------------|------|
| `doFirst()` upstream de `subscribeOn` | ✅ | Corre en el nuevo scheduler — la señal de suscripción ya cambió de hilo |
| `doFirst()` downstream de `subscribeOn` | ❌ | Corre en el hilo llamante — se ejecuta antes de que `subscribeOn` cambie el hilo |
| `doOnSubscribe()` (cualquier posición) | ✅ | **Siempre** corre en el nuevo scheduler, independientemente de su posición. A diferencia de `doFirst`, reacciona a la señal `onSubscribe` que viaja → downstream desde la fuente (ya en el scheduler). |

Durante la **fase de emisión de datos** (→ downstream), como la fuente ya corre en el nuevo scheduler, todos los operadores heredan ese hilo a menos que un `publishOn` lo cambie:

| Operador / Método | ¿Afectado? | Nota |
|-------------------|------------|------|
| `Flux.create()` / `Flux.generate()` | ✅ | La fuente corre en el nuevo scheduler |
| `Flux.range()` / `Flux.just()` / `Flux.fromIterable()` | ✅ | Cualquier fuente es afectada |
| `Mono.fromCallable()` / `Mono.fromSupplier()` / `Mono.defer()` | ✅ | Fuentes de Mono también |
| `map()` / `filter()` / `handle()` | ✅ | Corren en el scheduler de la fuente |
| `flatMap()` / `concatMap()` | ✅ | La función lambda corre en el scheduler |
| `doOnNext()` / `doOnComplete()` / `doOnError()` | ✅ | Callbacks de datos en el mismo scheduler |
| `doOnTerminate()` / `doOnCancel()` | ✅ | Callbacks de ciclo de vida |
| `take()` / `takeWhile()` / `takeUntil()` | ✅ | Operadores de corte |
| `buffer()` / `window()` / `groupBy()` | ✅ | Operadores de agrupación |
| `collectList()` / `reduce()` / `count()` | ✅ | Operadores de agregación |
| Subscriber (`onNext`, `onComplete`, `onError`) | ✅ | El subscriber final |
| Operadores después de un `publishOn` | ❌ | `publishOn` sobreescribe el hilo a partir de ese punto |

**Cuándo aplicarlo:**
- La fuente usa `Thread.sleep`, JDBC, `InputStream`, o cualquier llamada bloqueante.
- Querés que todo el pipeline corra en un scheduler específico desde el inicio.
- Usás Spring MVC (bloqueante) y querés integrar Reactor sin bloquear el hilo del servidor.

**Cuándo NO aplicarlo:**
- La fuente ya es no bloqueante (WebClient, R2DBC, `Flux.interval`). Agregar `subscribeOn` no da ningún beneficio y suma confusión.
- Cuando solo querés cambiar el hilo para una parte del pipeline — usá `publishOn` en ese punto.

**Scheduler recomendado:** `boundedElastic()` para I/O bloqueante.

```java
// ✅ Correcto: fuente bloqueante → subscribeOn
Flux.create(sink -> {
    List<User> users = jdbcRepo.findAll(); // bloqueante
    users.forEach(sink::next);
    sink.complete();
})
.subscribeOn(Schedulers.boundedElastic()) // ← fuente corre en boundedElastic
.map(User::getName)
.subscribe(log::info);

// ❌ Innecesario: fuente ya es no bloqueante
webClient.get().retrieve().bodyToFlux(User.class)
    .subscribeOn(Schedulers.boundedElastic()) // ← no aporta nada
    .subscribe(log::info);
```

---

### `publishOn(scheduler)`

**Problema que resuelve:** una parte específica del pipeline necesita correr en un scheduler diferente al de la fuente — por ejemplo, después de recibir datos de un Event Loop querés hacer procesamiento bloqueante sin bloquear ese Event Loop.

**Qué hace:** cambia el hilo **solo para los operadores que están después de él** (→ downstream). Actúa sobre el flujo de datos, no sobre la suscripción. Introduce una cola interna que desacopla al productor del consumidor.

**Operadores y métodos afectados:**

`publishOn` **no actúa durante la suscripción** — solo cuando los datos fluyen → downstream. Por eso los operadores de suscripción nunca son afectados:

| Operador / Método | ¿Afectado? | Nota |
|-------------------|------------|------|
| `doFirst()` | ❌ | Corre durante la suscripción (← upstream), `publishOn` no interviene |
| `doOnSubscribe()` | ❌ | Ídem, fase de suscripción |
| Fuente (`Flux.create`, `Flux.generate`, etc.) | ❌ | La fuente necesita `subscribeOn`, no `publishOn` |
| Operadores ANTES del `publishOn` | ❌ | Solo afecta lo que viene después |

Operadores colocados **después** del `publishOn` en el pipeline:

| Operador / Método | ¿Afectado? | Nota |
|-------------------|------------|------|
| `map()` / `filter()` / `handle()` | ✅ | Corren en el nuevo scheduler |
| `flatMap()` / `concatMap()` | ✅ | La función lambda corre en el nuevo scheduler |
| `doOnNext()` | ✅ | Callback de datos, corre en el nuevo scheduler |
| `doOnComplete()` / `doOnError()` | ✅ | Callbacks de señales de finalización |
| `doOnTerminate()` / `doOnCancel()` | ✅ | Callbacks de ciclo de vida |
| `take()` / `takeWhile()` / `takeUntil()` | ✅ | Operadores de corte downstream |
| `buffer()` / `window()` / `groupBy()` | ✅ | Operadores de agrupación downstream |
| `collectList()` / `reduce()` / `count()` | ✅ | Operadores de agregación downstream |
| Subscriber (`onNext`, `onComplete`, `onError`) | ✅ | El subscriber final corre en el nuevo scheduler |
| Un segundo `publishOn` más adelante | ✅ parcial | Tiene efecto independiente — vuelve a cambiar el hilo a partir de ese punto |

**Cuándo aplicarlo:**
- Recibís datos de un Event Loop (Netty/WebFlux) y necesitás hacer trabajo bloqueante (Lec06).
- Querés seccionar el pipeline: una parte en `boundedElastic` (I/O) y otra en `parallel` (CPU).
- Necesitás múltiples cambios de hilo a lo largo del pipeline — cada `publishOn` tiene efecto independiente.

**Cuándo NO aplicarlo:**
- Para mover la fuente a otro hilo — usá `subscribeOn`.
- Si toda la cadena debe correr en el mismo scheduler — usá solo `subscribeOn`.
- Si el pipeline ya es completamente no bloqueante — no hay necesidad de cambiar hilos.

**Scheduler recomendado:** `boundedElastic()` para procesamiento bloqueante post-I/O; `parallel()` para cómputo CPU-intensivo.

```java
// ✅ Correcto: Event Loop entrega el dato → publishOn descarga el trabajo bloqueante
client.getProductName(id)                      // corre en Event Loop (Netty)
    .publishOn(Schedulers.boundedElastic())    // ← descarga aquí
    .map(name -> heavyProcess(name))           // bloqueante, corre en boundedElastic
    .subscribe(log::info);

// ✅ Correcto: seccionar el pipeline en dos zonas de hilo
Flux.create(sink -> { /* fuente bloqueante */ })
    .subscribeOn(Schedulers.boundedElastic())  // fuente en boundedElastic
    .map(item -> parse(item))                  // sigue en boundedElastic
    .publishOn(Schedulers.parallel())          // ← cambia a parallel
    .map(item -> compute(item))               // CPU-bound en parallel
    .subscribe(log::info);
```

---

### `parallel(n)` + `runOn(scheduler)`

**Problema que resuelve:** tenés una operación CPU-intensiva que puede aplicarse a múltiples elementos de forma independiente y querés aprovechar todos los núcleos del procesador para reducir el tiempo total.

**Qué hace:** `parallel(n)` divide el stream en `n` rails independientes (round-robin). `runOn(scheduler)` asigna cada rail a un hilo del scheduler dado. Múltiples elementos se procesan **al mismo tiempo**.

**Cuándo aplicarlo:**
- Operaciones de cómputo pesado por elemento: procesamiento de imágenes, cálculos matemáticos, compresión, encriptación.
- El trabajo por elemento es independiente (no comparte estado mutable).
- El tiempo de procesamiento por elemento es significativo (segundos, no milisegundos).

**Cuándo NO aplicarlo:**
- Para I/O no bloqueante (WebClient, R2DBC): el Event Loop ya maneja miles de operaciones con un solo hilo. `parallel()` solo agrega overhead.
- Para operaciones rápidas (< 1ms por elemento): el costo de dividir y coordinar los rails supera el beneficio.
- Cuando el orden de salida es crítico y no podés usar `sequential()`.
- Para operaciones bloqueantes simples: `subscribeOn` + `publishOn` es más simple y suficiente.

**Scheduler recomendado:** `parallel()` (diseñado para CPU-bound, un hilo por núcleo).

```java
// ✅ Correcto: cómputo pesado por elemento
Flux.range(1, 100)
    .parallel(Runtime.getRuntime().availableProcessors()) // un rail por núcleo
    .runOn(Schedulers.parallel())
    .map(i -> expensiveCompute(i))   // CPU-bound, corre en paralelo
    .sequential()                    // reagrupa si necesitás orden
    .subscribe(log::info);

// ❌ Incorrecto: operación ya no bloqueante
webClient.get().retrieve().bodyToFlux(User.class)
    .parallel(4)                    // overhead innecesario
    .runOn(Schedulers.parallel())
    .subscribe(log::info);
```

---

### `sequential()`

**Problema que resuelve:** después de procesar en paralelo con `parallel()` + `runOn()`, necesitás volver a un flujo secuencial para aplicar operadores que requieren un único stream (`collectList()`, `reduce()`, `count()`, etc.).

**Qué hace:** fusiona los `n` rails paralelos de vuelta en un único `Flux`. El orden de salida no está garantizado (depende de qué rail termina primero), a menos que uses variantes ordenadas.

**Cuándo aplicarlo:**
- Necesitás usar operadores de agregación después de `parallel()` (`collectList`, `reduce`, `count`).
- Querés preservar el orden de los resultados (combinado con operadores de ordenamiento).
- Necesitás pasar la salida del procesamiento paralelo a otro sistema que espera un stream único.

**Cuándo NO aplicarlo:**
- Si el subscriber puede consumir directamente desde el `ParallelFlux` sin necesitar un stream único.
- Cuando el orden no importa y cada elemento puede entregarse al subscriber en cuanto esté listo — omitir `sequential()` reduce la latencia.

```java
// ✅ Con sequential(): necesitás collectList
Flux.range(1, 10)
    .parallel(4)
    .runOn(Schedulers.parallel())
    .map(i -> compute(i))
    .sequential()       // ← necesario antes de collectList
    .collectList()
    .subscribe(list -> log.info("total: {}", list.size()));

// ✅ Sin sequential(): cada resultado se entrega en cuanto está listo
Flux.range(1, 10)
    .parallel(4)
    .runOn(Schedulers.parallel())
    .map(i -> compute(i))
    .subscribe(result -> log.info("listo: {}", result)); // menor latencia
```

---

### Tabla de decisión rápida

| Situación | Operador |
|-----------|----------|
| Fuente bloqueante (JDBC, archivo, API síncrona) | `subscribeOn(boundedElastic())` |
| Event Loop entrega dato y el siguiente operador es bloqueante | `publishOn(boundedElastic())` |
| Parte del pipeline es CPU-intensiva (un elemento a la vez) | `publishOn(parallel())` |
| Múltiples elementos necesitan procesarse simultáneamente (CPU) | `parallel(n)` + `runOn(parallel())` |
| Fuente no bloqueante (WebClient, R2DBC, `Flux.interval`) | ❌ ninguno — ya es eficiente |
| Necesitás agregar (`collectList`, `reduce`) después de `parallel()` | `sequential()` antes del agregador |
| Solo una parte del pipeline necesita otro hilo | `publishOn()` en ese punto |
| Todo el pipeline necesita correr en otro hilo | `subscribeOn()` una sola vez, cerca de la fuente |
