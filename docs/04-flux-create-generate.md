# Sección 04 — Flux.create() y Flux.generate(): Emisión Programática

## Objetivo

Esta sección profundiza en las dos formas principales de crear un `Flux` con **lógica programática**: `Flux.create()` y `Flux.generate()`. Se exploran sus diferencias, cuándo usar cada uno, el concepto de thread safety del `FluxSink`, el respeto por la demanda del downstream (backpressure) y el operador `take`.

---

---

# `Flux.create()` — Modelo Push

## Para qué sirve

Su propósito es **envolver fuentes de datos existentes que ya empujan datos** de forma activa: listeners, callbacks, event buses, mensajes de red, botones de UI. La fuente produce datos a su propio ritmo, y `FluxSink` actúa como adaptador para inyectar esos datos en el pipeline reactivo.

> Pensalo como un **adaptador de enchufe**: el mundo externo (imperativo/callback) conecta con el mundo reactivo.

```java
// Caso real: listener de eventos externos → Flux
Flux.<String>create(sink -> {
    eventBus.addListener(event -> sink.next(event.payload())); // puede llamarse desde cualquier hilo
    eventBus.onClose(() -> sink.complete());
});
```

**API del `FluxSink`:**

| Método | Descripción |
|--------|-------------|
| `fluxSink.next(item)` | Emite un elemento al downstream |
| `fluxSink.complete()` | Finaliza el flujo correctamente |
| `fluxSink.error(t)` | Finaliza el flujo con error |
| `fluxSink.isCancelled()` | Verifica si el suscriptor canceló |
| `fluxSink.onRequest(n -> ...)` | Escucha cuántos elementos solicita el downstream (backpressure) |

---

## Lecciones

### Lec01 — `Flux.create()` básico

```java
Flux.create(fluxSink -> {
    String country;
    do {
        country = Util.faker().country().name();
        fluxSink.next(country);
    } while (!country.equalsIgnoreCase("canada"));
    fluxSink.complete();
}).subscribe(Util.subscriber());
```

`Flux.create()` permite emitir elementos de forma totalmente programática a través de un `FluxSink`. **El ciclo de generación se controla completamente por la lógica del developer.**

---

### Lec02 — Refactor de `Flux.create()`

Muestra cómo extraer la lógica del `FluxSink` a una clase separada para mantener el código limpio y reutilizable, implementando `Consumer<FluxSink<T>>`.

```java
public class NameGenerator implements Consumer<FluxSink<String>> {
    private FluxSink<String> sink;

    @Override
    public void accept(FluxSink<String> fluxSink) {
        this.sink = fluxSink;
    }

    public void generate() {
        this.sink.next(Util.faker().name().firstName());
    }
}

// Uso:
var generator = new NameGenerator();
Flux.create(generator).subscribe(...);
generator.generate(); // emite desde fuera del Flux
```

---

### Lec03 — Thread Safety del FluxSink

```java
// FluxSink es thread-safe: 10 hilos llaman a next() concurrentemente
Runnable runnable = () -> {
    for (int i = 0; i < 1000; i++) {
        generator.generate(); // internamente llama a fluxSink.next()
    }
};
for (int i = 0; i < 10; i++) {
    Thread.ofPlatform().start(runnable);
}
// Resultado: se reciben los 10.000 elementos sin pérdida ni corrupción
```

El `FluxSink` es **thread-safe**: múltiples hilos pueden llamar a `next()` concurrentemente sin causar condiciones de carrera. Esto se contrasta con `ArrayList`, que **no** es thread-safe y pierde elementos en acceso concurrente.

> ⚠️ Que sea thread-safe no significa que debas diseñar código concurrente innecesariamente. Es una característica para integración con APIs externas multi-hilo.

---

### Lec04 — Demanda del Downstream (Backpressure)

Por defecto, `Flux.create()` **no respeta la demanda** del suscriptor y emite todos los elementos de inmediato:

```java
// ❌ Modo "produce early" - emite todo sin esperar solicitudes
Flux.create(fluxSink -> {
    for (int i = 0; i < 10; i++) {
        fluxSink.next(name);
    }
    fluxSink.complete();
})
```

