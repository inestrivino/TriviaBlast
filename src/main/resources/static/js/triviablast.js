/**
* JAVASCRIPT PRINCIPAL DEL FRONTEND

* Contiene toda la lógica del lado del cliente para la interfaz de usuario
* Se inicializa cuando el DOM está listo
*/

/*
* initTrivia() / bloque DOMContentLoaded interno:
* Lógica completa del juego individual (single player):
* · showQuestion(index): muestra la pregunta y sus respuestas como botones
* · sendAnswer(answer, questionId, btn): hace POST a /game/answer con
* fetch(), recibe {correct, correctAnswer}, colorea el botón
* (verde/rojo) y muestra "Correct!" o "Incorrect!"
*/
document.addEventListener('DOMContentLoaded', () => {
    initAuthToggle();
    initBoard();
    initTableToggleButtons();
    initTrivia();

});

/* =========================
   AUTH (Login / Register)
========================= */
/*
* Alterna visibilidad entre el formulario de login y el de
* registro en login.html. Expone window.toggleForms() para
* llamarla desde onclick en el HTML
*/
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
   BOARD (SVG Game Board)
========================= */
/*
* Dibuja el tablero SVG de 8x8 con la librería SVG.js cuando
* existe el elemento #tablero-container. El tablero recorre
* las casillas en espiral numerándolas del 1 al 64. Se redibuja
* al cambiar el tamaño de la ventana (responsive)
*/
function initBoard() {
    const container = document.getElementById('tablero-container');
    if (!container || typeof SVG === "undefined") return;

    const draw = SVG().addTo('#tablero-container').size('100%', '100%');

    const filas = 8;
    const columnas = 8;

    function redrawBoard() {

        const header = document.querySelector('header');
        const footer = document.querySelector('footer');

        const headerHeight = header ? header.offsetHeight : 0;
        const footerHeight = footer ? footer.offsetHeight : 0;

        const availableHeight = window.innerHeight - headerHeight - footerHeight - 32;
        const availableWidth = container.parentElement.clientWidth;

        const boardSize = Math.min(availableWidth, availableHeight);

        container.style.width = boardSize + 'px';
        container.style.height = boardSize + 'px';

        const cellSize = Math.floor(boardSize / filas);

        draw.clear();
        draw.viewbox(0, 0, columnas * cellSize, filas * cellSize);

        let number = 1;
        let x = 0, y = 0;
        let dx = 1, dy = 0;
        let grid = Array(filas).fill().map(() => Array(columnas).fill(false));

        for (let i = 0; i < filas * columnas; i++) {

            draw.rect(cellSize, cellSize)
                .move(x * cellSize, y * cellSize)
                .fill('#e0e0e0')
                .stroke({ width: 1, color: '#999' });

            draw.text(number.toString())
                .font({ size: cellSize / 2.5, family: 'Arial', anchor: 'middle' })
                .center(
                    x * cellSize + cellSize / 2,
                    y * cellSize + cellSize / 2
                );

            grid[y][x] = true;
            number++;

            if (dx === 1 && (x + dx >= columnas || grid[y][x + dx])) { dx = 0; dy = 1; }
            else if (dy === 1 && (y + dy >= filas || grid[y + dy][x])) { dx = -1; dy = 0; }
            else if (dx === -1 && (x + dx < 0 || grid[y][x + dx])) { dx = 0; dy = -1; }
            else if (dy === -1 && (y + dy < 0 || grid[y + dy][x])) { dx = 1; dy = 0; }

            x += dx;
            y += dy;
        }
    }

    redrawBoard();
    window.addEventListener('resize', redrawBoard);
}

/* =========================
   SCOREBOARD
========================= */
/*
* Para la tabla de usuarios en admin.html. Cuando se hace clic
* en un botón .toggle-btn, hace un POST a /admin/toggleView/{userId}
* con fetch() y actualiza visualmente la fila (opacidad + texto
* del botón) según el nuevo estado de visibilidad
*/
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
                const tr = button.closest('tr');
                const isVisible = data.visibilityState; 

                if (!isVisible) {
                    tr.classList.add('opacity-50');
                    button.textContent = 'Display';
                    button.classList.replace('btn-outline-danger', 'btn-success');
                } else {
                    tr.classList.remove('opacity-50');
                    button.textContent = 'Hide';
                    button.classList.replace('btn-success', 'btn-outline-danger');
                }
            })
            .catch(err => console.error(err));
        });
    });
}

/* =========================
   SINGLEPLAYER TRIVIA SCRIPT
========================= */

document.addEventListener("DOMContentLoaded", () => {

    let currentIndex = 0;
    let score = 0;

    const statusEl = document.getElementById("gameStatus");
    const nextBtn = document.getElementById("nextBtn");

    function updateStatus() {
        const total = window.questions.length;
        statusEl.textContent = `${currentIndex + 1}/${total} ${score} points`;
    }

    function showQuestion(index) {
        const q = window.questions[index];

        document.getElementById("question").innerHTML = q.question;
        const answersEl = document.getElementById("answers");
        const feedbackEl = document.getElementById("feedback");

        answersEl.innerHTML = "";
        feedbackEl.innerHTML = "";
        nextBtn.disabled = true;

        q.answers.forEach(answer => {
            const btn = document.createElement("button");
            btn.className = "btn btn-outline-primary";
            btn.innerHTML = answer;

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
            .then(res => res.json()
                .then(data => {
                    console.log("Clicked:", answer);
                    console.log("Server says correct:", data.correctAnswer);

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
                }));
    }

    // avanza a la siguiente pregunta o muestra "Game Over!" con la puntuación final si se acabaron
    nextBtn.onclick = () => {
        currentIndex++;
        if (currentIndex < window.questions.length) {
            showQuestion(currentIndex);
        } else {
            document.getElementById("gameCard").innerHTML =
                `<div class="text-center fs-4 fw-bold">
                    Game Over!<br>Your Score: ${score} points
                </div>`;
            nextBtn.hidden = true;
        }
    };

    showQuestion(currentIndex);
});

/* =========================
   HELPERS
========================= */
// Decodifica entidades HTML (p.ej. &amp; → &) que vienen en el texto de las preguntas de OpenTDB
function decodeHtml(html) {
    const txt = document.createElement("textarea");
    txt.innerHTML = html;
    return txt.value;
}