# Programación Reactiva

## ¿Qué es la Programación Reactiva?

La programación reactiva es un paradigma de programación que consiste en procesar un flujo de mensajes de forma **no bloqueante** y **asíncrona** mientras se gestiona la **contrapresión** (backpressure). 

Al igual que en la programación orientada a objetos visualizamos todo como un objeto, en la programación reactiva visualizamos todas las dependencias externas como un modelo **Publisher-Subscriber** (Editor-Suscriptor), ya que en la programación reactiva se trata de hacer más eficientes las llamadas a la red externa.

## ¿Qué Problema Resuelve?

### 1. Gestión Eficiente de Recursos del Sistema

La programación reactiva resuelve el problema de la gestión ineficiente de recursos del sistema, particularmente relacionados con:

- **Hilos de CPU**: Más hilos no significa mejor rendimiento. Una CPU sólo puede ejecutar un hilo a la vez, así que si tienes una máquina con diez CPU, sólo necesitas diez hilos. **Sólo necesitamos un hilo por CPU**.

- **Llamadas de Red Bloqueantes**: A menudo la gente crea demasiados hilos principalmente debido a las llamadas de red. Estas son llamadas de bloqueo muy lentas que consumen mucho tiempo. Para aprovechar la CPU, la gente crea demasiados hilos. Sin embargo, si utilizas comunicación no bloqueante, sólo necesitas un hilo por CPU, y esta es la forma más eficiente de gestionar las llamadas de red.

### 2. Complejidad de la Programación Asíncrona

El I/O no bloqueante o asíncrono es muy difícil de hacer en realidad. La programación reactiva proporciona un **modelo de programación mejor** que simplifica el trabajo con operaciones asíncronas.

### 3. Limitaciones del Modelo Request-Response Tradicional

La programación tradicional se basa en el modelo **pull** y sólo admite la comunicación de tipo solicitud y respuesta. Tendrás que enviar la solicitud al servidor para obtener la respuesta de éste.

**Ejemplo del problema**: Si necesitas actualizaciones de precios de acciones o Bitcoin que cambian cada segundo, con el modelo tradicional tendrías que enviar una solicitud una y otra vez para obtener las actualizaciones. Esto es ineficiente y consume recursos innecesarios.

La programación reactiva resuelve esto proporcionando un modelo **híbrido push-pull** que soporta múltiples patrones de comunicación.

## ¿Qué Problemas NO Resuelve?

La programación reactiva **NO resuelve**:

- **Problemas de diseño de arquitectura**: No mejora automáticamente el diseño de tu aplicación si este es deficiente.
- **Problemas de lógica de negocio**: No resuelve errores en la lógica de negocio de tu aplicación.
- **Problemas de seguridad**: No proporciona seguridad adicional por sí misma.
- **Problemas de escalabilidad de base de datos**: No resuelve problemas de consultas lentas o diseño de base de datos ineficiente.
- **Tareas intensivas de CPU**: Las tareas intensivas requieren uso de CPU. La programación reactiva está optimizada para I/O no bloqueante, pero las tareas que requieren procesamiento intensivo de CPU siguen bloqueando y necesitan múltiples hilos o procesamiento paralelo.
- **Complejidad inherente**: Aunque simplifica la programación asíncrona, puede agregar complejidad conceptual que requiere aprendizaje.

## Pilares de la Programación Reactiva

### 1. Flujo de Mensajes (Stream of Messages)

La programación reactiva consiste en **procesar un flujo de mensajes**. En lugar de trabajar con datos individuales o respuestas únicas, la programación reactiva trata todo como un flujo continuo de mensajes que pueden ser procesados, transformados y combinados de manera declarativa. Este flujo de mensajes es la base sobre la cual se aplican los demás pilares (no bloqueante, asíncrono y contrapresión).

### 2. Procesamiento No Bloqueante (Non-Blocking)

El procesamiento no bloqueante significa que las operaciones no detienen la ejecución del hilo mientras esperan una respuesta. Esto permite que un solo hilo maneje múltiples operaciones concurrentes de manera eficiente.

### 3. Procesamiento Asíncrono (Asynchronous)

El procesamiento asíncrono permite que las operaciones se ejecuten de manera independiente sin esperar a que se complete una antes de iniciar otra. Esto mejora significativamente el rendimiento y la utilización de recursos.

### 4. Gestión de Contrapresión (Backpressure)

La contrapresión es un mecanismo que permite al consumidor (subscriber) controlar la velocidad a la que el productor (publisher) envía datos. Esto previene que el consumidor se sature cuando recibe datos más rápido de lo que puede procesar.

## Patrones de Comunicación

