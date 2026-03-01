# Sección 06 — Cold Publisher vs Hot Publisher

## Objetivo

Esta sección explora la diferencia fundamental entre **Cold Publishers** y **Hot Publishers**, cómo convertir uno en otro, y cuándo usar cada uno.

---

## Punto de partida: lo que vimos en secciones anteriores

En las secciones 03 y 04 aprendimos a crear publishers con `Flux.create()` y `Flux.generate()`. Siempre los usamos con **un solo suscriptor**. Pero, ¿qué pasa si tenemos **más de uno**?

---

## ¿Qué ve cada suscriptor con `Flux.create()` y `Flux.generate()`?

### Con `Flux.create()`

```java
var flux = Flux.create(fluxSink -> {
    log.info("lambda invocado"); // ← ¿cuántas veces se ejecuta esto?
    for (int i = 1; i <= 3; i++) {
        fluxSink.next(i);
    }
    fluxSink.complete();
});

flux.subscribe(Util.subscriber("sub1"));
flux.subscribe(Util.subscriber("sub2"));
```

**Output en consola:**
```
[main] lambda invocado         ← se ejecuta para sub1
[sub1] received: 1
[sub1] received: 2
[sub1] received: 3
[sub1] completed

[main] lambda invocado         ← se ejecuta OTRA VEZ para sub2
[sub2] received: 1
[sub2] received: 2
[sub2] received: 3
[sub2] completed
```

> Cada suscriptor dispara una ejecución **nueva e independiente** del lambda. `sub1` y `sub2` no comparten nada: cada uno recibe su propio stream desde cero.

---

### Con `Flux.generate()`

```java
var flux = Flux.generate(
    () -> { log.info("estado inicial creado"); return 1; }, // ← ¿cuántas veces?
    (state, sink) -> {
        sink.next("item-" + state);
        if (state == 3) sink.complete();
        return state + 1;
    }
);

flux.subscribe(Util.subscriber("sub1"));
flux.subscribe(Util.subscriber("sub2"));
```

**Output en consola:**
```
[main] estado inicial creado   ← se crea para sub1
[sub1] received: item-1
[sub1] received: item-2
[sub1] received: item-3
[sub1] completed

[main] estado inicial creado   ← se crea de nuevo para sub2
[sub2] received: item-1
[sub2] received: item-2
[sub2] received: item-3
[sub2] completed
```

> Mismo comportamiento: cada suscriptor tiene su **propia ejecución independiente** del generate. El estado no se comparte.

---

### Resumen: los dos se comportan igual con múltiples suscriptores

| | `Flux.create()` | `Flux.generate()` |
|-|----------------|-------------------|
| Lambda se ejecuta | Una vez **por suscriptor** | Una vez **por suscriptor** |
| Datos compartidos | ❌ Cada uno recibe su propio stream | ❌ Cada uno recibe su propio stream |
| Estado compartido | ❌ | ❌ |

---

## El problema: ¿y si queremos que compartan el mismo stream?

Imaginá un stream de **precios de acciones en tiempo real** (uno cada segundo):

```java
var stockPrices = Flux.generate(
    () -> 100,
    (price, sink) -> {
        int newPrice = price + (random.nextInt(11) - 5); // varía ±5
        sink.next(newPrice);
        return newPrice;
    }
).delayElements(Duration.ofSeconds(1));
```

Con dos suscriptores sin compartir:

```java
stockPrices.subscribe(Util.subscriber("trader1"));
stockPrices.subscribe(Util.subscriber("trader2"));
```

```
[trader1] received: 103    ← trader1 ve su propio stream generado desde cero
[trader2] received: 98     ← trader2 ve OTRO stream generado desde cero
[trader1] received: 107
[trader2] received: 94
```

**❌ Problema:** `trader1` y `trader2` están viendo precios **distintos** del mismo activo. Eso no tiene sentido en la realidad — ambos deberían ver el **mismo precio en tiempo real**.

> Este es el problema que resuelven los **Hot Publishers**: compartir un único stream entre todos los suscriptores.

---

---

## Conceptos Fundamentales

