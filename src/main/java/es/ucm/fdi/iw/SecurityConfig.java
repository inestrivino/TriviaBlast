package es.ucm.fdi.iw;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


/**
* CONFIGURACIÓN DE SEGURIDAD 

* Define qué URLs requieren login, qué roles pueden acceder a qué,
* y cómo funciona el formulario de login
*/


/**
 * Security configuration.
 * 
 * Most security configuration will appear in this file, but according to
 * https://spring.io/guides/topicals/spring-security-architecture/, it is not
 * a bad idea to also use method security (via @Secured annotations in methods)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private Environment env;

	/**
	 * Main security configuration.
	 * 
	 * The first rule that matches will be followed - so if a rule decides to grant
	 * access
	 * to a resource, a later rule cannot deny that access, and vice-versa.
	 * 
	 * To disable security entirely, just add an .antMatchers("**").permitAll()
	 * as a first rule. Note that this may break an application that expects to have
	 * login information available.
	 */

	/*
	* El método principal
	* Define las reglas de acceso:
	* · /css/**, /js/**, /img/**, /error → públicas (sin login)
	* · /login, /user/register, /proposal, /authors, /join_game,
	* /multi_game*, /game/join, /game/lobby/** → públicas
	* · /api/** → públicas (la API no requiere login por defecto)
	* · /admin/** → solo rol ADMIN
	* · /user/** → solo rol USER
	* · resto → cualquier usuario autenticado
	* Si hay modo debug (es.ucm.fdi.debug=true), permite acceso a /h2
	* (consola de la base de datos H2)
	*/

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

		// acceso a consola h2 en modo debug
		String debugProperty = env.getProperty("es.ucm.fdi.debug");
		if (debugProperty != null && Boolean.parseBoolean(debugProperty.toLowerCase())) {
			http.csrf(csrf -> csrf
					.ignoringRequestMatchers("/h2/**"));
			http.authorizeHttpRequests(authorize -> authorize
					.requestMatchers("/h2/**").permitAll() // <-- no login for h2 console
			);
			http.headers(header -> header.frameOptions(frameOptions -> frameOptions.sameOrigin()));
		}

		http
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/api/**", "/game/**")) // game puede estar mal 
				.authorizeHttpRequests(authorize -> authorize

						// recursos públicos
						.requestMatchers("/css/**", "/js/**", "/img/**", "/", "/error").permitAll()

						// vistas públicas
						.requestMatchers(
								"/user/register",
								"/proposal",
								"/authors",
								"/index",
								"/join_game",
								"/login",
								"/game/join")
						.permitAll()

						.requestMatchers("/api/**").permitAll()

						.requestMatchers("/admin/**").hasRole("ADMIN")
						.requestMatchers("/user/**").hasRole("USER")

						.anyRequest().authenticated())
				.formLogin(formLogin -> formLogin
						.loginPage("/login")
						.permitAll()
						.successHandler(loginSuccessHandler));

		return http.build();
	}

	/**
	 * Declares a PasswordEncoder bean.
	 * 
	 * This allows you to write, in any part of Spring-managed code,
	 * `@Autowired PasswordEncoder passwordEncoder`, and have it initialized
	 * with the result of this method.
	 */

	// bean que cifra contraseñas con BCrypt

	@Bean
	public PasswordEncoder getPasswordEncoder() {
		// by default in Spring Security 5, a wrapped new BCryptPasswordEncoder();
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Declares a springDataUserDetailsService bean.
	 * 
	 * This is used to translate from Spring Security users to in-application users.
	 */

	// bean que conecta Spring Security con nuestra tabla de usuarios (IwUserDetailsService)
	@Bean
	public IwUserDetailsService springDataUserDetailsService() {
		return new IwUserDetailsService();
	}

	/**
	 * Declares an AuthenticationManager bean.
	 * 
	 * This can be used to auto-login into the site after creating new users, for
	 * example.
	 * See
	 * https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/index.html#publish-authentication-manager-bean
	 */

	//  permite autenticar usuarios manualmente desde código Java (p.ej. al registrar un usuario nuevo)
	@Bean
	public AuthenticationManager authenticationManager(
			UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider();
		authenticationProvider.setUserDetailsService(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);

		return new ProviderManager(authenticationProvider);
	}

	@Autowired
	private LoginSuccessHandler loginSuccessHandler;
}