```java
// ✅ Modo "produce on demand" - respeta el backpressure
Flux.create(fluxSink -> {
    fluxSink.onRequest(request -> {
        for (int i = 0; i < request && !fluxSink.isCancelled(); i++) {
            fluxSink.next(generateElement());
        }
    });
})
```

Usando `fluxSink.onRequest(n -> ...)` se puede escuchar cuántos elementos solicita el suscriptor y producir exactamente esa cantidad.

| Modo | Respeta demanda | Recomendado para |
|------|----------------|-----------------|
| Produce early | ❌ | Pocos elementos conocidos |
| Produce on demand | ✅ | Generación costosa o flujos largos |

---

## ✅ Cuándo usar `Flux.create()`

| Situación | Por qué |
|-----------|---------|
| Integrás con una API basada en callbacks (`addListener`, `onMessage`, `onEvent`) | La fuente te empuja datos; no podés pedirlos uno por uno |
| Los datos vienen de múltiples hilos (WebSocket, cola de mensajes, eventos de UI) | `FluxSink` es thread-safe y serializa los emisores |
| Necesitás emitir **varios elementos** en respuesta a un único evento externo | `generate()` solo puede emitir uno por invocación |
| Estás haciendo un "bridge" entre código imperativo y reactivo | Es exactamente el patrón de diseño para el que fue creado |

```java
// ✅ Wrapping de un consumer de mensajes (RabbitMQ, Kafka, etc.)
Flux.<Message>create(sink -> {
    consumer.setMessageHandler(msg -> sink.next(msg));
    consumer.setErrorHandler(err -> sink.error(err));
    consumer.setCloseHandler(() -> sink.complete());
});
```

## ❌ Cuándo NO usar `Flux.create()`

- **Generás datos de forma local y algorítmica**: si solo hacés un `for` interno sin callbacks externos, `generate()` es más simple y seguro.
- **Necesitás backpressure automático sin esfuerzo**: `create()` requiere que implementes `onRequest()` manualmente; si olvidás hacerlo, inundás al downstream.
- **Tu lógica es single-threaded y simple**: la potencia de `create()` (thread-safety, flexibilidad) agrega complejidad innecesaria.

---

## Por qué `FluxSink` es thread-safe — explicación técnica

`FluxSink` fue diseñado para que **fuentes externas** (que no podés controlar) puedan llamar a `next()` en cualquier momento y desde cualquier hilo. Para garantizar esto, Reactor implementa internamente un patrón conocido como **Serialized Sink**:

1. **Cola MPSC (Multi-Producer Single-Consumer):** Cuando varios hilos llaman a `sink.next()` simultáneamente, los elementos son encolados en una estructura concurrente de alta performance (basada en `MpscLinkedQueue`). Esta cola permite múltiples escritores (producers) seguros y un único lector (consumer).

2. **Contador WIP atómico (Work In Progress):** Cada llamada a `next()` intenta ganar una carrera CAS (*Compare-And-Swap*) sobre un contador atómico. El hilo que gana la carrera se convierte en el "drainer": consume la cola y emite los elementos al downstream de forma serializada. Los otros hilos simplemente encolan y retornan.

3. **Serialización de señales:** Las llamadas a `complete()` y `error()` también pasan por el mismo mecanismo, garantizando que lleguen al downstream solo después de que todos los `next()` previos ya fueron procesados.

```
Hilo A → sink.next("a") ─┐
Hilo B → sink.next("b") ─┼──► [MpscQueue] ──► drainer (CAS/WIP) ──► downstream
Hilo C → sink.next("c") ─┘                      (serializado)
```

> El costo de este diseño es **overhead de sincronización**: accesos atómicos y posible contención en alta concurrencia.

---

---

# `Flux.generate()` — Modelo Pull

## Para qué sirve

Su propósito es **generar datos algorítmicamente, uno por uno, solo cuando el downstream los necesita**. La fuente no tiene datos pre-existentes: los fabrica en el momento en que se los piden. Es el mecanismo nativo de backpressure de Reactor.

