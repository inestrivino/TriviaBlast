/**
* JAVASCRIPT DE CLIENTE PRINCIPAL
* 
* Contiene toda la lógica del lado del cliente para la interfaz de usuario
* Está organizado en las siguientes secciones:
* - AUTH: para login y register
* - SCOREBOARD & ADMIN MODERATION : Para la renderización de la tabla de puntos y gestionar la visibilidad de los usuarios
* - ADMIN: Funciones para la visualización dinámica de la pantalla de admin
* - SINGLEPLAYER TRIVIA SCRIPT: Para manejar la visualización de los elementos del juego singleplayer
* - HELPERS: Funciones para manejar la entrada  de texto en HTML (evitar inyecciones y garantizar comparaciones correctas)
*/
"use strict";

// Cuando se cargue la página se inicializan las funciones de este documento
document.addEventListener('DOMContentLoaded', () => {
    // Inicializaciones comunes
    initAuthToggle();
    initTableToggleButtons();
    initUserReportButtons();

    // Inicializaciones exclusivas del panel de administración
    initAdminMessagesTable();
    initAdminWebSocketAlerts();

    // Inicialización del juego Singleplayer
    initSingleplayerGame();
});

/* =========================
   AUTH (Login / Register)
========================= */
// Para hacer funcionar el "toggle" para mostrar el formulario de log in o sign in
function initAuthToggle() {
    const login = document.getElementById("loginForm");
    const register = document.getElementById("registerForm");

    if (!login || !register) return;

    window.toggleForms = function () {
        const isHidden = login.style.display === "none";
        login.style.display = isHidden ? "block" : "none";
        register.style.display = isHidden ? "none" : "block";
    };
}

/* =========================
   SCOREBOARD & ADMIN MODERATION
========================= */
// Inicializa los botones de "Hide" o "Display" que cambian el estado de "enabled" de los usuarios
function initTableToggleButtons() {
    // Obtiene los botones
    const buttons = document.querySelectorAll('.toggle-btn');
    if (!buttons.length) return;

    // Les añade un event listener tal que cuando se hace click sobre ellos se llama al método para cambiar el
    // atributo "enabled" del usuario en la base de datos
    buttons.forEach(button => {
        button.addEventListener('click', () => {
            // se obtiene el id del usuario al que se refiere el botón para crear el url de llamada al backend
            const userId = button.dataset.id;
            const url = window.location.origin + '/admin/toggleView/' + userId;

            // llamada al backend
            fetch(url, {
                method: "POST",
                headers: {
                    [config.csrf.header]: config.csrf.value
                }
            })
                .then(res => {
                    //si da error se lanza un mensaje indicandolo
                    if (!res.ok) throw new Error("Error updating visibility");
                    return res.json();
                })
                .then(data => {
                    // si todo fue bien entonces se re-renderiza al usuario para que se vea apagado o mejor según su nueva visibilidad
                    if (!data) return;

                    // TO DO: HACER QUE FUNCIONE POR AJAX IGUAL QUE LO DE ADMIN
                    if (window.location.pathname.includes('scoreboard')) {
                        location.reload();
                        return;
                    }

                    const tr = button.closest('tr');
                    if (!tr) return;

                    const statusCell = tr.querySelector('.status-text');
                    const isVisible = data.enabled;

                    if (!isVisible) {
                        tr.classList.add('opacity-50');
                        button.textContent = 'Display';
                        button.classList.replace('btn-outline-danger', 'btn-success');

                        if (statusCell) {
                            statusCell.className = 'status-text fw-bold text-danger';
                            statusCell.textContent = 'Oculto';
                        }
                    } else {
                        tr.classList.remove('opacity-50');
                        button.textContent = 'Hide';
                        button.classList.replace('btn-success', 'btn-outline-danger');

                        if (statusCell) {
                            statusCell.className = 'status-text fw-bold text-success';
                            statusCell.textContent = 'Visible';
                        }
                    }
                })
                .catch(err => console.error("Error en toggle-user:", err));
        });
    });
}

// Permite a los usuarios "denunciar" a un usuario del scoreboard ante los administradores
function initUserReportButtons() {
    // Se obtienen los botones de report
    const reportButtons = document.querySelectorAll(".btn-report");
    if (!reportButtons.length) return;

    // a cada uno se le asigna un action listener para llamar al método de report cuando se hace click
    reportButtons.forEach(button => {
        button.addEventListener("click", function () {
            // se obtiene el id y el nombre del usuario al que se denuncia
            const userId = this.getAttribute("data-id");
            const username = this.getAttribute("data-username");

            //se le pide confirmación al usuario
            if (confirm(`¿Estás seguro de que deseas denunciar al jugador "${username}" ante los administradores?`)) {
                //llamada al método report en el backend con el id del usuario denunciado
                fetch(`/user/report/${userId}`, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        [config.csrf.header]: config.csrf.value
                    }
                })
                    .then(response => {
                        if (!response.ok) throw new Error("Network response error");
                        return response.json();
                    })
                    .then(data => {
                        if (data.status === "success") {
                            alert("Denuncia enviada de forma confidencial. El equipo de soporte lo revisará pronto.");
                        } else {
                            alert("Error: " + data.message);
                        }
                    })
                    .catch(error => {
                        console.error("Error al enviar el reporte:", error);
                        alert("No se pudo contactar con el servidor o procesar la denuncia.");
                    });
            }
        });
    });
}

