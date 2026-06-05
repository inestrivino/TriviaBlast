# TriviaBlast

## Contenido de la entrega

~~~
TriviaBlast
TriviaBlast/DatabaseSchema.png
TriviaBlast/deploy.py
TriviaBlast/.gitignore
TriviaBlast/howToDeploy.md
TriviaBlast/iwdata
TriviaBlast/iwdata/user
TriviaBlast/iwdata/user/977.jpg
TriviaBlast/iwdata.zip
TriviaBlast/iwdb.mv.db
TriviaBlast/iwdb.trace.db
TriviaBlast/LICENSE
TriviaBlast/pom.xml
TriviaBlast/README.md
TriviaBlast/requirements.txt
TriviaBlast/src
TriviaBlast/src/main
TriviaBlast/src/main/java
TriviaBlast/src/main/java/es
TriviaBlast/src/main/java/es/ucm
TriviaBlast/src/main/java/es/ucm/fdi
TriviaBlast/src/main/java/es/ucm/fdi/iw
TriviaBlast/src/main/java/es/ucm/fdi/iw/AppConfig.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/AdminController.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/ApiController.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos/AnswerReqDTO.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos/AnswerResDTO.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos/GameSetupDTO.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos/QuestionDataPrivateDTO.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/dtos/QuestionDataPublicDTO.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/GameController.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/RootController.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/TriviaCategory.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/controller/UserController.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/IwApplication.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/IwUserDetailsService.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/LocalData.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/LoginSuccessHandler.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/Game.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/Lorem.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/Message.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/Player.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/Transferable.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/model/User.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/SecurityConfig.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/StartupConfig.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/WebSocketConfig.java
TriviaBlast/src/main/java/es/ucm/fdi/iw/WebSocketSecurityConfig.java
TriviaBlast/src/main/resources
TriviaBlast/src/main/resources/application-container.properties
TriviaBlast/src/main/resources/application.properties
TriviaBlast/src/main/resources/import.sql
TriviaBlast/src/main/resources/static
TriviaBlast/src/main/resources/static/css
TriviaBlast/src/main/resources/static/css/bootstrap-5.3.3.css
TriviaBlast/src/main/resources/static/css/bootstrap.css.map
TriviaBlast/src/main/resources/static/css/custom.css
TriviaBlast/src/main/resources/static/css/simple-datatables-10.css
TriviaBlast/src/main/resources/static/img
TriviaBlast/src/main/resources/static/img/default-pic.jpg
TriviaBlast/src/main/resources/static/img/favicon_antiguo.ico
TriviaBlast/src/main/resources/static/img/favicon.png
TriviaBlast/src/main/resources/static/img/Inés.png
TriviaBlast/src/main/resources/static/img/Jieru.png
TriviaBlast/src/main/resources/static/img/logo_antiguo.png
TriviaBlast/src/main/resources/static/img/logo.png
TriviaBlast/src/main/resources/static/img/Nerea.png
TriviaBlast/src/main/resources/static/js
TriviaBlast/src/main/resources/static/js/ajax-demo.js
TriviaBlast/src/main/resources/static/js/bootstrap.bundle-5.3.3.js
TriviaBlast/src/main/resources/static/js/bootstrap.bundle.js.map
TriviaBlast/src/main/resources/static/js/gameClient.js
TriviaBlast/src/main/resources/static/js/iw.js
TriviaBlast/src/main/resources/static/js/js-eval.js
TriviaBlast/src/main/resources/static/js/simple-datatables-10.js
TriviaBlast/src/main/resources/static/js/stomp.js
TriviaBlast/src/main/resources/static/js/svg.min.js
TriviaBlast/src/main/resources/static/js/triviablast.js
TriviaBlast/src/main/resources/static/js/util.js
TriviaBlast/src/main/resources/templates
TriviaBlast/src/main/resources/templates/admin.html
TriviaBlast/src/main/resources/templates/authors.html
TriviaBlast/src/main/resources/templates/error.html
TriviaBlast/src/main/resources/templates/fragments
TriviaBlast/src/main/resources/templates/fragments/footer.html
TriviaBlast/src/main/resources/templates/fragments/head.html
TriviaBlast/src/main/resources/templates/fragments/nav.html
TriviaBlast/src/main/resources/templates/index.html
TriviaBlast/src/main/resources/templates/join_game.html
TriviaBlast/src/main/resources/templates/lobby.html
TriviaBlast/src/main/resources/templates/login.html
TriviaBlast/src/main/resources/templates/multi_game.html
TriviaBlast/src/main/resources/templates/multi_game_setup.html
TriviaBlast/src/main/resources/templates/profile.html
TriviaBlast/src/main/resources/templates/proposal.html
TriviaBlast/src/main/resources/templates/scoreboard.html
TriviaBlast/src/main/resources/templates/single_game.html
TriviaBlast/src/main/resources/templates/single_game_setup.html
TriviaBlast/src/test
TriviaBlast/src/test/java
TriviaBlast/src/test/java/es
TriviaBlast/src/test/java/es/ucm
TriviaBlast/src/test/java/es/ucm/fdi
TriviaBlast/src/test/java/es/ucm/fdi/iw
TriviaBlast/src/test/java/es/ucm/fdi/iw/PlantillaApplicationTests.java
TriviaBlast/src/test/java/external
TriviaBlast/src/test/java/external/answer-question.feature
TriviaBlast/src/test/java/external/create-account.feature
TriviaBlast/src/test/java/external/create-game.feature
TriviaBlast/src/test/java/external/delete-account.feature
TriviaBlast/src/test/java/external/edit-account.feature
TriviaBlast/src/test/java/external/ExternalRunnerTest.java
TriviaBlast/src/test/java/external/join-game.feature
TriviaBlast/src/test/java/internal
TriviaBlast/src/test/java/internal/users
TriviaBlast/src/test/java/internal/users/InternalRunner.java
TriviaBlast/src/test/java/internal/users/users.feature
TriviaBlast/src/test/java/karate-config.js
TriviaBlast/src/test/java/logback-test.xml
TriviaBlast/TriviaBlast-main
~~~        
     
