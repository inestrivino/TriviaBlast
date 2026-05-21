/**
* JAVASCRIPT PRINCIPAL DEL FRONTEND
* 
* Contiene toda la lógica del lado del cliente para la interfaz de usuario
* Se inicializa cuando el DOM está listo
*/

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
   SCOREBOARD & ADMIN VISIBILITY
========================= */
function initTableToggleButtons() {
    const buttons = document.querySelectorAll('.toggle-btn');
    if (!buttons.length) return;

    buttons.forEach(button => {
        button.addEventListener('click', () => {
            const userId = button.dataset.id;
            const url = window.location.origin + '/admin/toggleView/' + userId;

            fetch(url, {
                method: "POST",
                headers: {
                    [config.csrf.header]: config.csrf.value
                }
            })
                .then(res => {
                    if (!res.ok) throw new Error("Error updating visibility");
                    return res.json();
                })
                .then(data => {
                    if (!data) return;

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

/* =========================
   USER REPORTING (Banderita Roja)
========================= */
function initUserReportButtons() {
    const reportButtons = document.querySelectorAll(".btn-report");
    if (!reportButtons.length) return;

    reportButtons.forEach(button => {
        button.addEventListener("click", function () {
            const userId = this.getAttribute("data-id");
            const username = this.getAttribute("data-username");

            if (confirm(`¿Estás seguro de que deseas denunciar al jugador "${username}" ante los administradores?`)) {
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
   ADMIN: SISTEMA DE MENSAJES
========================= */
let adminDataTableInstance = null;

function initAdminMessagesTable() {
    const tableEl = document.getElementById("messages");
    const refreshBtn = document.getElementById("refresh");
    if (!tableEl) return;

    function refreshMessages() {
        window.go('/admin/all-messages', 'GET').then((d) => {
            if (adminDataTableInstance) {
                adminDataTableInstance.destroy();
                tableEl.innerHTML = '';
            }

            import("../js/simple-datatables-10.js").then((Module) => {
                const tableData = d.map(m => [m.from || 'Sistema', m.to || 'Todos', m.text]);
                adminDataTableInstance = new Module.DataTable('#messages', {
                    data: {
                        headings: ['From', 'To', 'Text'],
                        data: tableData
                    },
                    searchable: true,
                    paging: false
                });
            });
        });
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', refreshMessages);
    }
    refreshMessages();
}

/* =========================
   ADMIN: ALERTAS MODERACIÓN POR WEBSOCKETS
========================= */
function initAdminWebSocketAlerts() {
    const alertsPanel = document.getElementById("live-alerts-panel");
    if (!alertsPanel) return;

    function interceptarMensajesAdmin() {
        // Nos aseguramos de que iw.js haya inicializado el cliente y esté conectado
        if (typeof window.ws !== 'undefined' && window.ws.connected) {
            
            // Salvamos el comportamiento base para no romper otras funciones globales
            const originalReceive = window.ws.receive;

            // Sobrescribimos el manejador para capturar los mensajes entrantes de /topic/admin
            window.ws.receive = function (alerta) {
                
                if (typeof originalReceive === "function") {
                    originalReceive(alerta);
                }

                console.log("Mensaje capturado en panel de administración:", alerta);

                // Comprobamos que el mensaje sea efectivamente una denuncia
                if (alerta.from && alerta.from.includes("Denuncia") || alerta.text) {
                    
                    // 1. Limpiar el contenedor si estaba vacío
                    const noAlertsMsg = document.getElementById("no-alerts-msg");
                    if (noAlertsMsg) noAlertsMsg.remove();

                    // 2. Crear la fila con la alerta usando las clases de Bootstrap que ya tienes en el HTML
                    const item = document.createElement("div");
                    item.className = "list-group-item list-group-item-action d-flex justify-content-between align-items-center";

                    const hora = alerta.sent || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

                    // 3. Estructurar el HTML plano simulando exactamente lo que haría Thymeleaf
                    item.innerHTML = `
                        <div>
                            <span class="badge bg-danger me-2">Denuncia</span>
                            <span>${escapeHtml(alerta.text)}</span>
                        </div>
                        <small class="text-muted">${hora}</small>
                    `;

                    // 4. Insertar la alerta arriba del todo
                    alertsPanel.insertBefore(item, alertsPanel.firstChild);

                    // 5. Actualizar el contador numérico del panel
                    const contador = document.getElementById("alert-counter");
                    if (contador) {
                        const numeroAlertas = alertsPanel.querySelectorAll('.list-group-item:not(#no-alerts-msg)').length;
                        contador.innerText = `${numeroAlertas} nuevas`;
                        contador.classList.add("badge-alert"); // Aplica tu animación de parpadeo CSS
                    }
                }
            };

            console.log("Interceptor de administración acoplado correctamente a iw.js");
        } else {
            // Reintentar si el socket tarda unos milisegundos en conectar
            setTimeout(interceptarMensajesAdmin, 300);
        }
    }

    interceptarMensajesAdmin();
}

/* =========================
   SINGLEPLAYER TRIVIA SCRIPT
========================= */
function initSingleplayerGame() {
    const gameCard = document.getElementById("gameCard");
    if (!gameCard) return; // Evita ejecutar código innecesario si no estamos en la vista de juego

    let currentIndex = 0;
    let score = 0;

    const statusEl = document.getElementById("gameStatus");
    const nextBtn = document.getElementById("nextBtn");

    function updateStatus() {
        const total = window.questions.length;
        if (statusEl) {
            statusEl.textContent = `${currentIndex + 1}/${total} ${score} points`;
        }
    }

    function showQuestion(index) {
        if (!window.questions || !window.questions[index]) return;
        const q = window.questions[index];

        // CORRECCIÓN: Sanitización básica contra XSS usando textContent combinada con tu helper de decodificación
        document.getElementById("question").textContent = decodeHtml(q.question);

        const answersEl = document.getElementById("answers");
        const feedbackEl = document.getElementById("feedback");

        answersEl.innerHTML = "";
        feedbackEl.innerHTML = "";
        nextBtn.disabled = true;

        q.answers.forEach(answer => {
            const btn = document.createElement("button");
            btn.className = "btn btn-outline-primary";
            btn.textContent = decodeHtml(answer); // CORRECCIÓN: Evitamos innerHTML en las respuestas

            btn.onclick = () => sendAnswer(answer, q.id, btn);
            answersEl.appendChild(btn);
        });

        updateStatus();
    }

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

                Array.from(answersEl.children).forEach(btn => btn.disabled = true);
                const isCorrect = (data.correct === true || data.correct === 'true');

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

                nextBtn.disabled = false;
                updateStatus();
            });
    }

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