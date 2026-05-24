Feature: Pruebas del endpoint de eliminación de usuarios
  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false

    # Genera datos aleatorios
    * def timestamp = java.lang.System.currentTimeMillis()
    * def createUsername = function(suffix){ return 'user_' + timestamp + '_' + suffix }
    * def createEmail = function(suffix){ return 'email_' + timestamp + '_' + suffix + '@test.com' }

  Scenario: Un usuario puede eliminar su propia cuenta con éxito
    # Se crea la cuenta
    * def username = createUsername('self')
    * def email = createEmail('self')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = username
    And form field email = email
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    
    # Extraemos el ID del usuario creado desde la cabecera
    * def location = responseHeaders['Location'][0]
    * def segments = location.split('/')
    * def userId = segments[segments.length - 1]

    # Intentamos borrarnos
    Given path '/user/' + userId + '/delete'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    When method post
    Then status 302
    # Si se ha borrado a sí mismo, vamos a login
    And match responseHeaders['Location'][0] contains '/login'


  Scenario: Un administrador puede eliminar la cuenta de otro usuario
    # Iniciamos sesión como admin
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = 'a'
    And form field password = 'aa'
    When method post
    Then status 302

    # Crear un usuario indefenso que será borrado por el admin
    * def victimUsername = createUsername('victim')
    * def victimEmail = createEmail('victim')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = victimUsername
    And form field email = victimEmail
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    * def victimLocation = responseHeaders['Location'][0]
    * def victimSegments = victimLocation.split('/')
    * def victimId = victimSegments[victimSegments.length - 1]

    # Volvemos a iniciar sesión como admin
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = 'a'
    And form field password = 'aa'
    When method post
    Then status 302

    # El admin borra al usuario victima
    Given path '/user/' + victimId + '/delete'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    When method post
    Then status 302
    # Si no se borra a sí mismo se redirige al inicio
    And match responseHeaders['Location'][0] == 'http://localhost:8080/'


  Scenario: Un usuario común no puede eliminar la cuenta de otro usuario
    # Crear el Usuario A (Atacante)
    * def attackerUser = createUsername('attacker')
    * def attackerEmail = createEmail('attacker')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = attackerUser
    And form field email = attackerEmail
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302

    # Crear el Usuario B (Objetivo) 
    * def targetUser = createUsername('target')
    * def targetEmail = createEmail('target')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = targetUser
    And form field email = targetEmail
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    # Guardamos su id
    * def targetLocation = responseHeaders['Location'][0]
    * def targetSegments = targetLocation.split('/')
    * def targetId = targetSegments[targetSegments.length - 1]

    # Volver a loguearse como Usuario A 
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = attackerUser
    And form field password = 'Password123'
    When method post
    Then status 302

    # El usuario A intenta eliminar al usuario B
    Given path '/user/' + targetId + '/delete'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    When method post
    # Comprobamos que lance el error Forbidden
    Then status 403