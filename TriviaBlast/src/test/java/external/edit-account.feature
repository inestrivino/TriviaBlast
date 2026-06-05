Feature: Pruebas del endpoint de edición de usuarios

  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false

    # Generador de datos aleatorios únicos
    * def timestamp = java.lang.System.currentTimeMillis()
    * def createUsername = function(suffix){ return 'edit_user_' + timestamp + '_' + suffix }
    * def createEmail = function(suffix){ return 'edit_email_' + timestamp + '_' + suffix + '@test.com' }

  Scenario: Un usuario puede editar su propio nombre de usuario y email con éxito
    # Registrar al usuario e iniciar sesión automáticamente
    * def username = createUsername('base')
    * def email = createEmail('base')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = username
    And form field email = email
    And form field password = 'SecurePassword123'
    And form field pass2 = 'SecurePassword123'
    When method post
    Then status 302
    * def location = responseHeaders['Location'][0]
    * def segments = location.split('/')
    * def userId = segments[segments.length - 1]

    # Proceder a editar los datos con datos válidos
    * def newUsername = createUsername('new')
    * def newEmail = createEmail('new')
    Given path '/user/' + userId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'userNameEmail'
    And form field username = newUsername
    And form field email = newEmail
    When method post
    # Al tener éxito, el controlador devuelve "redirect:/user/" + id
    Then status 302
    And match responseHeaders['Location'][0] contains '/user/' + userId


  Scenario: No se puede cambiar el nombre de usuario a uno que ya está en uso por otro jugador
    # Crear el Usuario Víctima (quien poseerá el username codiciado)
    * def victimUsername = createUsername('victim')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = victimUsername
    And form field email = createEmail('victim')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302

    # Crear el Usuario Atacante (quien intentará robar el username)
    * def attackerUsername = createUsername('attacker')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = attackerUsername
    And form field email = createEmail('attacker')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    * def attackerLocation = responseHeaders['Location'][0]
    * def attackerSegments = attackerLocation.split('/')
    * def attackerId = attackerSegments[attackerSegments.length - 1]

    # El Atacante intenta editar su perfil usando el username del usuario Víctima
    Given path '/user/' + attackerId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'userNameEmail'
    And form field username = victimUsername
    And form field email = createEmail('attacker_new')
    When method post
    # Devuelve "profile" directamente pero envia un mensaje de error
    Then status 200
    And match response contains 'Username already in use'


  Scenario: No se puede cambiar el email a uno que ya está en uso por otro jugador
    # Crear el Usuario Víctima (quien poseerá el email codiciado)
    * def victimEmail = createEmail('target_mail')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = createUsername('holder')
    And form field email = victimEmail
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302

    # Crear el Usuario Atacante
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = createUsername('thief')
    And form field email = createEmail('thief_mail')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    * def attackerLocation = responseHeaders['Location'][0]
    * def attackerSegments = attackerLocation.split('/')
    * def attackerId = attackerSegments[attackerSegments.length - 1]

    # El Atacante intenta editar su perfil usando el email del usuario Víctima
    Given path '/user/' + attackerId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'userNameEmail'
    And form field username = createUsername('thief_new')
    And form field email = victimEmail
    When method post
    Then status 200
    And match response contains 'Email already in use'


  Scenario: Intentar cambiar la contraseña proporcionando una contraseña actual incorrecta
    # Crear usuario base
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = createUsername('pwd_test')
    And form field email = createEmail('pwd_test')
    And form field password = 'CorrectPassword123'
    And form field pass2 = 'CorrectPassword123'
    When method post
    Then status 302
    * def location = responseHeaders['Location'][0]
    * def segments = location.split('/')
    * def userId = segments[segments.length - 1]

    # Intentar actualizar contraseña enviando una 'currentPassword' errónea
    Given path '/user/' + userId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'password'
    And form field currentPassword = 'WrongPassword456'
    And form field password = 'NewSecurePassword789'
    And form field pass2 = 'NewSecurePassword789'
    When method post
    Then status 200
    And match response contains 'Current password is incorrect'

Scenario: Un administrador puede editar el perfil de cualquier otro usuario
    # Iniciar sesión como ADMIN
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = 'admin'
    And form field password = 'adminPassword123'
    When method post
    Then status 302

    # Crear un usuario "víctima" que será modificado por el administrador
    * def victimUsername = createUsername('to_be_edited')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = victimUsername
    And form field email = createEmail('to_be_edited')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    * def victimLocation = responseHeaders['Location'][0]
    * def victimSegments = victimLocation.split('/')
    * def victimId = victimSegments[victimSegments.length - 1]

    # Iniciamos sesión como admin otra vez
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = 'admin'
    And form field password = 'adminPassword123'
    When method post
    Then status 302

    # Admin edita el perfil ajeno con éxito
    * def adminChangesUsername = createUsername('edited_by_admin')
    Given path '/user/' + victimId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'userNameEmail'
    And form field username = adminChangesUsername
    And form field email = createEmail('edited_by_admin')
    When method post
    Then status 302
    And match responseHeaders['Location'][0] contains '/user/' + victimId


  Scenario: Un usuario común NO puede editar el perfil de otro usuario diferente a sí mismo
    # Creamos usuario A
    * def attackerUser = createUsername('attacker')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = attackerUser
    And form field email = createEmail('attacker')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302

    # Creamos usuario B
    * def targetUser = createUsername('target')
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = targetUser
    And form field email = createEmail('target')
    And form field password = 'Password123'
    And form field pass2 = 'Password123'
    When method post
    Then status 302
    * def targetLocation = responseHeaders['Location'][0]
    * def targetSegments = targetLocation.split('/')
    * def targetId = targetSegments[targetSegments.length - 1]

    # Iniciamos sesión como A
    Given path '/login'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = attackerUser
    And form field password = 'Password123'
    When method post
    Then status 302

    # A intenta editar a B y falla con un forbidden
    Given path '/user/' + targetId
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field formType = 'userNameEmail'
    And form field username = createUsername('illegal_change')
    And form field email = createEmail('illegal_change')
    When method post
    Then status 403