# Sección 05 — Operadores: handle, do-callbacks, errores, timeout y transform

## Objetivo

Esta sección explora un conjunto de **operadores esenciales** de Reactor que permiten controlar el ciclo de vida del pipeline reactivo: filtrar y transformar con `handle()`, observar eventos con callbacks `do*`, manejar errores de forma estratégica, gestionar valores por defecto, manejar timeouts y reutilizar lógica de pipeline con `transform()`.

---

## Lecciones

### Lec01 — `handle()`: filter + map en uno

```java
Flux.range(1, 10)
    .handle((item, sink) -> {
        switch (item) {
            case 1 -> sink.next(-2);           // transforma (como map)
            case 4 -> {}                        // filtra (como filter)
            case 7 -> sink.error(new RuntimeException("oops")); // emite error
            default -> sink.next(item);         // pasa sin cambios
        }
    })
    .cast(Integer.class)
    .subscribe(Util.subscriber());
```

`handle()` combina `filter()` y `map()` en una sola operación:
- Si llamas a `sink.next(v)` → emite un valor (transformación).
- Si no llamas a `sink.next()` → filtra el elemento.
- Si llamas a `sink.error(t)` → termina el flujo con error.

**Cuándo usar:** cuando la lógica de filtrado y transformación están estrechamente relacionadas, o cuando necesitas emitir errores condicionales.

---

### Lec02 — `handle()` + Assignment

Ejercicio de aplicación del operador `handle()` para detener el flujo bajo una condición, similar a `takeWhile()` pero con mayor flexibilidad.

---

### Lec03 — Do Callbacks (Hooks del ciclo de vida)

Los métodos `do*` permiten **observar** eventos del pipeline sin modificar los valores:

```java
Flux.create(...)
    .doFirst(() -> log.info("doFirst"))           // antes de todo
    .doOnSubscribe(s -> log.info("subscribed"))   // cuando llega la Subscription
    .doOnRequest(n -> log.info("requested {}", n)) // cuando el downstream solicita
    .doOnNext(i -> log.info("next: {}", i))       // por cada elemento
    .doOnComplete(() -> log.info("complete"))      // cuando completa exitosamente
    .doOnError(e -> log.info("error: {}", e))     // cuando hay error
    .doOnTerminate(() -> log.info("terminate"))    // complete o error
    .doOnCancel(() -> log.info("cancelled"))       // cuando se cancela
    .doOnDiscard(Object.class, o -> log.info("discard: {}", o)) // elementos descartados
    .doFinally(signal -> log.info("finally: {}", signal)) // siempre al final
```

| Callback | Cuándo se invoca | Dirección del flujo |
|----------|-----------------|---------------------|
| `doFirst` | Antes de todo, al suscribirse | ↑ (subscriber → producer) |
| `doOnSubscribe` | Al recibir la Subscription | ↑ |
| `doOnRequest` | Al solicitar elementos | ↑ |
| `doOnNext` | Por cada elemento emitido | ↓ (producer → subscriber) |
| `doOnComplete` | Al completar exitosamente | ↓ |
| `doOnError` | Al ocurrir un error | ↓ |
| `doOnTerminate` | Al completar O al haber error | ↓ |
| `doOnCancel` | Al cancelar la suscripción | ↑ |
| `doOnDiscard` | Al descartar elementos | — |
| `doFinally` | Siempre (complete, error o cancel) | — |

> ⚠️ El orden de ejecución de los `doFirst` es de **abajo hacia arriba** (desde el subscriber hacia el producer). El orden del resto sigue el flujo de datos: de arriba hacia abajo.

---

### Lec04 — `delayElements()`

```java
Flux.just("a", "b", "c")
    .delayElements(Duration.ofMillis(500))
    .subscribe(Util.subscriber());
```

Introduce un delay entre cada elemento emitido. Útil para simular respuestas lentas o para pruebas de timeout.

---

### Lec05 — `subscribe()` con lambdas

Recordatorio sobre las variantes del método `subscribe()` para manejar cada evento del ciclo de vida.

---

### Lec06 — Manejo de Errores

Cuatro estrategias para manejar errores en pipelines reactivos:

