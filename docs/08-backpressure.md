# Sección 08 — Backpressure: Contrapresión

## ¿Qué es el Backpressure?

### Definición formal

El término **backpressure** (contrapresión) proviene de la ingeniería de fluidos: es la resistencia que opone un sistema cuando no puede absorber el flujo que llega.

En programación reactiva, el concepto es equivalente: es el **mecanismo por el cual un subscriber comunica al publisher que no puede procesar datos tan rápido como éste los produce**, ejerciendo así una presión de vuelta (hacia atrás) en la dirección opuesta al flujo de datos.

La especificación **Reactive Streams** (que implementa Project Reactor) define backpressure como un principio de diseño fundamental: el subscriber siempre controla cuántos elementos recibe. Esto se logra a través del método `request(n)` de la interfaz `Subscription`:

```
Publisher  ──── datos ────►  Subscriber
Publisher  ◄── request(n) ─  Subscriber
```

El subscriber "tira" (pull) de los datos, en lugar de que el publisher los "empuje" (push) sin control. Mientras el subscriber no llame a `request(n)`, el publisher no debe emitir datos.

### ¿Por qué existe el problema?

En condiciones normales el modelo funciona perfectamente: el subscriber pide `n` items, el publisher produce exactamente `n`, el subscriber los consume y pide más. El ciclo se repite en equilibrio.

El problema aparece cuando:
- El subscriber pide todo de una vez (`request(Long.MAX_VALUE)`) sin poder procesarlo.
- El publisher y el subscriber corren en **hilos distintos** (múltiples schedulers).
- El publisher produce mucho más rápido de lo que el subscriber puede consumir.

En esas condiciones, la señal de control `request(n)` deja de ser efectiva para frenar al producer, y el flujo de datos se descontrola.

### El desequilibrio: producer rápido, consumer lento

Imagina una pipeline reactiva con múltiples schedulers:

```
Producer                  Operadores              Subscriber
(subscribeOn parallel)  →  publishOn parallel  →  (publishOn boundedElastic)
Velocidad: 1000 items/s                           Velocidad: 1 item/s
```

En 10 segundos: el producer habrá generado **10.000 items**, pero el subscriber sólo habrá procesado **10**. Los 9.990 restantes tienen que ir *a algún lado*.

Si el producer sigue generando sin control:
- Los items se acumulan en memoria.
- Puede producirse `OutOfMemoryError`.
- El sistema puede colapsar con comportamiento impredecible.

**En síntesis**: backpressure es la presión que ejerce el producer sobre el subscriber cuando produce más rápido de lo que el subscriber puede consumir. Es un problema de **desequilibrio de velocidades** entre productor y consumidor en un pipeline asíncrono.

> ✅ Si el subscriber es rápido y el producer lento → no hay problema.  
> ⚠️ Si el producer es rápido y el subscriber lento → hay backpressure.

---

## Lecciones

### Lec01 — Manejo Automático de Backpressure (`Flux.generate`)

```java
System.setProperty("reactor.bufferSize.small", "16"); // configura el buffer interno (mínimo 16)

var producer = Flux.generate(() -> 1, (state, sink) -> {
                    log.info("generating {}", state);
                    sink.next(state);
                    return state + 1;
                })
                .subscribeOn(Schedulers.parallel());

producer
    .publishOn(Schedulers.boundedElastic())
    .map(Lec01BackPressureHandling::timeConsumingTask) // tarea lenta: 1 segundo por item
    .subscribe(Util.subscriber());
```

#### ¿Cómo funciona la gestión automática?

Cuando el producer (`Flux.generate`) y el consumer corren en **hilos distintos**, Reactor gestiona el backpressure automáticamente a través de una **cola interna**.

```
Producer (parallel)  →  [ COLA INTERNA ]  →  Consumer (boundedElastic)
genera a máx vel.         (buffer)           procesa 1 item/segundo
```

El mecanismo es el siguiente:

1. El producer genera items y los coloca en la cola interna.
2. Cuando la cola se **llena** (por defecto 256 items, o 16 si se configuró `reactor.bufferSize.small`), el producer **se detiene automáticamente**.
3. El consumer sigue consumiendo items de la cola lentamente.
4. Cuando la cola queda al **75% vacía**, el producer **se reactiva automáticamente** y produce nuevos items hasta llenar la cola de nuevo.
5. Este ciclo se repite indefinidamente.

#### Diagrama del ciclo automático