> Pensalo como un **grifo de agua**: el agua solo sale cuando alguien lo abre (cuando hay demanda).

```java
// Caso real: leer un archivo línea por línea sin cargar todo en memoria
Flux.generate(
    () -> new BufferedReader(new FileReader("archivo.txt")),
    (reader, sink) -> {
        String line = reader.readLine();
        if (line != null) sink.next(line);
        else              sink.complete();
        return reader;
    },
    reader -> reader.close() // cleanup al terminar
);
```

**API del `SynchronousSink`:**

| Método | Descripción |
|--------|-------------|
| `sink.next(item)` | Emite exactamente **un** elemento (solo se puede llamar una vez por invocación) |
| `sink.complete()` | Finaliza el flujo |
| `sink.error(t)` | Finaliza el flujo con error |

> ⚠️ Solo podés llamar a `next()` **una vez** por invocación del lambda. Si llamás dos veces, obtenés una `IllegalStateException`.

---

## Lecciones

### Lec05 — Operador `take()` *(companion frecuente de `generate()`)*

`take` y sus variantes son la forma habitual de limitar un `Flux.generate()` infinito, cancelando la suscripción automáticamente cuando se cumple la condición:

```java
// Toma exactamente 3 elementos
Flux.range(1, 10).take(3).subscribe(...);

// Toma mientras i < 5 (NO incluye el 5)
Flux.range(1, 10).takeWhile(i -> i < 5).subscribe(...);

// Toma hasta que i < 5 (SÍ incluye el primer elemento que lo cumple)
Flux.range(1, 10).takeUntil(i -> i < 5).subscribe(...);
```

| Operador | Condición | Incluye último |
|----------|-----------|----------------|
| `take(n)` | N elementos fijos | — |
| `takeWhile(pred)` | Mientras predicate sea true | ❌ |
| `takeUntil(pred)` | Hasta que predicate sea true | ✅ |

**Ventaja sobre `filter()`:** cancela la generación de elementos restantes (más eficiente con flujos infinitos o grandes).

---

### Lec06 — `Flux.generate()` básico

```java
Flux.generate(synchronousSink -> {
    log.info("invoked");
    synchronousSink.next(1);
    // synchronousSink.complete(); // para terminar
}).take(4).subscribe(Util.subscriber());
```

- Emite **exactamente UN elemento** por invocación del lambda.
- El lambda se invoca **repetidamente** según la demanda del downstream.
- **Respeta backpressure automáticamente** (a diferencia de `Flux.create()`).
- Ejecuta en un **solo hilo** (no thread-safe por diseño).

---

### Lec07 — `Flux.generate()` hasta condición

```java
// Opción A: condición de terminación dentro del generate()
Flux.generate(synchronousSink -> {
    var country = Util.faker().country().name();
    synchronousSink.next(country);
    if (country.equalsIgnoreCase("canada")) {
        synchronousSink.complete();
    }
}).subscribe(Util.subscriber());

// Opción B (preferida): separar generación de condición de terminación
Flux.<String>generate(synchronousSink -> {
    synchronousSink.next(Util.faker().country().name());
})
.takeUntil(c -> c.equalsIgnoreCase("canada"))
.subscribe(Util.subscriber());
```

La variante con estado permite mantener el valor entre invocaciones:

```java
Flux.generate(
    () -> 1,                           // estado inicial
    (state, sink) -> {
        sink.next(state);
        if (state >= 10) sink.complete();
        return state + 1;              // nuevo estado
    }
)
```

> **Preferí la Opción B**: separar la generación de la condición de terminación es más flexible y reutilizable (principio de responsabilidad única).

---

### Lec08 — `Flux.generate()` con Estado y Limpieza

```java
Flux.generate(
    () -> 1,
    (state, sink) -> {
        sink.next(state);
        return state + 1;
    },
    state -> log.info("state {} cleaned", state) // se ejecuta al completar o cancelar
)
```

Tercera variante que incluye una función de limpieza para liberar recursos (conexiones, archivos, etc.) cuando el flujo termina por cualquier motivo.

