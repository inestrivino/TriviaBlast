package external;

import com.intuit.karate.junit5.Karate;

class ExternalRunnerTest {
    
    @Karate.Test
    Karate testLogin() {
        return Karate.run("login").relativeTo(getClass());
    }    

    @Karate.Test
    Karate testWs() {
        return Karate.run("ws").relativeTo(getClass());
    }  
    @Karate.Test
    Karate testJoinRoom() {
        return Karate.run("join-room").relativeTo(getClass());
    }

    @Karate.Test
    Karate testJoinRoomNoLogin() {
        return Karate.run("join-room-no-login").relativeTo(getClass());
    }
    @Karate.Test
    Karate testJoinRoomSingleplayer() {
        return Karate.run("join-room-singleplayer").relativeTo(getClass());
    }

    @Karate.Test
    Karate testAnswer() {
        return Karate.run("answer-question").relativeTo(getClass());
    }

    @Karate.Test
    Karate testCreateAccount() {
        return Karate.run("create-account").relativeTo(getClass());
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
    Karate testBoard() {
        return Karate.run("board-generated").relativeTo(getClass());
    }
    @Karate.Test
    Karate testDeleteAccount() {
        return Karate.run("delete-account").relativeTo(getClass());
    }   
}
