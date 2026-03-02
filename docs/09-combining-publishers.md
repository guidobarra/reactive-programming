# Sección 09 — Combinando Publishers

## ¿Por qué necesitamos combinar Publishers?

En una arquitectura de microservicios real, una sola petición del cliente raramente se resuelve consultando una única fuente de datos. Por ejemplo, en una página de producto de un e-commerce como Amazon:

- Información del producto → servicio de catálogo
- Recomendaciones ("también comprado con...") → servicio de recomendaciones
- Disponibilidad y tiempo de envío → servicio de logística
- Precio y descuentos → servicio de precios

Todos estos datos tienen que combinarse en una única respuesta. La pregunta es: **¿en qué orden y con qué nivel de concurrencia los llamamos?**

Reactor proporciona operadores para controlar exactamente eso:

| Necesidad | Operador |
|---|---|
| Emitir datos previos antes del producer principal | `startWith()` |
| Esperar que uno termine para empezar el otro | `concatWith()` |
| Llamar a todos en paralelo, sin importar el orden | `merge()` |
| Llamar a todos en paralelo y combinar sus resultados posicionalmente | `zip()` |
| Para cada elemento, hacer una llamada async y aplanar el resultado | `flatMap()` / `concatMap()` |
| Acumular todos los elementos en una lista | `collectList()` |
| Encadenar operaciones sin interés en el resultado intermedio | `then()` |

---

## Lecciones

### Lec01 + Lec02 — `startWith()`

#### ¿Qué problema resuelve?

Permite **anteponer datos** al inicio de un flujo sin necesidad de crear un nuevo publisher desde cero. El subscriber recibe primero los datos del `startWith` y luego los del publisher original.

**Caso típico:** cargar datos de caché antes de ir al servicio lento.

#### ¿Cómo funciona?

```
subscriber ← startWith(datos previos) ← publisher principal
                   ↑
            se suscriben en orden: primero el startWith, luego el principal
```

> **⚠️ Restricción de tipo:** `startWith()` requiere que el Publisher argumento emita el **mismo tipo `T`** que el publisher principal. No se puede combinar un `Flux<String>` con un `Flux<Integer>`.
> ```java
> Flux<String> flux = Flux.just("a", "b");
> flux.startWith("x", "y");           // ✅ String con String
> flux.startWith(Flux.just("z"));     // ✅ Flux<String> con Flux<String>
> flux.startWith(Flux.just(1, 2));    // ❌ no compila: Flux<String> con Flux<Integer>
> ```

```java
// Formas de usar startWith:
producer1()
    .startWith(-1, 0)               // antepone valores literales del mismo tipo
    .startWith(List.of(-2, -1, 0))  // antepone una lista del mismo tipo
    .startWith(producer2())          // antepone otro Publisher del mismo tipo
    .subscribe(Util.subscriber());
```

Con múltiples `startWith()` encadenados, el que se declaró **último en la cadena** emite primero. Internamente cada `startWith` es un `Flux.concat(prefixValues, sourceInterior)`, por lo que el operador más **externo** (el último en la cadena) envuelve a todos los demás y emite sus valores antes de delegar hacia adentro:

```java
producer1()
    .startWith(1000)        // ③ tercero — más interno, más cerca de la fuente
    .startWith(producer2()) // ② segundo
    .startWith(0)           // ① primero — más externo, el último .startWith() de la cadena
    .subscribe(...)
// Resultado: 0, 51, 52, 53, 1000, 1, 2, 3
//
// ¿Por qué? Cada startWith es concat(prefijo, interior):
//   startWith(0) = concat([0],           startWith(producer2()))
//   startWith(producer2()) = concat([51,52,53], startWith(1000))
//   startWith(1000) = concat([1000],      producer1())
//
// Resolución: 0 → 51,52,53 → 1000 → 1,2,3
```

#### Comportamiento con `take()`

Si el subscriber solicita menos elementos de los que `startWith` puede proveer, **el publisher principal nunca se suscribe**:

```java
producer1()           // emite 1, 2, 3
    .startWith(0)     // antepone 0
    .take(1)          // solo quiero 1 elemento
    .subscribe(...)
// Resultado: 0  → producer1() nunca se invocó
```

Esto es importante en términos de eficiencia: si la caché satisface la demanda, no se hace la llamada costosa.

#### Caso de uso real: Caché + Generación lazy

```java
// NameGenerator tiene una caché (Redis en producción) y genera nombres si faltan
public Flux<String> generateNames() {
    return Flux.fromIterable(redis)           // leer primero de caché
        .startWith(Flux.generate(sink -> {    // si faltan, generar y guardar
            String name = faker.name().fullName();
            redis.add(name);
            sink.next(name);
        }));
}

// Sam quiere 2 nombres: los genera y los guarda en caché
generateNames().take(2).subscribe(Util.subscriber("Sam"));

// Mike quiere 2 nombres: los sirve de caché, sin generar nada
generateNames().take(2).subscribe(Util.subscriber("Mike"));

// Jake quiere 3 nombres: sirve 2 de caché, genera 1 nuevo
generateNames().take(3).subscribe(Util.subscriber("Jake"));
```

#### ¿Cuándo usar `startWith()`?

✅ Cuando querés emitir datos "por defecto" o "previos" antes del flujo principal.  
✅ Cuando querés implementar un patrón cache-aside: servir de caché y generar solo si falta.  
✅ Cuando querés prefijar un valor centinela o encabezado al inicio de un stream.

❌ No usar para combinar dos fuentes de datos del mismo nivel (usar `concat` o `merge`).

---

### Lec03 — `concatWith()` / `Flux.concat()`

#### ¿Qué problema resuelve?

Permite **encadenar publishers en secuencia estricta**: primero se consume todo el primero, y solo cuando completa se suscribe al siguiente. Garantiza orden total.

#### ¿Cómo funciona?

```
subscriber ← [publisher1 completo] → [publisher2 completo] → completo
                   ↑
         NO hay solapamiento: el segundo no arranca hasta que el primero termina
```

> **⚠️ Restricción de tipo:** todos los publishers deben emitir el **mismo tipo `T`**. El resultado es un `Flux<T>`.
> ```java
> Flux<String> a = Flux.just("x", "y");
> Flux<String> b = Flux.just("z");
> a.concatWith(b);                   // ✅ Flux<String> con Flux<String>
>
> Flux<Integer> c = Flux.just(1, 2);
> a.concatWith(c);                   // ❌ no compila: tipos distintos (String vs Integer)
> ```

```java
// Forma 1: encadenamiento con operador de instancia
producer1().concatWith(producer2()).subscribe(Util.subscriber());

// Forma 2: método de fábrica (acepta N publishers)
Flux.concat(producer1(), producer2(), producer3()).subscribe(Util.subscriber());
```

#### Diferencia clave con `startWith()`

| | `startWith()` | `concatWith()` |
|---|---|---|
| Orden | El argumento va **antes** | El argumento va **después** |
| Analogía | "dame esto PRIMERO, luego el publisher" | "dame el publisher PRIMERO, luego esto" |

```java
A.startWith(B)   → B, luego A   (B primero)
A.concatWith(B)  → A, luego B   (A primero)
```

#### Comportamiento con `take()`

Igual que `startWith()`: si el primer publisher satisface la demanda, el segundo **nunca se invoca**:

```java
producer1()            // emite 1, 2, 3
    .concatWith(producer2())  // emite 51, 52, 53 (nunca se llama si take(2))
    .take(2)
    .subscribe(...)
// Resultado: 1, 2 → producer2() nunca se suscribió → sin trabajo innecesario
```

#### ¿Cuándo usar `concatWith()`?

✅ Cuando el orden es crítico: primero los datos del cache, luego los frescos del servicio.  
✅ Cuando tenés dependencia secuencial (el segundo publisher necesita que el primero termine).  
✅ Cuando querés agotar el primer publisher antes de pasar al siguiente.

❌ No usar cuando el orden no importa y querés máxima velocidad (usar `merge()`).  
❌ No usar para combinar datos que necesitan sincronización por posición (usar `zip()`).

---

### Lec04 — `concat()` con errores y `Flux.concatDelayError()`

#### Comportamiento por defecto ante errores

Con `concatWith()` estándar, si cualquier publisher emite un error, **los siguientes publishers no se suscriben** y el error se propaga inmediatamente al subscriber:

```java
producer1()            // emite 1, 2, 3
    .concatWith(Flux.error(new RuntimeException("¡Error!")))
    .concatWith(producer2())  // NUNCA se suscribe
    .subscribe(Util.subscriber());
// Resultado: 1, 2, 3, ERROR → producer2() nunca se invocó
```

