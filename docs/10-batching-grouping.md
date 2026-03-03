# Sección 10 — Batching y Agrupación: `buffer`, `window` y `groupBy`

---

## Introducción — ¿Qué problema resuelven estos operadores?

Imagina que tienes un stream de eventos que llegan continuamente: clics de usuarios, métricas de sensores, mensajes de Kafka, pedidos de e-commerce. Estos eventos llegan uno a uno, a alta velocidad, sin parar.

**El problema:** No siempre conviene procesar cada evento de forma individual. Hacerlo puede ser:
- **Ineficiente:** insertar un registro en la base de datos por cada evento significa miles de operaciones por segundo.
- **Costoso en red:** hacer una llamada HTTP por cada evento produce una avalancha de requests.
- **Difícil de analizar:** no podés calcular métricas de ventana temporal (ej. "ventas de los últimos 5 segundos") sin agrupar primero.

**La solución:** Los operadores de batching y agrupación permiten **acumular elementos** y procesarlos en conjunto, en lotes, o encaminarlos a sub-flujos especializados.

```
Sin agrupación:
eventos → [e1] → [e2] → [e3] → [e4] → [e5] → ...  (uno a uno, caro)

Con buffer(3):
eventos → [e1,e2,e3] → [e4,e5,e6] → ...            (en lotes de 3)

Con window(3s):
eventos → Flux[e1,e2] → Flux[e3,e4,e5] → ...       (ventanas temporales)

Con groupBy(categoría):
eventos → Flux[pedidos-autos] + Flux[pedidos-libros] (flujos paralelos por tipo)
```

### ¿Cuándo es relevante esta sección?

Esta sección es especialmente útil si usás:
- **Kafka, RabbitMQ, Pulsar** — consumís un topic y recibís eventos en streaming.
- **Bases de datos** — querés hacer inserts en batch para mejorar el throughput.
- **Análisis en tiempo real** — métricas de ventana temporal (ventas por minuto, errores por hora).
- **Procesamiento diferenciado** — distintas categorías de mensajes necesitan lógica diferente.

### Los tres operadores y su enfoque

| Operador | ¿Qué agrupa? | ¿Qué devuelve? | Enfoque principal |
|----------|-------------|----------------|-------------------|
| `buffer()` | Elementos en una lista | `Flux<List<T>>` | Procesar lotes completos |
| `window()` | Elementos en un sub-Flux | `Flux<Flux<T>>` | Procesar reactivamente por ventana |
| `groupBy()` | Elementos por clave/categoría | `Flux<GroupedFlux<K,T>>` | Enrutar elementos a flujos especializados |

---

## `buffer()` — Acumular elementos en listas

### ¿Qué hace?

`buffer()` **acumula elementos del Flux en una `List<T>`** y emite esa lista como un único elemento downstream. En lugar de procesar cada elemento individualmente, los agrupa en batches.

**Analogía:** Es como una cinta transportadora de fábrica con una caja al final. Los productos se van metiendo en la caja. Cuando la caja está llena (o pasa cierto tiempo), la caja entera se envía al siguiente proceso.

### Variantes

```java
// 1. buffer() sin argumento: acumula TODO hasta que la fuente completa
//    ⚠️ Peligroso con streams infinitos — nunca emitirá nada
eventStream()
    .buffer()
    .subscribe(list -> log.info("Recibí {} elementos", list.size()));

// 2. buffer(n): emite una lista cada N elementos
eventStream()
    .buffer(3)                      // lista de 3 en 3
    .subscribe(list -> insertarEnDB(list));

// 3. buffer(Duration): emite una lista cada X tiempo
eventStream()
    .buffer(Duration.ofSeconds(5))  // todo lo que llegó en 5 segundos
    .subscribe(list -> generarReporte(list));

// 4. bufferTimeout(n, Duration): el que ocurra PRIMERO (N items O tiempo límite)
eventStream()
    .bufferTimeout(3, Duration.ofSeconds(1))
    .subscribe(list -> procesarLote(list));
```

### Comportamiento cuando la fuente se completa antes de llenar el lote

Un comportamiento importante: si la fuente se completa y el lote no llegó a llenarse, `buffer` **emite el lote parcial**. No espera a que lleguen más elementos; emite lo que tiene y finaliza.

```java
Flux.just(1, 2, 3, 4, 5)
    .buffer(3)
    .subscribe(System.out::println);
// Output:
// [1, 2, 3]
// [4, 5]       ← lote parcial, la fuente completó con solo 2 elementos
```

