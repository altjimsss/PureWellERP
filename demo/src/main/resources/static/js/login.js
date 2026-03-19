const signInBtn = document.querySelector("#sign-in-btn");
const signUpBtn = document.querySelector("#sign-up-btn");
const container = document.querySelector(".container");

if (signUpBtn) {
	signUpBtn.addEventListener("click", () => {
		container.classList.add("sign-up-mode");
	});
}

if (signInBtn) {
	signInBtn.addEventListener("click", () => {
		container.classList.remove("sign-up-mode");
	});
}