```
Producer genera items...
│
▼
Cola [████████████████] LLENA (16 items) → Producer se DETIENE
│
Consumer consume lentamente (1 item/s)...
│
Cola [████░░░░░░░░░░░░] 75% vacía (12 items consumidos) → Producer se REACTIVA
│
Producer genera 12 items más...
│
Cola [████████████████] LLENA de nuevo → Producer se DETIENE
│
...y así sucesivamente
```

#### Configuración del buffer

```java
// El tamaño por defecto es 256. Puedes reducirlo (mínimo 16):
System.setProperty("reactor.bufferSize.small", "16");
```

> ⚠️ El valor mínimo es 16. No se puede reducir por debajo de ese número.
> Esta propiedad debe setearse **antes** de que Reactor inicialice sus colas.

#### ¿Por qué `Flux.generate` funciona tan bien aquí?

Porque `Flux.generate` está diseñado para ser **pull-based**: Reactor controla cuándo invoca al generador. Cuando la cola está llena, simplemente no lo llama. Cuando la cola baja al 75%, lo vuelve a invocar. El developer no necesita hacer nada.

---

### Lec02 — `limitRate()`

```java
producer
    .limitRate(10) // solicita de a 10 items por vez
    .publishOn(Schedulers.boundedElastic())
    .map(Lec01BackPressureHandling::timeConsumingTask)
    .subscribe(Util.subscriber());
```

`limitRate(n)` es una forma explícita de controlar la velocidad de solicitud al producer:

- Solicita `n` items al producer en el primer lote.
- Cuando el consumer procesa el **75%** de esos items, Reactor solicita automáticamente más (replenishment adaptativo).
- Así, el producer nunca produce más de `n` items sin que el consumer haya progresado.

```java
// Forma avanzada: controlar el lote inicial y el umbral de reposición
.limitRate(highTide, lowTide)
// highTide: cantidad inicial solicitada
// lowTide: cantidad consumida que dispara la próxima solicitud
```

#### ¿Cuándo usar `limitRate`?

Cuando querés comunicarle explícitamente al producer a qué ritmo puede generar datos, ajustado a la capacidad del consumer.

> **Nota importante:** `limitRate` funciona bien con `Flux.generate`, pero con `Flux.create` puede empeorar el problema (ver Lec04), porque `Flux.create` no respeta la demanda del subscriber.

---

### Lec03 — Múltiples Suscriptores

```java
// Subscriber lento (con limitRate)
producer
    .limitRate(5)
    .publishOn(Schedulers.boundedElastic())
    .map(Lec01BackPressureHandling::timeConsumingTask)
    .subscribe(Util.subscriber("sub-lento"));

// Subscriber rápido (sin limitRate, toma solo 100)
producer
    .take(100)
    .subscribeOn(Schedulers.parallel())
    .subscribe(Util.subscriber("sub-rapido"));
```

Con un **Cold Publisher** y `subscribeOn()`, cada subscriber recibe su **propio pipeline independiente**:

- El producer ajusta la velocidad de producción **según cada subscriber por separado**.
- El backpressure se gestiona de forma **independiente** para cada suscripción.
- El subscriber rápido no afecta al lento, y viceversa.

```
Producer (Cold) ──┬── Pipeline sub-lento (boundedElastic, limitRate(5))
                  └── Pipeline sub-rapido (parallel, take(100))
```

Cada pipeline tiene su propia cola interna y su propia negociación de backpressure.

---

### Lec04 — `Flux.create()` con Backpressure

```java
var producer = Flux.create(sink -> {
    for (int i = 1; i <= 500; i++) {
        if (!sink.isCancelled()) {
            log.info("generating {}", i);
            Util.sleep(Duration.ofMillis(50)); // 20 items/segundo
            sink.next(i);
        }
    }
    sink.complete();
});
```

#### El problema: `Flux.create` no respeta la demanda

A diferencia de `Flux.generate`, **`Flux.create` es push-based**: el producer controla el loop, no Reactor. El lambda del `create` se ejecuta completo sin importar cuántos items solicitó el subscriber.

Cuando hay dos schedulers distintos:

```
Producer (parallel)      →  [ COLA INTERNA ]  →  Consumer (boundedElastic)
genera 20 items/segundo      (buffer limitado)    procesa 1 item/segundo
(no respeta demanda)
```

Lo que ocurre:
1. El producer genera los primeros 16 items (el tamaño del buffer).
2. La cola se llena.
3. El producer **no se detiene** — sigue generando, pero los items se acumulan en una cola interna separada que Reactor gestiona internamente.
4. El consumer sólo ve los primeros 16 items, luego espera al producer para completar los 500.
5. Durante ese tiempo, los items se acumulan en memoria.