---

## ✅ Cuándo usar `Flux.generate()`

| Situación | Por qué |
|-----------|---------|
| Generás datos matemáticos o algorítmicos (Fibonacci, números primos, paginación) | Secuencial, con estado, produce exactamente lo que se pide |
| Leés de una fuente secuencial (archivo, cursor de BD, iterador) | Un elemento por demanda = sin desperdiciar memoria |
| Querés backpressure automático sin escribir código extra | El framework gestiona todo |
| El elemento siguiente depende del anterior (estado mutable) | La variante con estado es nativa |

```java
// ✅ Paginación lazy de una API REST
Flux.generate(
    () -> 1,
    (page, sink) -> {
        List<User> users = api.fetchPage(page);
        users.forEach(sink::next);
        if (users.isEmpty()) sink.complete();
        return page + 1;
    }
);
```

## ❌ Cuándo NO usar `Flux.generate()`

- **Tu fuente de datos te empuja eventos** (callbacks, listeners): `generate()` es pull-only; no podés registrar un listener dentro de él y esperar que el framework lo invoque al recibir un evento externo.
- **Necesitás emitir múltiples elementos por cada ciclo de demanda**: lanzará `IllegalStateException` si llamás a `next()` más de una vez por invocación.
- **Tenés múltiples productores en hilos distintos**: `SynchronousSink` no es thread-safe y corrompería el estado si fuera accedido concurrentemente.

---

## Por qué `SynchronousSink` NO es thread-safe — explicación técnica

### Primero: ¿qué significa realmente "no es thread-safe"?

> **"No es thread-safe"** no significa que `Flux.generate()` tenga un problema o un bug. Significa que **intencionalmente no tiene ningún mecanismo de protección contra acceso concurrente**, porque Reactor garantiza que jamás va a necesitarlo.

Es una **decisión de diseño por performance**. Reactor hace un contrato: *"yo, el framework, garantizo que nunca invoco al `SynchronousSink` desde más de un hilo al mismo tiempo. Por lo tanto, no tiene sentido gastar CPU en locks, CAS o colas."*

Comparado con `FluxSink`, que tiene toda esa maquinaria porque el código externo puede llamarlo desde cualquier hilo, `SynchronousSink` es deliberadamente más liviano.

La etiqueta "no thread-safe" es una **advertencia para el developer**: *"no intentes usarlo desde múltiples hilos, porque no tiene protección — no porque esté roto, sino porque no fue diseñado para eso."* Si lo hicieras, violarías el contrato y el comportamiento sería indefinido.

---

### ¿Por qué Reactor puede garantizar el single-thread?

`SynchronousSink` opera bajo el modelo **Pull**: el downstream es quien pide datos, y Reactor orquesta todo el ciclo:

1. **Protocolo request → respond:** Cada vez que el downstream solicita `n` elementos, el framework llama al lambda de `generate()` exactamente `n` veces, de forma secuencial y en el mismo hilo. No hay paralelismo.

2. **El developer no tiene acceso al `SynchronousSink` fuera del lambda:** A diferencia de `FluxSink` (que puede guardarse en una variable y llamarse desde cualquier hilo), el `SynchronousSink` vive solo dentro de la invocación del lambda. No hay forma de escapar esa referencia de forma legítima.

3. **Sin cola, sin CAS, sin locks:** No necesita ningún mecanismo de sincronización porque el framework controla cuándo y cómo se invoca el lambda. La ausencia de overhead lo hace más eficiente por elemento que `FluxSink`.

```
downstream.request(3)
    ↓
framework llama generate() → emite elemento 1   ← mismo hilo
framework llama generate() → emite elemento 2   ← mismo hilo
framework llama generate() → emite elemento 3   ← mismo hilo
```

> Si intentás usar `SynchronousSink` desde múltiples hilos, violás el contrato de la especificación Reactive Streams (que exige señales `onNext` estrictamente secuenciales) y obtendrás comportamiento indefinido.

---