## Corrección

### funcionalidad

* Hay muchas casillas en una partida multijugador estándar. Sería bueno poder modificarlas al iniciar la partida (aunque habría que retocar también código de pintado) - ver apartado B.

* El dado está muy chulo, y resuelve de forma elegante el problema de que otdb nos pueda echar por exceder el límite de consultas por minuto. Pero una solución más profunda y escalable sería poder usar una BD local de preguntas-y-respuestas.

* Las denuncias deberían tener un contexto para permitir al administrador entender porqué alguien nos cae mal.

* Se podría añadir mucha funcionalidad a la parte de scoreboard y perfil -- ver apartados del examen para ideas.

### diseño

* Para poder soportar decenas de partidas simultáneas (que tampoco son tantas) tendríais que pasar a usar una BD local de preguntas y respuestas. 

### nota

* Vuestro proyecto ha quedado muy decente, a pesar de que, como muchos juegos, haya más juego que aplicación web. Tal y como está, el proyecto se llevaría un 7.5, que podéis mejorar a 8 y más (con muchas mejoras, llegando al 9) enviando en la entrega post-exame soluciones (buenas) a los ejercicios de este examen.

## Instrucciones del examen

Estas son las instrucciones del examen. Léelas con atención antes de nada, y pregunta cualquier duda sobre ellas cuanto antes. Después, verás las 6 preguntas de las que consta: A, B, C, D, E y F. **No dejes ninguna pregunta sin contestar**, aunque no consigas solucionarlas.

* Está *estrictamente prohibido* comunicarte con tus compañeros o con terceros. Puedes acceder a material de Campus Virtual o documentación puesta a tu disposición por el profesor. No puedes, por ejemplo, usar mensajería o chat para comunicarte con terceros, o lanzar ChatGPT u otras LLMs.

