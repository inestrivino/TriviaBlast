# How to deploy to Docker

1. SSH por navegador en https://guacamole.containers.fdi.ucm.es
2. El puesto 80 de la aplicación estará expuesto en https://vmXXX.containers.fdi.ucm.es, somos root en nuestras VMs. 
3. Crear perfiles: SPRING_PROFILES_ACTIVE a x hace que se aplica application-x.properties sobre el application.properties
4. Para conectarse con terminal (útil para tener la aplicación lanzada dos veces en una máquina):
    4.1 Hay que estar conectado a la VPN o en la UCM
    4.2 Abres una terminal y haces:
    ```bash
    ssh -L 222:vmXXX
    ```
    está el resto en las diapositivas
5. Mejor usar deploy.py, con cambios como indica las diapositivas para que funcione, en lugar de mvn, ya que mvn es lento y feo.
credentials.json debe ser alterado con las credenciales para poder acceder a las cosas. NO subir a github este ficher, repito NO subir a github (está ya configurado en el gitignore)

Tarda 4 minutos o así en verse la página funcional en el contenedor tras realizar el despliegue. 