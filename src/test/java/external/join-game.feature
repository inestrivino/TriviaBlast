Feature: Pruebas extensas del proceso de unión a partida (Lobby)

  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false

    * def timestamp = java.lang.System.currentTimeMillis()
    * def createFields =
    """
    function(prefix) {
      return {
        username: 'player_' + prefix + '_' + timestamp,
        email: 'mail_' + prefix + '_' + timestamp + '@test.com',
        password: 'Password123',
        pass2: 'Password123'
      }
    }
    """
    
    * def registerAndLogin =
    """
    function(prefix) {
      var data = createFields(prefix);
      var res = karate.call('join-game.feature@RegisterUser', data);
      return data;
    }
    """

    @ignore @RegisterUser
    Scenario: Registrar usuario de soporte
        Given path '/user/register'
        And form fields __arg
        When method post
        Then status 302

    @ignore @RegisterAndJoin
    Scenario: Registrar y unir a un jugador de forma aislada
    # Registra al usuario (creando una sesión nueva limpia en este hilo de ejecución)
    Given path '/user/register'
    And form fields __arg
    When method post
    Then status 302
    
    # Se une inmediatamente al juego usando el código recibido en los argumentos
    Given path '/game/' + __arg.gameCode + '/join'
    When method post
    Then status 302


  Scenario: Intentar validar un código de partida que no existe
    # Registramos un usuario cualquiera para tener sesión activa
    * eval registerAndLogin('p_anon')
    Given url 'http://localhost:8080/game/join'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field gameCode = 'NONEXISTENT'
    When method post
    Then status 302

  Scenario: Intentar unirse a una partida que ya ha comenzado
    * eval registerAndLogin('host_started')
    Given path '/game/multi_game'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field difficulty = 'EASY'
    When method post
    Then status 302
    * def location = responseHeaders['Location'][0]
    * def segments = location.split('/')
    * def gameCode = segments[segments.length - 1]
    Given path '/game/' + gameCode + '/start'
    When method post
    Then status 302
    # Registrar un segundo usuario que intenta entrar por el formulario tradicional
    * eval registerAndLogin('p_tarde')
    Given url 'http://localhost:8080/game/join'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field gameCode = gameCode
    When method post
    # Al no estar en WAITING, el código mete el error en el modelo y devuelve "join_game"
    Then status 200

  Scenario: Intentar unirse a un código de partida inexistente
    # Registramos sesión activa
    * eval registerAndLogin('p_error_ajax')
    
    Given path '/game/FAKE99/join'
    When method post
    Then status 302