#### `Flux.concatDelayError()`: retrasar el error

A veces queremos que el subscriber reciba todos los datos posibles aunque uno de los publishers falle. `concatDelayError()` **guarda el error** y continúa con los siguientes publishers. El error se entrega al final:

```java
Flux.concatDelayError(
    producer1(),                                          // emite 1, 2, 3
    Flux.error(new RuntimeException("¡Error!")),          // falla
    producer2()                                           // emite 51, 52, 53 (SÍ se suscribe)
).subscribe(Util.subscriber());
// Resultado: 1, 2, 3, 51, 52, 53, ERROR (al final)
```

#### ¿Cuándo usar `concatDelayError()`?

✅ Cuando querés maximizar los datos recibidos aunque algún publisher falle.  
✅ Cuando el subscriber puede tolerar errores parciales (registrar la falla y seguir procesando).  
✅ Cuando el error final es suficiente para saber que algo salió mal.

❌ No usar si el error es crítico y no tiene sentido continuar procesando.

---

### Lec05 + Lec06 — `merge()` / `mergeWith()`

#### ¿Qué problema resuelve?

Permite **combinar múltiples publishers suscribiéndose a todos al mismo tiempo**, fusionando sus emisiones a medida que llegan. Los resultados se intercalan (interleaved) en función de cuándo cada publisher emite. No hay orden garantizado.

**Caso típico:** consultar múltiples servicios externos en paralelo y procesar los resultados a medida que llegan.

#### ¿Cómo funciona?

```
publisher1:  1 ──────── 2 ──── 3
publisher2:  ─── 11 ──────── 12
publisher3:  ──── 51 ────── 52 ─── 53

merge():     1 ─ 11 ─ 51 ─ 2 ─ 52 ─ 12 ─ 3 ─ 53
                          ↑
              orden = cuando llegue, sin garantías
```

> **⚠️ Restricción de tipo:** todos los publishers combinados deben emitir el **mismo tipo `T`**. El resultado es un `Flux<T>`.
> ```java
> Flux<String> a = Flux.just("hello");
> Flux<String> b = Flux.just("world");
> Flux.merge(a, b);          // ✅ Flux<String> + Flux<String>
>
> Flux<Integer> c = Flux.just(1, 2);
> Flux.merge(a, c);          // ❌ no compila: tipos distintos (String vs Integer)
> ```
> Si necesitás combinar publishers de tipos distintos, usá `zip()`.

```java
// Forma 1: método de fábrica
Flux.merge(producer1(), producer2(), producer3()).subscribe(Util.subscriber());

// Forma 2: encadenamiento
producer1().mergeWith(producer2()).mergeWith(producer3()).subscribe(Util.subscriber());
```

Ambas formas son equivalentes. El orden entre publishers es **no determinístico** (depende de los schedulers, la velocidad de cada fuente, etc.).

#### Comportamiento con `take()` y cancelación

Si el subscriber cancela (por ejemplo con `take(2)`), **todos los publishers son cancelados simultáneamente**:

```java
Flux.merge(producer1(), producer2(), producer3())
    .take(2)
    .subscribe(Util.subscriber());
// Cuando recibe 2 elementos, cancela a los 3 publishers a la vez
```

#### Caso de uso real: Buscador de vuelos (estilo Kayak)

Imagina un buscador de vuelos que consulta múltiples aerolíneas. No podés llamarlas una por una (demasiado lento). Llamás a todas en paralelo y mostrás resultados a medida que llegan. Si alguna tarda más de 2 segundos, la ignorás:

```java
// Clase Kayak (servicio que agrega resultados de múltiples aerolíneas)
public Flux<Flight> getFlights() {
    return Flux.merge(
        AmericanAirlines.getFlights(),  // 5-10 vuelos, responde en 300-1200ms
        Emirates.getFlights(),           // 1-5 vuelos, responde en 200-1000ms
        Qatar.getFlights()               // 3-5 vuelos, responde en 300-800ms
    )
    .take(Duration.ofSeconds(2));  // esperar máximo 2 segundos
}

// Cliente que consume el servicio
kayak.getFlights().subscribe(Util.subscriber());
```

```
t=0ms:   ← suscripción a las 3 aerolíneas simultáneamente
t=200ms: ← primer vuelo de Emirates llega
t=350ms: ← primeros 3 vuelos de Qatar llegan
t=600ms: ← vuelos de American Airlines empiezan a llegar
t=2000ms: ← timeout: se cancela a las aerolíneas que aún no respondieron
```

El subscriber recibe todos los vuelos disponibles en esos 2 segundos, sin importar de qué aerolínea vengan.

#### Diferencia clave `merge()` vs `concat()`

| | `concat()` / `concatWith()` | `merge()` / `mergeWith()` |
|---|---|---|
| Suscripción | Secuencial (uno a la vez) | Simultánea (todos a la vez) |
| Orden | Garantizado | No garantizado |
| Velocidad | Más lento (suma de tiempos) | Más rápido (máximo de tiempos) |
| Cuándo usar | Cuando el orden importa | Cuando el orden no importa |

#### ¿Cuándo usar `merge()`?

✅ Cuando querés máxima velocidad y el orden de los resultados no importa.  
✅ Cuando tenés múltiples fuentes independientes del mismo tipo de dato.  
✅ Para implementar el patrón "scatter-gather": dispersar solicitudes y recolectar respuestas.  
✅ Cuando querés un timeout global para todas las fuentes combinadas.

❌ No usar cuando el orden de los resultados importa (usar `concat()`).  
❌ No usar cuando los resultados de un publisher dependen de otro (usar `flatMap()` encadenado).

---

### Lec07 + Lec08 — `zip()`

#### ¿Qué problema resuelve?

Imagina una **cadena de montaje de automóviles**. Para construir un coche necesitás tres partes: la carrocería, el motor y los neumáticos. Tenés tres proveedores distintos que producen cada parte de forma independiente y a velocidades diferentes:

- Proveedor A (carrocería): produce una carrocería cada 100 ms → emite 5 carrocerías
- Proveedor B (motor): produce un motor cada 200 ms → emite solo 3 motores
- Proveedor C (neumáticos): produce neumáticos cada 75 ms → emite 10 neumáticos

El subscriber quiere coches completos. Ningún proveedor individual puede dárselos. Aquí es donde `zip()` actúa como la línea de ensamblaje: **toma una parte de cada proveedor y las combina para formar un coche completo**.

`zip()` permite **combinar elementos de múltiples publishers por posición**, formando tuplas. El resultado N-ésimo es la combinación del N-ésimo elemento de cada publisher. Todos los publishers se suscriben en paralelo, pero la emisión espera a que **todos hayan emitido su elemento número N**.

#### Regla fundamental: "todo o nada"

Esta es la regla más importante de `zip()`:

- **Para construir la tupla N**, todos los publishers deben haber emitido su elemento N-ésimo.
- Si un publisher produce más rápido que los demás, **sus elementos extra esperan** en un buffer interno.
- Cuando el publisher **más corto se completa**, `zip()` se completa también. Los elementos extra de los publishers más largos se **descartan**.

Con los datos del ejemplo anterior (5 carrocerías, 3 motores, 10 neumáticos):
- Solo se pueden construir **3 coches** (limitado por los 3 motores).
- 2 carrocerías y 7 neumáticos quedan sin usar.

Si un publisher emite **cero elementos** (señal `empty`), `zip()` completa inmediatamente y el subscriber recibe una señal vacía: **no hay coche**.

```
getBody():   [body-1]  [body-2]  [body-3]  [body-4]  [body-5]
getEngine(): [engine-1]          [engine-2]          [engine-3]  (lento, 200ms)
getTires():  [tires-1] [tires-2] [tires-3] [tires-4] ... [tires-10]

zip():  t=200ms → (body-1, engine-1, tires-1)  ← tupla 1 (espera al motor)
        t=400ms → (body-2, engine-2, tires-2)  ← tupla 2
        t=600ms → (body-3, engine-3, tires-3)  ← tupla 3 (engine se agota → FIN)
                    body-4, body-5, tires-4..10 se descartan
```

#### ¿Cómo usarlo?

