package es.ucm.fdi.iw;

import java.util.ArrayList;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import es.ucm.fdi.iw.model.User;

/**
* SERVICIO DE AUTENTICACIÓN 

* Spring Security necesita este servicio para saber cómo buscar un usuario
* por nombre de usuario en la BD
* Es el adaptador entre nuestra entidad User y el sistema de autenticación de Spring
*/

/**
 * Authenticates login attempts against a JPA database
 */
public class IwUserDetailsService implements UserDetailsService {

	private static Logger log = LogManager.getLogger(IwUserDetailsService.class);

    private EntityManager entityManager;
    
    @PersistenceContext
    public void setEntityManager(EntityManager em){
        this.entityManager = em;
    }

	/*
	* Busca el usuario en la BD con la named query "User.byUsername" (definida en User.java)
	* Si lo encuentra, construye un UserDetails de Spring con sus roles
	* (p.ej. "USER" → "ROLE_USER", "ADMIN" → "ROLE_ADMIN")
	* Si no lo encuentra, lanza UsernameNotFoundException
	*/

    public UserDetails loadUserByUsername(String username){
    	try {
	        User u = entityManager.createNamedQuery("User.byUsername", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
	        // build UserDetails object
	        ArrayList<SimpleGrantedAuthority> roles = new ArrayList<>();
	        for (String r : u.getRoles().split("[,]")) {
	        	roles.add(new SimpleGrantedAuthority("ROLE_" + r));
		        log.info("Roles for " + username + " include " + roles.get(roles.size()-1));
	        }
	        return new org.springframework.security.core.userdetails.User(
	        		u.getUsername(), u.getPassword(), roles); 
	    } catch (Exception e) {
    		log.info("No such user: " + username + " (error = " + e.getMessage() + ")");
    		throw new UsernameNotFoundException(username);
    	}
    }
}