> ⚠️ Con `Flux.create`, si el producer genera objetos grandes, esto puede causar `OutOfMemoryError`.

#### ¿Por qué `limitRate` empeora el problema aquí?

```java
producer
    .limitRate(1) // ← ¡esto empeora las cosas con Flux.create!
    .publishOn(Schedulers.boundedElastic())
    ...
```

Con `limitRate(1)`, la cola interna pasa a tener tamaño 1. Ahora el producer tiene que completar **todos los 500 items** antes de que el subscriber pueda recibir el segundo item, porque `Flux.create` no detiene su loop. El resultado es que el consumer recibe mucho menos de lo esperado hasta que el producer termina todo su ciclo.

**Conclusión:** con `Flux.create`, no hay manejo automático de backpressure. Necesitamos estrategias explícitas.

---

### Lec05 — Estrategias de Backpressure

Cuando el producer usa `Flux.create()` y el consumer es lento, Reactor ofrece **cuatro estrategias** para manejar el overflow. Cada una se coloca como operador entre el producer y el consumer, justo antes de `publishOn`:

```java
producer
    // ── Elige UNA de las siguientes estrategias ──
    .onBackpressureBuffer()          // Buffer ilimitado
    .onBackpressureBuffer(10)        // Buffer limitado de 10 elementos
    .onBackpressureError()           // Lanza error si el buffer se llena
    .onBackpressureDrop()            // Descarta elementos no solicitados
    .onBackpressureLatest()          // Mantiene solo el último elemento disponible
    // ──────────────────────────────────────────────
    .limitRate(1)
    .publishOn(Schedulers.boundedElastic())
    .map(this::timeConsumingTask)
    .subscribe(Util.subscriber());
```

---

#### 1️⃣ Estrategia Buffer (`onBackpressureBuffer`)

```java
.onBackpressureBuffer()   // ilimitado
.onBackpressureBuffer(10) // limitado a N elementos; error si se supera
```

**Cómo funciona:**

El operador actúa como un buffer ilimitado (o limitado) entre el producer y el subscriber:

```
Producer (rápido) → [ BUFFER ILIMITADO ] → Consumer (lento)
  genera items          acumula todo         consume uno a uno
```

- El producer deposita todos sus items en el buffer.
- El consumer toma items del buffer a su propio ritmo.
- El consumer siempre tiene items para procesar, sin importar la diferencia de velocidad.

**¿Cuándo usarla?**
- Cuando el producer tiene **picos ocasionales** de producción (por ejemplo, más tráfico a mediodía que a medianoche).
- Cuando asumís que el consumer **eventualmente se pondrá al día** con la velocidad media de producción.
- Cuando **no podés perder datos** y tenés memoria suficiente.

> ⚠️ Con `onBackpressureBuffer()` sin límite, si el producer es *siempre* más rápido que el consumer, eventualmente causará `OutOfMemoryError`.

---

#### 2️⃣ Estrategia Error (`onBackpressureError`)

```java
.onBackpressureError()
```

**Cómo funciona:**

El operador monitorea si el producer está generando más items de los que solicita el downstream. Si detecta overflow:

1. Emite una señal de **error** (`OverflowException`) al downstream.
2. Envía una señal de **cancelación** al upstream para que deje de producir.

```
Producer (rápido) → [ MONITOR ] → Consumer (lento)
  genera demasiado    detecta     recibe OverflowException
                      overflow    
```

**¿Cuándo usarla?**
- Cuando el overflow debe ser tratado como un **error explícito**, no ignorado ni descartado.
- Cuando querés detectar rápidamente que el sistema está desequilibrado.
- Útil en sistemas donde el overflow es una condición excepcional que debe manejarse en el código de error.

---

#### 3️⃣ Estrategia Drop (`onBackpressureDrop`)

```java
.onBackpressureDrop()
.onBackpressureDrop(dropped -> log.warn("descartado: {}", dropped)) // con callback
```

**Cómo funciona:**

El operador equilibra producer y consumer **descartando los items que el consumer no solicitó**:

```
Consumer solicita 2 items.
Producer genera 20 items.
→ onBackpressureDrop entrega los 2 solicitados y DESCARTA los 18 restantes.
```

- Cuando llega una solicitud del downstream, espera al próximo item que produzca el producer y se lo pasa.
- Todos los items producidos sin solicitud pendiente son **descartados silenciosamente**.
- El consumer no se satura, pero **puede perder datos**.