```java
// Simulación de la cadena de montaje de coches
Flux<String> getBody()   { return Flux.range(1, 5).map(i -> "body-"   + i).delayElements(Duration.ofMillis(100)); }
Flux<String> getEngine() { return Flux.range(1, 3).map(i -> "engine-" + i).delayElements(Duration.ofMillis(200)); }
Flux<String> getTires()  { return Flux.range(1, 10).map(i -> "tires-" + i).delayElements(Duration.ofMillis(75));  }

// Combinar los tres publishers: emite Tuple3<String, String, String>
Flux.zip(getBody(), getEngine(), getTires())
    .map(tuple -> new Car(tuple.getT1(), tuple.getT2(), tuple.getT3()))
    .subscribe(Util.subscriber());

// Output:
// Car{body=body-1, engine=engine-1, tires=tires-1}
// Car{body=body-2, engine=engine-2, tires=tires-2}
// Car{body=body-3, engine=engine-3, tires=tires-3}   ← solo 3 coches (limitado por motores)
```

> **✅ Tipos distintos permitidos:** a diferencia de `concat()`, `merge()` o `startWith()`, `zip()` acepta publishers de **tipos completamente distintos**. Cada publisher puede emitir un tipo diferente y el resultado es una `TupleN<T1, T2, ..., TN>` con tipado estático por posición:
> ```java
> Flux<String>     nombres  = Flux.just("Ana", "Bob");
> Flux<Integer>    edades   = Flux.just(30, 25);
> Flux<BigDecimal> sueldos  = Flux.just(new BigDecimal("3000"), new BigDecimal("2500"));
>
> Flux.zip(nombres, edades, sueldos)
>     .map(t -> t.getT1() + " tiene " + t.getT2() + " años y gana " + t.getT3())
>     .subscribe(Util.subscriber());
> // "Ana tiene 30 años y gana 3000"
> // "Bob tiene 25 años y gana 2500"
> ```
> El objeto `TupleN` tiene métodos `getT1()`, `getT2()`, ..., `getT8()` para acceder a cada elemento por posición con su tipo correcto.

#### Caso de uso real: Agregar información de múltiples microservicios con `Mono.zip()`

Un caso muy común en microservicios es el siguiente: el frontend pide `/product/1` y el backend necesita hacer **tres llamadas independientes en paralelo** para armar la respuesta:

```java
// El frontend quiere: nombre + precio + reseña del producto con ID dado
// Tres microservicios independientes, cada uno tarda hasta 1 segundo
public Mono<ProductInfo> getProduct(int productId) {
    return Mono.zip(
        getProductName(productId),    // Mono<String>  → llama a /demo05/product/{id}
        getProductPrice(productId),   // Mono<String>  → llama a /demo05/price/{id}
        getProductReview(productId)   // Mono<String>  → llama a /demo05/review/{id}
    ).map(tuple -> new ProductInfo(
        tuple.getT1(),   // nombre
        tuple.getT2(),   // precio
        tuple.getT3()    // reseña
    ));
}

// Obtener los 10 productos en paralelo
Flux.range(1, 10)
    .flatMap(client::getProduct)   // flatMap para hacer las llamadas en paralelo
    .subscribe(Util.subscriber());
```

Con `Mono.zip()`, las **tres llamadas se lanzan simultáneamente** y el `Mono<ProductInfo>` se emite cuando las tres completan (en ~1 segundo, en vez de ~3 segundos secuenciales). Si **cualquiera** devuelve vacío o error, el `Mono` completa vacío o con error.

#### `Flux.zip()` vs `Mono.zip()`

| | `Flux.zip()` | `Mono.zip()` |
|---|---|---|
| Cuándo usar | Múltiples elementos por publisher (cadena de montaje) | Cada publisher emite un único valor (ensamblar un objeto) |
| Cantidad de resultados | N coches (limitado por el publisher más corto) | 1 objeto ensamblado |
| Caso típico | Combinar streams de datos | Llamadas paralelas a microservicios |

#### Diferencia clave `zip()` vs `merge()`

| | `merge()` | `zip()` |
|---|---|---|
| Resultado | Elementos **sueltos** de todos los publishers | **Tuplas** combinadas por posición |
| Espera | No espera — emite cuando llega cualquier elemento | Espera a que **todos** emitan el elemento N |
| Tipos de datos | Todos deben ser el mismo tipo | Cada publisher puede tener un tipo distinto |
| Cuándo termina | Cuando **todos** los publishers completan | Cuando el **más corto** completa |
| Caso típico | Agregar vuelos de múltiples aerolíneas | Construir un objeto con partes de distintos servicios |

#### ¿Cuándo usar `zip()`?

✅ Cuando necesitás **combinar por posición**: el elemento 1 de A con el elemento 1 de B.  
✅ Para **construir un objeto compuesto** a partir de respuestas paralelas de múltiples servicios (patrón "API aggregator").  
✅ Cuando los publishers producen datos de **tipos distintos** que deben combinarse (nombre, precio, stock).  
✅ Cuando la concurrencia máxima es importante: todas las llamadas van en paralelo.

❌ No usar cuando el **orden posicional no tiene sentido** y solo querés recibir todo lo que llegue (usar `merge()`).  
❌ No usar cuando los publishers tienen **cantidades muy distintas** de elementos: los extras se descartan silenciosamente.  
❌ No usar cuando **un resultado depende del anterior** (usar `flatMap()` encadenado).  
❌ No usar si un publisher puede emitir vacío y querés seguir recibiendo datos de los demás (usar `merge()`).

---

### Lec09 — `Mono.flatMap()`

> **✅ Tipos distintos permitidos:** `flatMap()` transforma un `Mono<A>` en un `Mono<B>`, donde `A` y `B` pueden ser completamente distintos. El tipo de entrada y el tipo de salida no tienen que coincidir:
> ```java
> Mono<Integer> idMono = Mono.just(1);           // Mono<Integer>
> idMono.flatMap(id -> getUserName(id))          // Mono<Integer> → Mono<String>
>       .subscribe(Util.subscriber());           // el subscriber recibe String
> ```

#### ¿Qué problema resuelve?

Considera este escenario: tenés el **nombre de usuario** ("sam") y necesitás saber su **saldo de cuenta**. Para eso, primero tenés que llamar al `UserService` para obtener el ID de usuario, y luego con ese ID llamar al `PaymentService`. Son dos llamadas **dependientes**: la segunda necesita el resultado de la primera.

El problema es que **`map()` no sirve** para este patrón porque las llamadas a los servicios devuelven `Mono<T>`, no valores directos:

```java
// ❌ PROBLEMA: usar map() cuando la transformación devuelve un Mono
UserService.getUserId("sam")              // Mono<Integer>
    .map(id -> PaymentService.getBalance(id))  // devuelve Mono<BigDecimal>
    .subscribe(Util.subscriber());

// El tipo resultante es Mono<Mono<BigDecimal>> → el subscriber recibe el Mono interno, ¡no el saldo!
// Es como recibir la "promesa del saldo" en vez del saldo mismo.
```

El subscriber recibiría un `Mono<BigDecimal>` (un publisher) en vez del valor numérico del saldo. Alguien tiene que **suscribirse** a ese Mono interno. Ese es exactamente el trabajo de `flatMap()`:

```
map()     →  Mono<A>  →  f(a → Mono<B>)  →  Mono<Mono<B>>   ← ¡inútil!
flatMap() →  Mono<A>  →  f(a → Mono<B>)  →  Mono<B>          ← correcto
```

`flatMap()` se **suscribe automáticamente** al publisher interno, extrae su valor y lo emite como si fuera el resultado final. Así "aplana" el anidamiento.

#### ¿Cómo usarlo?

```
getUserId("sam")   →   flatMap(id → getBalance(id))   →   Mono<BigDecimal>
       ↑                          ↑
   llamada 1                 llamada 2 (usa el id del resultado anterior)
```

```java
// ✅ CORRECTO: usar flatMap() para encadenar llamadas asíncronas dependientes
UserService.getUserId("sam")                    // Mono<Integer>  → llama al servicio, retorna id=1
    .flatMap(PaymentService::getUserBalance)    // Integer → Mono<BigDecimal> → retorna 100.0
    .subscribe(Util.subscriber());
// Output: sub1 received: 100.0
//         sub1 received complete!
```

#### Cuándo `map()` sí funciona

`map()` es apropiado cuando la transformación es **una operación en memoria** que retorna un valor directo (no un Publisher):

```java
// ✅ map() está bien aquí: la transformación es en memoria, no async
UserService.getUserId("sam")
    .map(id -> "Hola, tu ID es: " + id)   // String::concat es en memoria, retorna String
    .subscribe(Util.subscriber());
// Output: sub1 received: Hola, tu ID es: 1
```

La regla es simple:
- Si la función devuelve un **valor simple** (`String`, `Integer`, etc.) → usá `map()`
- Si la función devuelve un **`Mono<T>`** (otra llamada async) → usá `flatMap()`

#### Diferencia conceptual `map()` vs `flatMap()`