/* =========================
   ADMIN
========================= */
//tabla de mensajes
let adminDataTableInstance = null;

// inicialización de mensajes de tabla de mensajes de admin
function initAdminMessagesTable() {
    //se obtienen los divs necesarios para la tabla de mensajes y el refresco
    const tableEl = document.getElementById("messages");
    const refreshBtn = document.getElementById("refresh");
    if (!tableEl) return;

    //funcion interna para renderizar la tabla de mensajes
    function refreshMessages() {
        //se obtienen los mensajes en la BD
        window.go('/admin/all-messages', 'GET').then((d) => {
            if (adminDataTableInstance) {
                adminDataTableInstance.destroy();
                tableEl.innerHTML = '';
            }

            const tableData = d.map(m => [m.from || 'Sistema', m.to || 'Todos', m.text]);
            
            //creamos la tabla (con la librería correspondiente) y la rellenamos
            //TO DO: HACER QUE FUNCIONE
            adminDataTableInstance = new simpleDatatables.DataTable('#messages', {
                data: {
                    headings: ['From', 'To', 'Text'],
                    data: tableData
                },
                searchable: true,
                paging: false
            });
        });
    }

    //se crea un event listener para que cuando se haga click al botón
    //de refresco se ejecute la funcion interna de render de la tabla de mensajes
    if (refreshBtn) {
        refreshBtn.addEventListener('click', refreshMessages);
    }
    //se la llama una vez al cargar la página
    refreshMessages();
}

// inicialización de tabla de mensajes de alerta para admin
function initAdminWebSocketAlerts() {
    // obtenemos el panel de alertas
    const alertsPanel = document.getElementById("live-alerts-panel");
    if (!alertsPanel) return;

    // funcion interna para recibir los mensajes dedicados a admin en el websocket
    function interceptarMensajesAdmin() {
        if (typeof window.ws !== 'undefined' && window.ws.connected) {
            // Salvamos el comportamiento base para no romper otras funciones globales
            const originalReceive = window.ws.receive;

            // Sobrescribimos el manejador para capturar los mensajes entrantes de /topic/admin
            window.ws.receive = function (alerta) {
                //empezamos realizando las acciones pre-definidas base para la
                //recepción de un mensaje en el websocket (mirar iw.js)
                if (typeof originalReceive === "function") {
                    originalReceive(alerta);
                }

                //linea para debugging
                console.log("Mensaje capturado en panel de administración:", alerta);

                // Comprobamos que el mensaje sea una denuncia
                if (alerta.from && alerta.from.includes("Denuncia") || alerta.text) {

                    // Obtenemos el elemento que indica la falta de alertas y lo quitamos
                    const noAlertsMsg = document.getElementById("no-alerts-msg");
                    if (noAlertsMsg) noAlertsMsg.remove();

                    // creamos un nuevo div para mostrar la alerta
                    const item = document.createElement("div");
                    item.className = "list-group-item list-group-item-action d-flex justify-content-between align-items-center";

                    // obtenemos la hora a la que fue enviada la alerta
                    const hora = alerta.sent || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                    // insertamos el html correspondiente para la renderización de la alerta
                    item.innerHTML = `
                        <div>
                            <span class="badge bg-danger me-2">Denuncia</span>
                            <span>${escapeHtml(alerta.text)}</span>
                        </div>
                        <small class="text-muted">${hora}</small>
                    `;

                    // La insertamos arriba (más nuevas primero)
                    alertsPanel.insertBefore(item, alertsPanel.firstChild);

                    // Actualizamos el controlador numérico del panel
                    const contador = document.getElementById("alert-counter");
                    if (contador) {
                        const numeroAlertas = alertsPanel.querySelectorAll('.list-group-item:not(#no-alerts-msg)').length;
                        contador.innerText = `${numeroAlertas} nuevas`;
                        contador.classList.add("badge-alert");
                    }
                }
            };

            // linea para debugging
            console.log("Interceptor de administración acoplado correctamente a iw.js");
        } else {
            // Reintentar si el socket tarda unos milisegundos en conectar
            setTimeout(interceptarMensajesAdmin, 300);
        }
    }

    // se inicializa una vez al cargar la página para mostrar todas las denuncias que hubiese
    interceptarMensajesAdmin();
}

