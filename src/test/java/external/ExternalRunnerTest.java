package external;

import com.intuit.karate.junit5.Karate;

class ExternalRunnerTest {

    // TESTS DE PARTIDA

    // crear partida

    @Karate.Test
    Karate testJoinRoom() {
        return Karate.run("join-room").relativeTo(getClass());
    }

    @Karate.Test
    Karate testAnswer() {
        return Karate.run("answer-question").relativeTo(getClass());
    }

    // TESTS DE MODERACION

    // envio de reporte al administrador
    // envio de reporte de partida al administrador

    // TESTS DE CUENTA

    @Karate.Test
    Karate testCreateAccount() {
        return Karate.run("create-account").relativeTo(getClass());
    }

    @Karate.Test
    Karate testLogin() {
        return Karate.run("login").relativeTo(getClass());
    }

    @Karate.Test
    Karate testLogout() {
        return Karate.run("logout").relativeTo(getClass());
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
