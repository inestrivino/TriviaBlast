Feature: cannot join singleplayer game

Scenario: user tries to join a singleplayer game
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")

    Given driver baseUrl + '/room/join'
    And input('#code', 'SINGLE123')
    When submit().click("button")

    Then match html('body') contains 'cannot join'