#### `onErrorContinue()` — Continuar después del error
```java
Flux.range(1, 10)
    .map(i -> i == 5 ? 5 / 0 : i)
    .onErrorContinue((ex, obj) -> log.error("error en elemento: {}", obj, ex))
    .subscribe(Util.subscriber());
```
- Omite el elemento que causó el error y **continúa** con el siguiente.
- Útil para procesamiento batch donde algunos elementos pueden fallar.

#### `onErrorComplete()` — Completar silenciosamente
```java
Mono.just(1).map(...).onErrorComplete().subscribe(...);
```
- Convierte el error en `onComplete()`.
- El suscriptor recibe una completación limpia, sin error.

#### `onErrorReturn()` — Valor por defecto
```java
Mono.just(5)
    .map(i -> i == 5 ? 5 / 0 : i)
    .onErrorReturn(ArithmeticException.class, -2)
    .onErrorReturn(-3)  // fallback genérico
    .subscribe(...);
```
- Emite un valor por defecto cuando ocurre el error.
- Puede ser específico por tipo de excepción o genérico.
- Los manejadores se evalúan en orden de arriba hacia abajo.

#### `onErrorResume()` — Publisher alternativo
```java
Mono.error(new RuntimeException("oops"))
    .onErrorResume(ArithmeticException.class, ex -> fallback1())
    .onErrorResume(ex -> fallback2())
    .onErrorReturn(-5)
    .subscribe(...);
```
- Cuando hay error, cambia a otro `Publisher` (Mono/Flux) como fallback.
- Más flexible que `onErrorReturn()` porque permite lógica compleja.
- Útil para fallback a servicios alternativos.

---

### Lec07 — `defaultIfEmpty()`

```java
Flux.empty()
    .defaultIfEmpty("valor por defecto")
    .subscribe(Util.subscriber());
```

Si el flujo completa sin emitir ningún elemento, emite el valor por defecto.

---

### Lec08 — `switchIfEmpty()`

```java
Flux.empty()
    .switchIfEmpty(Flux.just("A", "B", "C"))
    .subscribe(Util.subscriber());
```

Si el flujo completa sin emitir ningún elemento, **cambia a otro Publisher**. A diferencia de `defaultIfEmpty()`, permite un Publisher completo como alternativa (con múltiples elementos, lógica asíncrona, etc.).

---

### Lec09 — `timeout()`

```java
getProductName()
    .timeout(Duration.ofSeconds(1), fallback())
    .subscribe(Util.subscriber());
```

- Si el Publisher no emite un elemento dentro del tiempo especificado, emite un error `TimeoutException`.
- Se puede pasar un Publisher de fallback como segundo argumento para manejar el timeout.
- Pueden anidarse múltiples `timeout()`; el más cercano al suscriptor tiene efecto para el suscriptor.

---

### Lec10 — `transform()`

```java
private static <T> UnaryOperator<Flux<T>> addDebugger() {
    return flux -> flux
        .doOnNext(i -> log.info("received: {}", i))
        .doOnComplete(() -> log.info("completed"))
        .doOnError(err -> log.error("error", err));
}

// Uso
getCustomers()
    .transform(isDebugEnabled ? addDebugger() : Function.identity())
    .subscribe();
```

`transform()` permite **encapsular y reutilizar** lógica de pipeline (una cadena de operadores) como una función. Muy útil para:
- Agregar logging/debugging condicional.
- Aplicar la misma transformación a múltiples Flux de distintos tipos.
- Evitar duplicación de código en pipelines complejos.

---

## Resumen de Operadores de esta Sección

| Operador | Función |
|----------|---------|
| `handle()` | Filter + map en uno, con soporte para errores |
| `do*()` | Callbacks de observación del ciclo de vida |
| `delayElements()` | Delay entre elementos |
| `onErrorContinue()` | Continúa el flujo omitiendo el elemento que falló |
| `onErrorComplete()` | Convierte el error en onComplete() |
| `onErrorReturn()` | Emite un valor por defecto en caso de error |
| `onErrorResume()` | Cambia a un Publisher alternativo en caso de error |
| `defaultIfEmpty()` | Emite un valor si el flujo está vacío |
| `switchIfEmpty()` | Cambia a otro Publisher si el flujo está vacío |
| `timeout()` | Error si no llega elemento en el tiempo dado |
| `transform()` | Encapsula y reutiliza cadenas de operadores |