**Diferencia respecto al buffer:**
- `buffer` acumula todo en memoria.
- `drop` descarta lo que no se solicitó; el consumer recibe elementos "salteados" (1, 2, 20, 39...).

**¿Cuándo usarla?**
- Cuando es aceptable **perder datos intermedios** y sólo te importa procesar lo que el consumer puede manejar.
- Por ejemplo: telemetría, métricas de monitoreo, eventos de UI donde los intermedios no importan.

---

#### 4️⃣ Estrategia Latest (`onBackpressureLatest`)

```java
.onBackpressureLatest()
```

**Cómo funciona:**

Similar a `drop`, pero con una diferencia clave: **conserva el último item producido** en un slot de memoria:

```
Producer genera: 1, 2, 3, 4, 5, 6, ..., 19, 20
Consumer solicitó 2 items → recibe 1 y 2.
Mientras procesa 1 y 2:
  - Producer genera 3, 4, 5...19: cada nuevo item reemplaza al anterior en el slot.
  - Slot guarda siempre el más reciente.
  - Cuando hay nueva solicitud → entrega el 19 (el último guardado).
```

```
Sin solicitud pendiente:
  Producer:  3 → 4 → 5 → ... → 19
  Slot:      [3] → [4] → [5] → ... → [19]  ← siempre el más reciente

Con nueva solicitud:
  → Entrega el 19 al consumer
```

**Diferencia respecto a drop:**
| | `onBackpressureDrop` | `onBackpressureLatest` |
|---|---|---|
| Sin solicitud pendiente | Descarta todo | Descarta todos, guarda el último |
| Con nueva solicitud | Espera el próximo item producido | Entrega el último guardado inmediatamente |
| ¿Puede retener algún item? | No | Sí, exactamente 1 |

**¿Cuándo usarla?**
- Cuando el consumer siempre quiere recibir el **dato más reciente** disponible al estar listo.
- Por ejemplo: precio actual de una acción, temperatura de un sensor, posición GPS.
- El dato "viejo" no tiene valor, siempre querés el más fresco.

---

### Lec06 — Estrategia de overflow en `Flux.create()`

`Flux.create()` permite definir la estrategia de overflow **directamente en el momento de crear el producer**, como alternativa a usar los operadores `onBackpressure*`:

```java
Flux.create(sink -> {
    for (int i = 1; i <= 500; i++) {
        sink.next(i);
    }
    sink.complete();
}, OverflowStrategy.DROP);  // ← estrategia definida en el create
```

Las estrategias disponibles son:
| Estrategia | Equivalente operador |
|---|---|
| `OverflowStrategy.BUFFER` | `onBackpressureBuffer()` |
| `OverflowStrategy.ERROR` | `onBackpressureError()` |
| `OverflowStrategy.DROP` | `onBackpressureDrop()` |
| `OverflowStrategy.LATEST` | `onBackpressureLatest()` |
| `OverflowStrategy.IGNORE` | Sin manejo (ignora el overflow) |

#### ¿Cuándo usar la estrategia en `Flux.create` vs. como operador?

| Enfoque | Cuándo usarlo |
|---|---|
| **Estrategia en `Flux.create`** | Querés aplicar la misma estrategia para **todos** los subscribers del producer. |
| **Operador `onBackpressure*`** | Cada subscriber puede elegir **su propia estrategia** independientemente. |

```java
// Con operador: cada subscriber elige su estrategia
producer
    .onBackpressureLatest()         // sub1 usa LATEST
    .subscribe(Util.subscriber("sub1"));

producer
    .onBackpressureBuffer()         // sub2 usa BUFFER
    .subscribe(Util.subscriber("sub2"));
```

---

## Diagrama general del problema

```
Sin múltiples schedulers (todo en el mismo hilo):
  Producer → Operadores → Consumer      ← sin backpressure, todo sincrónico

Con múltiples schedulers:
  Producer (parallel) → [ ??? ] → Consumer (boundedElastic)
  1000 items/s                      1 item/s

¿Qué pasa en [ ??? ]?
  ├── Flux.generate  → cola interna, se para al 100% y retoma al 75% ✅
  └── Flux.create    → acumula en memoria, sin pausa automática ⚠️
```

---

## Tabla comparativa de estrategias