## Ejemplos: `Flux.generate()` mal usado y bien usado en contexto multi-hilo

### ❌ Mal uso 1 — Escapar el `SynchronousSink` para usarlo desde otro hilo

El `SynchronousSink` **solo es válido dentro de la invocación del lambda**. Una vez que el lambda retorna, Reactor marca ese "slot" como consumido. Guardarlo y llamarlo desde afuera viola el contrato:

```java
// ❌ INCORRECTO: guardar la referencia del SynchronousSink y usarlo desde otro hilo
AtomicReference<SynchronousSink<String>> sinkRef = new AtomicReference<>();

Flux.<String>generate(sink -> {
        sinkRef.set(sink);             // guardamos la referencia...
        sink.next("valor-del-lambda"); // Reactor espera que emitamos aquí y retornemos
    })
    .take(3)
    .subscribe(item -> log.info("received: {}", item));

// Desde otro hilo, intentamos emitir directamente al sink ya "consumido"
Thread.ofPlatform().start(() -> {
    Util.sleepMillis(50); // simulamos que llegamos tarde
    // ❌ El sink ya cerró su slot actual. Opciones posibles:
    //    - IllegalStateException: "SynchronousSink already terminated"
    //    - Se ignora silenciosamente
    //    - Corrupción de estado si coincide con una nueva invocación del lambda
    sinkRef.get().next("desde otro hilo"); // COMPORTAMIENTO INDEFINIDO
});

// El problema de fondo: generate() no espera eventos externos.
// Fue invocado por Reactor, emitió su elemento y terminó. No puede
// recibir datos desde afuera; no tiene ese mecanismo.
```

---

### ❌ Mal uso 2 — Usar `generate()` como bridge de múltiples productores

El error conceptual más común: intentar usar `generate()` para recolectar datos que vienen de varios hilos externos. `generate()` es pull-only y síncrono — no tiene forma de **esperar** datos asíncronos:

```java
// ❌ INCORRECTO: usar generate() para puente de múltiples productores externos
// Escenario: 3 hilos producen eventos; queremos emitirlos como un Flux

ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

// 3 hilos productores llenan la cola de forma asincrónica
for (int i = 0; i < 3; i++) {
    final int id = i;
    Thread.ofPlatform().start(() -> {
        for (int j = 0; j < 5; j++) {
            queue.offer("producer-" + id + ":item-" + j);
            Util.sleepMillis(100); // simula trabajo real
        }
    });
}

// Intentamos consumir con generate()
Flux.<String>generate(sink -> {
    String item = queue.poll();
    if (item != null) {
        sink.next(item);
    } else {
        // ❌ DILEMA sin solución limpia con generate():
        //
        // Opción A — sink.complete():
        //   Termina el Flux antes de que los productores terminen.
        //   Si los 3 hilos aún no pusieron todos sus items, los perdemos.
        //
        // Opción B — Thread.sleep() / busy-wait:
        //   Bloqueamos el hilo del framework esperando datos nuevos.
        //   Esto viola el modelo reactivo y puede causar deadlocks.
        //
        // No hay Opción C: generate() no tiene forma de suspenderse
        // y reanudarse cuando lleguen datos externos.
        sink.complete(); // termina prematuramente → ❌ perdemos items
    }
})
.subscribe(item -> log.info("received: {}", item));

// Resultado probable: 0–3 items recibidos (depende del timing de los hilos)
// Los productores aún están escribiendo cuando generate() ya terminó.
```

**¿Por qué falla?** `generate()` invoca el lambda de forma síncrona. Si la cola está vacía, no puede pausar y esperar; tiene que tomar una decisión ahora. Es el equivalente de pedirle a alguien que llene un formulario en tiempo real mientras la información todavía no llegó.

---

### ✅ Solución correcta — `Flux.create()` para el mismo escenario

`Flux.create()` y `FluxSink` fueron diseñados exactamente para este caso: múltiples fuentes externas que empujan datos de forma asincrónica:

