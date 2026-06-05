Feature: Pruebas del flujo de partida Single Player y validación de respuestas

  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false

    # Registrar e iniciar sesión
    * def timestamp = java.lang.System.currentTimeMillis()
    * def username = 'solo_' + timestamp
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = username
    And form field email = username + '@test.com'
    And form field password = 'SoloPassword123'
    And form field pass2 = 'SoloPassword123'
    When method post
    Then status 302


  Scenario: Flujo completo - Iniciar partida, capturar preguntas y validar respuestas (Acierto/Fallo)
    
    Given path '/game/start_single_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }

    And form field questionCount = 5
    And form field difficulty = 'EASY'
    And form field category = ''
    When method post
    # El controlador devuelve la vista "single_game"
    Then status 200
    And match response contains 'TriviaBlast - Single Player'

    # Enviamos una respuesta cualquiera
    Given path '/game/answer'
    * configure headers = { 'Content-Type': 'application/json' }
    And request { questionId: 0, answer: 'Cualquier Respuesta' }
    When method post
    Then status 200
    # Comprobamos la estructura del AnswerResDTO devuelto por el @ResponseBody
    And match response.correctAnswer == '#string'
    And match response.correct == '#boolean'
    * print response.correctAnswer
    * print response.correct
    * def solucion = response.correctAnswer;

    # Validación del acierto usando la respuesta descubierta
    Given path '/game/answer'
    * configure headers = { 'Content-Type': 'application/json' }
    # Creamos un esqueleto de JSON
    * def miPeticion = { questionId: 0, answer: '' }
    # Le inyectamos el valor de la variable de forma explícita
    * set miPeticion.answer = solucion
    And request miPeticion
    When method post
    Then status 200
    And match response.correct == true

    # Validación del fallo con una respuesta falsa
    Given path '/game/answer'
    And request { questionId: 0, answer: 'ESTO_ES_UNA_RESPUESTA_FALSA_SEGURO' }
    When method post
    Then status 200
    And match response.correct == false

  Scenario: Intentar responder a una pregunta con un ID inexistente o fuera de rango
    Given path '/game/start_single_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field questionCount = 5
    And form field difficulty = 'EASY'
    When method post
    Then status 302

    Given path '/game/answer'
    * configure headers = { 'Content-Type': 'application/json' }
    And request { questionId: 99, answer: 'No importa' }
    When method post
    Then status 200
    And match response.correct == false
    And match response.correctAnswer == '#null'