| Estrategia | ¿Pierde datos? | ¿Lanza error? | Buffer en memoria | Ideal para |
|---|---|---|---|---|
| `onBackpressureBuffer()` | ❌ No | ❌ No | Ilimitado | Picos ocasionales, consumer se pone al día |
| `onBackpressureBuffer(n)` | ❌ No (hasta N) | ✅ Sí (al superar N) | Limitado a N | Control de memoria con error explícito |
| `onBackpressureError()` | ❌ No | ✅ Sí (inmediato) | Ninguno | Overflow como condición de error |
| `onBackpressureDrop()` | ✅ Sí | ❌ No | Ninguno | Telemetría, métricas, datos intercambiables |
| `onBackpressureLatest()` | ✅ Sí (guarda 1) | ❌ No | 1 slot | Último valor relevante (precios, sensores) |

---

## Resumen

El backpressure es fundamental en pipelines reactivas con **múltiples schedulers**:

1. **Sin múltiples schedulers** (todo en el hilo actual): no hay backpressure, todo es sincrónico.

2. **Con `Flux.generate` y múltiples schedulers**: Reactor gestiona el backpressure **automáticamente** mediante una cola interna. El producer se detiene cuando la cola está llena y se reactiva cuando baja al 75%.

3. **Con `Flux.create` y múltiples schedulers**: **no hay manejo automático**. El producer sigue generando independientemente de la demanda. Hay que aplicar una estrategia explícita.

4. **`limitRate(n)`** funciona bien con `Flux.generate` para ajustar el ritmo, pero con `Flux.create` puede empeorar el problema.

5. **Las cuatro estrategias** permiten elegir el trade-off según el caso de uso:
   - ¿No puedo perder datos? → `buffer`
   - ¿El overflow es un error? → `error`
   - ¿Los datos intermedios no importan? → `drop`
   - ¿Solo necesito el dato más reciente? → `latest`

6. **`parallel()` con muchos rails no resuelve backpressure**: es caro en memoria y CPU, no frena al producer, y está diseñado para trabajo CPU-bound, no para compensar desequilibrios de velocidad.

---

## ¿Por qué no simplemente usar `parallel()` con muchos rails?

Una pregunta natural es: si el subscriber es lento y el producer es rápido, ¿por qué no crear 100 o 1000 rails con `parallel()` para que muchos hilos consuman en paralelo y se pongan al día?

```java
// ¿Por qué no hacer esto?
producer
    .parallel(1000)          // 1000 rails, 1000 hilos
    .runOn(Schedulers.boundedElastic())
    .map(this::timeConsumingTask)  // tarea lenta: 1 segundo
    .sequential()
    .subscribe(Util.subscriber());
```

La respuesta es que **`parallel()` no resuelve el problema de backpressure**, y en muchos casos lo empeora. Hay varias razones:

### 1. Los hilos son un recurso caro y limitado

Crear 100 o 1000 hilos de sistema operativo tiene un costo muy alto:

- **Memoria**: cada hilo de plataforma (OS thread) consume entre 512 KB y 1 MB de stack por defecto. 1000 hilos = hasta **1 GB de memoria** solo en stacks.
- **Context switching**: el sistema operativo debe alternar entre miles de hilos. Con 1000 hilos compitiendo por pocos núcleos de CPU, el costo de cambio de contexto puede superar al trabajo útil real.
- **Creación**: instanciar y destruir hilos tiene costo de tiempo y recursos del kernel.

```
1000 rails × 1 MB stack = ~1 GB solo en memoria de hilos
+ context switching overhead
= el sistema se degrada, no mejora
```

### 2. `parallel()` es para trabajo CPU-bound, no para compensar lentitud de I/O

`parallel()` + `runOn()` está diseñado para **distribuir trabajo intensivo en CPU** entre los núcleos disponibles. El número óptimo de rails es igual al número de núcleos de CPU (típicamente 4, 8, 16...).

Si la tarea lenta es una **operación bloqueante de I/O** (llamada a base de datos, HTTP, lectura de archivo), parallelizar no elimina el tiempo de espera — solo lo distribuye. 100 hilos bloqueados esperando I/O siguen siendo 100 hilos bloqueados.

```
Sin parallel: 1 hilo bloqueado 1 segundo por item
Con parallel(100): 100 hilos bloqueados 1 segundo por item
→ el throughput mejora, pero el problema de fondo no desaparece
   y el costo de recursos es 100x mayor
```

### 3. El producer sigue siendo más rápido que el conjunto de consumers

Si el producer genera 1000 items/segundo y cada consumer tarda 1 segundo por item:

- Con 1 consumer: procesa 1 item/segundo → backlog crece 999 items/segundo.
- Con 100 consumers: procesa 100 items/segundo → backlog crece 900 items/segundo.
- Con 1000 consumers: procesa 1000 items/segundo → equilibrio... pero con 1 GB de stacks y enorme context switching.

