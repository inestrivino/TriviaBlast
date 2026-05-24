# <img src="./src/main/resources/static/img/favicon.png" width="40" height="40" align="center"> TriviaBlast

**Plataforma de preguntas y respuestas en línea** para competir de forma individual o en salas multijugador.

---

## 1. Propuesta del Sistema

### Descripción General

TriviaBlast es un juego interactivo web diseñado para ofrecer entretenimiento.
Permite a los usuarios entrenar y acumular puntuación en partidas individuales, o competir en tiempo real con tablero clásico en salas privadas.
Todas las preguntas se consumen en tiempo real de la API externa [Open Trivia Database (OpenTDB)](https://opentdb.com/), permitiendo una alta diversidad de preguntas posibles que recibir.

### Dependencias y librerías

El proyecto hace uso de Bootstrap, Springboot, Maven, Thymeleaf, para crear una página dinámica con garantías de seguridad y funcionalidades básicas . También se usó la librería `svg.js` para el renderizado del taberlo de juego.

En ciertas partes de la página se puede apreciar el uso de tecnologías AJAX y Websockets, en específico durante la partida multijugador.

### Pruebas externas

Se han creado una serie de pruebas externas que comprueban la validez de la lógica de los endpoints definidos para las acciones de usuario (crear cuenta, editar perfil...) como para las acciones relacionadas con el juego (crear juego, unirse, comprobar preguntas...). Estas pruebas, creadas con Karate, pueden encontrarse en `src/test/external`. Ejecute `ExternalRunnerTest.java` para probarlas. Además, Karate proporciona un resúmen del resultado de los tests en `src/target/karate-reports/karate-summary.html` que es recomendable ver.

### Otros detalles

La mayor parte del js del lado del cliente de la aplicación se puede encontrar en el archivo `TriviaBlast.js`, salvo lo referido a el lobby y la partida multijugador, que debido a su extensión y complejidad se encuentra en el archivo `gameClient.js`.
Por lo demás, el proyecto sigue la estructura que se esperaría.

En el modo multijugador se dispone de un dado giratorio con animación. El "verdadero" dado se encuentra en el backend con una función Random, y se proporciona el resultado al dado del frontend, que no termina su animación hasta que lo recibe. Además, el backend tiene un buffer de 4 segundos para garantizar que no se hagan varias llamadas repetidas a la API, por lo que la animación durará como mínimo 4 segundos.

### Roles y Sistema de Permisos

| Rol | Permisos y Funcionalidades Operativas |
| :--- | :--- |
| **Jugador** | Registro e inicio de sesión, gestión avanzada del perfil propio, participación en modalidades singleplayer y multiplayer, visualización de clasificaciones (Leaderboard) y capacidad de reportar usuarios por comportamiento indebido. |
| **Administrador** | Auditoría del sistema a través de alertas en tiempo real, capacidad de moderación, borrado lógico de cuentas, e inhabilitación total de usuarios (ocultación de clasificaciones y bloqueo de login). |


## 2. Mecánicas de Juego Detalladas

### 2.1 Modalidad Individual (Singleplayer)

* **Configuración Avanzada:** El usuario puede parametrizar al completo su experiencia seleccionando el número de preguntas, la categoría temática y el nivel de dificultad.
* **Modo Invitado:** La plataforma permite a usuarios anónimos o no registrados iniciar partidas en modo individual para fomentar el juego casual. No obstante, de forma evidente, **estos usuarios no acumularán puntos a largo plazo** ni guardarán registros históricos en el Scoreboard global.
* * *Seguridad Anti-Trampas:* **La validación de las preguntas se realiza íntegramente en el backend.** El frontend nunca recibe la respuesta correcta antes de tiempo para evitar vulnerabilidades por inspección de código. Se puede auditar ingresando `window.questions` en la consola del navegador; el vector de respuestas vendrá enmascarado. Cada acierto asigna 10 puntos básicos y al fallar se le revela visualmente al usuario cuál era la respuesta correcta. Esto se aplica también a la partida multijugador.

### 2.2 Modalidad Multijugador

El modo multijugador se ha creado bajo estrictas reglas de sincronización para asegurar la equidad y consistencia de las partidas:

* **Gestión del Lobby:** Las salas privadas se configuran mediante un código de invitación único. Por motivos de rendimiento, optimización web y simplicidad logística, el lobby tiene un **máximo permitido de 4 jugadores**. Además, se optó por usar un método propio que solo crease códigos alfanuméricos (números y letras) para evitar problemas en casos en los que el código generado incluía otros símbolos (como un guión).
* **Identidad Visual en el Lobby:** Para facilitar la jerarquía visual, dentro del lobby el **Host (anfitrión) se resalta en color dorado**, mientras que el **usuario propio se renderiza en azul**.
* **Políticas de Desconexión y Cierre:** * Cualquier jugador puede abandonar libremente el lobby o ser expulsado por el anfitrión.
    * Si el Host decide salir del lobby, **se elimina automáticamente toda la partida**, disolviendo la sala.
    * Durante el juego en sí, **basta con que quede un solo usuario (el Host) para que la partida se cancele y elimine**, debido a que a esas alturas del flujo ya no se pueden unir nuevos contrincantes y una mecánica multijugador no puede ser disputada por un único participante.
* **Tablero de Juego:** Sistema competitivo por turnos sobre un tablero clásico con categorías representadas por colores. Gana el primer jugador en llegar al final del tablero.
* **Sistema de Incentivos:** Al finalizar el encuentro, los **3 primeros jugadores de la clasificación de la sala ganan un bonus extraordinario de puntos** directo a su balance de cuenta global.

## 3. Mensajería, Moderación y Seguridad

### 3.1 Chat en Vivo

Durante el transcurso de una partida multijugador, los usuarios disponen de un chat síncrono. Los mensajes enviados son recibidos por todos los participantes y muestran de forma explícita **la hora exacta de emisión y el usuario remitente**.

### 3.2 Canal de Alertas de Administración

La aplicación incorpora dos flujos reactivos de moderación instantánea para proteger la experiencia de la comunidad:

1.  **Comando de Emergencia (`@admin`):** Si un jugador inicia un mensaje en el chat con la cadena de texto `@admin`, **este mensaje no se muestra en la pantalla del chat común**. En su lugar, el sistema lo intercepta en el backend y despacha automáticamente una alerta privada directa a los administradores.
2.  **Denuncias desde el Scoreboard:** Cualquier usuario puede emitir un reporte contra perfiles específicos desde la propia tabla de clasificación (Scoreboard), lo cual genera una alerta en la consola/panel de gestión de los administradores.

### 3.3 Protocolo de Inhabilitación

Al procesar una alerta, los administradores tienen el poder de aplicar la acción de **"Esconder"** a un usuario infractor (lo que conmuta su estado en base de datos a `disabled`). Al ejecutarse:
* El usuario **deja de aparecer en el Scoreboard público para los usuarios generales**.
* Se revoca su token y permiso de acceso, **imposibilitando que vuelva a iniciar sesión** en la plataforma de manera indefinida.

## 4. Estructura de la Base de Datos


![Database Schema](DatabaseSchema.png)

## 5. Sobre la implementación

### Otras funcionalidades

* **Sign in & Sign up:** Registro e inicio de sesión completamente funcionales. Gestión exhaustiva de excepciones (como la detección de contraseñas no coincidentes al crear una cuenta). El sistema inicia la sesión de forma automatizada inmediatamente después de un registro correcto.
* **Pantalla de Inicio:** Si el usuario no está autenticado, presenta el acceso rápido a una partida singleplayer. Una vez logueado, inyecta los paneles para crear partidas multijugador o unirse a otras.
* **Perfil de Usuario:** Visualización de avatar, estadísticas de puntos y datos personales. Permite el cierre de sesión, la edición avanzada de perfil (con verificación previa de la contraseña antigua) y el borrado físico completo de la cuenta.
* **Scoreboard (Tablero global):** Listado síncrono de los usuarios registrados ordenados de mayor a menor puntuación. Implementa los filtros de control de administración, discriminando los perfiles marcados como invisibles (`disabled`).
* **Interacciones AJAX:** Actualización de datos asíncrona sin recarga de página completamente integrada en la vista del Scoreboard y multijugador.
* **Despliegue:** Configuración de entorno y despliegue completado con éxito en el docker proporcionado para el proyecto, gracias al script `deploy.py`.

### Usuarios de Prueba 

Para facilitar las tareas de evaluación al arrancar la aplicación, la base de datos se inicializa automáticamente con dos perfiles de prueba:

* **Usuario `a`:** Cuenta con rol de **Administrador**.
* **Usuario `b`:** Cuenta con rol de **Jugador** normal.

La contraseña es `aa` en ambos casos, y se dispone de botones para inicio de sesión rápido en la barra de navegación.

### Recursos Externos y Uso de IA

* Se ha hecho uso de IA a lo largo del desarrollo del proyecto sobretodo para tareas de refactorización del código, para conseguir un diseño de la interfaz agradable e intuitiva, para debuggear, y para escribir secciones del código, en específico aquellas que hiciesen uso de librerías externas poco conocidas, como la renderización del tablero.
* No se ha hecho uso de código (humano) de internet o libros para referencias. Se usó de referencia el material académico de la clase, incluída la plantilla de código proporcionada.
* Se consideró usar la librería `svg.js` que se usó para el tablero, así como *code snippets* de internet para lograr el efecto de dado giratorio en el modo multijugador. Sin embargo, debido a la dificultad de integración con el código existente y las limitaciones en tiempo, se consiguió el efecto con una mezcla de código humano (la base del dado, la estilización) e IA (la animación, gestión de las matemáticas involucradas para el efecto 3D). 

## 6. Trabajo Futuro 

1.  **Optimización Responsiva Completa:** Rediseñar los contenedores y layouts del entorno multijugador (especialmente el tablero), dado que la versión actual presenta problemas de escalado y solapamiento de componentes en pantallas pequeñas o dispositivos móviles.
2.  **Unificación de la Lógica de Preguntas:** Refactorizar el motor core de peticiones a OpenTDB para unificar la captura, almacenamiento temporal y procesado de preguntas entre los módulos individual y multijugador, reduciendo código duplicado y deuda técnica.
3.  **Módulo de historial de Partidas:** Desarrollar una pestaña adicional de "Histórico" en el perfil de usuario que consulte de manera retrospectiva los datos de partidas pasadas (fechas, puestos, rivales y estadísticas), aportando mayor valor al progreso del jugador.