### El problema de `buffer(n)` con streams que pueden pausarse

Supongamos que usamos `buffer(3)` y la fuente emite 10 elementos y luego se concatena con `Flux.never()` (nunca completa ni emite más):

```java
Flux.range(1, 10)
    .concatWith(Flux.never())  // simula un stream que se "cuelga"
    .buffer(3)
    .subscribe(System.out::println);
// Output:
// [1, 2, 3]
// [4, 5, 6]
// [7, 8, 9]
// ← el elemento 10 NUNCA se emite porque buffer espera al 11 y 12
```

**El elemento 10 queda atrapado** en el buffer esperando que lleguen 11 y 12 para completar el lote. Esto puede ser un problema real en streams de Kafka que reciben ráfagas de mensajes seguidas de períodos de silencio.

**Solución:** `bufferTimeout(n, Duration)` — emite el lote cuando se llena O cuando pasa el tiempo límite, lo que ocurra primero:

```java
Flux.range(1, 10)
    .concatWith(Flux.never())
    .bufferTimeout(3, Duration.ofSeconds(1))  // ← máximo 1 segundo de espera
    .subscribe(System.out::println);
// Output:
// [1, 2, 3]
// [4, 5, 6]
// [7, 8, 9]
// [10]          ← después de 1 segundo, se emite el lote parcial
```

### Resumen de variantes

| Variante | Cuándo emite | Problema que resuelve |
|----------|-------------|----------------------|
| `buffer()` | Cuando la fuente completa | Acumular todo para procesamiento final |
| `buffer(n)` | Cada N elementos | Inserts en batch, reducir llamadas |
| `buffer(Duration)` | Cada X tiempo | Reportes periódicos, métricas por intervalo |
| `bufferTimeout(n, Duration)` | N items O X tiempo | Streams con pausas, garantía de latencia |

### ✅ Cuándo usar `buffer()`

- **Inserts en base de datos en batch:** insertar 1000 eventos de una vez es mucho más eficiente que 1000 inserts individuales.
- **Envío de emails/notificaciones en lote:** agrupar notificaciones para enviar un resumen en lugar de emails individuales.
- **Generación de reportes periódicos:** "dame todas las ventas de los últimos 5 segundos".
- **Reducir llamadas a APIs externas:** en lugar de 100 llamadas, hacer 1 llamada con 100 IDs.

### ❌ Cuándo NO usar `buffer()`

- **`buffer()` sin argumentos en streams infinitos (como Kafka):** la variante sin argumentos espera a que la fuente complete para emitir la lista acumulada. Un stream de Kafka nunca completa, por lo que el buffer acumula elementos en memoria indefinidamente y el subscriber **nunca recibe nada**. Con Kafka siempre usá `buffer(n)`, `buffer(Duration)` o `bufferTimeout(n, Duration)`, que no dependen de que la fuente complete.

  ```java
  // ❌ Con stream infinito: acumula para siempre, el subscriber no recibe nada
  kafkaStream()
      .buffer()
      .subscribe(batch -> insertarEnDB(batch));  // nunca se ejecuta

  // ✅ Con stream infinito: usá siempre una variante con argumento
  kafkaStream()
      .bufferTimeout(100, Duration.ofSeconds(5))
      .subscribe(batch -> insertarEnDB(batch));  // recibe lotes regularmente
  ```

- **Cuando el orden de procesamiento importa y los lotes deben ser exactos:** `buffer(n)` puede emitir lotes parciales al final.
- **Cuando necesitás procesar cada elemento tan pronto como llega:** `buffer` introduce latencia deliberada.
- **Cuando los lotes pueden ser muy grandes:** toda la lista se mantiene en memoria, riesgo de OOM.

### 🏭 Casos de uso en producción

| Industria / Sistema | Problema | Cómo se usa `buffer()` |
|---------------------|----------|------------------------|
| **E-commerce (ej. Amazon, MercadoLibre)** | Registrar cada click/vista de producto en DB es muy costoso | `bufferTimeout(500, Duration.ofSeconds(2))` → bulk insert cada 2s o cada 500 clicks |
| **Telemetría / Observabilidad (ej. Datadog, New Relic)** | Millones de métricas por segundo desde agentes | `buffer(1000)` → enviar en batches a la API de ingestión |
| **Fintech / Pagos** | Reconciliación de transacciones al cierre del día | `buffer(Duration.ofHours(1))` → procesar por hora todas las transacciones del período |
| **Kafka consumers** | Insertar mensajes de Kafka en PostgreSQL/MongoDB | `bufferTimeout(200, Duration.ofSeconds(5))` → batch insert, reduce el número de roundtrips a DB |
| **IoT / Sensores industriales** | Lecturas de temperatura/presión cada 100ms → demasiadas escrituras | `buffer(50)` → agrupar 50 lecturas y guardar el promedio |