| | `map()` | `flatMap()` |
|---|---|---|
| Tipo de transformación | En memoria (síncrona) | Asíncrona (retorna otro Mono) |
| Resultado con f: A → B | `Mono<B>` ✅ | — |
| Resultado con f: A → Mono\<B\> | `Mono<Mono<B>>` ❌ | `Mono<B>` ✅ |
| Concurrencia | No aplica | El Publisher interno se ejecuta en su propio scheduler |
| Caso de uso | Transformar el valor (parsing, mapeo) | Llamadas a DB, HTTP, servicios externos |

#### ¿Cuándo usar `Mono.flatMap()`?

✅ Para llamadas async **secuenciales dependientes**: el resultado de A es el input de B.  
✅ Para evitar el anidamiento de Monos (`Mono<Mono<T>>`).  
✅ Para encadenar: autenticación → obtener usuario → obtener permisos → obtener datos.

❌ No usar cuando la segunda llamada **no depende** del resultado de la primera → usar `zip()` (las hace en paralelo, es más rápido).  
❌ No usar cuando necesitás múltiples resultados del Publisher interno → usar `flatMapMany()`.

---

### Lec10 — `Mono.flatMapMany()`

> **✅ Tipos distintos permitidos:** `flatMapMany()` transforma un `Mono<A>` en un `Flux<B>`, donde `A` y `B` pueden ser completamente distintos:
> ```java
> Mono<Integer> idMono = Mono.just(1);           // Mono<Integer>
> idMono.flatMapMany(id -> getOrders(id))        // Mono<Integer> → Flux<Order>
>       .subscribe(Util.subscriber());           // el subscriber recibe múltiples Order
> ```

#### ¿Qué problema resuelve?

Extiende el patrón de `flatMap()` para el caso en que la llamada encadenada devuelve **múltiples elementos** (un `Flux`) en lugar de uno solo (un `Mono`).

Considerá este escenario: tenés el nombre de usuario ("sam") y querés obtener **todos sus pedidos**. Primero debés llamar al `UserService` para obtener el ID, y luego con ese ID llamar al `OrderService` que devuelve un `Flux<Order>` (puede ser 0, 1, o muchos pedidos).

El problema: `Mono.flatMap()` asume que el publisher interno es también un `Mono` (un solo resultado). Si intentás usarlo con un `Flux`, obtenés `Mono<Flux<Order>>` en lugar de `Flux<Order>`:

```java
// ❌ PROBLEMA: flatMap asume que el publisher interno es Mono
UserService.getUserId("sam")              // Mono<Integer>
    .map(id -> OrderService.getOrders(id))  // Integer → Flux<Order>
    // El tipo es ahora Mono<Flux<Order>> → el subscriber recibe el Flux, no las órdenes
```

`flatMapMany()` resuelve esto: se suscribe al `Flux` interno y **aplana todos sus elementos** en el stream resultante:

```
Mono<Integer>  →  flatMapMany(id → Flux<Order>)  →  Flux<Order>
    userId=1              getUserOrders(1)          [order-A, order-B, order-C]
```

#### ¿Cómo usarlo?

```java
// Escenario: dado el nombre de usuario, obtener todas sus órdenes
UserService.getUserId("sam")               // Mono<Integer> → id=1
    .flatMapMany(OrderService::getOrders)  // Integer → Flux<Order> → [order-A, order-B]
    .subscribe(Util.subscriber());

// Output:
// sub1 received: Order{userId=1, product='Laptop', price=1200}
// sub1 received: Order{userId=1, product='Mouse', price=25}
// sub1 received complete!
```

```java
// Para el usuario "jake" (que no tiene pedidos):
UserService.getUserId("jake")
    .flatMapMany(OrderService::getOrders)  // getOrders(3) → Flux vacío
    .subscribe(Util.subscriber());
// Output: sub1 received complete!  ← Flux vacío → solo señal de completado
```

#### Diferencia `flatMap()` vs `flatMapMany()` en Mono

| | `Mono.flatMap()` | `Mono.flatMapMany()` |
|---|---|---|
| Publisher interno esperado | `Mono<R>` (un resultado) | `Flux<R>` (0 o más resultados) |
| Tipo resultante | `Mono<R>` | `Flux<R>` |
| Cuándo usar | La llamada interna devuelve **un** valor | La llamada interna devuelve **una lista** de valores |
| Caso típico | `getUserId() → getBalance()` | `getUserId() → getOrders()` |

#### ¿Cuándo usar `Mono.flatMapMany()`?

✅ Para el patrón "buscar por ID → obtener lista": `getUserId().flatMapMany(id -> getOrders(id))`.  
✅ Cuando tenés un único punto de entrada (`Mono`) pero la respuesta puede ser una colección.  
✅ Cuando el `Flux` interno puede ser vacío (lo maneja correctamente, emite solo `onComplete`).

❌ No usar si el Publisher interno devuelve un `Mono` → usar `flatMap()`.  
❌ Si tenés directamente un `Flux<T>` de entrada (no un `Mono`), usar `Flux.flatMap()` directamente.

---

### Lec11 + Lec12 — `Flux.flatMap()`

> **✅ Tipos distintos permitidos:** `Flux.flatMap()` transforma cada elemento de tipo `A` en un `Publisher<B>`, donde `B` puede ser distinto de `A`. El resultado es un `Flux<B>`:
> ```java
> Flux<Integer> userIds = Flux.just(1, 2, 3);   // Flux<Integer>
> userIds.flatMap(id -> getOrders(id))           // Integer → Flux<Order>
>        .subscribe(Util.subscriber());          // el subscriber recibe Order (no Integer)
>
> // También válido: Integer → Mono<String>
> userIds.flatMap(id -> getUserName(id))         // Flux<Integer> → Flux<String>
>        .subscribe(Util.subscriber());
> ```

#### ¿Qué problema resuelve?

Considerá este requisito: *obtener todos los pedidos de todos los usuarios*. El `OrderService` no tiene un endpoint que devuelva todos los pedidos de todos los usuarios a la vez. Solo tiene `getOrders(userId)` → un endpoint que requiere el ID de usuario.

Entonces la lógica es:
1. Llamar a `UserService.getAllUsers()` → obtener un `Flux<User>` con todos los usuarios.
2. Para **cada** usuario, llamar a `OrderService.getOrders(userId)` → obtener sus pedidos.
3. Combinar todos los resultados en un único stream.

El enfoque ingenuo sería secuencial: procesar usuario 1 (esperar respuesta), luego usuario 2 (esperar), etc. Pero si tenemos 100 usuarios y cada llamada tarda 500ms, esto tomaría **50 segundos**.

`Flux.flatMap()` resuelve esto lanzando las llamadas **en paralelo**: para cada elemento del `Flux` de entrada, lanza su Publisher interno **sin esperar** a que los anteriores terminen. Los resultados se emiten a medida que llegan (interleaved).

#### ¿Cómo funciona internamente?

El comportamiento de `flatMap()` es muy similar a `merge()`: cuando llega el elemento N del Flux externo, inmediatamente **crea el Publisher interno y se suscribe a él**, sin esperar que el Publisher del elemento N-1 haya completado. Así, múltiples Publishers internos están activos simultáneamente.

```
Flux<User>:  [user-1]    [user-2]    [user-3]
                ↓             ↓            ↓
flatMap:   getOrders(1)  getOrders(2)  getOrders(3)   ← suscripción inmediata a los 3
               ↓              ↓              ↓
           (500ms)         (500ms)        (500ms)
               ↓              ↓
resultado: [o1a] [o2a] [o1b] [o3a] [o2b] [o3b] [o3c]  ← interleaved, sin orden garantizado
           ↑
           el más rápido llega primero
```

Con el enfoque secuencial (sin `flatMap`): ~1500ms  
Con `flatMap` en paralelo: ~500ms (el máximo de los tres)

```java
// Escenario: obtener todos los pedidos de todos los usuarios en paralelo
UserService.getAllUsers()            // Flux<User>  → [user-1, user-2, user-3]
    .map(User::id)                  // Flux<Integer> → [1, 2, 3]
    .flatMap(OrderService::getOrders) // Integer → Flux<Order>, todos en paralelo
    .subscribe(Util.subscriber());

// Output (orden NO garantizado, el más rápido llega primero):
// sub1 received: Order{userId=2, product='Keyboard', price=80}
// sub1 received: Order{userId=1, product='Laptop', price=1200}
// sub1 received: Order{userId=2, product='Mouse', price=25}
// sub1 received: Order{userId=1, product='Monitor', price=300}
// sub1 received complete!
```

#### Control de concurrencia

