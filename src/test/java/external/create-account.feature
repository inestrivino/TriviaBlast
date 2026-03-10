Feature: create account and login

Scenario: user creates an account and logs in
    Given driver baseUrl + '/register'
    And input('#username', 'testuser123')
    And input('#password', 'testpass')
    When submit().click("button")
    Then waitForUrl(baseUrl + '/login')

    Given driver baseUrl + '/login'
    And input('#username', 'testuser123')
    And input('#password', 'testpass')
    When submit().click(".form-signin button")

    Then match html('body') contains 'user'