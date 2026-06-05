package es.ucm.fdi.iw;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
* CONFIGURACIÓN WEBSOCKET 

* Habilita el soporte de WebSockets con el protocolo STOMP, que permite
* comunicación en tiempo real bidireccional entre servidor y navegador
* Es lo que hace funcionar el chat y el lobby en tiempo real
*
* 1. el navegador se conecta a ws://servidor/ws con stomp.js
* 2. se suscribe a /topic/{codigo-partida}
* 3. el servidor llama a messagingTemplate.convertAndSend("/topic/"+codigo, json)
* para enviar mensajes a todos los jugadores de esa partida
*/

/**
 * Basic STOMP-powered websocket support
 * 
 * @see https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/web.html#websocket
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // límites de tiempo y tamaño de mensajes
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(15 * 1000).setSendBufferSizeLimit(512 * 1024);
    }

    // registra el endpoint /ws donde el navegador se conecta (via stomp.js en el frontend)
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("*");
        // allowedOrigins allows proxying; see https://stackoverflow.com/questions/33977803
    }

    /*
    * habilita el broker de mensajes en memoria con dos destinos:
    * · /topic → mensajes broadcast a todos los suscriptores de un tema
    * · /queue → mensajes a un usuario concreto
    */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
    }
}
