Feature: join room

Scenario: user joins a room using code
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")
    Then waitForUrl(baseUrl + '/user/2')

    Given driver baseUrl + '/room/join'
    And input('#code', '1234')
    When submit().click("button")

    Then match html('body') contains 'Game'