El backpressure persiste hasta que la cantidad de rails supera la velocidad del producer — lo cual es una solución costosa e inestable que colapsa si el producer acelera.

### 4. La solución correcta ataca la causa, no el síntoma

| Enfoque | ¿Qué hace? | Problema |
|---|---|---|
| `parallel(1000)` | Añade más consumers | Caro, no escala, no frena al producer |
| `onBackpressureBuffer` | Acepta el exceso en memoria | Riesgo de OOM si el desequilibrio es permanente |
| `onBackpressureDrop/Latest` | Descarta datos que no se pueden procesar | Pierde datos, pero protege al sistema |
| `onBackpressureError` | Cancela el stream al detectar overflow | Fuerza a revisar el diseño del sistema |
| **Rediseñar el producer** | Frena la producción según la demanda | ✅ Solución de raíz (ej: `Flux.generate`, paginación lazy) |

### 5. Cuándo sí tiene sentido `parallel()`

`parallel()` **sí es útil** cuando:
- La tarea es **CPU-bound** (transformaciones, cálculos, serialización).
- El número de rails es igual o cercano al número de núcleos de CPU.
- El producer y el consumer tienen velocidades similares, y sólo querés aprovechar múltiples núcleos.

```java
// ✅ Uso correcto: CPU-bound con N rails = N núcleos
Flux.range(1, 1000)
    .parallel(Runtime.getRuntime().availableProcessors()) // 4, 8, 16...
    .runOn(Schedulers.parallel())
    .map(item -> heavyCpuTransform(item))   // trabajo real de CPU
    .sequential()
    .subscribe();
```

**En resumen:** más rails no solucionan backpressure. La solución está en elegir la estrategia correcta según si podés perder datos, si el desequilibrio es ocasional o permanente, y si el problema está en el producer o en el consumer.

---

## ¿Y si usamos hilos virtuales? ¿Eso resuelve el problema?

Una pregunta muy válida: los **hilos virtuales** (Java 21+, Project Loom) son muchísimo más ligeros que los hilos de plataforma y están diseñados para I/O. ¿No eliminan el argumento del costo de los hilos?

### Hilos virtuales vs. hilos de plataforma

| | Hilo de plataforma (OS thread) | Hilo virtual (Project Loom) |
|---|---|---|
| Memoria por hilo | 512 KB – 1 MB (stack fijo) | Unos pocos KB (stack dinámico) |
| Administrado por | Sistema operativo | JVM |
| Bloqueado en I/O | Bloquea el OS thread completo | Se "desmonta" del carrier thread, libera el OS thread |
| Cantidad práctica | ~1.000 – 10.000 | Millones |

Con hilos virtuales, crear 1000 o incluso 100.000 rails ya no representa el mismo costo de memoria. Por eso, la objeción del punto 1 anterior se debilita considerablemente.

### ¿Se puede combinar `parallel()` + hilos virtuales?

**Sí**, y es un enfoque válido cuando la tarea es **I/O bloqueante** que no puede hacerse no-bloqueante:

```java
// Executor que crea un hilo virtual por tarea
Scheduler virtualThreadScheduler = Schedulers.fromExecutor(
    Executors.newVirtualThreadPerTaskExecutor()
);

producer
    .parallel(100)                      // 100 rails
    .runOn(virtualThreadScheduler)      // cada rail en un hilo virtual
    .map(this::blockingIoTask)          // operación bloqueante (DB, HTTP, archivo)
    .sequential()
    .subscribe(Util.subscriber());
```

O si ya tenés configurado `boundedElastic` con Virtual Threads (como vimos en Lec04 de la sección 07):

```java
System.setProperty("reactor.schedulers.defaultBoundedElasticOnVirtualThreads", "true");

producer
    .parallel(100)
    .runOn(Schedulers.boundedElastic())  // boundedElastic usa hilos virtuales internamente
    .map(this::blockingIoTask)
    .sequential()
    .subscribe(Util.subscriber());
```

### Entonces, ¿los hilos virtuales sí resuelven el backpressure?

**Parcialmente — pero no del todo.** Hay que separar dos problemas distintos:

| Problema | ¿Hilos virtuales ayudan? |
|---|---|
| **Costo de memoria por hilo** | ✅ Sí — KB en lugar de MB, podés tener miles sin problema |
| **Bloqueo de OS threads en I/O** | ✅ Sí — el hilo virtual se desmonta y libera el carrier thread |
| **Desequilibrio de velocidad producer/consumer** | ⚠️ Solo mitigan — no eliminan la causa raíz |