Por defecto, `flatMap(mapper)` sin argumento de concurrencia usa **256 publishers internos simultáneos**. Este valor proviene de la constante `Queues.SMALL_BUFFER_SIZE` de Reactor (verificado en el bytecode de `Flux.java`), configurable con la propiedad de sistema `reactor.bufferSize.small`. Si tenés 1000 usuarios, Reactor irá abriendo publishers internos de a batches de hasta 256 a la vez.

Podés controlar la concurrencia con el parámetro `maxConcurrency`:

```java
// Máximo 3 llamadas concurrentes al mismo tiempo
UserService.getAllUsers()
    .map(User::id)
    .flatMap(id -> OrderService.getOrders(id), 3)  // ← concurrencia máxima = 3
    .subscribe(Util.subscriber());

// Con concurrencia = 1: equivale a concatMap() (secuencial)
.flatMap(id -> getOrders(id), 1)  // suscribe 1 publisher interno a la vez → en orden
```

Cuidado con valores muy altos: si cada Publisher interno abre una conexión TCP/HTTP, un `maxConcurrency` de 5000 podría agotar los file descriptors del sistema operativo.

#### `Flux.flatMap()` vs `Mono.flatMapMany()`

Ambos aplanan un Publisher interno, pero tienen puntos de entrada distintos:

| | `Flux.flatMap()` | `Mono.flatMapMany()` |
|---|---|---|
| Fuente | `Flux<T>` (muchos elementos) | `Mono<T>` (un elemento) |
| Para cada elemento | Lanza un Publisher interno | Lanza UN Publisher |
| Resultado | Todos los Publishers internos mergeados | El único Flux interno aplanado |
| Caso típico | Lista de usuarios → pedidos de cada uno | ID único → lista de pedidos |

#### ¿Cuándo usar `Flux.flatMap()`?

✅ Para llamadas I/O **independientes en paralelo** sobre una colección: no hay dependencia entre los Publishers internos.  
✅ Cuando el orden de los resultados **no importa**: querés máxima velocidad.  
✅ Para maximizar el throughput: procesar N elementos llamando a servicios externos concurrentemente.  
✅ Cuando necesitás controlar la concurrencia con `maxConcurrency`.

❌ No usar cuando el **orden de los resultados es crítico** → usar `concatMap()`.  
❌ No usar para llamadas donde el resultado de una depende de la anterior → usar `Mono.flatMap()` encadenado.  
❌ Tener cuidado con un `maxConcurrency` muy alto en sistemas con límites de conexiones TCP.

---

### Lec13 — `concatMap()`

> **✅ Tipos distintos permitidos:** igual que `flatMap()`, `concatMap()` puede transformar elementos de tipo `A` en un `Publisher<B>` donde `B` puede ser diferente de `A`. La restricción de `concatMap()` es de **orden**, no de tipo:
> ```java
> Flux<Integer> pageIds = Flux.just(1, 2, 3);   // Flux<Integer>
> pageIds.concatMap(page -> fetchPage(page))     // Integer → Flux<String> (resultados de la página)
>        .subscribe(Util.subscriber());          // el subscriber recibe String, en orden de página
> ```

#### ¿Qué problema resuelve?

`flatMap()` es muy potente, pero su naturaleza paralela produce resultados en un orden imprevisible. Hay escenarios donde **el orden es crítico**:

- Procesar transacciones financieras en secuencia estricta (primero el débito, luego el crédito).
- Obtener páginas de resultados de una API paginada (página 1, luego página 2, luego página 3).
- Ejecutar pasos de un workflow donde cada paso depende del estado dejado por el anterior.

`concatMap()` hace exactamente lo mismo que `flatMap()` (para cada elemento lanza un Publisher interno y aplana el resultado), pero con una diferencia fundamental: **espera a que el Publisher interno actual complete antes de suscribirse al siguiente**. Esto garantiza que los resultados lleguen siempre en el mismo orden que los elementos del Flux de entrada.

#### ¿Cómo funciona?

La diferencia con `flatMap()` es análoga a la diferencia entre `merge()` y `concat()`:

```
flatMap() (paralelo):
  [user-1]  →  getOrders(1) ─────────────────────► [o1a, o1b]
  [user-2]  →  getOrders(2) ──────────────────► [o2a, o2b, o2c]
  [user-3]  →  getOrders(3) ────────────────────────► [o3a]
  resultado:  [o2a] [o3a] [o1a] [o2b] [o1b] [o2c]  ← interleaved, desordenado

concatMap() (secuencial):
  [user-1]  →  getOrders(1) ─► [o1a, o1b] ─► COMPLETO
                                               ↓ solo ahora empieza el siguiente
  [user-2]                               ─► getOrders(2) ─► [o2a, o2b, o2c] ─► COMPLETO
                                                                                  ↓
  [user-3]                                                                   ─► getOrders(3) ─► [o3a]
  resultado:  [o1a] [o1b]  [o2a] [o2b] [o2c]  [o3a]  ← orden respetado, pero más lento
```

```java
// Ejemplo: obtener productos en orden secuencial (1 a 10)
// Cada llamada tarda ~1 segundo → total ~10 segundos
Flux.range(1, 10)
    .concatMap(client::getProduct)  // secuencial: espera cada respuesta antes de la siguiente
    .subscribe(Util.subscriber());

// Output (siempre en orden):
// sub1 received: ProductInfo{id=1, name='Laptop', price='1200'}
// sub1 received: ProductInfo{id=2, name='Mouse', price='25'}
// sub1 received: ProductInfo{id=3, name='Keyboard', price='80'}
// ...
// sub1 received complete!   ← ~10 segundos
```

```java
// Mismo ejemplo con flatMap: todos en paralelo
Flux.range(1, 10)
    .flatMap(client::getProduct)   // paralelo: todas las llamadas van al mismo tiempo
    .subscribe(Util.subscriber());

// Output (orden no garantizado):
// sub1 received: ProductInfo{id=7, ...}  ← llegó primero (quizás fue más rápido)
// sub1 received: ProductInfo{id=3, ...}
// sub1 received: ProductInfo{id=1, ...}
// ...
// sub1 received complete!   ← ~1-2 segundos (el tiempo del más lento)
```

#### Comparación detallada `flatMap()` vs `concatMap()`

| Característica | `flatMap()` | `concatMap()` |
|---|---|---|
| Estrategia | **Paralelo** — todos los Publishers internos a la vez | **Secuencial** — 1 Publisher interno a la vez |
| Orden de resultados | ❌ No garantizado (el más rápido llega primero) | ✅ Garantizado (mismo orden que el source) |
| Velocidad en I/O | ⚡ Rápido: tiempo ≈ max(t1, t2, t3) | 🐢 Lento: tiempo ≈ t1 + t2 + t3 |
| Parámetro de concurrencia | ✅ `maxConcurrency` controlable | ❌ Siempre 1 (equivale a `maxConcurrency = 1`) |
| Caso típico | Búsqueda paralela de pedidos de N usuarios | Procesar transacciones en orden |
| Interno | Similar a `merge()` de los Publishers internos | Similar a `concat()` de los Publishers internos |

#### ¿Cuándo usar `concatMap()`?

✅ Cuando el **orden de los resultados debe respetarse estrictamente** (transacciones, auditoría, workflows).  
✅ Para **paginación API**: page 1 → page 2 → page 3 (siempre en orden).  
✅ Cuando hay **dependencia implícita de orden**: el servidor procesa solicitudes en secuencia y el estado de una afecta la siguiente.  
✅ Cuando no querés gestionar complejidad de concurrencia y el volumen es bajo.

❌ No usar si el orden no importa y el volumen es grande: perdés todo el beneficio de la programación reactiva paralela.  
❌ No usar si las llamadas son completamente independientes: `flatMap()` puede ser 10x-100x más rápido.

---

### Lec14 — `collectList()`

#### ¿Qué problema resuelve?

La programación reactiva procesa elementos **uno a uno** a medida que llegan. Pero hay situaciones donde necesitás la **colección completa** antes de poder continuar:

- Ordenar los resultados (no podés ordenar sin tener todos los elementos).
- Pasar los datos a una API legacy que solo acepta `List<T>`, no streams.
- Combinar todos los resultados en un único objeto de respuesta para el frontend.
- Hacer operaciones de "batch" (insertar todos de una vez, no uno por uno).

`collectList()` convierte un `Flux<T>` en un `Mono<List<T>>`: acumula todos los elementos en memoria y los emite como una única lista cuando el `Flux` completa.

#### ¿Cómo funciona?

