Feature: board generation

Scenario: board appears
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/user/2')

    Given driver baseUrl + '/game'
    Then match html('body') contains 'board'