### Cold Publisher (Editor Frío)

Por defecto, **todos los Publishers en Reactor son fríos**. Para cada suscriptor se crea un productor de datos **dedicado e independiente**.

**Analogía: Netflix** — Cada usuario ve su propia película desde el principio. Si alguien se une tarde, ve desde el inicio.

### Hot Publisher (Editor Caliente)

Un Hot Publisher **comparte el mismo flujo** entre todos los suscriptores. Los datos se generan **una sola vez** y se distribuyen.

**Analogía: Cine** — Todos ven la misma película al mismo tiempo. Si llegás tarde, te perdiste lo que ya pasó.

---

## Lecciones

### Lec01 — Cold Publisher

```java
var flux = Flux.create(fluxSink -> {
    log.info("invoked");   // se imprime dos veces (una por suscriptor)
    for (int i = 0; i < 3; i++) {
        fluxSink.next(i);
    }
    fluxSink.complete();
});

flux.subscribe(Util.subscriber("sub1")); // genera 0, 1, 2 independientemente
flux.subscribe(Util.subscriber("sub2")); // genera 0, 1, 2 independientemente
```

- El método de creación se invoca **para cada suscriptor**.
- Cada suscriptor recibe su propia copia completa de los datos.
- No hay estado compartido entre suscriptores.

**Casos de uso:** consultas a base de datos, lectura de archivos, llamadas REST, datos específicos por usuario.

---

### Lec02 — Hot Publisher con `share()`

```java
var hotFlux = coldFlux.share(); // alias de publish().refCount(1)
```

`share()` convierte un Cold Publisher en Hot:
- Comienza a emitir cuando **al menos 1 suscriptor** se suscribe.
- **Detiene** la emisión cuando todos los suscriptores cancelan.
- Si alguien se suscribe después de que se detuvo, **vuelve a comenzar desde el principio**.
- Todos los suscriptores activos comparten el mismo flujo.

Para requerir un mínimo de N suscriptores antes de empezar:
```java
coldFlux.publish().refCount(2); // espera 2 suscriptores
```

Con `share()`, el ejemplo de precios de acciones funcionaría correctamente:

```java
var stockPrices = Flux.generate(...)
    .delayElements(Duration.ofSeconds(1))
    .share(); // ← convierte a Hot

stockPrices.subscribe(Util.subscriber("trader1"));
stockPrices.subscribe(Util.subscriber("trader2"));
```

```
[trader1] received: 103    ← ambos ven el mismo precio
[trader2] received: 103    ← ✅ mismo valor, mismo stream
[trader1] received: 107
[trader2] received: 107
```

---

### Lec03 — Hot Publisher con `publish().autoConnect(0)`

```java
var hotFlux = coldFlux.publish().autoConnect(0);
```

- Comienza a emitir **inmediatamente**, incluso con 0 suscriptores.
- **No se detiene** cuando los suscriptores cancelan.
- Una vez iniciado, continúa produciendo independientemente de quién esté escuchando.

**Cuándo usar:** cuando querés un publisher que siempre esté activo, independientemente de los suscriptores.

---

### Lec04 — Hot Publisher con Caché: `replay()`

```java
var stockFlux = stockStream().replay(1).autoConnect(0);
```

Combinación que ofrece lo mejor de ambos mundos:
- `replay(n)` cachea los últimos `n` valores emitidos.
- Los suscriptores tardíos reciben primero los valores cacheados y luego los nuevos en tiempo real.

```
Flujo de precios (cada 3s): 45 → 67 → 23 → 89 ...

Sam se une en segundo 4:
  Sam recibe: [67 (cache)] → 23 → 89 ...

Mike se une en segundo 8:
  Mike recibe: [23 (cache)] → 89 ...
  Sam y Mike reciben lo mismo desde ahora
```

| Variante | Comportamiento |
|----------|----------------|
| `replay(1)` | Cachea solo el último valor |
| `replay(5)` | Cachea los últimos 5 valores |
| `replay()` | Cachea todos (puede agotar memoria) |
| `replay(Duration)` | Cachea valores del último período de tiempo |