```
Flux:  [1] [2] [3] [4] ... [10] [onComplete]
                                      ↓
                              (recibe onComplete)
                                      ↓
collectList: emite la lista acumulada:  [[1, 2, 3, 4, ..., 10]]
                                      ↓
Mono:  [[1, 2, 3, 4, ..., 10]]   ← una ÚNICA emisión, luego onComplete
```

```java
// Básico: recolectar números en una lista
Flux.range(1, 10)
    .collectList()                    // Flux<Integer> → Mono<List<Integer>>
    .subscribe(Util.subscriber());
// Output: sub1 received: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
//         sub1 received complete!
```

```java
// Caso de uso: obtener todos los pedidos de un usuario y devolverlos como lista
UserService.getUserId("sam")
    .flatMapMany(OrderService::getOrders)   // Flux<Order>
    .collectList()                           // Mono<List<Order>>
    .subscribe(Util.subscriber());
// Output: sub1 received: [Order{...}, Order{...}]
```

#### ¿Es `collectList()` una operación bloqueante?

**No.** Es completamente no-bloqueante. `collectList()` no espera activamente: simplemente registra cada elemento que llega vía `onNext` en una lista interna, y cuando recibe la señal `onComplete`, emite esa lista al subscriber. No bloquea ningún hilo.

#### Comportamiento ante errores

Si el `Flux` emite un error **antes de completar**, `collectList()` **descarta todos los elementos acumulados** y propaga el error:

```java
Flux.range(1, 5)
    .concatWith(Flux.error(new RuntimeException("¡Error en el medio!")))
    .concatWith(Flux.range(6, 5))    // nunca se ejecuta
    .collectList()
    .subscribe(Util.subscriber());

// Output: sub1 received error: ¡Error en el medio!
// ← NO se recibe la lista [1, 2, 3, 4, 5]. El error cancela la acumulación.
```

Esto es importante: `collectList()` es "todo o nada". Si el stream falla, perdés todos los datos acumulados hasta ese punto.

#### ¿Cuándo usar `collectList()`?

✅ Cuando necesitás la **colección completa** para procesarla de una vez (ordenar, agrupar, transformar en batch).  
✅ Para pasar el resultado a una **API que acepta `List<T>`** en lugar de streams reactivos.  
✅ Para construir una **respuesta JSON** completa a partir de un stream de elementos.  
✅ Para combinar con `zip()` y ensamblar objetos complejos con listas.

❌ **Nunca usar con flujos infinitos**: `collectList()` espera el `onComplete` que nunca llegará → la lista crece indefinidamente hasta `OutOfMemoryError`.  
❌ No usar si podés procesar elemento a elemento con `map()` o `flatMap()`: evitás acumular todo en memoria y el primer resultado llega antes.  
❌ Tener cuidado con flujos de millones de elementos: todo se acumula en el heap de la JVM.

---

### Lec15 — `then()`

#### ¿Qué problema resuelve?

Considerá este escenario: estás insertando un batch de registros en la base de datos. El driver de la base de datos te va notificando el resultado de cada inserción individual vía `onNext` ("registro A guardado", "registro B guardado"...). Pero vos no querés procesar esas notificaciones intermedias: solo te interesa saber **si todo el proceso completó con éxito o si hubo algún error**.

`then()` descarta todos los valores emitidos por un `Flux` y convierte el stream en un `Mono<Void>` que:
- Se completa **exitosamente** cuando el Flux emite `onComplete`.
- Propaga el **error** cuando el Flux emite `onError`.

También permite **encadenar otra acción** que se ejecutará solo cuando el Flux anterior haya completado con éxito.

#### El problema del enfoque incorrecto

Una trampa común es intentar encadenar operaciones usando `.subscribe()` directamente sobre cada etapa:

```java
// ❌ INCORRECTO: las dos operaciones corren en paralelo, no en secuencia
saveAllRecords(records)          // Flux<String>
    .subscribe(Util.subscriber()); // suscripción #1

sendNotification()               // Mono<Void>
    .subscribe(Util.subscriber()); // suscripción #2 → se ejecuta INMEDIATAMENTE, en paralelo
```

Con este enfoque, `sendNotification()` se ejecuta **al mismo tiempo** que `saveAllRecords()`, sin esperar a que termine. En la programación reactiva, ambas son fuentes no-bloqueantes que corren concurrentemente. El log mostraría la notificación antes de que los registros estén guardados.

#### Formas de uso correctas

```java
// Forma 1: then() sin argumento → Mono<Void> — solo me importa si completó o falló
saveAllRecords(records)   // Flux<String>  emite: "A guardado", "B guardado", "C guardado"
    .then()               // descarta "A guardado", "B guardado", etc. → Mono<Void>
    .subscribe(
        unused -> {},
        error  -> log.error("Error al guardar: {}", error.getMessage()),
        ()     -> log.info("Todos los registros guardados exitosamente")
    );
```

```java
// Forma 2: then(Mono<V>) — cuando el Flux termina, ejecutar otro Mono y emitir su valor
saveAllRecords(records)                          // Flux<String>
    .then(sendNotification())                    // ✅ sendNotification() se ejecuta SOLO si saveAllRecords completa
    .subscribe(Util.subscriber());

// Log:
// [INFO] sub1 received: Records saved successfully. Notification sent.
// [INFO] sub1 received complete!
// ← sendNotification() NUNCA se ejecuta si saveAllRecords falla con error
```

```java
// Forma 3: encadenamiento de múltiples operaciones secuenciales
deleteOldData()          // Mono<Void> — paso 1
    .then(loadNewData()) // Mono<Void> — paso 2 (solo si paso 1 completó)
    .then(sendNotification()) // Mono<Void> — paso 3 (solo si paso 2 completó)
    .subscribe(Util.subscriber());

// Si cualquier paso falla, los siguientes NO se ejecutan → el error se propaga al subscriber
```

#### ¿Cómo funciona internamente?

`then()` es equivalente a `ignoreElements()` seguido de `thenEmpty()`. Reactor se suscribe al Flux original, ignora todos los valores de `onNext`, y cuando recibe `onComplete` (o `onError`), lo propaga. El Mono argumento (si se pasa) se activa solo en caso de éxito, similar a `flatMap()` pero ignorando el valor:

```
Flux:  [A] [B] [C] [onComplete]
         ↓   ↓   ↓       ↓
       (ignorado) (ignorado)
                          ↓
         then(sendNotification()) ← se activa ahora, NO antes
                          ↓
Mono:  ["Notification sent"] [onComplete]
```

#### Diferencia `then()` vs `flatMap()`

| | `flatMap()` | `then()` |
|---|---|---|
| ¿Usa el valor emitido? | ✅ Sí — el valor de `onNext` es el input del siguiente paso | ❌ No — ignora todos los `onNext` |
| Resultado | `Mono<R>` o `Flux<R>` con el resultado del Publisher interno | `Mono<Void>` o `Mono<V>` del Mono argumento |
| Caso típico | `getUserId().flatMap(id -> getBalance(id))` | `saveAll().then(notify())` |
| ¿Necesito el valor intermedio? | Sí | No, solo necesito que completó |

#### ¿Cuándo usar `then()`?

✅ Para encadenar operaciones donde el resultado intermedio **no importa**: solo importa que completó con éxito.  
✅ Para el patrón **"guardar → notificar"**: insert batch → send email.  
✅ Para workflows secuenciales de limpieza/actualización: delete → load → refresh.  
✅ Para convertir un `Flux<T>` en `Mono<Void>` cuando el caller solo necesita saber si falló o tuvo éxito.

❌ No usar si necesitás el **valor emitido** por el Flux original para la siguiente operación → usar `flatMap()`.  
❌ Tener cuidado: si el Flux falla, `then()` **no ejecuta el Mono argumento** y propaga el error directamente al subscriber.

---

## Comparación completa de operadores

