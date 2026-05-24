Feature: Pruebas del endpoint de registro
  # Si se envían datos incorrectos, envía al usuario a la página de login otra vez
  # Si sí son correctos, entonces 
  
  Background:
    * url 'http://localhost:8080'
    * configure followRedirects = false
    
    # Generar datos aleatorios únicos
    * def timestamp = java.lang.System.currentTimeMillis()
    * def uniqueUsername = 'user_' + timestamp
    * def uniqueEmail = 'email' + timestamp + '@test.com'

  Scenario: Crear una cuenta con datos válidos 
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = uniqueEmail
    And form field password = 'SecurePassword123'
    And form field pass2 = 'SecurePassword123'
    When method post
    Then status 302
    And match responseHeaders['Location'][0] contains '/user/'

  Scenario: Intentar crear una cuenta con contraseñas distintas
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = uniqueEmail
    And form field password = 'SecurePassword123'
    And form field pass2 = 'PasswordDiferente456'
    When method post
    Then status 302
    And match responseHeaders['Location'][0] == 'http://localhost:8080/login'

  Scenario: Intentar crear una cuenta omitiendo la contraseña
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = uniqueEmail
    When method post
    Then status 302
    And match responseHeaders['Location'][0] == 'http://localhost:8080/login'

Scenario: Intentar crear una cuenta con un nombre de usuario que ya existe
    # 1. Primer registro
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = uniqueEmail
    And form field password = 'SecurePassword123'
    And form field pass2 = 'SecurePassword123'
    When method post
    Then status 302

    # 2. Segundo registro (mismo username)
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = 'different_' + uniqueEmail
    And form field password = 'AnotherPassword123'
    And form field pass2 = 'AnotherPassword123'
    When method post
    Then status 302
    And match responseHeaders['Location'][0] == 'http://localhost:8080/login'

  Scenario: Intentar crear una cuenta con un email que ya existe
    # 1. Primer registro
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername
    And form field email = uniqueEmail
    And form field password = 'SecurePassword123'
    And form field pass2 = 'SecurePassword123'
    When method post
    Then status 302

    # 2. Segundo registro (mismo email)
    Given path '/user/register'
    * configure headers = { 'Content-Type': 'application/x-www-form-urlencoded' }
    And form field username = uniqueUsername + '_different'
    And form field email = uniqueEmail
    And form field password = 'AnotherPassword123'
    And form field pass2 = 'AnotherPassword123'
    When method post
    Then status 302
    And match responseHeaders['Location'][0] == 'http://localhost:8080/login'