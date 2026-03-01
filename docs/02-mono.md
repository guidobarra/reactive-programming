# Sección 02 — Mono: Publisher de 0 o 1 Elemento

## Objetivo

Esta sección introduce **`Mono<T>`**, el tipo de Project Reactor que representa un flujo reactivo de **0 o 1 elemento**. Se exploran las distintas formas de crearlo, cómo suscribirse, cómo manejar ausencia de datos y errores, y la diferencia fundamental entre creación y ejecución del pipeline.

---

## Lecciones

### Lec01 — Evaluación Perezosa con Java Streams

Antes de entrar a Reactor, se demuestra el concepto de **lazy evaluation** usando Java Streams. Los operadores intermedios (como `peek`) no se ejecutan hasta que se llama a un operador terminal (como `toList`).

> Este concepto aplica directamente a Reactor: ningún Publisher hace trabajo hasta que alguien se suscribe.

---

### Lec02 — `Mono.just()`

```java
Mono.just("vins").subscribe(subscriber);
```

- El valor se evalúa **inmediatamente** al crear el `Mono` (no es lazy).
- Una vez que el Publisher envía `onComplete()`, las solicitudes adicionales no tienen efecto.
- Si el valor es `null`, lanza `NullPointerException`.

**Cuándo usar:** cuando el valor ya está en memoria.

---

### Lec03 — Métodos sobrecargados de `subscribe()`

```java
mono.subscribe(
    i -> log.info("received: {}", i),      // onNext
    err -> log.error("error", err),        // onError
    () -> log.info("completed"),           // onComplete
    subscription -> subscription.request(1) // onSubscribe
);
```

Permite manejar todos los eventos del ciclo de vida reactivo de forma declarativa sin implementar la interfaz `Subscriber` completa.

---

### Lec04 — `Mono.empty()` y `Mono.error()`

```java
case 1 -> Mono.just("sam");
case 2 -> Mono.empty();                             // sin valor, pero completa correctamente
default -> Mono.error(new RuntimeException("..."));  // error
```

| Método | Comportamiento |
|--------|----------------|
| `Mono.just(v)` | Emite el valor y luego `onComplete()` |
| `Mono.empty()` | Emite solo `onComplete()` (equivale a `null` reactivo) |
| `Mono.error(t)` | Emite `onError()` con la excepción indicada |

---

### Lec05 — `Mono.fromSupplier()`

```java
Mono.fromSupplier(() -> sum(list)).subscribe(...);
```

- El `Supplier` **no se ejecuta** hasta que hay una suscripción (evaluación **lazy**).
- No puede lanzar excepciones verificadas (checked exceptions).
- Si el `Supplier` retorna `null`, se emite `Mono.empty()`.

---

### Lec06 — `Mono.fromCallable()`

```java
Mono.fromCallable(() -> sum(list)).subscribe(...);
```

Idéntico a `fromSupplier` pero el `Callable` **puede lanzar excepciones verificadas**. Si lanza una excepción, se convierte en `onError()`.

---

### Lec07 — `Mono.fromRunnable()`

```java
Mono.fromRunnable(() -> notifyBusiness(productId))
```

- Ejecuta un `Runnable` y luego emite `Mono.empty()`.
- **Siempre** completa sin valor.
- Útil para efectos secundarios (logging, notificaciones) que no retornan datos.

---

### Lec08 — `Mono.fromFuture()`

```java
Mono.fromFuture(Lec08MonoFromFuture::getName).subscribe(...);
```

- Convierte un `CompletableFuture` existente en un `Mono`.
- Si el Future completa exitosamente → emite el valor.
- Si el Future falla → emite `Mono.error()`.
- Útil para integrar código legacy o APIs que usan `CompletableFuture`.

---

### Lec09 — Creación vs Ejecución del Publisher

```java
private static Mono<String> getName() {
    log.info("entered the method"); // se ejecuta al llamar getName()
    return Mono.fromSupplier(() -> {
        log.info("generating name"); // se ejecuta solo al suscribirse
        return Util.faker().name().firstName();
    });
}
```

**Crear un Mono es una operación liviana.** La lógica costosa dentro del `Supplier` no se ejecuta hasta la suscripción. Esto permite componer pipelines complejos sin ejecutarlos inmediatamente.

---

### Lec10 — `Mono.defer()`

```java
Mono.defer(Lec10MonoDefer::createPublisher).subscribe(...);
```

- Retrasa **tanto la creación del Publisher** como su ejecución hasta que hay una suscripción.
- Retorna un Publisher "fresco" para cada suscripción.
- Útil cuando la creación del Publisher en sí misma es costosa o depende de estado mutable.

| | `Mono.just()` | `fromSupplier()` | `Mono.defer()` |
|--|--|--|--|
| Evaluación | Inmediata | Al suscribirse | Al suscribirse |
| Creación Publisher | Inmediata | Inmediata | Al suscribirse |

---

### Lec11 — I/O No Bloqueante

```java
// No bloqueante (CORRECTO)
client.getProductName(i).subscribe(Util.subscriber());

// Bloqueante (NO RECOMENDADO)
var name = client.getProductName(i).block();
```

Se demuestra la diferencia entre llamadas bloqueantes y no bloqueantes haciendo 100 solicitudes a un servicio externo:

- **Con `subscribe()`:** todas las solicitudes se lanzan concurrentemente y retornan inmediatamente. Un solo hilo maneja todas las operaciones I/O.
- **Con `block()`:** cada solicitud espera a que la anterior termine. Requiere múltiples hilos para concurrencia y es muy ineficiente.

---

## Resumen de Fábricas de Mono

| Método | Lazy | Puede lanzar checked | Retorna valor |
|--------|------|---------------------|---------------|
| `just(v)` | ❌ | ❌ | ✅ |
| `empty()` | ❌ | — | ❌ |
| `error(t)` | ❌ | — | ❌ |
| `fromSupplier(s)` | ✅ | ❌ | ✅ |
| `fromCallable(c)` | ✅ | ✅ | ✅ |
| `fromRunnable(r)` | ✅ | ❌ | ❌ (siempre empty) |
| `fromFuture(f)` | ✅ | — | ✅ |
| `defer(s)` | ✅ | — | ✅ (nuevo Publisher por suscripción) |
