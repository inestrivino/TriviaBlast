/**
* JAVASCRIPT DE LOBBY Y MULTIJUGADOR PRINCIPAL
* 
* Contiene toda la lógica del lado del cliente para la interfaz del lobby y la partida multijugador
* Está organizado en las siguientes secciones:
* - La declaración del struct cfg con valores útiles para la lógica que se reciben del frontend, el estado de websocket, el código del lobby/partida, etc
* - WEBSOCKET: Declaración de funcoines para inicializar el websocket, suscribirse y manejar los mensajes que reciba
* - PLAYERS RENDER: Muestra los jugadores en el lobby/partida de manera dinámica
* - ACCIONES DEL LOBBY: Funciones para unirse al lobby, salirse, echar, y la inicialización de los botones correspondientes
* - UI STATUS: Para mostrar mensajes de error o éxito en la interfaz
* - FUNCIONES DEL JUEGO: Para gestionar la renderización de elementos como el tablero, el dado, o el podio, 
*                        así como la renderización de preguntas y envío de respuestas.
* - ACCIONES DEL CHAT: Funciones para gestionar la renderización de mensajes, y el envío de estos
*/
"use strict";

window.GameClient = (() => {
    // -------------------------------
    // CONFIG: Datos importantes para la lógica
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
    // WEBSOCKET
    // -------------------------------
    //funcion para suscribirse el topic correspondiente al lobby/partida
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

    //funcion para manejar los mensajes recibidos por websocket segun su tipo
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

        //si el mensaje websocket es un text, entonces es un mensaje de chat y se muestra como tal
        if (data.text !== undefined) {
            agregarMensajeAlPanel(data);
            return;
        }

        //en caso de no ser un mensaje de chat
        switch (data.type) {
            //si la partida ha terminado se renderiza
            case "game_finished":
                renderizarFinDePartida(data.podium);
                break;

            //si se recibe una pregunta se recargan los jugadores, el tablero+dado y se abre el modal con la pregunta
            case "question":
                if (data.players) renderPlayers(data.players);
                if (data.gameState) currentGameState = data.gameState;

                initBoard();
                actualizarVisibilidadDado();

                if (data.gameState && data.gameState.activeQuestion) {
                    gestionarModalPregunta(data.gameState.activeQuestion);
                }
                break;

            //si se recibe un mensaje update se recargan los jugadores y el tablero+dado
            case "update":
                //TO DO : TODA ESTA SECCIÓN NO PODRÍA SER SIMPLEMENTE EL LAODPLAYERS?
                if (data.players) renderPlayers(data.players);
                if (data.gameState) currentGameState = data.gameState;

                initBoard();
                actualizarVisibilidadDado();

                //comprobamos si antes de re-actualizar nos encontrabamos en una pregunta, en cuyo caso la re-mostramos
                //esto es para evitar soft-blocks durante el juego si se recarga la pestaña
                if (data.gameState && data.gameState.activeQuestion) {
                    gestionarModalPregunta(data.gameState.activeQuestion);
                } else {
                    gestionarModalPregunta(null);
                }
                break;

            //si la partida acaba de empezar se redirige a los usuarios a la pantalla multi-game de la partida
            case "game_start":
                if (typeof setStatus === "function") setStatus("Game starting...", "success");
                setTimeout(() => {
                    window.location.href = `/game/multi_game/${cfg.gameCode}`;
                }, 800);
                break;

            //si un jugador ha sido echado se re-renderizan los jugadores
            //TO DO : ESTO TAMBIEN DEBERIA MOSTRARSE EN LA PARTIDA EN SI
            case "player_kicked":
                if (data.players) {
                    renderPlayers(data.players);
                    if (!window.currentGameState) window.currentGameState = {};
                    window.currentGameState.players = data.players;
                }
                //si han sido echados todos los jugadores a la vez (el host ha abandonado), se indica y se redirige
                if (data.kickedPlayer === "all") {
                    if (typeof setStatus === "function") setStatus(data.message || "Host left the game", "danger");
                    setTimeout(() => { window.location.href = "/"; }, 1500);
                    break;
                }
                //si el jugador que ha sido echado eres tu, se te indica y se te redirige
                if (Number(data.kickedPlayer) === Number(cfg.currentUserId)) {
                    if (typeof setStatus === "function") setStatus("You've been kicked", "danger");
                    setTimeout(() => { window.location.href = "/"; }, 1500);
                }
                loadPlayers();
                break;
        }
    }

    //funcion para inicializar la configuracion y suscribirse al websocket de la partida
    function init(config, ws) {
        cfg = { ...cfg, ...config };
        wsClient = ws;

        lobbyTopic = `/topic/${cfg.gameCode}`;

        if (wsClient) {
            subscribeWS();
        }

        //terminamos inicializando también los action listener de los botones
        bindUI();
    }

    // -------------------------------
    // PLAYERS RENDER
    // -------------------------------
    //funcion para cargar los jugadores en la partida y actualizar por ajax la interfaz
    function loadPlayers() {
        //llamada a la funcion para renderización por ajax
        return go(`/game/${cfg.gameCode}/players`, "GET")
            .then(data => {
                //actualizamos la lista de jugadores en la partida
                if (data.players) currentGameState.players = data.players || [];
                if (data.gameState) {
                    currentGameState = data.gameState;
                }
                //renderizamos los jugadores con la nueva lista disponible
                renderPlayers(currentGameState.players);

                //comprobamos si nos encontramos en vista de partida (en lugar de lobby)
                const isMatch = cfg.mode === "match";
                if (isMatch) {
                    //si estamos en vista de partida recargamos también el tablero y la visibilidad del dado
                    initBoard();
                    actualizarVisibilidadDado();

                    //comprobamos si había una pregunta pendiente de ser respondida antes de la reactualización
                    const infoTurno = currentGameState.turnInfo || {};
                    const preguntaPendiente = infoTurno.subState === "QUESTION_PENDING";

                    // si la había, la extraemos del gameState
                    const preguntaActiva = currentGameState.activeQuestion;

                    if (preguntaPendiente && preguntaActiva) {
                        console.log("Restaurando pregunta recuperada directamente desde el GameState...");

                        //la mostramos otra vez
                        setTimeout(() => {
                            gestionarModalPregunta(preguntaActiva);
                        }, 150);
                    } else {
                        gestionarModalPregunta(null);
                    }
                }
                return data;
            });
    }

    //funcion para manejar la renderizacion dinámica de jugadores en la partida
    function renderPlayers(players) {
        //obtenemos el div correspondiente
        const list = document.querySelector(".player-list, #player-list");
        if (!list) return;

        //si no hay jugadores se muestra una "carga"
        if (!players?.length) {
            list.innerHTML = `
            <li class="list-group-item text-muted">
                Waiting...
            </li>`;
            return;
        }

        //obtenemos el div que muestra la cantidad de jugadores
        const count = document.getElementById("player-count");
        if (count) {
            count.textContent = players.length;
        }

        //vamos a renderizar por cada jugador p en players
        list.innerHTML = players.map(p => {
            //primero queremos saber si p soy yo, si p es el host, si estamos en una partida, y si yo soy el host
            const isMe = p.id === cfg.currentUserId;
            const isHost = (p.id === cfg.hostId);
            const isMatch = cfg.mode === "match";
            const iAmHost = (cfg.currentUserId === cfg.hostId);

            //estilos especificos para el host o para el jugador propio
            const style = isHost
                ? "background: linear-gradient(90deg,#fff4d6,#fff)"
                : isMe
                    ? "background: linear-gradient(90deg,#dff3ff,#fff)"
                    : "";
            const icon = isHost
                ? `<i class="bi bi-patch-check-fill text-warning me-2"></i>`
                : "";

            //declaramos los botones de accion sobre el jugador p
            let actionBtn = "";
            //si p no soy yo pero soy host, debo poder echarle
            if (!isMe && iAmHost) {
                actionBtn = `
                    <button
                    class="btn btn-outline-danger btn-sm ms-2"
                    data-kick-id="${p.id}"
                    data-kick-name="${escapeHtml(p.username)}">
                    <i class="bi bi-person-x"></i>
                    </button>`;
            }

            //si p soy yo debo poder salirme
            else if (isMe) {
                actionBtn = `
                <button
                    class="btn btn-outline-secondary btn-sm ms-2"
                    id="leave-inline-btn">

                    <i class="bi bi-box-arrow-right"></i>

                </button>`;
            }

            //si estamos en una partida, deberían verse los puntos que llevo acumulados
            let pointsBadge = "";
            if (isMatch) {
                pointsBadge = `
                <span class="badge bg-primary">
                    ${p.points ?? 0} pts
                </span>`;
            }

            //renderizamos al usuario con su foto de perfil y nombre, sus estilos unicos si tiene, 
            // y los botones de accion que correspondan 
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

        //creamos el botón de empezar partida y lo desactivamos si aún no hay al menos dos jugadores
        const startBtn = document.getElementById("start-btn");
        if (startBtn) {
            startBtn.disabled = players.length < 2;
        }
    }

    // -------------------------------
    // ACCIONES DEL LOBBY
    // -------------------------------
    //unirse al lobby
    function joinLobby() {
        //llamada al backend para gestionar la union
        return go(`/game/${cfg.gameCode}/join`, "POST")
            .then(data => {
                //si la partida está llena se avisa
                if (data.status === "full") {
                    setStatus(data.message, "danger");
                    document.getElementById("leave-btn").textContent = "Go back";
                    return;
                }
                //si todo va bien, renderiza a los jugadores
                //TO DO: ESTO NO DEBERIA SER UN LOAD PLAYERS COMPLETO?
                if (data.players) renderPlayers(data.players);
                //y te da la bienvenida
                setStatus(`Welcome ${data.username}`, "success");
            })
            .catch(() => {
                setStatus("Connected (limited mode)", "warning");
            });
    }

    //empezar la partida
    function startGame(btn) {
        //cuando se hace click en el boton de empezar partida se desactiva y se cambia su texto
        if (!btn) return;
        btn.disabled = true;
        btn.textContent = "Starting...";

        //se llama al backend para comenzar la partida
        go(`/game/${cfg.gameCode}/start`, "POST")
            .then(d => {
                if (d.status === "success") {
                    //si hay exito se comienza la partida
                    setStatus("Starting game...", "success");
                } else {
                    //si hay error se muestra
                    setStatus(d.message || "Error", "danger");
                    btn.disabled = false;
                    btn.textContent = "Start game";
                }
            })
            .catch(() => {
                //si hay error se muestra
                setStatus("Error starting game", "danger");
                btn.disabled = false;
            });
    }

    //al abandonar el juego se llama a la funcion correspondiente de backend y se redirige al inicio
    function leaveGame() {
        return go(`/game/${cfg.gameCode}/leave`, "POST")
            .finally(() => {
                window.location.href = "/";
            });
    }

    //si se echa a un jugador se pide confirmación y se llama a la funcion del backend para manejarlo
    function kickPlayer(username, userId) {
        if (!confirm(`Kick ${username}?`)) return;

        go(`/game/${cfg.gameCode}/kick/${encodeURIComponent(userId)}`, "POST")
            .catch(() => setStatus("Error kicking player", "danger"));
    }

    //inicialización de botones para salir de la partida o expulsar
    let uiBound = false;
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
    // UI STATUS
    // -------------------------------
    //para mostrar errores o exito en un div en el frontend
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
    // FUNCIONES DEL JUEGO
    // -------------------------------
    //inicialización del tablero
    function initBoard() {
        const container = document.getElementById('tablero-container');
        if (!container || typeof SVG === "undefined") return;

        // Empezamos vaciando el código del tablero
        container.innerHTML = "";
        const draw = SVG().addTo('#tablero-container');

        // Paleta de colores para el tablero (arcoiris)
        const CATEGORY_COLORS = [
            '#E15759', // Rojo coral / Salmón oscuro
            '#F28E2B', // Naranja suave
            '#EDC948', // Amarillo mostaza claro (contrasta bien con blanco)
            '#59A14F', // Verde hierba suave
            '#4E79A7', // Azul clásico apagado
            '#B07AA1', // Lavanda / Violeta elegante
            '#FF9DA7', // Rosa pastel
        ];

        // recuperamos las categorias de la partida (envidad en las configuraciones)
        const categories = cfg.categories || [];
        // le asignamos un color a cada categoría
        // dos categorias pueden tener el mismo color, pero una categoria siempre tiene el mismo color
        const categoryColorMap = {};
        categories.forEach((cat, idx) => {
            const key = cat.name || cat.key || cat;
            categoryColorMap[key] = CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
        });

        //creación de un patrón "checkerboard" para el inicio y la meta
        function checkerPattern(group, size) {
            const half = size / 2;
            group.rect(half, half).move(0, 0).fill('#fff');
            group.rect(half, half).move(half, 0).fill('#000');
            group.rect(half, half).move(0, half).fill('#000');
            group.rect(half, half).move(half, half).fill('#fff');
        }

        //función interna para obtener la categoría de una casilla
        function getCategoryForPosition(pos) {
            if (!categories.length) return null;
            // (pos - 1) hace que la casilla 1 use la primera categoría elegida, la 2 la segunda, etc.
            return categories[(pos - 1) % categories.length];
        }

        //función interna para dibujar a los jugadores en el tablero
        function drawPlayers(cellGroup, pos, cellSize) {
            //se obtiene la lista de jugadores
            const players = currentGameState.players || [];
            const playersHere = players.filter(p => {
                //se colocan en la casilla correspondiente con su boardPosition
                let boardPos = p.boardPosition;
                if (boardPos < 1) boardPos = 0;
                if (boardPos > 64) boardPos = 65;
                return boardPos === pos;
            });

            //se dibuja el avatar
            const avatarSize = cellSize * 0.35;
            const positions = [
                { x: 0.15, y: 0.35 }, { x: 0.50, y: 0.35 },
                { x: 0.15, y: 0.65 }, { x: 0.50, y: 0.65 }
            ];
            //nota: en cada casilla se busca que haya hueco para los 4 jugadores que puede haber
            playersHere.slice(0, 4).forEach((p, idx) => {
                const offset = positions[idx];
                const ax = cellSize * offset.x;
                const ay = cellSize * offset.y;

                const clip = cellGroup.circle(avatarSize).move(ax, ay);

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

        //el tablero tiene 9 columnas y 8 filas
        const cols = 9;
        const rows = 8;

        //funcion interna para saber en qué coordenadas dibujar la casilla pos
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

        //funcion interna para redibujar el tablero
        function redraw() {
            draw.clear();

            //obtenemos el div para mostrar la categoría de una casilla
            const displayText = document.getElementById('categoria-display');
            const defaultText = "Pasa el ratón por una casilla para ver su categoría";

            //establecemos el tamaño de las casillas y del viewbox respecto al tamaño de la pantalla
            const size = Math.max(600, Math.min(container.clientWidth, window.innerHeight * 0.85));
            container.style.height = `${size}px`;
            const cell = size / cols;
            draw.viewbox(0, 0, size, cell * rows);

            //por cada una de las 66 casillas (de la 0 (salida) a la 65(meta))
            for (let i = 0; i <= 65; i++) {
                //se obtienen las coordenadas de la casilla
                const { x, y } = getBoardCoords(i);
                const px = x * cell;
                const py = y * cell;

                const group = draw.group().transform({ translate: [px, py] });

                //si la casilla es salida o meta se dibuja el checker pattern
                if (i === 0 || i === 65) {
                    checkerPattern(group, cell);
                    group.text(i === 0 ? "START" : "GOAL")
                        .font({ size: cell / 4, anchor: 'middle', weight: 'bold' })
                        .fill('#fff')
                        .stroke({ color: '#000', width: 1, linejoin: 'round' })
                        .center(cell / 2, cell / 2);

                    if (displayText) {
                        group.mouseover(() => {
                            displayText.innerText = i === 0 ? "Casilla de Salida (START)" : "¡Meta! (GOAL)";
                        });
                        group.mouseleave(() => {
                            displayText.innerText = defaultText;
                        });
                    }
                } else {
                    //se obtiene la categoría de la casilla
                    const category = getCategoryForPosition(i);
                    const categoryKey = category ? (category.name || category.key || category) : null;
                    const categoryLabel = category ? (category.label || categoryKey) : "Pregunta General";

                    //se dibuja el color de la casilla según su categoría
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
                        // Action listener para cuando el cursor entra en los límites de este grupo (casilla)
                        group.mouseover(() => {
                            //se muestra la categoría de la casilla
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

                //dibujamos a los jugadores
                drawPlayers(group, i, cell);
            }
        }

        //dibujamos el tablero una vez inicial
        redraw();
        //cada vez que se redimensione la pestaña se redibuja el tablero para ajustarse
        window.addEventListener('resize', redraw);

    }

    // Variable de control para no duplicar escuchadores del botón dado
    let dadoListenerAsignado = false;
    //funcion para actualizar si el dado es visible (es el turno del jugador) o no
    function actualizarVisibilidadDado() {
        //obtenemos el botón del dado
        const btnDado = document.getElementById("btn-tirar-dado");
        if (!btnDado) return;

        //obtenemos el estado del juego
        const state = currentGameState || {};

        //comprobamos si es el turno del jugador y mostramos el dado si es el turno del jugador
        const miTurno = Number(state.currentTurnPlayerId) === Number(cfg.currentUserId);
        if (miTurno) {
            btnDado.classList.remove("d-none");
        } else {
            btnDado.classList.add("d-none");
        }

        if (!dadoListenerAsignado) {
            //action listener para que cuando hacemos click en el botón se llame
            //al backend con el método roll dice
            btnDado.addEventListener("click", () => {
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

    // Instancia del modal de Bootstrap (solo puede haber uno a la vez)
    let bootstrapModalInstance = null;

    //función para gestionar el modal de pregunta en el modo multi
    function gestionarModalPregunta(activeQuestion) {
        //obtenemos el div del modal para la pregunta
        const modalEl = document.getElementById("questionModal");
        if (!modalEl) return;

        //si no hay ya un modal creado, lo creamos
        if (!bootstrapModalInstance) {
            bootstrapModalInstance = new bootstrap.Modal(modalEl);
        }

        //si no hay una pregunta activa cerramos el modal
        if (!activeQuestion) {
            bootstrapModalInstance.hide();
            return;
        }

        //obtenemos la pregunta activa, el estado interno del juego, si es el turno propio, y quién es el jugador activo
        const q = activeQuestion;
        const state = currentGameState || {};
        const miTurno = Number(state.currentTurnPlayerId) === Number(cfg.currentUserId);
        const jugadorActivo = (state.players || []).find(p => Number(p.id) === Number(state.currentTurnPlayerId));

        //indicamos el jugador activo
        document.getElementById("modal-player-title").innerText = `Turn for player: ${jugadorActivo ? jugadorActivo.username : 'Player'}`;
        //indicamos el resultado del dado
        const diceRollEl = document.getElementById("modal-dice-roll");
        if (diceRollEl) {
            diceRollEl.innerText = state.lastDiceRoll ? state.lastDiceRoll : "-";
        }
        //indicamos la categoria de la pregunta
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

        //escribimos la pregunta y obtenemos los divs para mostrar las posibles respuestas, el feedback y otros controles
        document.getElementById("multi-question-text").innerHTML = q.question;
        const container = document.getElementById("multi-answers-container");
        const feedback = document.getElementById("multi-feedback");
        const footer = document.getElementById("modal-footer-controls");
        container.innerHTML = "";
        feedback.innerHTML = "";
        footer.classList.add("d-none");

        //renderizamos por cada respuesta posible un botón con el texto de la respuesta
        q.answers.forEach(answer => {
            const btn = document.createElement("button");
            btn.className = "btn btn-outline-primary text-start px-3 py-2 fw-semibold";
            btn.innerHTML = answer;

            //si no es mi turno o la pregunta a sido respondida, los botones no están activos
            if (!miTurno || q.answered) {
                btn.disabled = true;
            }

            //si la pregunta ha sido respondida entonces se muestra el feedback y los botones cambian de color
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

        //si se responde la pregunta
        if (q.answered) {
            //comprobamos si se acertó y mostramos el texto de feedback
            const acierto = q.selectedAnswer === q.correctAnswer;
            feedback.className = acierto ? "text-success text-center mt-3 fw-bold fs-5" : "text-danger text-center mt-3 fw-bold fs-5";
            feedback.textContent = acierto ? "Correct!" : "Incorrect";

            //si es mi turno, se muestra además la opcion de cerrar el modal para continuar la partida
            if (miTurno) {
                footer.classList.remove("d-none");
                document.getElementById("btn-cerrar-modal").onclick = () => {
                    const headers = { 'Content-Type': 'application/json' };
                    if (config && config.csrf && config.csrf.header && config.csrf.value) {
                        headers[config.csrf.header] = config.csrf.value;
                    }

                    //si se cierra el modal se avisa al backend para que actualice el estado interno de la partida
                    fetch(`/game/${cfg.gameCode}/close-question-modal`, {
                        method: 'POST',
                        headers: headers
                    });
                };
            }
        }

        //se muestra el modal de pregunta
        bootstrapModalInstance.show();
    }

    //función para enviar la respuesta sobre la que se ha hecho click
    function enviarRespuestaMulti(answer) {
        // Bloqueamos los botones locales de inmediato para evitar clicks fantasmas
        const hijos = document.getElementById("multi-answers-container").children;
        Array.from(hijos).forEach(b => b.disabled = true);

        const headers = { 'Content-Type': 'application/json' };
        if (config && config.csrf && config.csrf.header && config.csrf.value) {
            headers[config.csrf.header] = config.csrf.value;
        }

        //se envia la respuesta elegida al backend para decidir si es correcta o no
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

    //funcion para cambiar lo que se muestra al acabar la partida
    function renderizarFinDePartida(podium) {
        //Si hay algún modal de pregunta abierto en cualquier pantalla, lo cerramos
        if (bootstrapModalInstance) {
            bootstrapModalInstance.hide();
        }

        //Ocultamos el tablero y el panel de jugadores
        const zonaJuego = document.getElementById("contenedor-juego-activo");
        if (zonaJuego) zonaJuego.classList.add("d-none");

        //Mostramos el contenedor del podio final
        const zonaPodio = document.getElementById("contenedor-podio-final");
        if (zonaPodio) zonaPodio.classList.remove("d-none");

        //tomamos el div con las lista del podio
        const listaPodioEl = document.getElementById("lista-podio");
        if (listaPodioEl) {
            listaPodioEl.innerHTML = "";
            //por cada jugador, mostramos su nombre, posición y puntos totales
            podium.forEach((player) => {
                const fila = document.createElement("div");
                // Estilo especial para los 3 primeros puestos con emoji único
                let claseMedalla = "bg-light";
                let iconoMedalla = "";
                if (player.gamePosition === 1) { claseMedalla = "bg-warning bg-opacity-25 border-warning"; iconoMedalla = "🥇"; }
                else if (player.gamePosition === 2) { claseMedalla = "bg-secondary bg-opacity-10"; iconoMedalla = "🥈"; }
                else if (player.gamePosition === 3) { claseMedalla = "bg-danger bg-opacity-10"; iconoMedalla = "🥉"; }
                //asignamos valores al div
                fila.className = `list-group-item d-flex justify-content-between align-items-center p-3 mb-2 rounded shadow-sm ${claseMedalla}`;

                //insertamos el html en el div
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
    // ACCIONES DEL CHAT
    // -------------------------------
    //funcion para cargar mensajes de una partida
    function loadMessages() {
        return go(`/game/${cfg.gameCode}/msg`, "GET")
            .then(mensajes => {
                const contenedor = document.getElementById("mensajes");
                if (!contenedor) return;

                //limpiamos el panel de mensajes
                contenedor.innerHTML = "";
                //por cada mensaje que recibimos, lo añadimos al panel
                mensajes.forEach(m => agregarMensajeAlPanel(m));
            })
            .catch(err => console.error("Error cargando mensajes del chat:", err));
    }

    //función para enviar un mensaje al chat
    function enviarMensajeChat() {
        //tomamos el input y el botón de enviar
        const input = document.getElementById("chat-input");
        const btn = document.getElementById("chat-send-btn");
        if (!input || !input.value.trim()) return;

        const texto = input.value.trim();
        // Vaciamos el input y ponemos el botón a disabled inmediatamente (evitar envios dobles)
        input.value = "";
        if (btn) btn.disabled = true;

        // Mandamos el mensaje al backend para ser insertado en la base de datos
        go(`/game/${cfg.gameCode}/msg`, "POST", { message: texto })
            .then(() => {
                //una vez procesado por el backend volvemos a permitir hacer envios
                if (btn) btn.disabled = false;
                input.focus();
            })
            .catch(err => {
                console.error("Error al enviar mensaje:", err);
                if (btn) btn.disabled = false;
            });
    }

    //función para agregar un mensaje al panel
    function agregarMensajeAlPanel(msg) {
        //obtenemos el contenedor de mensajes
        const contenedor = document.getElementById("mensajes");
        if (!contenedor) return;

        //creamos un div para el mensaje
        const divMsg = document.createElement("div");
        divMsg.className = "mb-2 p-2 rounded shadow-sm border-0";

        // Obtenemos la fecha (hora) del mensaje
        const fechaRaw = msg.sent || new Date();
        const d = new Date(fechaRaw);
        const horaFormateada = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        // Comparamos si el usuario que lo envió somos nosotros y estilizamos el div correspondientemente
        const esMio = msg.from === cfg.currentUsername;
        if (esMio) {
            divMsg.style.backgroundColor = "#e1f5fe";
            divMsg.style.marginLeft = "20px";
            divMsg.className += " text-end";
        } else {
            divMsg.style.backgroundColor = "#ffffff";
            divMsg.style.marginRight = "20px";
            divMsg.style.borderLeft = "4px solid #0d6efd";
        }

        // Creamos el html con los valores del mensaje y lo insertamos en el contenedor
        divMsg.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-1 text-muted fw-bold" style="font-size: 0.75rem;">
                <span class="text-primary">${escapeHtml(msg.from)}</span>
                <span style="font-size: 0.7rem; font-weight: normal;"><i class="bi bi-clock me-1"></i>${horaFormateada}</span>
            </div>
            <span class="d-block text-start text-dark" style="word-break: break-word; max-width: 100%; font-size: 0.9rem;">
                ${escapeHtml(msg.text)}
            </span>
        `;
        contenedor.appendChild(divMsg);

        // Auto-scroll
        contenedor.scrollTo({
            top: contenedor.scrollHeight,
            behavior: 'smooth'
        });
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
        loadPlayers,
        loadMessages,
        enviarMensajeChat,
        agregarMensajeAlPanel
    };

})();