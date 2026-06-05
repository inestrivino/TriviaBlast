package es.ucm.fdi.iw;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletContext;

/**
* CONFIGURACIÓN AL ARRANQUE
*
* Ejecuta código cuando Spring termina de inicializar el contexto
* Principalmente expone el flag "debug" al contexto de servlets,
* de forma que las plantillas Thymeleaf puedan acceder a él con ${application.debug}
*/


/**
 * This code will execute when the application first starts.
 * 
 * @author mfreire
 */
@Component
public class StartupConfig {
	
	private static final Logger log = LogManager.getLogger(StartupConfig.class);
	
	@Autowired
	private Environment env;

	@Autowired
	private ServletContext context;
	
	/*
	* lee la propiedad "es.ucm.fdi.debug" de
	* application.properties y la guarda en el ServletContext como
	* atributo "debug" (booleano)
	*/
	@EventListener(ContextRefreshedEvent.class)
	public void contextRefreshedEvent() {
		String debugProperty = env.getProperty("es.ucm.fdi.debug");
		context.setAttribute("debug", debugProperty != null 
				&& Boolean.parseBoolean(debugProperty.toLowerCase()));
		log.info("Setting global debug property to {}", 
				context.getAttribute("debug"));
	}
}