```java
// ✅ CORRECTO: usar create() con FluxSink para múltiples productores
// FluxSink serializa internamente todas las llamadas a next() con MPSC + CAS

CountDownLatch allDone = new CountDownLatch(3); // esperar a los 3 productores

Flux.<String>create(sink -> {
    // 3 productores emiten directamente al FluxSink desde sus propios hilos
    for (int i = 0; i < 3; i++) {
        final int id = i;
        Thread.ofPlatform().start(() -> {
            for (int j = 0; j < 5; j++) {
                // ✅ thread-safe: FluxSink serializa los accesos concurrentes
                sink.next("producer-" + id + ":item-" + j);
                Util.sleepMillis(100);
            }
            allDone.countDown(); // este productor terminó
        });
    }

    // Un hilo monitor completa el Flux cuando todos los productores terminaron
    Thread.ofPlatform().start(() -> {
        try {
            allDone.await();       // espera a los 3 productores
            sink.complete();       // ✅ completo solo cuando todos terminaron
        } catch (InterruptedException e) {
            sink.error(e);
        }
    });
})
.subscribe(item -> log.info("[{}] received: {}", Thread.currentThread().getName(), item));

// Resultado: los 15 items (3 × 5) se reciben sin pérdida ni corrupción.
// FluxSink encola y serializa las llamadas concurrentes de los 3 hilos.
```

---

### Resumen visual de los tres casos

```
❌ Mal uso 1 — SynchronousSink escapado
─────────────────────────────────────────────────────────────
Reactor         SynchronousSink      Hilo externo
   │──invoca lambda──►│                   │
   │◄──sink.next()───│                   │
   │    lambda retorna│                   │
   │    [slot cerrado]│                   │
   │                  │◄──next() externo──│  ← INVÁLIDO
   │                  │  (slot ya cerrado o en medio de otra invocación)

❌ Mal uso 2 — generate() esperando datos asincrónicos
─────────────────────────────────────────────────────────────
  queue vacía al inicio
  generate() → poll() → null → complete() ← termina ya
  [los productores siguen escribiendo... pero nadie los escucha]

✅ Correcto — create() con múltiples productores
─────────────────────────────────────────────────────────────
Productor A ──sink.next()─┐
Productor B ──sink.next()─┼──► [MpscQueue] ──► drainer ──► subscriber
Productor C ──sink.next()─┘         ↑
Monitor ────sink.complete()─────────┘  (cuando todos terminaron)
```

---

---

# Comparación final: `Flux.create()` vs `Flux.generate()`

| Aspecto | `Flux.create()` | `Flux.generate()` |
|---------|----------------|-------------------|
| Modelo de producción | **Push** (fuente empuja datos) | **Pull** (downstream pide datos) |
| Elementos por invocación | Múltiples | Exactamente 1 |
| Backpressure automático | ❌ (manual con `onRequest`) | ✅ |
| Thread-safety | ✅ (`FluxSink` serializa accesos con MPSC + CAS) | ❌ no necesita: Reactor garantiza single-thread |
| Estado entre emisiones | Manual | Nativo (variante con estado) |
| Complejidad | Mayor | Menor |
| Casos de uso | APIs con callbacks, eventos externos, multi-hilo | Secuencias generadas, con estado, CPU-bound |

## Resumen técnico de thread-safety

| Pregunta | `FluxSink` (create) | `SynchronousSink` (generate) |
|----------|---------------------|------------------------------|
| ¿Quién controla cuándo se produce? | La fuente externa | El framework / downstream |
| ¿Puede haber múltiples productores? | Sí → necesita serialización | No → single invocation por diseño |
| Mecanismo interno | Cola MPSC + CAS atómico (WIP drain loop) | Sin mecanismo — no lo necesita |
| Overhead | Mayor (sincronización) | Mínimo (sin locks, sin colas) |
| ¿Por qué no necesita protección? | No aplica | Reactor garantiza que solo un hilo invoca el lambda |
| Si se usa mal desde múltiples hilos | Manejado internamente (encola) | `IllegalStateException` / comportamiento indefinido |

---

---

# Concepto clave: ¿Qué es el Downstream?