**Casos de uso:** precios de acciones, métricas del sistema, dashboards de monitoreo, estado actual de aplicaciones.

---

### Lec05 — Problema con `Flux.create()` y su Solución

Cuando se usa `Flux.create()` como base para un Hot Publisher con `share()`, puede haber un problema de estado compartido: el `FluxSink` original no debería usarse después de que todos los suscriptores cancelaron. Este ejemplo muestra el problema y cómo resolverlo correctamente.

---

## Comparación General

| Aspecto | Cold Publisher | Hot Publisher |
|---------|---------------|---------------|
| Flujo de datos | Independiente por suscriptor | Compartido entre suscriptores |
| Invocación del creador | Una vez por suscriptor | Una vez para todos |
| Si te unís tarde | Ves desde el principio | Te perdés lo que ya pasó |
| Datos | Específicos por usuario/solicitud | Iguales para todos |
| Analogía | Netflix | Cine |
| Eficiencia | Menor (regenera datos) | Mayor (genera una vez) |

## Métodos para Crear Hot Publishers

| Método | Mínimo suscriptores | Se detiene sin suscriptores | Cache |
|--------|--------------------|-----------------------------|-------|
| `share()` | 1 | ✅ | ❌ |
| `publish().refCount(n)` | n | ✅ | ❌ |
| `publish().autoConnect(0)` | 0 | ❌ | ❌ |
| `replay(n).autoConnect(0)` | 0 | ❌ | ✅ (n valores) |

## Cuándo Usar Cada Uno

**Cold Publisher** cuando:
- Los datos son específicos para cada usuario o solicitud.
- Cada suscriptor necesita todos los datos desde el principio.
- Querés aislamiento completo entre suscriptores.

**Hot Publisher** cuando:
- Necesitás difundir la misma información a múltiples usuarios.
- Los datos son costosos de generar y querés compartirlos.
- Múltiples suscriptores necesitan ver el mismo estado actual.
- Querés eficiencia de recursos (no regenerar datos).

---

---

## ¿Se puede tener múltiples productores y múltiples suscriptores que se dividan los datos?

Sí, pero hay que entender una distinción clave primero.

### La diferencia entre "broadcast" y "dividir"

| Patrón | Descripción | Cada item llega a… |
|--------|-------------|-------------------|
| **Hot Publisher** (`share`) | Todos ven todo | **Todos** los suscriptores |
| **Dividir datos** (`parallel`) | Cada item va a un solo procesador | **Un solo** suscriptor/rail |

```
Hot Publisher (broadcast):
  Producer ──► item1 ──► [sub1] recibe item1
                    ──► [sub2] recibe item1   ← todos ven lo mismo
                    ──► [sub3] recibe item1

Dividir datos (parallel):
  Producer ──► item1 ──► [worker1]   ← solo worker1 procesa item1
           ──► item2 ──► [worker2]   ← solo worker2 procesa item2
           ──► item3 ──► [worker3]   ← solo worker3 procesa item3
```

---

### Múltiples productores → un stream

Usando `Flux.create()` con `FluxSink` (thread-safe) podés tener varios hilos productores alimentando el mismo stream:

```java
// 3 productores independientes → un único Flux
Flux<String> multiProducerFlux = Flux.<String>create(sink -> {

    // Productor 1: genera pedidos cada 200ms
    Thread.ofPlatform().start(() -> {
        for (int i = 1; i <= 5; i++) {
            sink.next("pedido-A" + i);
            Util.sleepMillis(200);
        }
    });

    // Productor 2: genera pedidos cada 300ms
    Thread.ofPlatform().start(() -> {
        for (int i = 1; i <= 5; i++) {
            sink.next("pedido-B" + i);
            Util.sleepMillis(300);
        }
    });

    // Productor 3: genera pedidos cada 500ms
    Thread.ofPlatform().start(() -> {
        for (int i = 1; i <= 5; i++) {
            sink.next("pedido-C" + i);
            Util.sleepMillis(500);
        }
    });
});
```

```
Output (mezclado por tiempo):
pedido-A1, pedido-B1, pedido-A2, pedido-C1, pedido-A3, pedido-B2 ...
```