**Referencia:** Patrón documentado en la guía oficial de Spring WebFlux y en arquitecturas de microservicios reactivos [[Certidevs — Operadores reactivos avanzados](https://certidevs.com/tutorial-spring-boot-webflux-operadores-reactivos-avanzados)] [[Spring Reactive Streams — Sergio Márquez](https://blog.sergiomarquez.dev/post/desarrollo-spring-reactive-streams-programacion-reactiva-spring-boot-3x)]

---

## `window()` — Ventanas como sub-Flux reactivos

### ¿Qué hace?

`window()` es **conceptualmente similar a `buffer()`**, pero en lugar de emitir una `List<T>`, emite un **`Flux<T>` interno** (una ventana). Cada ventana es un publisher independiente al que podés suscribirte y aplicar operadores reactivos.

**Analogía:** En lugar de llenar una caja y enviarla completa, abrís un caño (Flux) y dejás que los elementos fluyan por él en tiempo real. Cuando se acaba la ventana, cerrás ese caño y abrís uno nuevo.

### La diferencia fundamental con `buffer()`

```
buffer(3):
  fuente: 1 2 3 4 5 6 7
  ↓ espera hasta tener 3 elementos
  ↓ acumula en memoria: [1,2,3]
  ↓ emite la lista completa al subscriber

window(3):
  fuente: 1 2 3 4 5 6 7
  ↓ abre Flux#1, emite 1, emite 2, emite 3, cierra Flux#1
  ↓ abre Flux#2, emite 4, emite 5, emite 6, cierra Flux#2
  ↓ subscriber puede procesar cada elemento A MEDIDA QUE LLEGA
```

Con `buffer`: el subscriber recibe `[1,2,3]` (ya completo, en memoria).
Con `window`: el subscriber recibe un `Flux<T>` y puede procesar `1`, `2`, `3` uno por uno a medida que llegan.

### Sintaxis

```java
// window(n): una nueva ventana cada N elementos
eventStream()
    .window(5)
    .flatMap(windowFlux -> procesarVentana(windowFlux))
    .subscribe();

// window(Duration): una nueva ventana cada X tiempo
eventStream()
    .window(Duration.ofMillis(1800))
    .flatMap(windowFlux -> procesarVentana(windowFlux))
    .subscribe();
```

### Característica importante: solo una ventana abierta a la vez

Con `window()`, **en cualquier momento solo hay un Flux interno abierto**. Cuando se alcanza el límite (N elementos o tiempo), esa ventana se cierra y se abre una nueva. Las ventanas no se solapan (a menos que uses `window(int, int)` con parámetros distintos).

```
window(Duration.ofSeconds(5)):

  t=0s → abre Flux#1
  t=2s   → llega e1 → va a Flux#1
  t=3s   → llega e2 → va a Flux#1
  t=5s → cierra Flux#1 (se emiten e1, e2)
  t=5s → abre Flux#2
  t=6s   → llega e3 → va a Flux#2
  t=10s → cierra Flux#2 (se emite e3)
  ...
```

### Por qué NO usar `window()` como si fuera `buffer()`

Un error común es usar `window` y luego suscribirse directamente con `Util.subscriber()` dentro de un `flatMap`:

```java
// ❌ Uso incorrecto: equivale a NO tener window, misma salida que sin agrupar
eventStream()
    .window(5)
    .flatMap(flux -> flux)    // simplemente "desenvuelve" la ventana
    .subscribe(Util.subscriber());
// Resultado: elementos llegan uno por uno, no hay diferencia con el stream original
```

El propósito real de `window` es **cambiar el subscriber** o la lógica de procesamiento para cada ventana:

```java
// ✅ Uso correcto: cada ventana se procesa de forma diferente
eventStream()
    .window(Duration.ofMillis(1800))
    .flatMap(windowFlux -> procesarVentana(windowFlux))
    .subscribe();

private static Mono<Void> procesarVentana(Flux<String> ventana) {
    return ventana
        .doOnNext(e -> System.out.print("*"))   // imprime * por cada elemento
        .doOnComplete(System.out::println)       // salto de línea al cerrar ventana
        .then();                                  // devuelve Mono<Void>
}
// Output: (cada línea es una ventana de 1.8 segundos)
// ****
// ***
// *****
// ...
```

### Caso de uso real: escribir cada ventana a un archivo diferente

```java
// Un archivo nuevo por cada ventana de tiempo
AtomicInteger counter = new AtomicInteger(0);
String fileNameFormat = "src/main/resources/sec10/file-%d.txt";

eventStream()
    .window(Duration.ofSeconds(2))
    .flatMap(windowFlux -> FileWriter.create(
        windowFlux,
        Path.of(fileNameFormat.formatted(counter.incrementAndGet()))
    ))
    .subscribe();

// Resultado: file-1.txt, file-2.txt, file-3.txt...
// Cada archivo contiene los eventos de esa ventana de 2 segundos
```

### ✅ Cuándo usar `window()`

- **Escribir logs a archivos rotativos:** un nuevo archivo por ventana de tiempo.
- **Procesar streams de eventos en tiempo real** sin acumularlos todos en memoria (ventanas grandes).
- **Cambiar el subscriber o procesador** para cada ventana (distinta lógica por período).
- **Streaming dentro de una ventana:** si necesitás empezar a procesar elementos de la ventana antes de que esta se cierre.

### ❌ Cuándo NO usar `window()`

- **Cuando necesitás todos los elementos del lote antes de procesar:** usá `buffer()` directamente.
- **Cuando la lógica de procesamiento es simple** (como un simple `map` o `filter`): la complejidad de `window` no se justifica.
- **Si no vas a hacer nada especial con el Flux interno:** si lo único que hacés es `flatMap(f -> f)`, equivale a no usar `window`.

### 🏭 Casos de uso en producción

| Industria / Sistema | Problema | Cómo se usa `window()` |
|---------------------|----------|------------------------|
| **Sistemas de logging (ej. Logstash, Fluentd)** | Logs continuos que deben rotarse en archivos por hora/día | `window(Duration.ofHours(1))` → cada hora abre un nuevo archivo de log, cierra el anterior |
| **Análisis de series temporales (ej. Grafana, InfluxDB)** | Calcular métricas (promedio, máximo) por intervalos de tiempo sin cargar todo en memoria | `window(Duration.ofMinutes(5))` → cada ventana es un Flux al que se le aplica `.reduce()` |
| **Trading / Mercados financieros** | Calcular indicadores técnicos (media móvil, VWAP) sobre ventanas de velas (candlesticks) | `window(1000)` elementos → procesar reactivamente cada vela de 1000 ticks |
| **Streaming de audio/video** | Dividir el stream en segmentos para HLS (HTTP Live Streaming) | `window(Duration.ofSeconds(6))` → cada segmento de 6s se escribe a un archivo `.ts` |
| **Pipelines de ML / Data Science** | Procesar un stream de eventos y entrenar modelos sobre mini-batches | `window(500)` → cada ventana de 500 eventos alimenta una iteración de gradient descent |

**La clave que diferencia `window` de `buffer` en producción:** cuando el procesamiento de cada elemento dentro del lote no puede esperar a que el lote esté completo (ej. escribir a disco en streaming, aplicar backpressure dentro de la ventana, o cuando las ventanas pueden ser muy largas).

**Referencia:** Patrón de ventanas temporales documentado en arquitecturas de stream processing [[Certidevs — Operadores reactivos avanzados](https://certidevs.com/tutorial-spring-boot-webflux-operadores-reactivos-avanzados)]

### Diferencia clave `buffer()` vs `window()`

| Aspecto | `buffer()` | `window()` |
|---------|-----------|-----------|
| Tipo emitido | `List<T>` (colección completa) | `Flux<T>` (stream reactivo) |
| Cuándo llegan los datos al subscriber | Al llenarse el lote completo | A medida que llegan, dentro de la ventana |
| Memoria | Acumula todo el lote en RAM | Procesa elemento a elemento (streaming) |
| Reactivo dentro del lote | ❌ No | ✅ Sí |
| Ventanas simultáneas abiertas | — (emite listas) | Solo 1 abierta a la vez |
| Caso típico | Inserts en batch a DB | Logs rotativos, escritura a archivos |

---

## `groupBy()` — Enrutar elementos a flujos especializados por clave

### ¿Qué hace?

`groupBy()` **divide un Flux en múltiples sub-flujos** (`GroupedFlux`), uno por cada clave distinta que aparezca en el stream. Los elementos se **enrutan automáticamente** al sub-flujo que corresponde a su clave.

**Analogía:** Imaginá una cinta transportadora de paquetes en un depósito. Llegan paquetes de todo tipo mezclados. El operador `groupBy` actúa como el clasificador automático: los paquetes de "electrónica" van a la sección A, los de "ropa" a la sección B, los de "alimentos" a la sección C. Cada sección tiene su propio proceso de manejo.

```
Stream mezclado:
  bola-rosa, bola-verde, bola-púrpura, bola-rosa, bola-verde

groupBy(color):
  Flux["rosa"]   → bola-rosa, bola-rosa, ...
  Flux["verde"]  → bola-verde, bola-verde, ...
  Flux["púrpura"]→ bola-púrpura, ...
```

### Sintaxis

```java
Flux.range(1, 30)
    .delayElements(Duration.ofSeconds(1))
    .groupBy(i -> i % 2 == 0 ? "par" : "impar")  // clave: "par" o "impar"
    .flatMap(groupedFlux -> procesarGrupo(groupedFlux))
    .subscribe();

private static Mono<Void> procesarGrupo(GroupedFlux<String, Integer> grupo) {
    log.info("Nuevo sub-flujo para clave: {}", grupo.key());
    return grupo
        .doOnNext(i -> log.info("clave={}, item={}", grupo.key(), i))
        .then();
}
```

Output:
```
Nuevo sub-flujo para clave: impar   ← se crea solo la primera vez que aparece esta clave
key=impar, item=1
Nuevo sub-flujo para clave: par
key=par, item=2
key=impar, item=3
key=par, item=4
...
```

### ¿Cómo funciona internamente?

Cuando llega un elemento, `groupBy` calcula su clave. Si ya existe un `GroupedFlux` para esa clave, **enruta el elemento a ese flujo existente**. Si no existe, **crea un nuevo `GroupedFlux`** y lo emite hacia downstream para que sea procesado.

```
elemento 1 → clave="impar" → crea GroupedFlux["impar"] → emite el GroupedFlux
elemento 2 → clave="par"   → crea GroupedFlux["par"]   → emite el GroupedFlux
elemento 3 → clave="impar" → GroupedFlux["impar"] ya existe → enruta directo (no crea uno nuevo)
elemento 4 → clave="par"   → GroupedFlux["par"] ya existe   → enruta directo
```

Por eso el método `procesarGrupo` se llama **solo dos veces** (una por clave única encontrada), aunque pasen 30 elementos.

### ⚠️ Advertencia crítica: cardinalidad baja

Los `GroupedFlux` internos **permanecen abiertos** mientras el stream principal esté activo. Si la fuente no completa, los sub-flujos tampoco completan. Esto tiene una consecuencia importante:

**Nunca usés `groupBy` con claves de alta cardinalidad** (muchos valores distintos posibles):

```java
// ❌ PELIGROSO: agrupando por número de teléfono (millones de valores posibles)
orderStream()
    .groupBy(order -> order.getPhoneNumber())
    // Creará un GroupedFlux por cada número de teléfono distinto
    // → potencialmente millones de flujos internos abiertos
    // → memoria agotada, degradación del rendimiento
    .flatMap(...)
    .subscribe();

// ✅ CORRECTO: agrupando por categoría (pocos valores posibles)
orderStream()
    .groupBy(order -> order.getCategory())
    // Solo hay N categorías (ej. "autos", "ropa", "libros")
    // → N GroupedFlux abiertos, manejable
    .flatMap(...)
    .subscribe();
```

### ⚠️ Advertencia: los GroupedFlux deben consumirse

Cada `GroupedFlux` **debe ser suscrito/consumido**, de lo contrario el flujo principal se bloquea. Siempre usá `flatMap` (o `concatMap`) para suscribirte a cada sub-flujo:

```java
// ❌ INCORRECTO: no consumir los GroupedFlux
eventStream()
    .groupBy(i -> i % 2)
    .subscribe();  // Los GroupedFlux nunca se procesan → el pipeline se traba

// ✅ CORRECTO: consumir cada GroupedFlux con flatMap
eventStream()
    .groupBy(i -> i % 2)
    .flatMap(group -> group.doOnNext(i -> log.info("key={} item={}", group.key(), i)).then())
    .subscribe();
```

### Diferencia entre `window()` y `groupBy()`: ventanas vs. particiones

Esta es una diferencia fundamental que se confunde frecuentemente:

| Aspecto | `window()` | `groupBy()` |
|---------|-----------|-------------|
| ¿Cuántos Flux internos abiertos a la vez? | **Solo 1** (secuenciales) | **Múltiples** (uno por clave, simultáneos) |
| ¿Cuándo se cierra un Flux interno? | Al alcanzar el límite (N items o tiempo) | Solo cuando la fuente completa (o se cancela) |
| ¿Basado en qué? | Posición/tiempo | Contenido del elemento (su clave) |
| ¿Los elementos van en orden? | Sí, uno tras otro por ventana | No necesariamente, se intercalan entre grupos |
| Caso típico | Logs rotativos, reportes por período | Pedidos por categoría, eventos por tipo |

```
window(Duration.ofSeconds(5)):
  t=0-5s  → [Flux#1 abierto]
  t=5-10s → [Flux#2 abierto]   ← solo uno a la vez

groupBy(categoría):
  [Flux["autos"] abierto]
  [Flux["libros"] abierto]     ← múltiples simultáneos, nunca se cierran
  [Flux["ropa"] abierto]
```

### Caso de uso real: procesamiento diferenciado por categoría

```java
// Pedidos de compra → categorías: "autos" y "kids"
// Reglas:
// - autos: sumar $100 al precio
// - kids: agregar un pedido gratis (buy-one-get-one)

orderStream()
    .filter(OrderProcessingService::canProcess)        // solo categorías que manejamos
    .groupBy(PurchaseOrder::category)                  // un flujo por categoría
    .flatMap(group ->
        group.transform(                               // aplicar procesador según la clave
            OrderProcessingService.getProcessor(group.key())
        )
    )
    .subscribe(Util.subscriber());
```

Esta arquitectura permite que cada categoría tenga su **propia lógica de negocio** aplicada como un operador `UnaryOperator<Flux<T>>`, sin mezclar la lógica de "autos" con la de "kids".

### ✅ Cuándo usar `groupBy()`

- **Procesamiento diferenciado por tipo:** distintas reglas de negocio según la categoría.
- **Enrutamiento de eventos:** mensajes de Kafka con distintos tipos (ERROR, WARNING, INFO).
- **Estadísticas por grupo:** calcular métricas separadas para cada región, producto, etc.
- **Patrón de "particionado lógico":** cuando el stream mezclado necesita separarse en canales especializados.

### ❌ Cuándo NO usar `groupBy()`

- **Alta cardinalidad** (muchos valores de clave distintos): nunca agrupar por IDs únicos de usuario, UUID, número de teléfono, etc.
- **Cuando solo necesitás procesar elementos juntos temporalmente:** para eso usá `buffer` o `window`.
- **Cuando el stream de entrada no completa y las claves son impredecibles:** los GroupedFlux quedan abiertos indefinidamente.
- **En lugar de un simple `filter`:** si querés separar elementos en dos grupos y procesarlos igual, dos `filter` separados son más simples.

### 🏭 Casos de uso en producción

| Industria / Sistema | Problema | Cómo se usa `groupBy()` |
|---------------------|----------|--------------------------|
| **E-commerce / Marketplaces** | Pedidos mezclados de múltiples categorías que requieren lógica de negocio diferente (envío, descuento, impuestos) | `groupBy(Order::category)` → "electrónica" con seguro de envío, "ropa" con descuento estacional |
| **Fintech / Detección de fraude** | Transacciones de distintos tipos (débito, crédito, transferencia) con reglas de validación diferentes | `groupBy(Transaction::type)` → cada tipo tiene su propio motor de reglas de fraude |
| **Telecomunicaciones** | Stream de eventos de red mezclados: errores, warnings, info — cada tipo requiere diferente SLA de respuesta | `groupBy(NetworkEvent::severity)` → "CRITICAL" con alerta inmediata, "INFO" con log silencioso |
| **Kafka multi-topic consumer** | Mensajes de diferentes topics con schemas distintos llegan en un stream unificado | `groupBy(KafkaRecord::topic)` → cada topic tiene su propio deserializador y procesador |
| **Gaming / Eventos de usuario** | Eventos de juego: compras, logros, chats, matchmaking — cada uno va a un microservicio distinto | `groupBy(GameEvent::eventType)` → enrutamiento reactivo sin un router explícito |

**La clave que diferencia `groupBy` en producción:** es el operador de **enrutamiento** del mundo reactivo. Cuando en un sistema no-reactivo usarías un `switch/case` o un `Map<String, Handler>` para decidir qué procesar con qué lógica, en un pipeline reactivo usás `groupBy`.

**Referencia:** Patrón de procesamiento diferenciado por categoría documentado en arquitecturas de microservicios reactivos con Spring WebFlux [[Certidevs — Operadores reactivos avanzados](https://certidevs.com/tutorial-spring-boot-webflux-operadores-reactivos-avanzados)] [[Spring Reactive Streams](https://blog.sergiomarquez.dev/post/desarrollo-spring-reactive-streams-programacion-reactiva-spring-boot-3x)]

---

## Comparación General

### Cuadro comparativo completo

| Aspecto | `buffer()` | `window()` | `groupBy()` |
|---------|-----------|-----------|------------|
| Tipo de salida | `Flux<List<T>>` | `Flux<Flux<T>>` | `Flux<GroupedFlux<K,T>>` |
| ¿Qué agrupa? | Elementos por cantidad o tiempo | Elementos por cantidad o tiempo | Elementos por clave de contenido |
| Procesamiento interno | Toda la lista ya acumulada | Streaming reactivo elemento a elemento | Streaming por sub-flujo de clave |
| Memoria | Acumula lote en RAM | Minimal (procesa a medida que llega) | Un buffer interno por grupo |
| Flujos internos simultáneos | — (listas) | 1 a la vez | Uno por clave (todos abiertos) |
| Cuando se cierra un grupo | Al llenarse o vencer tiempo | Al llenarse o vencer tiempo | Cuando completa la fuente |
| Cardinalidad | No aplica | No aplica | Debe ser **baja** |
| Ideal para | Inserts en batch, reportes | Logs rotativos, escritura a archivos | Enrutamiento por tipo/categoría |

### Cuándo usar cada uno — Guía de decisión rápida

```
¿Necesito acumular elementos y procesarlos todos juntos?
├── ¿Importa el tipo/categoría de cada elemento?
│   └── SÍ → groupBy()          (procesamiento diferenciado)
│   └── NO → ¿Necesito procesar reactivamente dentro del lote?
│             ├── SÍ → window()  (escribir a archivos, streaming interno)
│             └── NO → buffer()  (inserts en batch, reportes simples)
```

### Ejemplos de cada caso de uso

**`buffer()` — Insert en batch a DB:**
```java
kafkaEventStream()
    .bufferTimeout(100, Duration.ofSeconds(5))   // 100 eventos o 5 segundos
    .flatMap(batch -> database.insertBatch(batch))
    .subscribe();
```

**`window()` — Rotación de archivos de log:**
```java
logEventStream()
    .window(Duration.ofHours(1))                 // un archivo por hora
    .flatMap(hourWindow -> FileWriter.create(hourWindow, nextLogFile()))
    .subscribe();
```

**`groupBy()` — Enrutamiento por tipo de pago:**
```java
orderStream()
    .groupBy(Order::paymentMethod)               // "credit_card", "bitcoin", "paypal"
    .flatMap(group ->
        group.flatMap(order ->
            PaymentService.process(group.key(), order)
        )
    )
    .subscribe();
```

---

## Resumen

- **`buffer`:** reunir elementos en una lista y procesarlos todos juntos. Útil para gestionar recursos del sistema de forma más eficiente (inserts en batch).

- **`window`:** similar al buffer, pero abre un nuevo Flux basado en el intervalo o recuento dado. **En un momento dado solo habrá un Flux interno abierto.** Útil para dividir el flujo largo en sub-flujos más pequeños y procesar cada uno como se desee (ej. escribir a un archivo diferente).

- **`groupBy`:** agrupar elementos en función de una propiedad del elemento. **Cuidado: baja cardinalidad es obligatoria.** El operador crea múltiples Flux internos (uno por clave); todos deben estar suscritos y los mensajes deben ser drenados correctamente. Al crear múltiples flujos, podemos añadir operadores específicos a cada categoría para aplicar lógica de negocio diferenciada.
asdadasdasddsdfds