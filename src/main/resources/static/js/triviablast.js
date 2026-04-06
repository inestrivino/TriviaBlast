document.addEventListener('DOMContentLoaded', () => {
    initAuthToggle();
    initBoard();
    initTableToggleButtons();

    if (window.gameCode) {
    initMultiplayer();
    } else {
    initSingleplayer();
    }

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
   BOARD (SVG Game Board)
========================= */
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

function initSingleplayer() {

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
};

/* =========================
   MULTIPLAYER TRIVIA SCRIPT
========================= */

function initMultiplayer() {

    let currentIndex = 0;

    const statusEl = document.getElementById("gameStatus");
    const nextBtn = document.getElementById("nextBtn");

    function updateStatus() {
        const total = window.questions.length;
        statusEl.textContent = `${currentIndex + 1}/${total}`;
    }
    const socket = new SockJS('/ws');
    const stomp = Stomp.over(socket);

    stomp.connect({}, () => {

        console.log("Connected to multiplayer");
        stomp.subscribe(`/topic/game/${window.gameCode}`, (msg) => {
            const data = JSON.parse(msg.body);
            handleLiveUpdate(data);
        });

    });

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

            btn.onclick = () => {
                stomp.send(`/app/game/${window.gameCode}/answer`, {}, JSON.stringify({
                    questionId: q.id,
                    answer: answer,
                    userId: window.userId
                }));
            };

            answersEl.appendChild(btn);
        });

        updateStatus();
    }

    function handleLiveUpdate(data) {

        const answersEl = document.getElementById("answers");
        const feedbackEl = document.getElementById("feedback");
        Array.from(answersEl.children).forEach(btn => btn.disabled = true);

        const correctAnswer = decodeHtml(data.correctAnswer).trim().toLowerCase();
        Array.from(answersEl.children).forEach(btn => {
            if (btn.textContent.trim().toLowerCase() === correctAnswer) {
                btn.classList.replace("btn-outline-primary", "btn-success");
            }
        });

        const isCorrect= Boolean(data.correct === true || data.correct === 'true')
        if (isCorrect) {
            feedbackEl.textContent = "Correct!";
        } else {
            feedbackEl.textContent = "Wrong!";
        }
        setTimeout(() => {
            currentIndex++;

            if (currentIndex < window.questions.length) {
                showQuestion(currentIndex);
            } else {
                document.getElementById("gameCard").innerHTML =
                    `<div class="text-center fs-4 fw-bold">
                        Game Over!
                    </div>`;
            }
        }, 2000);
    }

    showQuestion(currentIndex);
}

/* =========================
   HELPERS
========================= */
function decodeHtml(html) {
    const txt = document.createElement("textarea");
    txt.innerHTML = html;
    return txt.value;
}