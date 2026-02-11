const passwordForm = document.getElementById("passwordForm");
const oldPassword = document.getElementById("oldPassword");
const newPassword = document.getElementById("newPassword");
const togglePassword = document.getElementById("togglePassword");


newPassword.oninput = function () {
    if (newPassword.value.length < 6) {
        newPassword.classList.add("is-invalid");
        newPassword.classList.remove("is-valid");
    } else {
        newPassword.classList.remove("is-invalid");
        newPassword.classList.add("is-valid");
    }
};


togglePassword.onchange = function () {
    newPassword.type = this.checked ? "text" : "password";
};


passwordForm.onsubmit = function (e) {
    e.preventDefault();

    if (!oldPassword.value || !newPassword.value) {
        alert("Completa los dos campos");
        return;
    }

    alert("Contraseña cambiada 👍");
};