A lo largo de este documento se menciona mucho la palabra **downstream**. Acá está la explicación completa.

## La metáfora del río

Imaginá un río que fluye de las montañas hacia el mar:

```
  [Nacimiento]  ──agua──►  [Pueblo A]  ──agua──►  [Pueblo B]  ──agua──►  [Mar]
   (fuente)                (filtro)               (transformación)       (destino)
```

- **Upstream** (aguas arriba) = hacia donde el agua *viene*
- **Downstream** (aguas abajo) = hacia donde el agua *va*

En programación reactiva es exactamente lo mismo, pero con datos en lugar de agua:

```
  [Publisher]  ──datos──►  [Operador 1]  ──datos──►  [Operador 2]  ──datos──►  [Subscriber]
   upstream                                                                      downstream
```

## Definición formal

> **Downstream** es todo componente que está **más cerca del consumidor final (Subscriber)** en la cadena reactiva.
> **Upstream** es todo componente que está **más cerca de la fuente (Publisher)**.

Cada operador en una cadena es simultáneamente:
- **Downstream** respecto al operador anterior (recibe datos de él)
- **Upstream** respecto al operador siguiente (le envía datos a él)

## Ejemplo en código

```java
Flux.range(1, 100)          // ← Publisher (el más upstream de todos)
    .filter(n -> n % 2 == 0) // ← downstream de range(), upstream de map()
    .map(n -> "item-" + n)   // ← downstream de filter(), upstream de take()
    .take(5)                 // ← downstream de map(), upstream de subscribe()
    .subscribe(System.out::println); // ← Subscriber (el más downstream de todos)
```

Desde el punto de vista de `Flux.range()`:
- Todo lo que viene después (`filter`, `map`, `take`, `subscribe`) es su **downstream**.

Desde el punto de vista de `map()`:
- `filter` y `range` son su **upstream** (le proveen datos).
- `take` y `subscribe` son su **downstream** (reciben sus datos).

## Downstream demand (demanda del downstream)

Este es el uso más frecuente de la palabra en este archivo. La **demanda del downstream** es la cantidad de elementos que el Subscriber (o un operador intermedio) le *pide* al Publisher mediante `request(n)`.

```
  [Publisher]  ◄──"dame 3 elementos"──  [Subscriber]
                    (demanda)
```

```
  [Publisher]  ──"elemento 1"──►  [Subscriber]
  [Publisher]  ──"elemento 2"──►  [Subscriber]
  [Publisher]  ──"elemento 3"──►  [Subscriber]
                    (datos)
```

Los datos fluyen **downstream** (Publisher → Subscriber).
La demanda fluye **upstream** (Subscriber → Publisher).

Esto es la esencia del **backpressure**: el downstream controla la velocidad a la que el upstream produce datos.

```java
// "Respetar la demanda del downstream" significa:
// producir datos SOLO cuando el subscriber los solicita, y SOLO la cantidad pedida

Flux.<String>generate(sink -> {
    sink.next(generarDato()); // ✅ se ejecuta UNA VEZ por cada request() del downstream
})
// generate() respeta automáticamente la demanda: solo produce cuando se lo piden

Flux.<String>create(sink -> {
    sink.onRequest(n -> {     // ✅ escucha cuántos elementos pidió el downstream
        for (int i = 0; i < n; i++) {
            sink.next(generarDato()); // produce exactamente lo que pidieron
        }
    });
})
// create() requiere implementar onRequest() manualmente para respetar la demanda
```

## Resumen rápido

| Término | Significado | Ejemplo |
|---------|-------------|---------|
| **Upstream** | Más cerca del Publisher / fuente | `Flux.range()`, `Flux.create()` |
| **Downstream** | Más cerca del Subscriber / consumidor | `subscribe()`, último operador |
| **Demanda del downstream** | Cuántos elementos pide el consumidor con `request(n)` | `subscription.request(5)` |
| **Respetar la demanda** | El producer solo genera lo que se le pidió | `generate()` lo hace automático |
| **No respetar la demanda** | El producer genera todo sin esperar pedidos | `create()` sin `onRequest()` |
