package external;

import com.intuit.karate.junit5.Karate;

class ExternalRunnerTest {

    // TESTS DE PARTIDA

    @Karate.Test
    Karate testCreateGame() {
        return Karate.run("create-game").relativeTo(getClass());
    }

    @Karate.Test
    Karate testJoinRoom() {
        return Karate.run("join-game").relativeTo(getClass());
    }

    @Karate.Test
    Karate testAnswer() {
        return Karate.run("answer-question").relativeTo(getClass());
    }

    // TESTS DE CUENTA

    @Karate.Test
    Karate testCreateAccount() {
        return Karate.run("create-account").relativeTo(getClass());
    }

    @Karate.Test
    Karate testEditAccount() {
        return Karate.run("edit-account").relativeTo(getClass());
    }

    @Karate.Test
    Karate testDeleteAccount() {
        return Karate.run("delete-account").relativeTo(getClass());
    }
}