/* =========================
   SINGLEPLAYER TRIVIA SCRIPT
========================= */

// se inicializan los elementos para el juego singleplayer
function initSingleplayerGame() {
    // Evita ejecutar código innecesario si no estamos en la vista de juego
    const gameCard = document.getElementById("gameCard");
    if (!gameCard) return;

    // valores al principio del juego
    let currentIndex = 0;
    let score = 0;

    // se obtienen el div donde se muestran el indice de la pregunta y puntos obtenidos, 
    // y el botón para pasar a la siguiente pregunta
    const statusEl = document.getElementById("gameStatus");
    const nextBtn = document.getElementById("nextBtn");

    // funcion interna para actualizar lo que se muestra en el div de manera dinámica
    function updateStatus() {
        const total = window.questions.length;
        if (statusEl) {
            statusEl.textContent = `${currentIndex + 1}/${total} ${score} points`;
        }
    }

    // funcion interna para mostrar las preguntas recibidas desde el backend dinámicamente
    function showQuestion(index) {
        if (!window.questions || !window.questions[index]) return;
        //las preguntas se inyectan en los datos del navegador como un array *questions* 
        // y es de ahí de donde las sacamos para mostrarlas
        const q = window.questions[index];

        //se obtiene el texto de la pregunta actual
        document.getElementById("question").textContent = decodeHtml(q.question);

        //se obtiene el div de las respuestas posibles y el div para mostrar el feedback
        //ante la respuesta del usuario
        const answersEl = document.getElementById("answers");
        const feedbackEl = document.getElementById("feedback");

        answersEl.innerHTML = "";
        feedbackEl.innerHTML = "";
        nextBtn.disabled = true;

        //por cada respuesta a la pregunta actual se crea un botón con el texto, 
        // que envíe la respuesta al hacer click
        q.answers.forEach(answer => {
            const btn = document.createElement("button");
            btn.className = "btn btn-outline-primary";
            btn.textContent = decodeHtml(answer);

            btn.onclick = () => sendAnswer(answer, q.id, btn);
            answersEl.appendChild(btn);
        });

        // cada vez que se muestra una pregunta nueva se actualiza el status de la partida
        // (cual es el indice actual y cuantos puntos se llevan)
        updateStatus();
    }

    //función interna para enviar la respuesta elegida al backend para validación
    function sendAnswer(answer, questionId, clickedBtn) {
        fetch("/game/answer", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                [config.csrf.header]: config.csrf.value
            },
            body: JSON.stringify({ questionId, answer })
        })
            .then(res => res.json())
            .then(data => {
                const answersEl = document.getElementById("answers");
                const feedbackEl = document.getElementById("feedback");

                // se desabilitan los botones (no se puede enviar la respuesta dos veces)
                Array.from(answersEl.children).forEach(btn => btn.disabled = true);
                const isCorrect = (data.correct === true || data.correct === 'true');

                // se actualiza el div de feedback y los botones dependiendo de si la respuesta fue correcta o no
                if (isCorrect) {
                    clickedBtn.classList.replace("btn-outline-primary", "btn-success");
                    feedbackEl.textContent = "Correct!";
                    score += 10;
                } else {
                    clickedBtn.classList.replace("btn-outline-primary", "btn-danger");
                    feedbackEl.textContent = "Incorrect!";

                    const correctAnswer = decodeHtml(data.correctAnswer).trim().toLowerCase();

                    Array.from(answersEl.children).forEach(btn => {
                        if (btn.textContent.trim().toLowerCase() === correctAnswer) {
                            btn.classList.replace("btn-outline-primary", "btn-success");
                        }
                    });
                }

                // se muestra el botón para pasar a la siguiente pregunta
                nextBtn.disabled = false;
                // se actualiza el estado de la partida (reflejar los puntos nuevos si hay)
                updateStatus();
            });
    }

    // action listener para el botón de siguiente pregunta. aumenta el indice de la pregunta
    // y determina cuándo se acabó la partida
    nextBtn.onclick = () => {
        currentIndex++;
        if (currentIndex < window.questions.length) {
            showQuestion(currentIndex);
        } else {
            gameCard.innerHTML =
                `<div class="text-center fs-4 fw-bold">
                    Game Over!<br>Your Score: ${score} points
                </div>`;
            nextBtn.hidden = true;
        }
    };

    // Empezamos mostrando la primera pregunta
    if (window.questions && window.questions.length > 0) {
        showQuestion(currentIndex);
    }
}

/* =========================
   HELPERS
========================= */
function decodeHtml(html) {
    const txt = document.createElement("textarea");
    txt.innerHTML = html;
    return txt.value;
}

function escapeHtml(text) {
    if (!text) return "";
    return text.toString()
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}