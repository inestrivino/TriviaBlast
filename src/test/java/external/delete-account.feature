Feature: delete account

Scenario: user deletes account
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")

    Given driver baseUrl + '/user/edit'
    When submit().click("{button}delete")

    Then waitForUrl(baseUrl + '/login')