| Operador | ¿Suscripción? | Orden | Restricción de tipo | Resultado | Problema que resuelve |
|---|---|---|---|---|---|
| `startWith()` | Secuencial (primero el argumento) | Garantizado | **Mismo tipo `T`** | `Flux<T>` | Prefijar datos (caché) antes del publisher |
| `concatWith()` | Secuencial (primero el publisher) | Garantizado | **Mismo tipo `T`** | `Flux<T>` | Encadenar publishers en orden estricto |
| `concatDelayError()` | Secuencial | Garantizado | **Mismo tipo `T`** | `Flux<T>` | Encadenar tolerando errores parciales |
| `merge()` / `mergeWith()` | **Paralela** (todos a la vez) | No garantizado | **Mismo tipo `T`** | `Flux<T>` | Máxima velocidad: combinar fuentes independientes |
| `zip()` | **Paralela** | Por posición | **Tipos distintos OK** ✅ | `Flux<TupleN<T1,T2,...>>` | Combinar elementos posicionalmente de múltiples fuentes |
| `Mono.flatMap()` | Secuencial | — | **Tipos distintos OK** ✅ | `Mono<R>` | Encadenar llamadas async dependientes |
| `Mono.flatMapMany()` | Secuencial | — | **Tipos distintos OK** ✅ | `Flux<R>` | Mono que abre un Flux de resultados |
| `Flux.flatMap()` | **Paralela** (por elemento) | No garantizado | **Tipos distintos OK** ✅ | `Flux<R>` | Llamadas I/O paralelas por elemento |
| `Flux.concatMap()` | Secuencial (por elemento) | Garantizado | **Tipos distintos OK** ✅ | `Flux<R>` | Llamadas I/O secuenciales con orden preservado |
| `collectList()` | — | Preservado | Mismo tipo `T` | `Mono<List<T>>` | Acumular todo el Flux en una lista |
| `then()` | — | — | Ignora el tipo | `Mono<Void>` | Encadenar acciones post-completado |

> **Regla práctica:**
> - Si el operador **mezcla/fusiona** dos o más streams del mismo nivel → el tipo debe ser el **mismo** (`startWith`, `concat`, `merge`).
> - Si el operador **transforma** un elemento en un Publisher interno → puede cambiar de tipo (`flatMap`, `concatMap`, `flatMapMany`).
> - `zip()` es la excepción: **mezcla** publishers pero cada uno puede ser de un tipo distinto, generando tuplas tipadas.

---

## Guía de decisión rápida

```
¿Necesitás combinar publishers?
│
├── ¿Tenés datos "previos" a emitir antes del publisher?
│       └── startWith()
│
├── ¿Querés encadenar publishers UNO TRAS OTRO (en orden)?
│   ├── Sin tolerancia a errores → concatWith() / Flux.concat()
│   └── Con tolerancia a errores → Flux.concatDelayError()
│
├── ¿Querés combinar TODOS EN PARALELO?
│   ├── Mismo tipo, sin importar el orden → merge() / mergeWith()
│   └── Tipos distintos, combinando por posición → zip()
│
└── ¿Para CADA ELEMENTO querés hacer una llamada async?
    ├── Resultados en paralelo, orden no importa → Flux.flatMap()
    └── Resultados en secuencia, orden importa  → Flux.concatMap()
```

---

## Resumen de la sección

En una arquitectura de microservicios real, cada petición del usuario puede involucrar múltiples llamadas de red a distintos servicios. El desafío no es solo hacer esas llamadas, sino **coordinarlas correctamente**: a veces en paralelo, a veces una tras otra, a veces combinando sus resultados. Reactor proporciona un conjunto de operadores para controlar exactamente eso.

### `startWith()` y `concatWith()` — orden secuencial

Ambos suscriben a los publishers **uno por uno, en orden**. La diferencia es solo cuál va primero:

- `startWith(B)` → emite B primero, luego el publisher principal.
- `concatWith(B)` → emite el publisher principal primero, luego B.

**Caso de uso clave:** cachéside — primero revisá la caché (`startWith`), y solo si no alcanza, hacé la llamada costosa a la base de datos. Si la caché satisface la demanda (con `take()`), el segundo publisher **nunca se invoca**.

### `merge()` — suscripción simultánea, orden libre

Suscribe a **todos los publishers al mismo tiempo** y emite los elementos a medida que llegan, sin orden garantizado. Cuando el subscriber cancela, **todos los publishers son cancelados simultáneamente**.

**Caso de uso clave:** patrón *scatter-gather* — enviás la misma pregunta a múltiples servicios (ej. aerolíneas) y procesás las respuestas a medida que llegan. El más rápido gana.

### `zip()` — suscripción simultánea, combinación por posición

Similar a `merge()` en que suscribe a todos en paralelo, pero a diferencia de él, **espera a que todos emitan su N-ésimo elemento** para formar la tupla N. Es "todo o nada": si un publisher no tiene más elementos, `zip()` completa y los sobrantes de los otros se descartan.

**Caso de uso clave:** construir un objeto compuesto con partes de distintos servicios (nombre + precio + stock). Necesitás que todos respondan para armar la respuesta.

### `flatMap()` — por cada elemento, un Publisher interno en paralelo

Cuando tenés un `Flux` de elementos y para cada uno debés hacer una llamada asíncrona (que retorna otro Publisher), `flatMap()` lanza todos los Publishers internos **en paralelo** (comportamiento interno similar a `merge()`). El orden de los resultados no está garantizado: llega primero el que responde más rápido.

**Caso de uso clave:** dado un `Flux` de usuarios, obtener los pedidos de cada uno en paralelo.

### `concatMap()` — por cada elemento, un Publisher interno en secuencia

Igual que `flatMap()` pero **secuencial** (comportamiento interno similar a `concat()`): espera que el Publisher interno del elemento actual complete antes de pasar al siguiente. Garantiza el orden, pero sacrifica velocidad.

**Caso de uso clave:** procesar transacciones en orden estricto, o hacer paginación secuencial (página 1 → página 2 → página 3).

### `then()` — ignorar el resultado, solo importa si completó

Cuando no te interesa lo que emite el publisher sino solo **si terminó bien o con error**, `then()` descarta todos los `onNext` y convierte el stream en un `Mono<Void>` (o en un `Mono<V>` con el resultado de otro Mono). Útil para encadenar operaciones secuenciales sin necesitar los valores intermedios.

**Trampa común:** no encadenar con dos `.subscribe()` separados — eso ejecuta ambas operaciones en paralelo. Siempre usar `then()` para garantizar que la segunda operación se ejecute *después* de que la primera complete.

### Tabla mental rápida

| Necesidad | Operador | Velocidad | Orden |
|---|---|---|---|
| Caché → DB (secuencial) | `startWith` / `concatWith` | Lenta | Garantizado |
| Múltiples servicios, no importa el orden | `merge()` | ⚡ Rápida | No garantizado |
| Múltiples servicios, construir un objeto | `zip()` | ⚡ Rápida | Por posición |
| Lista → llamadas en paralelo por elemento | `Flux.flatMap()` | ⚡ Rápida | No garantizado |
| Lista → llamadas secuenciales por elemento | `Flux.concatMap()` | Lenta | Garantizado |
| ID → resultado único asíncrono | `Mono.flatMap()` | Depende | — |
| ID → lista asíncrona | `Mono.flatMapMany()` | Depende | — |
| Ignorar resultado, encadenar acción | `then()` | — | — |

---

## Preguntas frecuentes y casos de análisis

### ❓ ¿Cuál de estos operadores tiene suscripción *lazy*?
> `zip` / `merge` / `mergeWith` / `concat`

**Respuesta: `concat` (y `concatWith`)**

| Operador | Tipo de suscripción | ¿Cuándo se suscribe al siguiente publisher? |
|---|---|---|
| `concat` / `concatWith` | **Lazy** 🐢 | Solo cuando el publisher anterior emite `onComplete` |
| `merge` / `mergeWith` | **Eager** ⚡ | Inmediatamente — todos a la vez |
| `zip` | **Eager** ⚡ | Inmediatamente — todos a la vez |

El comportamiento lazy de `concat` es exactamente lo que hace útil el patrón **caché → DB**: si la caché tiene suficientes datos, el publisher de la base de datos **nunca se invoca**, ahorrando el costo de la llamada.

```java
// Lazy: producer2() NUNCA se suscribe si producer1() satisface el take(2)
producer1().concatWith(producer2()).take(2).subscribe(...);

// Eager: producer2() se suscribe INMEDIATAMENTE aunque no se necesite
Flux.merge(producer1(), producer2()).take(2).subscribe(...);
```

---

### ❓ ¿Cambiar de `map()` a `flatMap()` con `Mono.just()` mejora el rendimiento?

```java
// V1
Flux.fromIterable(list)  // 1 millón de Strings
    .map(String::toUpperCase)
    .subscribe(Util.subscriber());

// V2
Flux.fromIterable(list)
    .flatMap(s -> Mono.just(s.toUpperCase()))
    .subscribe(Util.subscriber());
```

**Respuesta: No mejora. V2 es más lento que V1.**

`toUpperCase()` es una operación **en memoria** (no I/O). `Mono.just(s.toUpperCase())` evalúa `toUpperCase()` de forma **inmediata y síncrona** al crear el Mono — no hay ninguna operación asíncrona que `flatMap` pueda aprovechar para paralelizar.