El backpressure sigue existiendo porque **los hilos virtuales reducen el costo de tener muchos consumidores, pero no frenan al producer**. Si el producer genera 10.000 items/segundo y cada tarea tarda 1 segundo:

```
Con hilos virtuales ilimitados:
  → 10.000 hilos virtuales activos simultáneamente
  → Memoria: manejable (KB por hilo)
  → Pero: 10.000 tareas bloqueadas esperando I/O al mismo tiempo
  → La base de datos / API / recurso externo puede colapsar bajo esa carga
```

El problema se traslada: ya no colapsa la JVM por memoria de hilos, pero sí puede colapsar el **recurso externo** (base de datos, servidor HTTP) al recibir miles de conexiones simultáneas.

### La combinación correcta: hilos virtuales + estrategia de backpressure

La solución más robusta para tareas I/O bloqueantes es combinar ambas herramientas:

```java
Scheduler virtualThreadScheduler = Schedulers.fromExecutor(
    Executors.newVirtualThreadPerTaskExecutor()
);

producer
    .onBackpressureBuffer(500)          // ① limitar el buffer para no saturar
    .parallel(50)                       // ② cantidad razonable de rails concurrentes
    .runOn(virtualThreadScheduler)      // ③ cada rail en un hilo virtual (I/O eficiente)
    .map(this::blockingIoTask)          // ④ tarea bloqueante (DB, HTTP...)
    .sequential()
    .subscribe(Util.subscriber());
```

```
Producer  →  [ buffer 500 ]  →  parallel(50 rails)  →  Consumer
(rápido)      (controla flujo)   (hilos virtuales)      (bloqueante I/O)
```

- `onBackpressureBuffer(500)` frena al producer si se acumulan demasiados items.
- `parallel(50)` distribuye el trabajo en 50 tareas concurrentes.
- Los hilos virtuales manejan la espera de I/O sin bloquear OS threads.
- El recurso externo recibe máximo 50 conexiones simultáneas — controlado.

### Cuándo usar cada combinación

| Escenario | Solución recomendada |
|---|---|
| Tarea CPU-bound (cálculos, transformaciones) | `parallel(N)` con `Schedulers.parallel()`, N = núcleos CPU. Sin hilos virtuales (no ayudan). |
| Tarea I/O bloqueante (DB, HTTP, archivos) | `parallel(N)` + hilos virtuales + estrategia de backpressure |
| Tarea I/O no-bloqueante (WebClient, R2DBC) | `flatMap(task, concurrency)` directamente. Reactor ya es no-bloqueante; no se necesitan hilos virtuales. |
| Producer permanentemente más rápido | Rediseñar el producer (`Flux.generate`, paginación lazy) o usar `onBackpressureDrop/Latest` |

---

## ¿Qué pasa si el producer siempre produce más de lo que el subscriber puede procesar?

Hasta ahora vimos estrategias para manejar el backpressure. Pero hay un escenario especialmente crítico: el producer genera eventos **de forma sostenida y permanente** más rápido de lo que el subscriber puede consumir. No es un pico ocasional — es la condición normal del sistema.

### El problema con las estrategias habituales en este escenario

```
Producer: 500 eventos/segundo  (constante, siempre)
Consumer: 10 eventos/segundo   (constante, siempre)
Diferencia: 490 eventos/segundo que nunca se procesan
```

- `onBackpressureBuffer()` → el buffer crece 490 items/segundo sin parar → **OOM inevitable**.
- `onBackpressureBuffer(500)` → el buffer llena en ~1 segundo → lanza error y cancela el stream.
- `parallel(50)` + hilos virtuales → consumer procesa 50×10 = 500 eventos/segundo → equilibrio, pero requiere 50 tareas concurrentes constantes, lo cual puede saturar el recurso externo (DB, API).

Ninguna de estas opciones resuelve el problema de raíz: **el producer produce a una velocidad que el sistema no puede sostener**.

### Las tres soluciones reales

#### Solución 1 — Frenar al producer (la mejor opción)

Si tenés control sobre el producer, la solución de raíz es diseñarlo para que **respete la demanda del subscriber**. Con `Flux.generate`, Reactor controla automáticamente el ritmo de producción:

```java
// ✅ Producer pull-based: solo produce cuando el consumer lo pide
Flux<Integer> producer = Flux.generate(
    () -> 0,
    (state, sink) -> {
        sink.next(state);          // produce exactamente 1 item por demanda
        return state + 1;
    }
);

producer
    .subscribeOn(Schedulers.boundedElastic())
    .publishOn(Schedulers.boundedElastic())
    .map(this::blockingIoTask)     // tarea lenta: 1 segundo
    .subscribe(Util.subscriber());
```

