Feature: logout

Scenario: user logs out
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/user/2')

    When submit().click("{button}logout")
    Then waitForUrl(baseUrl + '/login')