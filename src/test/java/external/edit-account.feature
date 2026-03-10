Feature: edit account

Scenario: change username
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/user/2')

    Given driver baseUrl + '/user/edit'
    And input('#username', 'b2')
    When submit().click("button")

    Then match html('body') contains 'b2'