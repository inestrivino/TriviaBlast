# TriviaBlast

**Juego de preguntas y respuestas en línea** para jugar de forma individual o en salas multijugador (hasta 8 jugadores).

---

## Descripción

TriviaBlast es un juego interactivo que permite a los usuarios practicar y ganar puntos en partidas individuales, o competir en un tablero clásico con categorías por colores en salas privadas. Las preguntas se obtienen de la [Open Trivia Database (OpenTDB)](https://opentdb.com/), ofreciendo una amplia variedad de categorías y niveles de dificultad.

---

## Roles y permisos

| Rol           | Permisos                                                                 |
|---------------|--------------------------------------------------------------------------|
| **Jugador**   | Gestionar cuenta, jugar partidas individuales/multijugador, ver leaderboard. |
| **Administrador** | Ocultar, editar, borrar o restaurar visibilidad de usuarios (con captcha). |

---

## Modalidades de juego

### Partida individual
- Configuración personalizada: número de preguntas, categoría y dificultad.
- Límite de tiempo por pregunta.
- Puntuación basada en dificultad y rapidez al responder.

### Partida multijugador
- Sala privada con código de invitación.
- Tablero por turnos, con categorías por colores.
- Gana el primer jugador que complete correctamente todas las categorías.

---

## Sistema de puntos y leaderboard
- Los puntos se obtienen según el tipo de partida y se reflejan en el leaderboard (solo visible para usuarios registrados).
- Los jugadores aparecen ordenados de mayor a menor puntuación.
- Los administradores pueden ocultar jugadores del leaderboard.

---

## Flujo de juego resumido
1. Registro o inicio de sesión.
2. Selección de modalidad de juego.
3. Desarrollo de la partida.
4. Cálculo de puntos y actualización del leaderboard.

---