La programación reactiva soporta cuatro patrones de comunicación:

### 1. Request-Response (Solicitud-Respuesta)
El patrón tradicional donde envías una solicitud y recibes una respuesta. También soportado en programación reactiva.

### 2. Request-Streaming (Solicitud-Respuesta de Streaming)
En lugar de enviar múltiples solicitudes para obtener actualizaciones, puedes enviar **una solicitud para obtener una respuesta de streaming** desde el servidor remoto. El manejo de la respuesta de streaming es mucho más fácil con la programación reactiva.

**Ejemplo**: Actualizaciones de precios de Bitcoin en tiempo real. En lugar de hacer polling constante, el servidor envía un stream continuo de actualizaciones.

### 3. Streaming-Request (Solicitud de Streaming)
Puedes enviar un stream de datos al servidor en lugar de datos individuales.

**Ejemplo**: Streaming de vídeo, streaming de audio, donde tu micrófono podría observar todas estas cosas y deseas guardar todo en un servidor remoto.

### 4. Bidirectional Streaming (Streaming Bidireccional)
Comunicación bidireccional donde tanto el cliente como el servidor pueden enviar streams de datos simultáneamente.

**Ejemplo**: Juegos online en tiempo real, aplicaciones de chat en tiempo real.

## Modelo Publisher-Subscriber

En la programación reactiva, visualizamos las dependencias externas como un modelo **Publisher-Subscriber**:

- **Publisher (Editor/Productor)**: Es quien va a dar los datos. Actúa como fuente de datos.
- **Subscriber (Suscriptor/Consumidor)**: Es quien pide datos o los consume. Actúa como consumidor de datos.

## Reglas de la Programación Reactiva

Existen reglas fundamentales que debemos seguir en la programación reactiva:

### 1. Lazy Evaluation (Evaluación Perezosa)

**El suscriptor tiene que suscribirse y solicitar al productor que produzca artículos. Hasta entonces el productor no hace nada.**

Tenemos que ser perezosos en la medida de lo posible. ¿Qué sentido tiene producir cuando no vemos ninguna solicitud del suscriptor? El productor no tiene que hacer ningún trabajo hasta que vea la solicitud.

### 2. Unsubscribe (Cancelación de Suscripción)

**El suscriptor puede darse de baja en cualquier momento, y el productor debe dejar de producir en ese momento.**

Tenemos que estar atentos y el productor debe dejar de producir cuando el suscriptor se da de baja. ¿Qué sentido tiene seguir produciendo cuando no hay nadie para observar los artículos? Así que tenemos que parar cuando el suscriptor se da de baja.

### 3. Métodos de Comunicación

El productor se comunica con el suscriptor a través de métodos específicos:

- **onNext()**: El productor produce artículos o los comparte con el suscriptor utilizando este método.
- **onComplete()**: Después de producir todos los artículos, o cuando el productor no tiene nada que darle al suscriptor, invocará este método.
- **onError()**: Cuando el productor se enfrente a algún problema, invocará este método para notificar el error.

### 4. Estado Final

**El productor no hará ningún trabajo, no invocará nada después de invocar `onComplete()` o `onError()`.**

Después de invocar `onComplete()` o `onError()`, la cancelación de la solicitud de suscripción no tendrá ningún efecto, ya que el productor ha terminado su trabajo.

## Resumen

La programación reactiva es un paradigma que:

- Optimiza el uso de recursos del sistema (CPU, memoria, hilos)
- Simplifica la programación asíncrona y no bloqueante
- Proporciona múltiples patrones de comunicación más allá del request-response tradicional
- Gestiona automáticamente la contrapresión para prevenir la saturación
- Utiliza el modelo Publisher-Subscriber para visualizar dependencias externas
- Sigue reglas estrictas de comunicación entre productores y consumidores

## Estructura del Proyecto

Este proyecto utiliza **Project Reactor** (Reactor Core) para implementar programación reactiva en Java y está organizado en secciones que cubren diferentes aspectos de la programación reactiva.

## Interfaces Fundamentales: Publisher, Subscriber y Subscription

Las interfaces **Publisher**, **Subscriber** y **Subscription** son parte de la especificación **Reactive Streams** (no específicamente de Reactor). Reactive Streams es una especificación estándar que define un protocolo para el procesamiento asíncrono de flujos de datos con contrapresión.

**Project Reactor** implementa estas interfaces y proporciona abstracciones de más alto nivel como `Mono` y `Flux`, pero las interfaces base provienen de la especificación Reactive Streams.

### Publisher (Productor/Editor)

**`Publisher<T>`** es la interfaz que representa un proveedor de una secuencia potencialmente ilimitada de elementos de tipo `T`, que publica elementos según la demanda recibida de sus `Subscriber`s.