* Para tu entrega, usa exclusivamente los fuentes entregados con este enunciado. *No* uses fuentes descargados de GitHub, ni de ningún otro sitio sin consultarlo antes con el profesor.

* Recuerda que el objetivo de este examen no es mejorar la entrega, sino *demostrar que sabes bien cómo funciona*, y que te desenvuelves bien con los conceptos, herramientas y tecnologías vistas en la asignatura. 

* Si resuelves un ejercicio correctamente, no hace falta describir mucho cómo lo has conseguido: el código cambiado hablará por sí solo. Aun así, escribe por favor una frase en el apartado correspondiente del fichero "markdown" (_TriviaBlast.md_) del enunciado describiendo la idea de la solución.

* Si *no* te funciona algo, comenta, en el apartado correspondiente del fichero "markdown" del enunciado (_TriviaBlast.md_) qué has intentado, porqué crees que falla, y cómo lo intentarías solucionar si tuvieses más tiempo. Aquí sí es importante describir tu solución, ya que será la única forma en que podré valorar tus conocimientos sobre el tema.

* Para trabajar en el examen, recomiendo

  1. descomprimir el fichero TriviaBlast.zip en `u:\hlocal\TriviaBlast`
  2. abre una consola con Maven - escribe `maven` en el buscador de la barra de inicio para que te autocomplete. 
  3. ejecuta `code u:\hlocal\TriviaBlast` en la consola Maven

* Cuando quieras entregar, sigue estos pasos:

  1. cierra el editor
  2. elimina la carpeta `u:\hlocal\TriviaBlast\target` (si no haces el paso 1, el editor la volverá a crear; eso haría que tu entrega ocupe más de lo necesario, porque `target` se regenera a partir de los fuentes)
  3. comprime la carpeta `u:\hlocal\TriviaBlast` a `u:\hlocal\TriviaBlast.zip`; de esta forma, el .zip contendrá tanto tus **fuentes modificados** como el fichero "markdown" con tus **respuestas a las preguntas**.
  4. entrega ese .zip vía Campus Virtual y pásate por el puesto del profesor para firmar.

## Examen en sí

### pregunta A

¿En qué partes de este proyecto has trabajado más? ¿En cuáles menos? Indica tu participación describiendo las 3 ó 4 partes más importantes de tu aplicación, y el porcentaje del total de cada parte al que consideras que has contribuído. 

(contesta en el fichero `.md`, reemplazando este texto)

### pregunta B

Añadid la posibilidad de limitar (cuando se configura la partida) el número total de casillas del tablero. Lo más sencillo sería usar múltiplos de 8, para que el pintado no tenga que mostrar filas incompletas.

### pregunta C

Mostrad en el perfil de los usuarios una tabla con las últimas 5 partidas jugadas, cuándo se jugaron, contra quién, y con qué posición final (= número de casilla).

### pregunta D

Explicad qué cambios tendríais que hacer para implementar casillas comodín. Una casilla comodín permitiría a quien cae en ella elegir la categoría de pregunta que quiere intentar contestar. Para explicar los cambios, usa el siguiente formato: para cada fichero que tendrías que cambiar, da su nombre, y en una frase, explica de forma resumida qué cambiarías en él.

(contesta en el fichero `.md`, reemplazando este texto)

### pregunta E

Añade, en el diálogo de elección de respuesta de partidas multijugador, un checkbox "doble o nada" que aparezca sin marcar. Si se marca, y se acierta la pregunta, avanzas doble; pero si la fallas, retrocedes en lugar de avanzar (en ambos casos, el número de casillas que diga el dado).

### pregunta F

En la vista de scoreboard, haz que haya 2 apartados: el actual global, y uno que sólo muestre puntos de la última semana (el día actual y los 6 anteriores).
  
