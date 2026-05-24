Feature: Pruebas de creación de partida multijugador

  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false
    
    # Generar un usuario único para ser el Host de las partidas
    * def timestamp = java.lang.System.currentTimeMillis()
    * def hostUsername = 'host_' + timestamp
    * def hostEmail = 'host_' + timestamp + '@test.com'
    
    # Registrar al usuario Host 
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = hostUsername
    And form field email = hostEmail
    And form field password = 'SecurePassword123'
    And form field pass2 = 'SecurePassword123'
    When method post
    Then status 302

  Scenario: Crear una partida multijugador con categorías por defecto (vacías)
    Given path '/game/multi_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    # Enviamos solo la dificultad, simulando no haber marcado ninguna categoría
    And form field difficulty = 'MEDIUM'
    When method post
    # El controlador debe responder con un redirect al lobby
    Then status 302
    # Validamos que estamos en /game/lobby/{codigo}
    * def location = responseHeaders['Location'][0]
    And match location contains '/game/lobby/'
    # Extraemos el código generado a través de la url
    * def segments = location.split('/')
    * def gameCode = segments[segments.length - 1]
    # Validar que el código tiene la longitud correcta configurada en el backend
    And match gameCode == '#regex [A-Za-z0-9]{6}'

  Scenario: Crear una partida seleccionando categorías específicas
    Given path '/game/multi_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    # Mandamos dificultad y una lista de IDs de categorías (IDs 10 y 11)
    And form field difficulty = 'HARD'
    And form field categories = ['10', '11']
    When method post
    Then status 302
    * def location = responseHeaders['Location'][0]
    And match location contains '/game/lobby/'
    * def segments = location.split('/')
    * def gameCode = segments[segments.length - 1]
    And match gameCode == '#regex [A-Za-z0-9]{6}'

  Scenario: Intentar crear una partida sin estar autenticado 
    * configure cookies = null
    Given path '/game/multi_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field difficulty = 'EASY'
    When method post
    Then status 302
    # Verificamos que lo mande a la página de inicio de sesión
    And match responseHeaders['Location'][0] contains '/login'