> `FluxSink` serializa los accesos concurrentes. Los 3 hilos pueden llamar a `next()` al mismo tiempo de forma segura (ver sección 04).

---

### Múltiples suscriptores que se dividen los datos con `parallel()`

`parallel(n)` divide el stream en `n` "carriles" (rails). Cada item va a **un solo carril** — no se duplica.

```java
multiProducerFlux
    .parallel(3)                       // divide en 3 carriles
    .runOn(Schedulers.boundedElastic()) // cada carril corre en su propio hilo
    .subscribe(item ->
        log.info("[{}] procesando: {}", Thread.currentThread().getName(), item)
    );
```

```
Output:
[parallel-1] procesando: pedido-A1   ← worker 1
[parallel-2] procesando: pedido-B1   ← worker 2
[parallel-3] procesando: pedido-C1   ← worker 3
[parallel-1] procesando: pedido-A2   ← worker 1 de nuevo (round-robin)
[parallel-2] procesando: pedido-A3
[parallel-3] procesando: pedido-B2
```

> Cada item es procesado por **exactamente un worker**. Es el patrón clásico de producer-consumer con múltiples workers en paralelo.

---

### División por contenido con `groupBy()`

Si querés que **cada tipo de dato vaya a un suscriptor específico** según alguna regla de negocio:

```java
multiProducerFlux
    .groupBy(item -> item.charAt(7)) // 'A', 'B' o 'C' según el prefijo del pedido
    .subscribe(group -> {
        log.info("nuevo grupo para: {}", group.key());
        group.subscribe(item ->
            log.info("  [grupo-{}] procesando: {}", group.key(), item)
        );
    });
```

```
Output:
nuevo grupo para: A
  [grupo-A] procesando: pedido-A1
nuevo grupo para: B
  [grupo-B] procesando: pedido-B1
nuevo grupo para: C
  [grupo-C] procesando: pedido-C1
  [grupo-A] procesando: pedido-A2
  [grupo-B] procesando: pedido-B2
```

> Cada tipo de pedido tiene su propio "carril" dedicado. No hay mezcla entre grupos.

---

### El patrón completo: múltiples productores + múltiples workers

```
                    ┌─── Productor A (hilo 1) ──┐
                    │                           │
                    ├─── Productor B (hilo 2) ──┼──► FluxSink ──► stream único
                    │                           │    (thread-safe)
                    └─── Productor C (hilo 3) ──┘
                                                          │
                                              .parallel(3).runOn(...)
                                                          │
                                          ┌───────────────┼───────────────┐
                                          ▼               ▼               ▼
                                      [worker-1]      [worker-2]      [worker-3]
                                    (procesa 1/3    (procesa 1/3    (procesa 1/3
                                     de los items)   de los items)   de los items)
```

```java
// Código completo del patrón
Flux.<String>create(sink -> {
    // múltiples productores — FluxSink serializa todo
    for (int p = 1; p <= 3; p++) {
        final int producerId = p;
        Thread.ofPlatform().start(() -> {
            for (int i = 1; i <= 10; i++) {
                sink.next("P" + producerId + "-item" + i);
                Util.sleepMillis(100);
            }
        });
    }
})
.parallel(3)                        // divide entre 3 workers
.runOn(Schedulers.boundedElastic())  // cada worker en su propio hilo
.subscribe(item ->
    log.info("[{}] ✔ {}", Thread.currentThread().getName(), item)
);
```

---

### Resumen: ¿cuándo usar cada patrón?

| Necesidad | Herramienta | Resultado |
|-----------|-------------|-----------|
| Todos los suscriptores ven todo | `share()` / Hot Publisher | Broadcast |
| Dividir trabajo entre workers | `parallel()` + `runOn()` | Cada item a un worker |
| Dividir por tipo/contenido | `groupBy()` | Cada grupo a su carril |
| Múltiples productores → un stream | `Flux.create()` + FluxSink | Fan-in thread-safe |
| Productores + workers en paralelo | `Flux.create()` + `parallel()` | Fan-in + Fan-out |
