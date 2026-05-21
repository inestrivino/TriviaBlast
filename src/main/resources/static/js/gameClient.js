// ===============================
// GAME CLIENT (LOBBY + MATCH)
// ===============================

window.GameClient = (() => {

    // -------------------------------
    // CONFIG (inyectado desde Thymeleaf)
    // -------------------------------
    let cfg = {
        rootUrl: "",
        gameCode: null,
        currentUserId: 0,
        hostId: 0,
        categories: null
    };

    // -------------------------------
    // STATE
    // -------------------------------
    let wsClient = null;
    let lobbyTopic = null;
    let statusTimer = null;

    let currentGameState = {
        players: []
    };

    // -------------------------------
    // INIT
    // -------------------------------
    uiBound = false;
    function bindUI() {

        if (uiBound) return;
        uiBound = true;

        document.addEventListener("click", e => {
            // LEAVE
            if (e.target.closest("#leave-inline-btn")) {
                leaveGame();
            }

            // KICK
            const kickBtn = e.target.closest("[data-kick-id]");

            if (kickBtn) {
                kickPlayer(
                    kickBtn.dataset.kickName,
                    kickBtn.dataset.kickId
                );
            }
        });
    }

    // -------------------------------
    // WEBSOCKET
    // -------------------------------
    let subscribed = false;
    function subscribeWS() {
        if (subscribed) return;

        if (!wsClient?.connected) {
            setTimeout(subscribeWS, 200);
            return;
        }
        wsClient.subscribe(lobbyTopic, msg => {
            handleWSMessage(msg);
        });
        subscribed = true;
    }

    function handleWSMessage(msg) {
        if (!msg) return;
        let data = msg;
        if (msg.body) {
            try {
                data = JSON.parse(msg.body);
            } catch (e) {
                console.error("Error parseando el mensaje del WebSocket:", e);
                return;
            }
        }

        switch (data.type) {
            case "game_finished":
                renderizarFinDePartida(data.podium);
                break;

            case "question":
                if (data.players) renderPlayers(data.players);
                if (data.gameState) currentGameState = data.gameState;

                initBoard();
                actualizarVisibilidadDado();

                if (data.gameState && data.gameState.activeQuestion) {
                    gestionarModalPregunta(data.gameState.activeQuestion);
                }
                break;

            case "update":
                if (data.players) renderPlayers(data.players);
                if (data.gameState) currentGameState = data.gameState;

                initBoard();
                actualizarVisibilidadDado();

                if (data.gameState && data.gameState.activeQuestion) {
                    gestionarModalPregunta(data.gameState.activeQuestion);
                } else {
                    gestionarModalPregunta(null);
                }
                break;

            case "game_start":
                if (typeof setStatus === "function") setStatus("Game starting...", "success");
                setTimeout(() => {
                    window.location.href = `/game/multi_game/${cfg.gameCode}`;
                }, 800);
                break;

            case "player_kicked":
                if (data.players) {
                    renderPlayers(data.players);
                    if (!window.currentGameState) window.currentGameState = {};
                    window.currentGameState.players = data.players;
                }
                if (data.kickedPlayer === "all") {
                    if (typeof setStatus === "function") setStatus(data.message || "Host left the game", "danger");
                    setTimeout(() => { window.location.href = "/"; }, 1500);
                    break;
                }
                if (Number(data.kickedPlayer) === Number(cfg.currentUserId)) {
                    if (typeof setStatus === "function") setStatus("You've been kicked", "danger");
                    setTimeout(() => { window.location.href = "/"; }, 1500);
                }
                loadPlayers();
                break;
        }
    }

    function init(config, ws) {
        cfg = { ...cfg, ...config };
        wsClient = ws;

        lobbyTopic = `/topic/${cfg.gameCode}`;

        if (wsClient) {
            subscribeWS();
        }

        bindUI();
    }

    // -------------------------------
    // PLAYERS RENDER (LOBBY + GAME)
    // -------------------------------
    function loadPlayers() {
        return go(`/game/${cfg.gameCode}/players`, "GET")
            .then(data => {
                if (data.players) currentGameState.players = data.players || [];
                if (data.gameState) {
                    currentGameState = data.gameState;
                }
                renderPlayers(currentGameState.players);
                const isMatch = cfg.mode === "match";
                if (isMatch) {
                    initBoard();
                    actualizarVisibilidadDado();
                }
                return data;
            });
    }

    function renderPlayers(players) {
        const list = document.querySelector(".player-list, #player-list");

        if (!list) return;

        if (!players?.length) {

            list.innerHTML = `
            <li class="list-group-item text-muted">
                Waiting...
            </li>`;

            return;
        }

        const count = document.getElementById("player-count");

        if (count) {
            count.textContent = players.length;
        }

        list.innerHTML = players.map(p => {

            const isMe = p.id === cfg.currentUserId;
            const isHost = (p.id === cfg.hostId);
            const isMatch = cfg.mode === "match";
            const iAmHost = (cfg.currentUserId === cfg.hostId);

            const style = isHost
                ? "background: linear-gradient(90deg,#fff4d6,#fff)"
                : isMe
                    ? "background: linear-gradient(90deg,#dff3ff,#fff)"
                    : "";

            const icon = isHost
                ? `<i class="bi bi-patch-check-fill text-warning me-2"></i>`
                : "";

            // -------------------------
            // ACTION BUTTONS
            // -------------------------

            let actionBtn = "";
            // HOST CAN KICK EVERYWHERE
            if (!isMe && iAmHost) {
                actionBtn = `
                    <button
                    class="btn btn-outline-danger btn-sm ms-2"
                    data-kick-id="${p.id}"
                    data-kick-name="${escapeHtml(p.username)}">
                    <i class="bi bi-person-x"></i>
                    </button>`;
            }

            // PLAYER CAN LEAVE
            else if (isMe) {
                actionBtn = `
                <button
                    class="btn btn-outline-secondary btn-sm ms-2"
                    id="leave-inline-btn">

                    <i class="bi bi-box-arrow-right"></i>

                </button>`;
            }

            // -------------------------
            // MATCH ONLY
            // -------------------------

            let pointsBadge = "";
            if (isMatch) {
                pointsBadge = `
                <span class="badge bg-primary">
                    ${p.points ?? 0} pts
                </span>`;
            }

            return `
        <li class="list-group-item d-flex justify-content-between align-items-center" style="${style}">

            <div class="d-flex align-items-center">
            <img src="/user/${p.id}/pic"
             class="rounded-circle me-2"
             style="width:38px;height:38px;object-fit:cover">

            <span class="${isHost ? 'fw-bold text-warning' : ''}">
            ${icon}${escapeHtml(p.username)}
            </span>
            </div>

            <div class="d-flex align-items-center gap-2">
            ${pointsBadge}  
            ${actionBtn}
            </div>

        </li>`;
        }).join("");
        const startBtn = document.getElementById("start-btn");

        if (startBtn) {
            startBtn.disabled = players.length < 2;
        }
    }

    // -------------------------------
    // LOBBY ACTIONS
    // -------------------------------
    function joinLobby() {
        return go(`/game/${cfg.gameCode}/join`, "POST")
            .then(data => {
                if (data.status === "full") {
                    setStatus(data.message, "danger");
                    document.getElementById("leave-btn").textContent = "Go back";
                    return;
                }
                if (data.players) renderPlayers(data.players);
                setStatus(`Welcome ${data.username}`, "success");
            })
            .catch(() => {
                setStatus("Connected (limited mode)", "warning");
            });
    }

    function startGame(btn) {
        if (!btn) return;

        btn.disabled = true;
        btn.textContent = "Starting...";

        go(`/game/${cfg.gameCode}/start`, "POST")
            .then(d => {
                if (d.status === "started") {
                    setStatus("Starting game...", "success");
                } else {
                    setStatus(d.message || "Error", "danger");
                    btn.disabled = false;
                    btn.textContent = "Start game";
                }
            })
            .catch(() => {
                setStatus("Error starting game", "danger");
                btn.disabled = false;
            });
    }

    function leaveGame() {
        return go(`/game/${cfg.gameCode}/leave`, "POST")
            .finally(() => {
                window.location.href = "/";
            });
    }

    function kickPlayer(username, userId) {
        if (!confirm(`Kick ${username}?`)) return;

        go(`/game/${cfg.gameCode}/kick/${encodeURIComponent(userId)}`, "POST")
            .catch(() => setStatus("Error kicking player", "danger"));
    }

    // -------------------------------
    // UI STATUS
    // -------------------------------
    function setStatus(text, type = "info") {
        const el = document.getElementById("lobby-status");
        if (!el) return;

        el.className = `alert alert-${type}`;
        el.textContent = text;
        el.style.display = "block";

        clearTimeout(statusTimer);
        statusTimer = setTimeout(() => {
            el.style.display = "none";
        }, 3000);
    }

    // -------------------------------
    // BOARD (GAME)
    // -------------------------------

    function initBoard() {
        const container = document.getElementById('tablero-container');
        if (!container || typeof SVG === "undefined") return;

        // ¡LA SOLUCIÓN QUIRÚRGICA! 
        // Vaciamos el contenedor borrando cualquier instancia previa de SVG antes de crear la nueva.
        container.innerHTML = "";

        const draw = SVG().addTo('#tablero-container');

        // 1. PALETA DE COLORES FIJOS
        const CATEGORY_COLORS = [
            '#E15759', // Rojo coral / Salmón oscuro
            '#F28E2B', // Naranja suave
            '#EDC948', // Amarillo mostaza claro (contrasta bien con blanco)
            '#59A14F', // Verde hierba suave
            '#4E79A7', // Azul clásico apagado
            '#B07AA1', // Lavanda / Violeta elegante
            '#FF9DA7', // Rosa pastel
        ];

        // 2. RECUPERAR CATEGORÍAS
        const categories = cfg.categories || [];

        const categoryColorMap = {};
        categories.forEach((cat, idx) => {
            const key = cat.name || cat.key || cat;
            categoryColorMap[key] = CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
        });

        function checkerPattern(group, size) {
            const half = size / 2;
            group.rect(half, half).move(0, 0).fill('#fff');
            group.rect(half, half).move(half, 0).fill('#000');
            group.rect(half, half).move(0, half).fill('#000');
            group.rect(half, half).move(half, half).fill('#fff');
        }

        // 3. OBTENER LA CATEGORÍA CORRESPONDIENTE A LA CASILLA
        function getCategoryForPosition(pos) {
            if (!categories.length) return null;
            // (pos - 1) hace que la casilla 1 use la primera categoría elegida, la 2 la segunda, etc.
            return categories[(pos - 1) % categories.length];
        }

        // Dibujado síncrono de avatares (las imágenes ya están garantizadas en la caché del navegador)
        function drawPlayers(cellGroup, pos, cellSize) {
            const players = currentGameState.players || [];
            const playersHere = players.filter(p => {
                let boardPos = p.boardPosition;
                if (boardPos < 1) boardPos = 0;
                if (boardPos > 64) boardPos = 65;
                return boardPos === pos;
            });

            const avatarSize = cellSize * 0.35;
            const positions = [
                { x: 0.15, y: 0.35 }, { x: 0.50, y: 0.35 },
                { x: 0.15, y: 0.65 }, { x: 0.50, y: 0.65 }
            ];

            playersHere.slice(0, 4).forEach((p, idx) => {
                const offset = positions[idx];
                const ax = cellSize * offset.x;
                const ay = cellSize * offset.y;

                const clip = cellGroup.circle(avatarSize).move(ax, ay);

                // Al estar precargada, se renderiza con sus dimensiones correctas inmediatamente
                cellGroup.image(`/user/${p.id}/pic`)
                    .move(ax, ay)
                    .size(avatarSize, avatarSize)
                    .clipWith(clip);

                cellGroup.circle(avatarSize)
                    .move(ax, ay)
                    .fill('none')
                    .stroke({ color: '#fff', width: 2 });
            });
        }

        const cols = 9;
        const rows = 8;

        function getBoardCoords(pos) {
            if (pos === 0) return { x: 0, y: 7 };
            if (pos === 65) return { x: 0, y: 0 };

            const adjPos = pos - 1;
            const gridCols = 8;

            const rowFromBottom = Math.floor(adjPos / gridCols);
            let col = adjPos % gridCols;

            if (rowFromBottom % 2 === 1) {
                col = gridCols - 1 - col;
            }

            return { x: col + 1, y: 7 - rowFromBottom };
        }

        function redraw() {
            draw.clear();

            // Buscamos el elemento de texto en el HTML
            const displayText = document.getElementById('categoria-display');
            const defaultText = "Pasa el ratón por una casilla para ver su categoría";

            const size = Math.max(600, Math.min(container.clientWidth, window.innerHeight * 0.85));
            container.style.height = `${size}px`;
            const cell = size / cols;
            draw.viewbox(0, 0, size, cell * rows);

            for (let i = 0; i <= 65; i++) {
                const { x, y } = getBoardCoords(i);
                const px = x * cell;
                const py = y * cell;

                const group = draw.group().transform({ translate: [px, py] });

                if (i === 0 || i === 65) {
                    checkerPattern(group, cell);
                    group.text(i === 0 ? "START" : "GOAL")
                        .font({ size: cell / 4, anchor: 'middle', weight: 'bold' })
                        .fill('#fff')
                        .stroke({ color: '#000', width: 1, linejoin: 'round' })
                        .center(cell / 2, cell / 2);

                    // Opcional: Mostrar START o GOAL en el h3 al pasar por encima
                    if (displayText) {
                        group.mouseover(() => {
                            displayText.innerText = i === 0 ? "Casilla de Salida (START)" : "¡Meta! (GOAL)";
                        });
                        group.mouseleave(() => {
                            displayText.innerText = defaultText;
                        });
                    }
                } else {
                    const category = getCategoryForPosition(i);
                    const categoryKey = category ? (category.name || category.key || category) : null;
                    const categoryLabel = category ? (category.label || categoryKey) : "Pregunta General";
                    const fill = categoryKey ? categoryColorMap[categoryKey] : '#f2f2f2';

                    group.rect(cell - 2, cell - 2)
                        .move(1, 1)
                        .fill(fill)
                        .stroke({ width: 1, color: '#999' })
                        .radius(8);

                    group.text(i.toString())
                        .font({ size: cell / 5, anchor: 'middle', weight: 'bold' })
                        .fill('#fff')
                        .center(cell / 2, cell * 0.25);

                    if (category) {
                        group.attr('data-tooltip', categoryLabel);
                    }

                    if (displayText) {
                        // Cuando el cursor entra en los límites de este grupo
                        group.mouseover(() => {
                            displayText.innerText = `Casilla ${i}: ${categoryLabel}`;
                            displayText.style.color = fill;
                        });

                        // Cuando el cursor sale del grupo, restauramos el texto y el color original
                        group.mouseleave(() => {
                            displayText.innerText = defaultText;
                            displayText.style.color = '#333';
                        });
                    }
                }
                drawPlayers(group, i, cell);
            }
        }

        redraw();
        // Registramos el evento resize de forma limpia
        window.addEventListener('resize', redraw);

    }

    // Variable de control para no duplicar escuchadores del botón
    let dadoListenerAsignado = false;

    function actualizarVisibilidadDado() {
        const btnDado = document.getElementById("btn-tirar-dado");
        if (!btnDado) return;

        // Leemos de la variable del módulo
        const state = currentGameState || {};

        // Comprobamos los IDs asegurando tipos numéricos
        const miTurno = Number(state.currentTurnPlayerId) === Number(cfg.currentUserId);

        if (miTurno) {
            btnDado.classList.remove("d-none");
        } else {
            btnDado.classList.add("d-none");
        }

        // Asignamos el evento click una sola vez
        if (!dadoListenerAsignado) {
            btnDado.addEventListener("click", () => {
                // Deshabilitamos inmediatamente para evitar doble click accidental
                btnDado.disabled = true;

                const headers = {
                    'Content-Type': 'application/json'
                };

                if (config && config.csrf && config.csrf.header && config.csrf.value) {
                    headers[config.csrf.header] = config.csrf.value;
                }

                fetch(`/game/${cfg.gameCode}/roll-dice`, {
                    method: 'POST',
                    headers: headers
                })
                    .then(res => res.json())
                    .then(data => {
                        btnDado.disabled = false;
                        if (data.status === "error") {
                            alert(data.message);
                        }
                    })
                    .catch(err => {
                        btnDado.disabled = false;
                        console.error("Error al tirar el dado:", err);
                    });
            });
            dadoListenerAsignado = true;
        }
    }

    // Instancia del modal de Bootstrap
    let bootstrapModalInstance = null;

    function gestionarModalPregunta(activeQuestion) {
        const modalEl = document.getElementById("questionModal");
        if (!modalEl) return;

        if (!bootstrapModalInstance) {
            bootstrapModalInstance = new bootstrap.Modal(modalEl);
        }

        if (!activeQuestion) {
            bootstrapModalInstance.hide();
            return;
        }

        const q = activeQuestion;
        const state = currentGameState || {};

        const miTurno = Number(state.currentTurnPlayerId) === Number(cfg.currentUserId);

        const jugadorActivo = (state.players || []).find(p => Number(p.id) === Number(state.currentTurnPlayerId));
        document.getElementById("modal-player-title").innerText = `Turn for player: ${jugadorActivo ? jugadorActivo.username : 'Player'}`;

        const diceRollEl = document.getElementById("modal-dice-roll");
        if (diceRollEl) {
            diceRollEl.innerText = state.lastDiceRoll ? state.lastDiceRoll : "-";
        }

        const categoryLabelEl = document.getElementById("modal-category-label");
        if (categoryLabelEl) {
            let nombreCategoria = "General";

            const categoriasPartida = cfg.categories || [];

            if (jugadorActivo && categoriasPartida.length > 0) {
                const pos = jugadorActivo.boardPosition || 0;
                if (pos >= 1 && pos <= 64) {
                    const idx = (pos - 1) % categoriasPartida.length;
                    const catElegida = categoriasPartida[idx];
                    nombreCategoria = catElegida.label || catElegida.name || catElegida;
                }
            }
            categoryLabelEl.innerText = nombreCategoria;
        }

        document.getElementById("multi-question-text").innerHTML = q.question;

        const container = document.getElementById("multi-answers-container");
        const feedback = document.getElementById("multi-feedback");
        const footer = document.getElementById("modal-footer-controls");

        container.innerHTML = "";
        feedback.innerHTML = "";
        footer.classList.add("d-none");

        // Renderizar las respuestas en forma de botones
        q.answers.forEach(answer => {
            const btn = document.createElement("button");
            btn.className = "btn btn-outline-primary text-start px-3 py-2 fw-semibold";
            btn.innerHTML = answer;

            if (!miTurno || q.answered) {
                btn.disabled = true;
            }

            // EVALUACIÓN VISUAL TRAS RESPONDER:
            if (q.answered) {
                const esLaQueSePulso = answer === q.selectedAnswer;
                const esLaCorrectaReal = answer === q.correctAnswer;

                if (esLaCorrectaReal) {
                    btn.classList.replace("btn-outline-primary", "btn-success");
                } else if (esLaQueSePulso) {
                    btn.classList.replace("btn-outline-primary", "btn-danger");
                }
            } else {
                btn.onclick = () => enviarRespuestaMulti(answer, btn);
            }

            container.appendChild(btn);
        });

        // REVELACIÓN DEL RESULTADO:
        if (q.answered) {
            const acierto = q.selectedAnswer === q.correctAnswer;
            feedback.className = acierto ? "text-success text-center mt-3 fw-bold fs-5" : "text-danger text-center mt-3 fw-bold fs-5";
            feedback.textContent = acierto ? "Correct!" : "Incorrect";

            if (miTurno) {
                footer.classList.remove("d-none");
                document.getElementById("btn-cerrar-modal").onclick = () => {
                    const headers = { 'Content-Type': 'application/json' };
                    if (config && config.csrf && config.csrf.header && config.csrf.value) {
                        headers[config.csrf.header] = config.csrf.value;
                    }

                    fetch(`/game/${cfg.gameCode}/close-question-modal`, {
                        method: 'POST',
                        headers: headers
                    });
                };
            }
        }

        bootstrapModalInstance.show();
    }

    function enviarRespuestaMulti(answer, botonPulsado) {
        // Bloqueamos los botones locales de inmediato para evitar clicks fantasmas
        const hijos = document.getElementById("multi-answers-container").children;
        Array.from(hijos).forEach(b => b.disabled = true);

        const headers = { 'Content-Type': 'application/json' };
        if (config && config.csrf && config.csrf.header && config.csrf.value) {
            headers[config.csrf.header] = config.csrf.value;
        }

        fetch(`/game/${cfg.gameCode}/submit-answer`, {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({
                answer: answer
            })
        })
            .then(res => res.json())
            .then(data => {
                if (data.status === "error") {
                    alert(data.message);
                    // Si el servidor da un error de validación, liberamos los botones
                    Array.from(hijos).forEach(b => b.disabled = false);
                }
            })
            .catch(err => {
                console.error("Error al enviar la respuesta:", err);
                Array.from(hijos).forEach(b => b.disabled = false);
            });
    }

    function renderizarFinDePartida(podium) {
        console.log("HEMOS ENTRADO")
        // 1. Si hay algún modal de pregunta abierto en cualquier pantalla, lo cerramos
        if (bootstrapModalInstance) {
            bootstrapModalInstance.hide();
        }

        // 2. Ocultamos el tablero y el panel de jugadores por completo
        const zonaJuego = document.getElementById("contenedor-juego-activo");
        if (zonaJuego) zonaJuego.classList.add("d-none");

        // 3. Mostramos el contenedor del podio final
        const zonaPodio = document.getElementById("contenedor-podio-final");
        if (zonaPodio) zonaPodio.classList.remove("d-none");

        // 4. Rellenamos la lista de jugadores con su desglose de puntos
        const listaPodioEl = document.getElementById("lista-podio");
        if (listaPodioEl) {
            listaPodioEl.innerHTML = ""; // Limpiamos residuos

            podium.forEach((player) => {
                const fila = document.createElement("div");

                // Estilo especial para los 3 primeros puestos usando clases de Bootstrap
                let claseMedalla = "bg-light";
                let iconoMedalla = "";

                if (player.gamePosition === 1) { claseMedalla = "bg-warning bg-opacity-25 border-warning"; iconoMedalla = "🥇"; }
                else if (player.gamePosition === 2) { claseMedalla = "bg-secondary bg-opacity-10"; iconoMedalla = "🥈"; }
                else if (player.gamePosition === 3) { claseMedalla = "bg-danger bg-opacity-10"; iconoMedalla = "🥉"; }

                fila.className = `list-group-item d-flex justify-content-between align-items-center p-3 mb-2 rounded shadow-sm ${claseMedalla}`;

                fila.innerHTML = `
                    <div class="text-start">
                        <span class="fs-4 me-2">${iconoMedalla}</span>
                        <strong class="fs-5 text-dark">${player.gamePosition}º - ${player.username}</strong>
                    </div>
                    <div class="text-end">
                        <span class="badge bg-primary rounded-pill fs-6 px-3">${player.totalEarned} pts totales</span>
                        <div class="small text-muted mt-1" style="font-size: 0.8rem;">
                            (${player.points} juego + ${player.bonusPoints} bonus)
                        </div>
                    </div>
                `;

                listaPodioEl.appendChild(fila);
            });
        }
    }

    // -------------------------------
    // HELPERS
    // -------------------------------
    function escapeHtml(str) {
        const d = document.createElement("div");
        d.textContent = str;
        return d.innerHTML;
    }

    // -------------------------------
    // PUBLIC API
    // -------------------------------
    return {
        init,
        joinLobby,
        startGame,
        leaveGame,
        kickPlayer,
        renderPlayers,
        setStatus,
        initBoard,
        loadPlayers
    };

})();