**Características principales**:
- **Método único**: `subscribe(Subscriber<? super T> subscriber)`
- **Responsabilidad**: Crear una `Subscription` cuando un `Subscriber` se suscribe y gestionar la transmisión de datos según la demanda
- **Comportamiento**: No produce datos hasta que el `Subscriber` solicite explícitamente mediante la `Subscription`
- **Evaluación perezosa**: La suscripción no inicia la producción de datos automáticamente
- **Secuencia potencialmente ilimitada**: Puede producir una cantidad ilimitada de elementos

**Contrato**:
- Debe crear una instancia de `Subscription` y pasarla al `Subscriber` mediante `onSubscribe()`
- Puede tener múltiples suscriptores, cada uno con su propia `Subscription`
- Debe respetar la demanda del `Subscriber` a través de la `Subscription`

### Subscriber (Suscriptor/Consumidor)

**`Subscriber<T>`** es la interfaz que representa un consumidor de datos que recibe elementos de tipo `T` de un `Publisher`.

**Métodos principales**:
- **`onSubscribe(Subscription subscription)`**: Invocado cuando el `Publisher` acepta la suscripción. El `Subscriber` debe almacenar la `Subscription` para poder solicitar datos o cancelar.
- **`onNext(T item)`**: Invocado cada vez que el `Publisher` produce un nuevo elemento. Puede ser llamado múltiples veces.
- **`onError(Throwable throwable)`**: Invocado cuando ocurre un error durante la producción de datos. Después de esto, no se llamará más a `onNext()` ni `onComplete()`.
- **`onComplete()`**: Invocado cuando el `Publisher` ha terminado de producir todos los datos. Después de esto, no se llamará más a `onNext()` ni `onError()`.

**Contrato**:
- Debe llamar a `subscription.request(n)` para solicitar datos (donde `n > 0`)
- Puede llamar a `subscription.cancel()` para cancelar la suscripción
- Después de `onError()` o `onComplete()`, la suscripción se considera terminada y no se deben hacer más llamadas a `request()` o `cancel()`
- Debe estar preparado para recibir llamadas a `onNext()`, `onError()` o `onComplete()` en cualquier momento después de `onSubscribe()`

### Subscription (Suscripción)

**`Subscription`** es la interfaz que representa la relación **uno a uno** entre un `Publisher` y un `Subscriber`. Es el mecanismo mediante el cual el `Subscriber` controla el flujo de datos y gestiona la contrapresión.

**Métodos principales**:
- **`request(long n)`**: Solicita al `Publisher` que envíe hasta `n` elementos. El `Publisher` puede producir menos elementos (incluso 0), pero **nunca más** de los solicitados. El parámetro `n` debe ser mayor que 0.
- **`cancel()`**: Cancela la suscripción. Después de esto, el `Publisher` debe dejar de producir datos y las llamadas a `request()` deben ser ignoradas. También debe liberar los recursos asociados.

**Características clave**:
- **Contrapresión**: Permite al `Subscriber` controlar la velocidad a la que recibe datos mediante `request(n)`
- **Comunicación bidireccional**: El `Subscriber` puede solicitar más datos o cancelar
- **Relación uno a uno**: Cada `Subscription` conecta un único `Subscriber` con un único `Publisher`
- **Estado**: Debe mantener el estado de si está cancelada o no

**Contrato**:
- Debe respetar las solicitudes del `Subscriber` (puede producir menos, pero nunca más)
- Debe dejar de producir datos cuando se cancela
- Debe liberar recursos cuando se cancela
- Puede enviar `onError()` si hay un problema con la solicitud (por ejemplo, si `n <= 0`)
- Debe enviar `onComplete()` cuando no hay más datos que producir

### Relación entre las Interfaces

```
Publisher --subscribe()--> Subscriber
    |                          |
    |                          |
    +--crea--> Subscription <--recibe--
                          |
                          |
                    request(n) / cancel()
```

**Flujo típico**:
1. El `Subscriber` llama a `publisher.subscribe(subscriber)`
2. El `Publisher` crea una `Subscription` y llama a `subscriber.onSubscribe(subscription)`
3. El `Subscriber` almacena la `Subscription` y llama a `subscription.request(n)` cuando está listo
4. El `Publisher` (a través de la `Subscription`) produce datos llamando a `subscriber.onNext(item)`
5. Este ciclo continúa hasta que:
   - El `Subscriber` cancela la suscripción (`subscription.cancel()`)
   - El `Publisher` completa (`subscriber.onComplete()`)
   - Ocurre un error (`subscriber.onError(throwable)`)

