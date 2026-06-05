package es.ucm.fdi.iw;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;


/**
* CONFIGURACIÓN GENERAL DE beans

* Clase de configuración de Spring
* Define beans globales accesibles con @Autowired en cualquier parte de la aplicación
*/

/**
 * General configuration for a Spring app.
 * 
 * Declares multiple beans (which can later be accessed anywhere) via
 * Spring magic. 
 */
@Configuration	
public class AppConfig {

	@Autowired
	private Environment env;
	
	/**
	 * Declares a LocalData bean.
	 * 
	 * This allows you to write, in any part of Spring-managed code, 
	 * `@Autowired LocalData localData`, and have it initialized
	 * with the result of this method. 
	 */	

	/*
	* Crea el bean LocalData a partir de la propiedad
	* "es.ucm.fdi.base-path" definida en application.properties
	* LocalData es el objeto que gestiona dónde se guardan los ficheros
	* de usuario (fotos de perfil, etc.)
	*/
    @Bean(name="localData")
    public LocalData getLocalData() {
    	return new LocalData(new File(env.getProperty("es.ucm.fdi.base-path")));
    } 
    
	/**
	 * Declares a MessageSource Spring bean.
	 * 
	 * This will be used to fill in internationalized (i18n for short) messages
	 * in your web templates.  
	 */   

	/*
	* Configura internacionalización (i18n)
	* Lee mensajes del fichero Messages.properties para mostrarlos en las plantillas
	* Thymeleaf con #{clave} 
	*/ 
    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("Messages");
        return messageSource;
    }
}