Además, V2 introduce overhead innecesario: crea un objeto `Mono` por cada uno de los 1.000.000 de elementos, gestiona colas internas y suscripciones, y genera más presión sobre el GC.

`flatMap` solo mejora el rendimiento cuando la función interna es verdaderamente asíncrona (retorna un `Mono` de una llamada HTTP, DB, I/O, etc.):

```java
// flatMap SI mejora aqui: la operacion interna es una llamada de red real
Flux.fromIterable(productIds)
    .flatMap(id -> httpClient.getProduct(id))  // llamada HTTP no-bloqueante
    .subscribe(Util.subscriber());
```

---

### 🔬 Análisis de casos de código

#### Caso 1: `startWith` con el mismo Flux

```java
Flux<String> flux = Flux.just("a", "b", "c");
flux.startWith(flux)
        .subscribe(Util.subscriber());
Util.sleepSeconds(3);
```

**¿Qué output produce?**

```
sub received: a
sub received: b
sub received: c
sub received: a
sub received: b
sub received: c
sub received complete!
```

**¿Por qué?**

`Flux.just()` es un **Cold Publisher**: cada suscripción genera una secuencia nueva e independiente. Cuando escribís `flux.startWith(flux)`, estás pasando la misma referencia de objeto, pero `startWith` se suscribe a ella de forma independiente de la suscripción principal.

Lo que Reactor ve internamente:

1. **Suscripción 1** (del `startWith`): se suscribe a `flux` → emite "a", "b", "c", completa.
2. **Suscripción 2** (del publisher principal): se suscribe a `flux` → emite "a", "b", "c", completa.

```
startWith(flux) → [a, b, c]  (suscripcion #1 al cold publisher)
         ↓
flux principal  → [a, b, c]  (suscripcion #2 al cold publisher)
         ↓
resultado:         a, b, c, a, b, c
```

No hay ningún error ni loop infinito porque `Flux.just()` es finito. Si usaras un Hot Publisher (como `Sinks.Many`), ambas suscripciones compartirían el mismo stream y el comportamiento sería completamente diferente.

**Conclusión:** con Cold Publishers, pasar el mismo objeto como argumento de `startWith` simplemente lo suscribe dos veces de forma independiente. El mismo patrón aplica con `concatWith(flux)` sobre el mismo `flux`.

---

#### Caso 2: `merge` con distintos tiempos de emisión

```java
Mono<Integer> mono1 = Mono.just(1).delayElement(Duration.ofSeconds(1));
Flux<Integer> flux1 = Flux.just(2);
Flux<Integer> flux2 = flux1.map(i -> i + 1).delayElements(Duration.ofMillis(500));

Flux.merge(mono1, flux1, flux2)
        .subscribe(Util.subscriber());
```

**¿Qué output produce?**

```
sub received: 2       <- t=0ms    (flux1, sin delay)
sub received: 3       <- t=500ms  (flux2, delay de 500ms)
sub received: 1       <- t=1000ms (mono1, delay de 1 segundo)
sub received complete!
```

**¿Por qué el orden es 2 → 3 → 1 y no 1 → 2 → 3?**

`merge()` suscribe a los tres publishers **simultáneamente** en t=0ms. A partir de ahí, los elementos se emiten en función de cuándo cada publisher produce su valor:

```
t=0ms:    merge() suscribe a mono1, flux1 y flux2 al mismo tiempo
t=0ms:    flux1 emite 2 inmediatamente         -> subscriber recibe 2
t=500ms:  flux2 emite 3 (delay de 500ms)       -> subscriber recibe 3
t=1000ms: mono1 emite 1 (delay de 1 segundo)   -> subscriber recibe 1
t=1000ms: todos completaron                    -> onComplete
```

**Detalle clave sobre `flux2`**: `flux2 = flux1.map(i -> i + 1).delayElements(...)`. Cuando `merge()` se suscribe a `flux2`, este crea una **nueva suscripción independiente** a `flux1` (cold publisher). Por eso `flux1` termina siendo suscripto **dos veces** en total:

- Una vez **directamente** por `merge()` → emite `2` en t=0ms.
- Una vez **internamente** por `flux2` → mapea `2` a `3`, aplica delay → emite `3` en t=500ms.

```
merge() --- suscribe --> flux1               → emite 2 (t=0ms)
        |
        +-- suscribe --> flux2 --> flux1     → mapea a 3, delay → emite 3 (t=500ms)
        |                          (2da suscripcion independiente al cold publisher)
        |
        +-- suscribe --> mono1               → emite 1 (t=1000ms)
```

**Conclusión:** en `merge()`, el **orden de declaración no determina el orden de llegada**. El elemento que llega primero es el que se emite antes. Aunque `mono1` (que emite `1`) está primero en la lista, llega último porque tiene el delay más largo. Este comportamiento ilustra perfectamente por qué `merge()` no garantiza el orden de los elementos.

---

## ¿Qué pasa si aplicamos `collectList()` a un Flux que siempre está produciendo valores?

Esta es una pregunta trampa muy frecuente.

`collectList()` necesita esperar a que el Flux emita la señal `onComplete` para poder armar y emitir la lista con todos los elementos acumulados. Solo entonces emite el `Mono<List<T>>`.

**¿Qué pasa si el Flux nunca completa?** → `collectList()` **nunca emite nada** y el subscriber **nunca recibe ningún elemento**. La lista se acumula en memoria indefinidamente, creciendo sin límite hasta producir un `OutOfMemoryError`.

```java
// ⚠️ PELIGRO: Flux infinito + collectList() = bloqueo total + OOM eventual
Flux.interval(Duration.ofMillis(100))   // produce 0, 1, 2, 3... para siempre, sin completar
    .collectList()                       // acumula todos los elementos... esperando onComplete
    .subscribe(list -> System.out.println("Tamaño: " + list.size()));
    // Esta línea NUNCA se ejecuta
```

El diagrama de lo que ocurre internamente:

```
t=100ms:  Flux emite 0  → collectList acumula [0]                (onComplete? No → sigue)
t=200ms:  Flux emite 1  → collectList acumula [0, 1]             (onComplete? No → sigue)
t=300ms:  Flux emite 2  → collectList acumula [0, 1, 2]          (onComplete? No → sigue)
   ...
t=∞:      Flux nunca completa → collectList nunca emite → OOM 💥
```

### ¿Por qué ocurre esto?

`collectList()` implementa internamente un `List<T>` donde va agregando cada `onNext`. Solo cuando recibe `onComplete`, llama a `sink.next(lista)` y termina. Si `onComplete` nunca llega, el `sink.next(...)` nunca se llama y la lista crece sin control.

### ¿Cómo solucionarlo?

La solución depende de lo que querés lograr. Algunas opciones:

**Opción 1 — Limitar la cantidad de elementos antes de coleccionar** (`take()`):
```java
// Tomá solo los primeros N elementos, forzando la señal complete
Flux.interval(Duration.ofMillis(100))
    .take(10)              // toma 10 elementos y emite onComplete
    .collectList()         // ahora sí tiene un final → emite la lista
    .subscribe(list -> System.out.println("Lista: " + list));
// Lista: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
```

**Opción 2 — Coleccionar en ventanas de tiempo** (`buffer()`):
```java
// En vez de esperar al complete, coleccioná cada N elementos o cada intervalo de tiempo
Flux.interval(Duration.ofMillis(100))
    .buffer(5)             // agrupa de a 5 elementos y emite una lista cada vez
    .subscribe(list -> System.out.println("Batch: " + list));
// Batch: [0, 1, 2, 3, 4]
// Batch: [5, 6, 7, 8, 9]
// ... cada 5 elementos
```

**Opción 3 — Si necesitás acumular todos los valores**, el diseño debe ser revisado: un Flux infinito no debería necesitar `collectList()`. Considerá si realmente querés procesamiento por lotes (`buffer`/`window`) o si el Flux debería ser finito.

### Resumen

| Escenario | Resultado con `collectList()` |
|---|---|
| Flux finito (emite `onComplete`) | ✅ Emite `Mono<List<T>>` con todos los elementos |
| Flux infinito (`interval`, `generate` sin límite) | ❌ Nunca emite, la lista crece hasta `OutOfMemoryError` |
| Flux que emite error (`onError`) | ❌ La lista se descarta, el error se propaga al subscriber |
| Flux vacío (emite solo `onComplete`) | ✅ Emite `Mono<List<T>>` con lista vacía `[]` |

> **Regla de oro:** antes de usar `collectList()`, preguntate siempre: *¿este Flux tiene un fin definido?* Si la respuesta es no, usá `buffer()`, `window()`, o `take()` primero.
