package es.ucm.fdi.iw.model;

/**
* INTERFAZ TRANSFERABLE 

* Interfaz genérica que deben implementar las entidades JPA que
* quieren poder serializarse a JSON de forma segura (para WebSocket
* o respuestas API)
*
* las entidades JPA tienen relaciones bidireccionales
* (@OneToMany / @ManyToOne) que pueden causar recursión infinita al
* serializar a JSON. La clase interna Transfer (o similar) rompe
* ese ciclo copiando solo los campos necesarios a un objeto plano
*/

/**
 * Used to json-ize objects
 */
public interface Transferable<T> {
    T toTransfer();
}
