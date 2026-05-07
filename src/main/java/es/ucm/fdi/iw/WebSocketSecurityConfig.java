package es.ucm.fdi.iw;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import es.ucm.fdi.iw.model.User;

/**
* SEGURIDAD WEBSOCKET 

* Similar a SecurityConfig pero para mensajes WebSocket STOMP
* Controla quién puede enviar y recibir mensajes por WebSocket
*/

/**
 * Similar to SecurityConfig, but for websockets that use STOMP.
 * 
 * @see https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html
 */
@Configuration
@EnableWebSocketSecurity  
public class WebSocketSecurityConfig {

    /*  
    * define las reglas:
    * · Enviar/recibir mensajes de /admin/** → solo ADMIN
    * · Cualquier otro mensaje → usuario autenticado
    */
    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        
        // necesario estar logeado para poder enviar
        // necesario ser admin para poder enviar a /admin/**, y para poder recibir de /admin/**
        messages
                .simpDestMatchers("/admin/**").hasRole("ADMIN")
                .simpSubscribeDestMatchers("/admin/**").hasRole("ADMIN")
                .anyMessage().authenticated();
        return messages.build();
    }
}