```
Consumer pide 1 item → Producer genera 1 → Consumer procesa → pide otro → ...
Velocidad de producción = Velocidad de consumo  ← equilibrio perfecto
```

Reactor frena automáticamente al producer cuando el consumer está ocupado. No se acumula nada.

**Cuándo aplica:** cuando controlás la fuente de datos (generación propia, paginación de DB, lectura de archivos).

---

#### Solución 2 — Descartar eventos que no se pueden procesar (si perder datos es aceptable)

Si el producer es externo y no podés frenarlo (Kafka, WebSocket, sensor IoT), y la pérdida de datos es aceptable, usá `onBackpressureDrop` o `onBackpressureLatest`:

```java
// ✅ Drop: descarta lo que no se puede procesar
producer
    .onBackpressureDrop(dropped -> log.warn("Descartado: {}", dropped))
    .publishOn(Schedulers.boundedElastic())
    .map(this::blockingIoTask)
    .subscribe(Util.subscriber());
```

```
Producer: 500/s  →  [ DROP ]  →  Consumer: 10/s
                      ↓
              490 eventos/s descartados
              Consumer siempre recibe lo que puede manejar
              Sin acumulación en memoria
```

Con `onBackpressureLatest`, en lugar de descartar todo lo que sobra, se guarda siempre el **último evento** recibido. Cuando el consumer queda libre, recibe el más reciente:

```java
// ✅ Latest: siempre procesa el evento más reciente disponible
producer
    .onBackpressureLatest()
    .publishOn(Schedulers.boundedElastic())
    .map(this::blockingIoTask)
    .subscribe(Util.subscriber());
```

```
Producer emite: 1, 2, 3, 4, 5 ... 498, 499, 500
Consumer libre: toma 500 (el más reciente), descarta 2..499
→ útil para: precios de acciones, temperatura de sensores, posición GPS
```

**Cuándo aplica:** métricas, telemetría, precios, posición, cualquier dato donde lo viejo ya no tiene valor.

---

#### Solución 3 — Escalar el consumer hasta igualar la velocidad del producer

Si no podés frenar al producer **y** no podés perder datos, la única opción es **aumentar la capacidad del consumer** hasta igualar la velocidad de producción. Con hilos virtuales esto es mucho más viable que con hilos de plataforma:

```java
Scheduler virtualThreadScheduler = Schedulers.fromExecutor(
    Executors.newVirtualThreadPerTaskExecutor()
);

// Producer: 500 eventos/segundo
// Consumer: 10 eventos/segundo por tarea
// Necesitamos: 500/10 = 50 tareas concurrentes para equilibrar
producer
    .onBackpressureBuffer(1000)             // buffer de seguridad
    .parallel(50)                           // 50 tareas concurrentes
    .runOn(virtualThreadScheduler)          // hilos virtuales: ligeros para I/O
    .map(this::blockingIoTask)
    .sequential()
    .subscribe(Util.subscriber());
```

```
Producer: 500/s  →  [ buffer 1000 ]  →  parallel(50 rails)  →  Consumer: 50×10 = 500/s
                      (amortiguador)       (hilos virtuales)      equilibrio ✅
```

> ⚠️ **Atención:** 50 tareas concurrentes pueden saturar el recurso externo (base de datos, API).
> Hay que verificar que el recurso externo pueda manejar esa concurrencia.
> Si no puede, esta solución traslada el cuello de botella al recurso externo.

### Árbol de decisión: producer siempre más rápido

```
¿Tenés control sobre el producer?
    ├── SÍ → Usar Flux.generate (pull-based). El consumer controla la velocidad.
    │         Es la solución más limpia y eficiente.
    │
    └── NO (fuente externa: Kafka, IoT, WebSocket...)
          │
          ¿Podés perder datos?
              ├── SÍ → onBackpressureDrop (descarta intermedios)
              │         onBackpressureLatest (guarda el último)
              │
              └── NO (cada evento importa)
                    │
                    ¿El recurso externo aguanta más concurrencia?
                        ├── SÍ → parallel(N) + hilos virtuales + onBackpressureBuffer(límite)
                        │
                        └── NO → El sistema no puede sostener esta carga.
                                  Opciones: rate limiting en el producer,
                                  agregar más instancias del consumer (escalar horizontalmente),
                                  o revisar el diseño de la arquitectura.
```
