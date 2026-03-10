Feature: cannot join room without login

Scenario: try to join room without login
    Given driver baseUrl + '/room/join'
    And input('#code', '1234')
    When submit().click("button")

    Then waitForUrl(baseUrl + '/login')