### ¿Es parte de Reactor?

**No directamente**. Las interfaces `Publisher`, `Subscriber` y `Subscription` son parte de la especificación **Reactive Streams** (paquete `org.reactivestreams`), que es un estándar independiente.

**Project Reactor**:
- Implementa estas interfaces (por ejemplo, `Mono` y `Flux` implementan `Publisher`)
- Proporciona implementaciones de `Subscriber` y `Subscription`
- Agrega abstracciones de más alto nivel y operadores adicionales
- Simplifica el uso con APIs más amigables

La especificación Reactive Streams garantiza la interoperabilidad entre diferentes librerías reactivas (RxJava, Akka Streams, Project Reactor, etc.).

### Sección 01: Implementación Custom de Publisher-Subscriber

La **sección 01** (`sec01`) implementa una versión personalizada del patrón **Publisher-Subscriber** siguiendo la especificación de Reactive Streams. Esta implementación demuestra los conceptos fundamentales de la programación reactiva desde cero.

#### Componentes Principales

1. **`PublisherImpl`** (`sec01/publisher/PublisherImpl.java`)
   - Implementa la interfaz `Publisher<String>` de Reactive Streams
   - Cuando un suscriptor se suscribe, crea una instancia de `SubscriptionImpl` y notifica al suscriptor mediante `onSubscribe()`
   - Actúa como la fuente de datos (productor)

2. **`SubscriptionImpl`** (`sec01/publisher/SubscriptionImpl.java`)
   - Implementa la interfaz `Subscription` de Reactive Streams
   - Contiene la lógica principal de producción de datos
   - **Características clave**:
     - **Evaluación perezosa**: Solo produce datos cuando el suscriptor los solicita mediante `request()`
     - **Respeto de límites**: Produce únicamente la cantidad de items solicitados (puede producir menos, incluso 0)
     - **Validación**: Valida que no se soliciten más de 10 items (si se solicita más, envía un error)
     - **Cancelación**: Maneja la cancelación de la suscripción y deja de producir datos
     - **Señales de terminación**: Envía `onComplete()` cuando ha producido todos los datos disponibles (máximo 10 items)
     - **Manejo de errores**: Envía `onError()` cuando se violan las reglas de validación

3. **`SubscriberImpl`** (`sec01/subscriber/SubscriberImpl.java`)
   - Implementa la interfaz `Subscriber<String>` de Reactive Streams
   - Recibe y procesa los datos del publisher
   - **Métodos implementados**:
     - `onSubscribe()`: Recibe la suscripción y la almacena para poder hacer requests posteriores
     - `onNext()`: Procesa cada item recibido (en este caso, direcciones de email generadas con Faker)
     - `onError()`: Maneja errores recibidos del publisher
     - `onComplete()`: Se ejecuta cuando el publisher ha terminado de enviar todos los datos

4. **`Demo`** (`sec01/Demo.java`)
   - Contiene ejemplos prácticos que demuestran el funcionamiento del patrón Publisher-Subscriber
   - **Ejemplos incluidos**:
     - `demo1()`: Suscripción básica sin solicitar datos
     - `demo2()`: Múltiples solicitudes de datos (`request()`) en diferentes momentos
     - `demo3()`: Demostración de cancelación de suscripción y cómo las solicitudes posteriores son ignoradas
     - `demo4()`: Múltiples solicitudes con diferentes cantidades de items

#### Reglas Implementadas

Esta sección demuestra las reglas fundamentales de la programación reactiva:

1. **El publisher no produce datos a menos que el subscriber los solicite**: El `PublisherImpl` solo crea la suscripción, pero `SubscriptionImpl` solo produce datos cuando se llama a `request()`

2. **El publisher produce solo ≤ items solicitados**: El publisher puede producir menos items de los solicitados (incluso 0), pero nunca más

3. **El subscriber puede cancelar la suscripción**: Cuando se llama a `cancel()`, el publisher deja de producir datos y todas las solicitudes posteriores son ignoradas

4. **El producer puede enviar señal de error**: Si se solicita más de 10 items, el publisher envía un error mediante `onError()` y cancela la suscripción

#### Propósito Educativo

Esta implementación custom es fundamental para entender:
- Cómo funciona internamente el patrón Publisher-Subscriber
- La importancia de la evaluación perezosa (lazy evaluation)
- Cómo se gestiona la contrapresión mediante el método `request()`
- El ciclo de vida completo de una suscripción reactiva
- La comunicación bidireccional entre publisher y subscriber a través de la Subscription

Antes de usar librerías como Project Reactor, es esencial entender estos conceptos fundamentales que esta sección demuestra con código propio.

