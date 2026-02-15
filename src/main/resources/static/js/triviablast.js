document.addEventListener('DOMContentLoaded', () => {
    initAuthToggle();
    initBoard();
    initTableToggleButtons();
    initTrivia();

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
   TABLE ROW TOGGLE
========================= */
function initTableToggleButtons() {

    const buttons = document.querySelectorAll('.toggle-btn');
    if (!buttons.length) return;

    buttons.forEach(button => {

        button.addEventListener('click', () => {

            const tr = button.closest('tr');
            tr.style.opacity = tr.style.opacity === '0.5' ? '1' : '0.5';

            if (button.textContent === 'Hide') {
                button.textContent = 'Display';
                button.classList.replace('btn-outline-danger', 'btn-success');
            } else {
                button.textContent = 'Hide';
                button.classList.replace('btn-success', 'btn-outline-danger');
            }

        });

    });
}

/* =========================
   TRIVIA LOGIC
========================= */
function initTrivia() {
    const questionData = window.questionData;
    if (!questionData) return;
    if (typeof questionData === "undefined") return;

    const questionEl = document.getElementById('question');
    const answersEl = document.getElementById('answers');
    const feedback = document.getElementById('feedback');

    if (!questionEl || !answersEl) return;

    if (questionData.response_code === 5) {
        questionEl.textContent =
            'API rate limit reached, wait a few seconds and reload';
        answersEl.innerHTML = '';
        return;
    }

    if (!questionData.results || questionData.results.length === 0) {
        questionEl.textContent = 'No questions available.';
        answersEl.innerHTML = '';
        return;
    }

    const correctAnswer = decodeHTML(questionData.results[0].correct_answer);

    let answers = questionData.results[0].incorrect_answers.map(decodeHTML);
    answers.push(correctAnswer);
    answers.sort(() => Math.random() - 0.5);

    questionEl.innerHTML = decodeHTML(questionData.results[0].question);
    answersEl.innerHTML = '';

    answers.forEach(answer => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn-outline-primary';
        btn.textContent = answer;

        btn.addEventListener('click', () => checkAnswer(btn, correctAnswer));

        answersEl.appendChild(btn);
    });

    function checkAnswer(button, correctAnswer) {

        document.querySelectorAll('#answers button')
            .forEach(btn => btn.disabled = true);

        if (button.textContent === correctAnswer) {
            button.classList.replace('btn-outline-primary', 'btn-success');
            feedback.textContent = 'Correct!';
            feedback.classList.replace('text-danger', 'text-success');
        } else {
            button.classList.replace('btn-outline-primary', 'btn-danger');
            feedback.textContent = 'Incorrect!';
            feedback.classList.replace('text-success', 'text-danger');

            document.querySelectorAll('#answers button')
                .forEach(btn => {
                    if (btn.textContent === correctAnswer) {
                        btn.classList.replace('btn-outline-primary', 'btn-success');
                    }
                });
        }
    }
}

/* =========================
   HELPERS
========================= */
function decodeHTML(html) {
    const txt = document.createElement("textarea");
    txt.innerHTML = html;
    return txt.value;
}