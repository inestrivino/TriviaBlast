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
        isHost: false
    };

    // -------------------------------
    // STATE
    // -------------------------------
    let wsClient = null;
    let lobbyTopic = null;
    let statusTimer = null;

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
        console.log("WS EVENT RECEIVED:", msg);
        if (!msg) return;

        switch (msg.type) {
            case "update":
                console.log(msg.players);
                if (msg.players) renderPlayers(msg.players);
                break;

            case "game_start":
                setStatus("Game starting...", "success");
                setTimeout(() => {
                    window.location.href =
                        `/game/multi_game/${cfg.gameCode}`;
                }, 800);
                break;
            case "pause":
                if (typeof onGamePaused === "function") onGamePaused(msg);
                break;

            case "resume":
                if (typeof onGameResumed === "function") onGameResumed(msg);
                break;
            case "player_kicked":
                if (msg.players) {
                    renderPlayers(msg.players);
                }
                if (msg.kickedPlayer === "all") {
                    setStatus(msg.message || "Host left the game", "danger");

                    setTimeout(() => {
                        window.location.href = "/";
                    }, 1500);

                    break;
                }
                if (Number(msg.kickedPlayer) === Number(cfg.currentUserId)) {
                    setStatus("You've been kicked", "danger");

                    setTimeout(() => {
                        window.location.href = "/";
                    }, 1500);
                }

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
                if (data.players) renderPlayers(data.players);
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
            const isHost = p.id === cfg.hostId;
            const isMatch = cfg.mode === "match";

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
            if (cfg.isHost && !isMe) {

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
<li class="list-group-item d-flex justify-content-between align-items-center"
    style="${style}">

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

        const draw = SVG().addTo('#tablero-container');

        const filas = 8;
        const columnas = 8;

        function redraw() {
            draw.clear();

            const size = Math.min(container.clientWidth, window.innerHeight * 0.7);
            container.style.height = size + "px";

            const cell = size / filas;

            draw.viewbox(0, 0, size, size);

            let grid = Array.from({ length: filas }, () =>
                Array(columnas).fill(false)
            );

            let x = 0, y = 0;
            let dx = 1, dy = 0;

            for (let i = 1; i <= filas * columnas; i++) {

                const isCorner =
                    (dx !== 0 && dy !== 0);

                draw.rect(cell - 2, cell - 2)
                    .move(x * cell + 1, y * cell + 1)
                    .fill('#f2f2f2')
                    .stroke({ width: 1, color: '#999' });

                draw.text(i.toString())
                    .font({ size: cell / 3, anchor: 'middle' })
                    .center(
                        x * cell + cell / 2,
                        y * cell + cell / 2
                    );

                grid[y][x] = true;

                if (dx === 1 && (x + dx >= columnas || grid[y][x + dx])) { dx = 0; dy = 1; }
                else if (dy === 1 && (y + dy >= filas || grid[y + dy][x])) { dx = -1; dy = 0; }
                else if (dx === -1 && (x + dx < 0 || grid[y][x + dx])) { dx = 0; dy = -1; }
                else if (dy === -1 && (y + dy < 0 || grid[y + dy][x])) { dx = 1; dy = 0; }

                x += dx;
                y += dy;
            }
        }

        redraw();
        window.addEventListener("resize", redraw);
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