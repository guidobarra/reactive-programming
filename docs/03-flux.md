# Sección 03 — Flux: Publisher de 0 a N Elementos

## Objetivo

Esta sección presenta **`Flux<T>`**, el tipo de Project Reactor que representa un flujo reactivo de **0 a N elementos**. Se exploran las distintas formas de crearlo, cómo usarlo con múltiples suscriptores, las diferencias críticas entre cada método de creación y las ventajas del procesamiento reactivo frente a colecciones tradicionales.

---

## Lecciones

### Lec01 — `Flux.just()`

```java
Flux.just(1, 2, 3, "sam").subscribe(Util.subscriber());
```

- Emite una secuencia fija de elementos conocidos y luego completa.
- Los valores se evalúan **inmediatamente** al crear el Flux.
- Acepta múltiples valores de distintos tipos (aunque se recomienda homogeneidad).

**Cuándo usar:** secuencias cortas y fijas de datos conocidos en memoria.

---

### Lec02 — Múltiples Suscriptores y Operadores (`filter`, `map`)

```java
var flux = Flux.just(1, 2, 3, 4, 5, 6);

flux.subscribe(Util.subscriber("sub1"));
flux.filter(i -> i > 7).subscribe(Util.subscriber("sub2"));
flux.filter(i -> i % 2 == 0).map(i -> i + "a").subscribe(Util.subscriber("sub3"));
```

- Un mismo `Flux` puede tener múltiples suscriptores independientes.
- Cada `subscribe()` crea una **nueva suscripción** con su propia ejecución del pipeline.
- Los operadores `filter()` y `map()` se aplican de forma independiente para cada suscriptor.

---

### Lec03 — `Flux.fromIterable()` y `Flux.fromArray()`

```java
Flux.fromIterable(List.of(1, 2, 3, 4)).subscribe(...);
```

- Convierte una colección (`List`, `Set`, etc.) o un array en un Flux.
- Crea un nuevo `Iterator` para cada suscripción automáticamente (seguro para múltiples suscripciones).

---

### Lec04 — `Flux.fromStream()`

```java
// ❌ PROBLEMÁTICO: el Stream se consume en la primera suscripción
Flux.fromStream(list.stream());

// ✅ CORRECTO: se crea un nuevo Stream para cada suscripción
var flux = Flux.fromStream(list::stream);
flux.subscribe(Util.subscriber("sub1"));
flux.subscribe(Util.subscriber("sub2")); // funciona correctamente
```

Un Java `Stream` solo puede consumirse **una vez**. Si se pasa directamente, la segunda suscripción fallará con `IllegalStateException`. Siempre usar un `Supplier` (`list::stream`) para que cada suscripción obtenga un Stream fresco.

---

### Lec05 — `Flux.range()`

```java
Flux.range(1, 5).subscribe(Util.subscriber());
// Emite: 1, 2, 3, 4, 5
```

- Genera una secuencia de enteros consecutivos.
- `range(start, count)` — inicia desde `start` y emite `count` elementos.
- Más eficiente que `Flux.just(1, 2, 3, 4, 5)` para rangos grandes.

---

### Lec06 — `log()`

```java
Flux.range(1, 5).log().subscribe(Util.subscriber());
```

- El operador `log()` registra todos los eventos del flujo reactivo (onSubscribe, request, onNext, onComplete, etc.).
- Muy útil para **debugging** y entender el flujo de mensajes.

---

### Lec07 — Flux vs List

```java
// List: genera los 10 elementos inmediatamente y los almacena en memoria
var list = NameGenerator.getNamesList(10);

// Flux: genera solo los elementos solicitados
NameGenerator.getNamesFlux(10).subscribe(subscriber);
subscriber.getSubscription().request(3); // solo se generan 3
subscriber.getSubscription().cancel();   // los 7 restantes nunca se generan
```

| Aspecto | `List` | `Flux` |
|---------|--------|--------|
| Generación | Inmediata, todos a la vez | On-demand (lazy) |
| Memoria | Todos en memoria | Solo los solicitados |
| Control | Sin control | `request(n)` y `cancel()` |
| Cancelación | No aplicable | En cualquier momento |

**Cuándo usar Flux:** cuando el tamaño es grande/desconocido, cuando quieres procesamiento por demanda, cuando necesitas cancelar antes de terminar.

---

### Lec08 — I/O No Bloqueante con Streaming

```java
client.getNames().subscribe(Util.subscriber("sub1"));
client.getNames().subscribe(Util.subscriber("sub2"));
```

- El servicio externo envía múltiples mensajes en un stream continuo.
- Cada suscriptor recibe su propia copia del stream.
- Las operaciones son no bloqueantes: el hilo no espera.

**Diferencia con request-response:**
- Tradicional: una solicitud → una respuesta → fin.
- Streaming reactivo: una solicitud → múltiples respuestas → hasta `onComplete()`.

---

### Lec09 — `Flux.interval()`

```java
Flux.interval(Duration.ofMillis(500))
    .map(i -> Util.faker().name().firstName())
    .subscribe(Util.subscriber());
```

- Emite números incrementales (`0, 1, 2, ...`) a intervalos regulares de tiempo.
- Se ejecuta en un `Scheduler` separado, **no bloquea** el hilo principal.
- Es **infinito** por defecto — usar `take()` o cancelar explícitamente para limitar.

---

### Lec10 — `Flux.empty()` y `Flux.error()`

Equivalentes a `Mono.empty()` y `Mono.error()` pero para flujos de múltiples elementos.

---

### Lec11 — Conversión entre Flux y Mono

```java
Flux.just(1, 2, 3).next()           // Mono con el primer elemento
Flux.just(1, 2, 3).elementAt(1)     // Mono con el segundo elemento
mono.flux()                          // Flux de 0 o 1 elemento
```

---

## Resumen de Fábricas de Flux

| Método | Descripción |
|--------|-------------|
| `Flux.just(...)` | Valores fijos en memoria |
| `Flux.fromIterable(col)` | Desde una colección |
| `Flux.fromArray(arr)` | Desde un array |
| `Flux.fromStream(supplier)` | Desde un Java Stream (usar Supplier) |
| `Flux.range(start, count)` | Rango de enteros consecutivos |
| `Flux.interval(duration)` | Emisión periódica infinita |
| `Flux.empty()` | Sin elementos, solo `onComplete()` |
| `Flux.error(t)` | Solo `onError()` |
| `Flux.create(sink -> ...)` | Emisión programática (ver Sección 04) |
| `Flux.generate(sink -> ...)` | Generación uno a uno (ver Sección 04) |
