Feature: answer question behaviour

Scenario: correct answer
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")

    Given driver baseUrl + '/game/question'
    And input('#answer', '4')
    When submit().click("button")

    Then match html('body') contains 'Correct'

Scenario: wrong answer
    Given driver baseUrl + '/login'
    And input('#username', 'b')
    And input('#password', 'aa')
    When submit().click(".form-signin button")

    Given driver baseUrl + '/game/question'
    And input('#answer', '5')
    When submit().click("button")

    Then match html('body') contains 'Incorrect'